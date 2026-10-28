package cc.huoyan.retail.learning;

import cc.huoyan.retail.checkout.MainActivity;

import static cc.huoyan.retail.common.EventBusModel.EVENT_CAMERA_INIT;
import static cc.huoyan.retail.common.EventBusModel.EVENT_CAMERA_STATUS_SUCCESS;
import static cc.huoyan.retail.common.FileUtil.corpRecognizeBitmap;
import static cc.huoyan.retail.common.FileUtil.cropBitmap;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.BaseActivity;
import cc.huoyan.retail.device.camera.AutoFitTextureView;
import cc.huoyan.retail.device.camera.CameraFrame;
import cc.huoyan.retail.device.camera.CameraController;
import cc.huoyan.retail.device.camera.NV21ToBitmap;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.common.EventBusModel;
import cc.huoyan.sdk.client.model.DetectBox;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.retail.goods.SdkClassMapping;
import cc.huoyan.retail.common.ui.DebounceClickListener;
import cc.huoyan.retail.common.ui.DialogUtil;
import cc.huoyan.retail.diagnostics.Logger;
import cc.huoyan.retail.device.camera.CameraUtils;
import cc.huoyan.retail.common.ui.ToastEx;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cc.huoyan.sdk.client.model.DetectResult;
import cc.huoyan.sdk.client.SdkImage;

/** 学习页 View：相机预览、控件与展示钩子；学习事务和反馈由 StudyPresenter 协调。 */
public abstract class StudyActivity extends BaseActivity implements StudyPresenter.View {
    /** 供列表 Presenter 和适配器渲染的图片集合，不保存学习事务状态。 */
    protected final List<StudyPicEntity> mStudyPicList = new ArrayList<>();
    /** 相机及检测预览日志保留原标签。 */
    private static final String LOG_TAG = "StudyActivity";
    /** 学习操作协调器，拥有业务状态和待反馈批次。 */
    private StudyPresenter study;
    /** 学习图片展示与删除按钮适配器。 */
    protected StudyPicAdapter studyPicAdapter;
    /** UVC 相机预览表面。 */
    private AutoFitTextureView surfaceView;
    /** 当前学习商品标题。 */
    private TextView selectedGoodsNameText;
    /** Presenter 已接受选择的展示快照；切换必须经 selectLearningGoods。 */
    protected GoodsEntity mSelectedGoods;
    /** 同一帧的像素和尺寸一起发布给检测线程，不依赖预览布局是否变化。 */
    private volatile CameraFrame latestFrame;
    /** 检测和学习共用串行队列，保持 SDK 请求不重叠；销毁时有序关闭。 */
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    /** 仅取消预览周期任务，暂停页面不撤销已提交学习。 */
    private java.util.concurrent.ScheduledFuture<?> detectionTask;
    /** 页面生命周期内持续检查学习资格，授权失效时立即禁用保存。 */
    private StudyAvailability availability;

    /** 共享相机管理器，销毁时移除本页监听。 */
    private CameraController cameraController;
    /** 页面实例独立的订阅键，旧页面销毁不能移除新订阅。 */
    private final String cameraListenerKey = getClass().getSimpleName() + "@" + System.identityHashCode(this);
    /** 预览帧专用转换器，检测结束后释放。 */
    private NV21ToBitmap mNv21ToBitmap;
    /** 检测框叠加视图。 */
    private StudyDrawView drawView;

    /** 绑定相机视图与学习交互，学习操作由 Presenter 调度。 */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        study = new StudyPresenter(this, new StudyLearningSource(getApplicationContext()), scheduledExecutor,
                new android.os.Handler(android.os.Looper.getMainLooper())::post);
        RecyclerView picRecycler = findViewById(R.id.picRecycler);
        surfaceView = findViewById(R.id.imageView);
        selectedGoodsNameText = findViewById(R.id.selectedGoodsName);
        selectedGoodsNameText.setText(getString(R.string.study_select_goods_prompt));
        TextView saveStudyText = findViewById(R.id.saveStudy);
        availability = new androidx.lifecycle.ViewModelProvider(this).get(StudyAvailability.class);
        availability.ready.observe(this, ready -> {
            saveStudyText.setEnabled(Boolean.TRUE.equals(ready));
            saveStudyText.setAlpha(Boolean.TRUE.equals(ready) ? 1f : 0.45f);
        });
        TextView clearStudyText = findViewById(R.id.clearStudy);
        TextView captureText = findViewById(R.id.capture);
        drawView = findViewById(R.id.drawView);
        findViewById(R.id.quit).setOnClickListener(v -> finish());

        // 初始化图片
        studyPicAdapter = createPicAdapter();
        studyPicAdapter.setList(mStudyPicList);
        picRecycler.setLayoutManager(new GridLayoutManager(mContext, 5));
        picRecycler.setAdapter(studyPicAdapter);
        studyPicAdapter.setListener(position -> {
            try {
                StudyPicEntity studyPicEntity = mStudyPicList.get(position);
                study.remove(studyPicEntity);
            } catch (Exception e) {
                Toast.makeText(StudyActivity.this, getString(R.string.toast_clear_study_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
        saveStudyText.setOnClickListener(new DebounceClickListener() {
            /** 显示保存进度并提交待反馈批次。 */
            @Override
            public void onDebouncedClick(View v) {
                DialogUtil.showLoadingDialog(StudyActivity.this, getString(R.string.dialog_saving_study));
                if (Boolean.TRUE.equals(availability.ready.getValue())) study.save();
                else DialogUtil.dismissLoadingDialog();
            }
        });
        clearStudyText.setOnClickListener(new DebounceClickListener() {
            /** 有学习目标时弹出整组遗忘确认。 */
            @Override
            public void onDebouncedClick(View v) {
                if (mSelectedGoods == null) {
                    Toast.makeText(StudyActivity.this, getString(R.string.tip_no_goods_selected), Toast.LENGTH_SHORT).show();
                    return;
                }
                WarningDialog confirmDialog = new WarningDialog();
                confirmDialog.setListener(new WarningDialog.WarningDialogListener() {
                    /** 确认整组遗忘后展示进度并提交当前商品。 */
                    @Override
                    public void onConfirm() {
                        DialogUtil.showLoadingDialog(StudyActivity.this, getString(R.string.dialog_forgetting));
                        study.clear();
                    }

                    /** 取消确认时不提交业务操作。 */
                    @Override
                    public void onCancel() {

                    }
                });
                confirmDialog.show(getSupportFragmentManager(), "confirm");
            }
        });
        captureText.setOnClickListener(new DebounceClickListener() {
            /** 在点击时克隆相机帧，防止驱动复用缓冲后像素变化。 */
            @Override
            public void onDebouncedClick(View v) {
                CameraFrame frame = latestFrame;
                if (!cameraController.isCurrent(frame)) frame = null;
                byte[] captureBytes = frame == null || frame.getNv() == null ? null : frame.getNv().clone();
                study.capture(captureBytes, frame == null ? 0 : frame.getWidth(), frame == null ? 0 : frame.getHeight());
            }
        });
        mNv21ToBitmap = new NV21ToBitmap(this);
        cameraController = CameraController.getInstance();
        cameraController.addListener(cameraListenerKey, new CameraController.FrameListener() {
            /** 整体发布相机帧与尺寸，并按需调整预览比例。 */
            @Override
            public void onFrame(CameraFrame entity) {
                latestFrame = entity;
                if (entity.getWidth() != surfaceView.getRatioWidth() || entity.getHeight() != surfaceView.getRatioHeight()) {
                    postMainHandler(() -> {
                        surfaceView.setAspectRatio(entity.getWidth(), entity.getHeight());
                    });
                }
            }
        });
        CameraUtils.getInstance().setPreview(surfaceView);
    }

    /** 为学习页面提供学习图片适配器。 */
    protected abstract StudyPicAdapter createPicAdapter();

    /** 直接切换学习目标，保留拍照互斥和待提交反馈的收尾行为。 */
    protected void selectLearningGoods(GoodsEntity target) {
        study.select(target);
    }

    /** 渲染 Presenter 接受的新选择，历史图片仍由列表 Presenter 加载。 */
    @Override public void showLearningTarget(GoodsEntity target) {
        mSelectedGoods = target;
        selectedGoodsNameText.setText(getString(R.string.study_plu_name_format, target.getPlu(), target.getName()));
        if (SdkClassMapping.clazzFromPlu(mSelectedGoods.getPlu()) == null) {
            // 非 v2 可学习形态（非无前导零正整数 PLU）：选中放行，拍照/遗忘时报原因
            Logger.logDebug(LOG_TAG, "[Biz] study_clazz=invalid plu=" + mSelectedGoods.getPlu());
        }
        mStudyPicList.clear();
        studyPicAdapter.setList(mStudyPicList);
        onSelectedGoodsChanged();
    }

    /** 拍照回执返回前不退出，避免用户在处理过程中误切换页面。 */
    @Override public void finish() {
        if (study != null && !study.finish()) return;
        super.finish();
    }

    /** 商品选择变化后加载已有样本。 */
    protected abstract void onSelectedGoodsChanged();

    /** 学习数据变更成功后通知页面刷新展示，不额外提交 SDK 操作。 */
    protected abstract void onLearningDataChanged(GoodsEntity goods, boolean captured);

    /** 保存收尾时更新图片展示。 */
    protected abstract void onLearningSaved();

    /** 单图删除成功后的展示刷新入口。 */
    protected abstract void onLearningPictureRemoved(StudyPicEntity picture);

    /** 将本次学习回执加入显示列表。 */
    protected abstract void showCapturedPictures(List<StudyPicEntity> pictures);

    /** 解绑学习视图，已提交任务完成后释放两套转换资源。 */
    @Override
    protected void onDestroy() {
        if (study != null) study.detach();
        DialogUtil.dismissLoadingDialog();
        super.onDestroy();
        if (detectionTask != null) detectionTask.cancel(false);
        if (mNv21ToBitmap != null) scheduledExecutor.execute(mNv21ToBitmap::release);
        scheduledExecutor.shutdown();
        if (cameraController != null) { cameraController.removeListener(cameraListenerKey); cameraController.unbind(surfaceView); }
    }

    /** 展示拍照互斥提示，保持现有反馈入口。 */
    @Override public void busy() { ToastEx.createToast(this, getString(R.string.learning_busy)); }

    /** 请求被接受后显示拍照进度。 */
    @Override public void captureStarted() {
        DialogUtil.showLoadingDialog(this, getString(R.string.dialog_taking_photo));
    }

    /** 展示已成功学习的样本，图片缺失也保留 UID 记录。 */
    @Override public void captured(GoodsEntity goods, List<StudyPicEntity> pictures) {
        DialogUtil.dismissLoadingDialog(); showCapturedPictures(pictures);
        onLearningDataChanged(goods, true); Log.d(LOG_TAG, "学习成功");
    }

    /** 保存只更新展示，SDK 写入和反馈提交由 Presenter 完成。 */
    @Override public void saved(GoodsEntity goods) {
        DialogUtil.dismissLoadingDialog(); onLearningSaved(); onLearningDataChanged(goods, false);
        Log.d(LOG_TAG, "保存成功"); ToastEx.showSuccessToast(this, getString(R.string.toast_save_success));
    }

    /** 整组遗忘保持原清除成功和保存成功提示。 */
    @Override public void forgotten(GoodsEntity goods) {
        Toast.makeText(this, R.string.toast_clear_success, Toast.LENGTH_SHORT).show(); saved(goods);
    }

    /** 同一商品的历史查询可能替换实体，始终按 UID 移除显示项。 */
    @Override public void removed(StudyPicEntity picture) {
        for (int i = 0; i < mStudyPicList.size(); i++) {
            if (mStudyPicList.get(i).getUid() == picture.getUid()) {
                mStudyPicList.remove(i); studyPicAdapter.removeAt(i); break;
            }
        }
        onLearningPictureRemoved(picture);
        Log.d(LOG_TAG, "清除记录成功");
        Toast.makeText(this, R.string.toast_clear_records_success, Toast.LENGTH_SHORT).show();
    }

    /** 停止进度展示并沿用各操作的本地化失败文案。 */
    @Override public void failed(int message, String reason) {
        DialogUtil.dismissLoadingDialog();
        if (message == R.string.tip_no_goods_selected) ToastEx.showFailureToast(this, getString(message));
        else Toast.makeText(this, getString(message, reason), Toast.LENGTH_LONG).show();
    }

    /** 恢复相机和周期检测，不重建在途学习队列。 */
    @Override
    protected void onResume() {
        super.onResume();
        cameraController.bind(surfaceView);
        availability.start();
        // 学习域通道连接幂等发起（对齐 MainActivity 的
        // checkSdkChannelStatus connect 语义；检测/学习调用按 isConnected 自行丢帧重试）
        cc.huoyan.sdk.client.session.SdkConnection.await(SdkChannelManager.getInstance().getClient(), 0, 100, () -> true);
        initDetection();
    }

    /**
     * 初始化检测定位（迁移 SDK 2.0 通道 detect，AIDL/WS 双通道同语义）。
     *
     * <p>v1 发送识别区裁剪帧（{@code bitmapToByteArray} needCorp=true 内部裁剪）经
     * WS v1 SDK_DETECTION 异步回执画框；当前裁剪 Bitmap 按通道编码（AIDL RGB888、WS JPEG90），
     * 经 detect 同步回执，boxes
     * （发送帧画布绝对坐标）经 {@link SdkClassMapping} 归一后走既有
     * {@code setDetectionEntities} 画框链路（invertPoints 坐标系约定不变）。
     * scheduleWithFixedDelay 单线程串行天然覆盖阻塞往返（在途不叠加新帧）；
     * 通道未连接/回执异常丢弃本帧（v1 发送失败同样仅记日志，桥内自动重连后续跑）；
     * 循环首部巡检 {@link #watchCameraClosed()}（相机被上层关死后自动重开），
     * 启动时（onResume 路径）相机已关则跳过观察窗立即重开（2026-08-27 反馈①）。</p>
     */
    private void initDetection() {
        // 进入页面主动立即重开相机（2026-08-27 用户反馈①）：开发机「不保留活动」下
        // MainActivity 销毁 stopCamera 可能在本页 onResume 前已把相机关死，仅靠
        // watchCameraClosed 须先观察满 1.5s 观察窗才重开（进入学习页黑 1-2s）。
        // onResume 时机不存在 setCamaraSize 分辨率协商瞬态关开，直接跳过观察窗；
        // 记入节流时间戳防重开在途期间看门狗重复发起；运行中关闭仍由看门狗兜底。
        long now = SystemClock.elapsedRealtime();
        boolean cameraOpen = cameraController.isCameraOpen();
        boolean cameraRestarting = cameraController.isCameraRestarting();
        if (CameraUtils.shouldReopenCamera(cameraOpen, false, lastCameraRestartMs, now, CAMERA_RESTART_THROTTLE_MS)) {
            if (cameraRestarting) {
                // 重开在途（节流已满）：不重复发起，防周期性关开振荡（2026-08-27 用户反馈①）
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "[Biz] camera_restart=skipped_inflight");
                }
            } else {
                lastCameraRestartMs = now;
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "[Biz] camera_restart=entry_closed");
                }
                CameraUtils.getInstance().setPreview(surfaceView);
            }
        }
        // 观察窗基线随检测会话重置（每 onResume 重启预览，规避上一会话的
        // 陈旧首见时刻让看门狗跳过观察窗立即重启）
        cameraClosedSinceMs = 0L;
        detectionTask = scheduledExecutor.scheduleWithFixedDelay(new Runnable() {
            /** 在串行队列检测一帧，结束后无条件回收裁剪图和原图。 */
            @Override
            public void run() {
                watchCameraClosed();
                CameraFrame frame = latestFrame;
                if (!cameraController.isCurrent(frame)) frame = null;
                if (frame == null) return;
                byte[] frameBytes = frame.getNv();
                int frameWidth = frame.getWidth();
                int frameHeight = frame.getHeight();
                if (frameBytes == null || frameWidth <= 0 || frameHeight <= 0) {
                    return;
                }
                Bitmap bitmap = null;
                Bitmap cropBitmap = null;
                try {
                    bitmap = mNv21ToBitmap.nv21ToBitmap(frameBytes, frameWidth, frameHeight);
                    cropBitmap = corpRecognizeBitmap(bitmap);
                    sendDetectionPreviewRequest(cropBitmap, frame);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "sendSDK: ", e);
                } finally {
                    if (cropBitmap != null && cropBitmap != bitmap) {
                        cropBitmap.recycle();
                    }
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                }
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    /** 检测断连日志最小间隔，避免周期预览刷屏。 */
    private static final long DETECT_DROP_LOG_INTERVAL_MS = 3000L;
    /** 上次断连日志时刻，用于抑制每帧重复输出。 */
    private volatile long lastDetectDropLogMs;

    /** 相机连续关闭超过此窗口才尝试重开，避开分辨率协商。 */
    private static final long CAMERA_REOPEN_GRACE_MS = 1500L;
    /** 相机重启的最小间隔，避免协商期间重复重开。 */
    private static final long CAMERA_RESTART_THROTTLE_MS = 5000L;
    /** 当前连续关闭区间的起点，打开相机后清零。 */
    private volatile long cameraClosedSinceMs;
    /** 最近重启请求时间，用于重开节流。 */
    private volatile long lastCameraRestartMs;

    /** 后台发送识别区帧，成功后在主线程刷新检测框。 */
    private void sendDetectionPreviewRequest(Bitmap cropBitmap, CameraFrame frame) {
        if (!cameraController.isCurrent(frame)) return;
        HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
        if (!bridge.isConnected()) {
            // 通道未连接：丢弃本帧。根因修复（2026-08-27 C50 实测）：连接此前仅
            // onResume 发起一次，SDK AIDL 绑定就绪前（实测最长 18 分钟）本页
            // 静默丢帧、画框消失且无任何提示。此处节流补发 connect（幂等：AIDL
            // 在途退避时可提前重绑、WS 已启动时空操作）+ 打点，通道恢复后自动续跑
            long now = SystemClock.elapsedRealtime();
            if (now - lastDetectDropLogMs >= DETECT_DROP_LOG_INTERVAL_MS) {
                lastDetectDropLogMs = now;
                cc.huoyan.sdk.client.session.SdkConnection.await(bridge, 0, 100, () -> true);
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "[Biz] detect_drop=disconnected");
                }
            }
            return;
        }
        long startedAt = SystemClock.elapsedRealtime();
        SdkImage imageRequest = bridge.image(cropBitmap);
        long encodedAt = SystemClock.elapsedRealtime();
        if (imageRequest == null) {
            return;
        }
        DetectResult result = bridge.detect(imageRequest);
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "[Biz] detect_timing channel=" + bridge.getClass().getSimpleName()
                    + " format=" + (imageRequest.getFormat() == SdkImage.FORMAT_JPEG ? "jpeg90" : "rgb")
                    + " payloadBytes=" + imageRequest.getSize()
                    + " imageEncodeMs=" + (encodedAt - startedAt)
                    + " totalMs=" + (SystemClock.elapsedRealtime() - startedAt));
        }
        if (result == null || result.getCode() != 0) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Biz] detect_v2=fail code=" + (result == null ? "no_receipt" : result.getCode())
                        + (result == null || result.getReason() == null ? "" : " reason=" + result.getReason()));
            }
            return;
        }
        List<DetectBox> entities = result == null || result.getCode() != 0 ? java.util.Collections.emptyList() : result.getBoxes();
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "[Biz] detect_v2=ok boxes=" + entities.size());
        }
        postMainHandler(() -> {
            if (cameraController.isCurrent(frame)) drawView.setDetectionEntities(entities, frame.getWidth(), frame.getHeight());
        });
    }

    /**
     * 相机关闭看门狗（2026-08-27 C50 实测补充修复，detect 循环内巡检）：本机开启
     * 「不保留活动」（always_finish_activities=1）时，进入学习页后 MainActivity
     * 随即销毁 → {@code stopCamera()} 关闭相机——晚于本页 onCreate（当时相机仍开，
     * {@code setPreview} 仅挂 surface），此后无任何页面重开相机：预览黑屏、onFrame
     * 停更、检测/拍照持续使用关闭前最后一帧（商品摆上秤台也无法识别/学习）。
     * 关闭持续超过观察窗（跳过瞬态关开）且距上次重启超过节流间隔时，经
     * {@code setPreview} 重开（相机已关分支内部即 startCameraPreview，
     * onCameraOpen 重挂 surface 并恢复帧回调）。执行器 onPause 即停，页面
     * 不可见期间不会重启相机。
     */
    private void watchCameraClosed() {
        long now = SystemClock.elapsedRealtime();
        if (cameraController.isCameraOpen()) {
            cameraClosedSinceMs = 0L;
            return;
        }
        if (cameraClosedSinceMs == 0L) {
            cameraClosedSinceMs = now;
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Biz] camera_closed=observed");
            }
            return;
        }
        if (now - cameraClosedSinceMs < CAMERA_REOPEN_GRACE_MS
                || now - lastCameraRestartMs < CAMERA_RESTART_THROTTLE_MS) {
            return;
        }
        if (cameraController.isCameraRestarting()) {
            // 重开在途：不重复发起（防重复 new CameraHelper 周期性关开振荡，2026-08-27 用户反馈①）
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Biz] camera_restart=skipped_inflight");
            }
            return;
        }
        lastCameraRestartMs = now;
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "[Biz] camera_restart=closed_for_" + (now - cameraClosedSinceMs) + "ms");
        }
        postMainHandler(() -> CameraUtils.getInstance().setPreview(surfaceView));
    }


    /** 暂停周期预览，已提交学习仍在串行队列中完成。 */
    @Override
    protected void onPause() {
        availability.stop();
        cameraController.unbind(surfaceView);
        super.onPause();
        if (detectionTask != null) detectionTask.cancel(false);
    }


    /** 收到相机就绪事件时恢复预览表面的可见性。 */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(EventBusModel event) {
        if (event.getCode() == EVENT_CAMERA_INIT) {
            // 相机
            switch (event.getStatus()) {
                case EVENT_CAMERA_STATUS_SUCCESS:
                    surfaceView.setVisibility(View.VISIBLE);
                    break;
            }

        }
    }
}
