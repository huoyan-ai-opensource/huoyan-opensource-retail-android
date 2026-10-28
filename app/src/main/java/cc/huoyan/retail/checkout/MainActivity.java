package cc.huoyan.retail.checkout;

import cc.huoyan.retail.home.HomeActivity;
import cc.huoyan.retail.checkout.integration.CheckoutGoodsLoader;
import cc.huoyan.retail.checkout.integration.CheckoutOutput;
import cc.huoyan.retail.checkout.domain.CheckoutSession;
import cc.huoyan.retail.checkout.domain.CheckoutState;
import cc.huoyan.retail.recognition.FrameChangeGate;
import cc.huoyan.retail.recognition.RecognitionSensitivity;
import cc.huoyan.retail.recognition.RecognitionLoop;
import cc.huoyan.retail.recognition.RecognitionResultGate;
import cc.huoyan.retail.settings.SettingsPageActivity;

import static cc.huoyan.retail.common.EventBusModel.EVENT_CAMERA_INIT;
import static cc.huoyan.retail.common.EventBusModel.EVENT_CAMERA_STATUS_ERROR;
import static cc.huoyan.retail.common.EventBusModel.EVENT_CAMERA_STATUS_SUCCESS;
import static cc.huoyan.retail.common.EventBusModel.EVENT_CAMERA_STATUS_SWITCH;
import static cc.huoyan.retail.common.EventBusModel.EVENT_GOODS_TASK;
import static cc.huoyan.retail.common.EventBusModel.EVENT_GOODS_TASK_FALSE;
import static cc.huoyan.retail.common.EventBusModel.EVENT_WS_CONNECT;
import static cc.huoyan.retail.common.EventBusModel.EVENT_WS_CONNECT_CLOSE;
import static cc.huoyan.retail.common.EventBusModel.EVENT_WS_CONNECT_OPENED;
import static cc.huoyan.retail.data.SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL;
import static cc.huoyan.retail.data.SPManager.SP_ACLAS_SCALE_PORT;
import static cc.huoyan.retail.data.SPManager.SP_SHOP_NAME;
import static cc.huoyan.retail.data.SPManager.SP_STABLE_RECOGNITION;
import static cc.huoyan.retail.common.FileUtil.corpRecognizeBitmap;
import static cc.huoyan.retail.common.FileUtil.cropBitmap;
import static cc.huoyan.retail.common.FileUtil.invertPoints;
import static cc.huoyan.retail.common.PermissionUtil.verifyBasePermissions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkManager;

import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.BaseActivity;
import cc.huoyan.retail.device.camera.AutoFitTextureView;
import cc.huoyan.retail.device.camera.CameraController;
import cc.huoyan.retail.device.camera.CameraFrame;
import cc.huoyan.retail.device.camera.NV21ToBitmap;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.device.camera.RecognizeAreaEntity;
import cc.huoyan.retail.device.scale.AclasWeightSnapshot;
import cc.huoyan.retail.common.EventBusModel;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.checkout.CheckoutRecognition;
import cc.huoyan.retail.device.scale.AclasScaleManager;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.pricing.PricingMode;
import cc.huoyan.retail.receiver.DebugInputReceiver;
import cc.huoyan.retail.recognition.RecognitionMessages;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.session.SdkStartupController;
import cc.huoyan.retail.device.camera.CameraUtils;
import cc.huoyan.retail.device.camera.CompareImage;
import cc.huoyan.retail.common.DisplayFormatUtil;
import cc.huoyan.retail.diagnostics.Logger;
import cc.huoyan.retail.common.MainHandler;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.retail.common.VersionUtil;
import cc.huoyan.retail.device.VoicePromptManager;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import cc.huoyan.sdk.client.model.RecognitionItem;
import cc.huoyan.sdk.client.model.RecognizeResult;
import cc.huoyan.sdk.client.model.StatusSnapshot;

public class MainActivity extends BaseActivity implements CheckoutPresenter.View {
    private static final String LOG_TAG = "MainActivity";
    private static final String BUSINESS_TIMING_LOG_TAG = "BusinessTiming";
    private static final long STABLE_RESULT_TIMEOUT_MS = 3000L;
    /** debug 注入无相机帧时的占位帧尺寸（识别预览绘制坐标系兜底，仅 debug 链路） */
    private static final int DEBUG_PREVIEW_FRAME_WIDTH = 1280;
    private static final int DEBUG_PREVIEW_FRAME_HEIGHT = 720;
    private static final long SCALE_OPEN_TIMEOUT_MS = 5000L;

    private CheckoutPresenter presenter;
    private volatile CameraFrame latestCameraFrame;
    private int saveWidth;
    private int saveHeight;
    private ImageView recognizeIcon;
    private TextView recognizeText;
    private View recognizeStatus;
    private TextView retryRecognize;
    private TextView versionTv;
    private AutoFitTextureView surfaceView;
    private final RecognitionLoop recognitionLoop = new RecognitionLoop(this::processLatestDetectFrame);
    private RecognizeResultAdapter resultAdapter;
    private RecognizeResultPreviewView previewView;
    /** 底部三项汇总，主线程按同一会话快照更新。 */
    private TextView summaryKinds, summaryCount, summaryAmount;
    /** 空态引导及水印，不参与商品行布局。 */
    private ImageView trayGuide;
    private TextView emptyTitle, emptyHint;
    private RecognitionWatermarkView resultWatermark;
    /** 当前引导显示模式，避免每次重量刷新重启动画。 */
    private int guideMode = -1;
    /** 顶部皮重及毛重；没有设备回执时显示未知。 */
    private TextView tareWeightTv, grossWeightTv;
    /** 最近一次设备读数，仅在当前连接有效。 */
    private AclasWeightSnapshot latestScaleSnapshot;
    /** 自动去皮的页面级等待弹窗，保留待提交实例以避免重复添加。 */
    private cc.huoyan.retail.common.ui.LoadingDialog automaticTareLoading;
    /** 结果确认的页面级等待弹窗；暂停或输出结束时关闭。 */
    private cc.huoyan.retail.common.ui.LoadingDialog confirmationLoading;
    /** 连续三次点击版本号的时间窗口。 */
    private long versionTapAt;
    private int versionTapCount;
    private TextView modifyGoods;
    private View emptyPanel;
    private volatile CheckoutRecognition detectWsResp;
    /** 仅比较主识别项（包含数量），相似候选变化不影响结果判定。 */
    private final RecognitionResultGate recognitionResultGate = new RecognitionResultGate();
    private final Runnable recognitionResultTimeoutRunnable = () -> {
        if (!presenter.state().recognitionLocked && presenter.state().recognitionActive
                && !presenter.state().hasStableWeight && recognitionResultGate.timedOut(SystemClock.elapsedRealtime())
                && hasDetectResults(detectWsResp)) {
            completeRecognition(detectWsResp);
        }
    };
    private TextView shopNameTv;
    private TextView weightTv;

    private View connectLine;
    private View countWeightTypeLayout;

    private TextView confirmSale;
    private boolean activityResumed;
    private BroadcastReceiver debugRecognizeReceiver;
    /** debug 批量识别注入的计件确认顺序；真实识别帧始终为空 */
    private volatile List<String> pendingDebugRecognizePlus = Collections.emptyList();
    private volatile CheckoutRecognition pendingDebugRecognizeResp;
    /** debug 批量注入确认完成标志：确认后暂停真实识别，防止真实摄像头帧覆盖注入购物车 */
    private volatile boolean pendingDebugRecognizeBatch;
    /** 相邻帧变化启动一轮识别；会话清理与相机线程通过同步方法交接。 */
    private final FrameChangeGate frameChangeGate = new FrameChangeGate();
    /** 识别线程最近已消费的相机帧，所有模式下均避免重复发送同一帧。 */
    private CameraFrame requestedFrame;
    /** 相机回调中的上一帧，用于识别摄像头代次变化。 */
    private CameraFrame comparedFrame;
    /** 已保存的画面变化开关，主线程更新、相机线程读取。 */
    private volatile boolean stableRecognition;
    /** 已保存的汉明距离阈值，主线程更新、相机线程读取。 */
    private volatile int frameChangeThreshold;
    /** 计件确认键盘 DialogFragment tag（是否在显示 = 识别暂停以等待数量确认） */
    private static final String COUNT_KEYBOARD_TAG = "mainRecognizeCountKeyboard";
    /** 称重商品修改重量弹窗 DialogFragment tag */
    private static final String WEIGHT_INPUT_TAG = "mainRecognizeWeightInput";
    private CameraController cameraController;
    /** 页面实例独立的订阅键，旧页面销毁不能移除新订阅。 */
    private final String cameraListenerKey = getClass().getSimpleName() + "@" + System.identityHashCode(this);
    private NV21ToBitmap mNv21ToBitmap;
    private MainDrawView drawView;
    /** SDK 已连接且 sdkState=READY、modelReady=true 后才允许发识别。 */
    private volatile boolean sdkRecognitionReady;
    /** 最近一次 getStatus() 对识别页状态标识的明确结果；0 表示暂未取得阻断状态。 */
    private volatile int sdkStatusTextRes;
    private final SdkStartupController sdkStartup = SdkChannelManager.getInstance().newStartupController();
    private volatile long lastDetectRequestAt;
    private volatile long stableScaleAt;
    private volatile long saleFlowStartedAt;
    private boolean saleFlowDurationLogged;
    private volatile Bitmap stableRecognizeFrame;
    private final Object stableRecognizeFrameLock = new Object();
    private final Map<String, Integer> goodsColorMap = new LinkedHashMap<>();
    private CheckoutOutput checkoutOutput;
    private final AclasScaleManager aclasScaleManager = new AclasScaleManager();
    private final VoicePromptManager voicePromptManager = new VoicePromptManager();
    private final Runnable scaleOpenTimeoutRunnable = () -> {
        if (!presenter.state().scaleConnected) {
            updateScaleConnectionState(false, false);
            updateRecognizeStatus(R.string.status_scale_connect_timeout, R.drawable.bg_red_round);
        }
    };
    /** 非称重确认完成后保留结果十秒，主线程到期重开识别。 */
    private long printResumeAt;
    /** 页面暂停时移除任务，恢复时按原截止时间重新安排。 */
    private final Runnable printResumeRunnable = () -> {
        if (!activityResumed || presenter.state().weighingEnabled
                || presenter.state().phase != CheckoutState.Phase.SOLD_OR_REPORTED) return;
        printResumeAt = 0;
        restartRecognition();
    };
    private final Runnable stableResultTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (presenter.state().recognitionLocked || !presenter.state().detecting || !presenter.state().hasStableWeight) {
                return;
            }
            CheckoutRecognition latestResp = detectWsResp;
            if (hasDetectResults(latestResp)) {
                lockStableRecognition(latestResp);
                return;
            }
            updateRecognizeStatus(R.string.status_recognition_timeout_continuing, R.drawable.bg_orange_round);
        }
    };

    @Override
    protected int initContentView() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkoutOutput = new CheckoutOutput(this);
        presenter = new CheckoutPresenter(this, new CheckoutGoodsLoader(runnable -> MainHandler.get().post(runnable)),
                new CheckoutPresenter.Output() {
                    /** 主线程固定图片与保留、删除结果，后台按提交时的开关执行打印及反馈。 */
                    @Override public void submit(cc.huoyan.retail.checkout.domain.SalePrintJob job, boolean printEnabled,
                            CheckoutRecognition response, List<GoodsVo> rows, List<GoodsVo> deletedRows,
                            Consumer<Exception> completion) {
                        checkoutOutput.submit(job, printEnabled,
                                response, rows, deletedRows, copyStableRecognizeFrame(), completion);
                    }
                    /** 全部删除仍保留本轮原图，提交无小票的删除反馈。 */
                    @Override public void submitDeletion(CheckoutRecognition response, List<GoodsVo> deletedRows) {
                        checkoutOutput.submitDeletion(response, deletedRows, copyStableRecognizeFrame());
                    }
                });

        surfaceView = findViewById(R.id.surfaceView);
        mNv21ToBitmap = new NV21ToBitmap(this);
        recognizeStatus = findViewById(R.id.recognizeStatus);
        recognizeIcon = findViewById(R.id.recognizeIcon);
        recognizeText = findViewById(R.id.recognizeText);
        retryRecognize = findViewById(R.id.retryRecognize);
        retryRecognize.setOnClickListener(v -> restartRecognition());
        shopNameTv = findViewById(R.id.shopName);
        drawView = findViewById(R.id.drawView);
        previewView = findViewById(R.id.previewView);
        summaryKinds = findViewById(R.id.summaryKinds);
        summaryCount = findViewById(R.id.summaryCount);
        summaryAmount = findViewById(R.id.summaryAmount);
        trayGuide = findViewById(R.id.trayGuide);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyHint = findViewById(R.id.emptyHint);
        resultWatermark = findViewById(R.id.resultWatermark);
        tareWeightTv = findViewById(R.id.tareWeight);
        grossWeightTv = findViewById(R.id.grossWeight);
        modifyGoods = findViewById(R.id.modifyGoods);
        emptyPanel = findViewById(R.id.emptyPanel);
        weightTv = findViewById(R.id.weightTv);

        connectLine = findViewById(R.id.connectLine);
        countWeightTypeLayout = findViewById(R.id.countWeightTypeLayout);
        presenter.setWeighingEnabled(ModuleConfig.isWeighingEnabled(this));
        applyWeighingModuleVisibility();
        registerDebugRecognizeReceiver();

        confirmSale = findViewById(R.id.confirm);
        voicePromptManager.init(this);
        recognizeStatus.setOnClickListener(v -> new SdkStatusDialog().show(getSupportFragmentManager(), "sdkStatus"));
        for (int id : new int[]{R.id.netWeightCard, R.id.tareWeightCard, R.id.grossWeightCard})
            findViewById(id).setOnClickListener(v -> showScaleDialog());
        versionTv = findViewById(R.id.version);
        versionTv.setOnClickListener(view -> onVersionTap());
        try {
            versionTv.setText(getString(R.string.version_format, VersionUtil.getVersionName(mContext)));
        } catch (PackageManager.NameNotFoundException e) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Toast] " + getString(R.string.toast_get_version_failed, e.getMessage()));
            }
            Toast.makeText(mContext, getString(R.string.toast_get_version_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
        RecyclerView resultRecycler = findViewById(R.id.resultRecycler);
        resultAdapter = new RecognizeResultAdapter();
        resultAdapter.setOnGoodsVoActionListener(new RecognizeResultAdapter.OnGoodsVoActionListener() {
            @Override
            public void onChangeCount(GoodsVo goodsVo) {
                presenter.editQuantity(goodsVo);
            }

            @Override
            public void onPreviewVisibleChanged(GoodsVo goodsVo) {
                presenter.toggleRow(goodsVo);
            }
        });
        resultAdapter.setOnDeleteGoodsClickListener(presenter::deleteRow);
        resultRecycler.setLayoutManager(new LinearLayoutManager(mContext));
        resultRecycler.setAdapter(resultAdapter);
        previewView.setOnDetectBoxClickListener((goodsVo, detectItem) -> presenter.toggleItem(detectItem));

        modifyGoods.setOnClickListener(v -> presenter.modifySelected());
        confirmSale.setOnClickListener(v -> presenter.confirmResult(ModuleConfig.isPrintEnabled(this)));
        getSupportFragmentManager().setFragmentResultListener("exitVerified", this,
                (key, result) -> returnToHome());
        presenter.render();
        cameraController = CameraController.getInstance();
        verifyBasePermissions(this);
    }

    /** 退出页面时立即解除展示引用，避免在销毁前收到打印完成提示。 */
    @Override public void finish() {
        if (presenter != null) presenter.detach();
        super.finish();
    }

    /** 只为已完成的非称重单据安排恢复，避免旧任务清空新单。 */
    private void schedulePrintResume() {
        MainHandler.removeCallback(printResumeRunnable);
        if (activityResumed && printResumeAt > 0)
            MainHandler.postDelay(printResumeRunnable, Math.max(0, printResumeAt - SystemClock.elapsedRealtime()));
    }

    private void returnToHome() {
        HomeActivity.open(this);
        finish();
    }

    /** 系统返回键与导航按钮一致，结束本次收银并返回首页。 */
    @Override public void onBackPressed() {
        requestExit();
    }

    /** 首页重复进入时丢弃旧清单，重新创建识别会话。 */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("freshCheckout", false)) restartRecognition();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        schedulePrintResume();
        sdkRecognitionReady = false;
        checkSdkChannelStatus();
        String spShopName = SPManager.getInstance().getString(SP_SHOP_NAME, getString(R.string.default_shop_name));
        shopNameTv.setText(spShopName);
        presenter.setDeletionEnabled(SPManager.getInstance().getBoolean(SPManager.SP_DELETE_RECOGNITION_RESULT));
        stableRecognition = SPManager.getInstance().getBoolean(SP_STABLE_RECOGNITION);
        frameChangeThreshold = RecognitionSensitivity.threshold(SPManager.getInstance()
                .getInt(SP_RECOGNITION_SENSITIVITY_LEVEL, RecognitionSensitivity.DEFAULT_LEVEL));
        latestCameraFrame = null;
        frameChangeGate.reset();
        cameraController.addListener(cameraListenerKey, entity -> {
            CheckoutState state = presenter.state();
            if (!state.recognitionLocked && state.recognitionActive) {
                boolean accepted = true;
                if (FrameChangeGate.enabled(stableRecognition, state.weighingEnabled)) {
                    if (comparedFrame == null || !cameraController.isCurrent(comparedFrame)) frameChangeGate.reset();
                    comparedFrame = entity;
                    // 变化只启动本轮；触发后的静止新帧仍须继续识别以完成结果确认。
                    accepted = frameChangeGate.accept(CompareImage.frameHash(entity), frameChangeThreshold);
                }
                if (accepted) {
                    latestCameraFrame = entity;
                    recognitionLoop.wakeUp();
                }
            }
            if (saveWidth != entity.getWidth() || saveHeight != entity.getHeight()) {
                saveWidth = entity.getWidth();
                saveHeight = entity.getHeight();
                postMainHandler(() -> surfaceView.setAspectRatio(saveWidth, saveHeight));
            }
        });
        CameraUtils.getInstance().setPreview(surfaceView);
        refreshWeighingModuleState();
        presenter.render();
        if (presenter.state().recognitionLocked) {
            // 清单保存人工数量及商品选择，不能从检测框数量重新构建。
            presenter.refreshRows();
            if (presenter.needsGoodsReload() && hasDetectResults(detectWsResp)) presenter.loadGoods(detectWsResp);
            updatePreviewFrame();
            if (presenter.state().weighingEnabled) openScale();
            return;
        }
        cameraController.resumePreview();
        if (presenter.state().weighingEnabled) {
            updateRecognizeStatus(R.string.status_wait_weighing, R.drawable.bg_orange_round);
            openScale();
        } else {
            // 持续识别（决策 2）：weighing 关闭时无称重会话，直接启动识别循环
            sdkResumeInit();
            if (sdkRecognitionReady) startDetect();
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        showAutomaticTareLoading(false);
        showConfirmationLoading(false);
        MainHandler.removeCallback(printResumeRunnable);
        presenter.pause();
        sdkStartup.stop();
        sdkRecognitionReady = false;
        sdkStatusTextRes = 0;
        cameraController.pausePreview();
        // 功能页返回时保留已锁定清单及人工修改，仅停止设备与识别任务。
        stopDetect();
        resetStableResultTimeout();
        resetRecognitionResultWindow();
        super.onPause();
        closeScale();
        cameraController.removeListener(cameraListenerKey);
    }

    @Override
    protected void onDestroy() {
        unregisterDebugRecognizeReceiver();
        showConfirmationLoading(false);
        presenter.detach();
        super.onDestroy();
        WorkManager.getInstance(mContext).cancelAllWork();
        closeScale();
        aclasScaleManager.release();
        voicePromptManager.release();
        MainHandler.removeCallback(scaleOpenTimeoutRunnable);
        MainHandler.removeCallback(stableResultTimeoutRunnable);
        stopDetect();
        recognitionLoop.close();
        replaceStableRecognizeFrame(null);
        if (mNv21ToBitmap != null) {
            mNv21ToBitmap.release();
        }
        cameraController.removeListener(cameraListenerKey);
        CameraController.getInstance().unbind(surfaceView);
    }

    private void sdkResumeInit() {
        if (presenter.state().recognitionLocked) {
            updateRecognizeStatus(R.string.status_recognition_stable, R.drawable.bg_green_round);
            return;
        }
        if (SdkChannelManager.getInstance().getClient().isConnected()) {
            updateRecognizeStatus(R.string.status_sdk_connected, R.drawable.bg_green_round);
        } else {
            updateRecognizeStatus(R.string.status_sdk_disconnected, R.drawable.bg_red_round);
        }
    }

    /** 复用首页的 SDK 启动流程，以模型就绪状态控制识别。 */
    private void checkSdkChannelStatus() {
        sdkStartup.start((initCode, status, modelReady) -> {
            sdkRecognitionReady = modelReady;
            sdkStatusTextRes = resolveSdkChannelIssueTextRes(initCode, status);
            if (!modelReady) {
                updateRecognizeStatus(sdkStatusTextRes, R.drawable.bg_red_round);
            } else {
                sdkStatusTextRes = 0;
                sdkResumeInit();
                if (!presenter.state().weighingEnabled && presenter.state().recognitionActive) startDetect();
                // 称重轮次可能已在等待模型；就绪事件主动唤醒，不依赖下一张相机帧。
                recognitionLoop.wakeUp();
            }
        });
    }

    /** 将连接结果及 sdkState 映射为提示；reason 仅诊断，不参与状态推断。 */
    static int resolveSdkChannelIssueTextRes(int initCode, StatusSnapshot status) {
        switch (cc.huoyan.sdk.client.status.SdkStatus.issue(initCode, status)) {
            case STOPPED: return R.string.status_sdk_stopped;
            case CLEANUP_PENDING: return R.string.status_sdk_cleanup_pending;
            case NOT_INIT: return R.string.status_sdk_uninitialized;
            case INITIALIZING: return R.string.status_sdk_initializing;
            case NOT_ACTIVATED: return R.string.status_sdk_not_activated;
            case MODEL_PREPARING: return R.string.status_sdk_model_preparing;
            case MODEL_NOT_READY: return R.string.status_sdk_model_not_ready;
            case KEY_ROTATION_PENDING: return R.string.status_sdk_key_rotation_pending;
            case KEY_ROTATION_REJECTED: return R.string.status_sdk_key_rotation_rejected;
            case KEY_ROTATION_RATE_LIMITED: return R.string.status_sdk_key_rotation_rate_limited;
            case KEY_ROTATION_RESTORING: return R.string.status_sdk_key_rotation_restoring;
            case CERTIFICATE_EXPIRED: return R.string.status_sdk_certificate_expired;
            case CERTIFICATE_INVALID: return R.string.status_sdk_certificate_invalid;
            case BACKEND_UNAVAILABLE: return R.string.status_sdk_backend_unavailable;
            case STARTUP_FAILED: return R.string.status_sdk_startup_failed;
            case DISABLED: return R.string.status_sdk_disabled;
            case NONE: return 0;
            case NOT_CONNECTED: return R.string.status_sdk_not_started;
            case STATUS_UNAVAILABLE: return R.string.status_sdk_status_unavailable;
            default: return R.string.status_sdk_status_unavailable;
        }
    }

    private void startDetect() {
        sdkResumeInit();
        recognitionLoop.start();
    }

    private void stopDetect() {
        resetRecognitionResultWindow();
        resetStableResultTimeout();
        recognitionLoop.stop();
    }

    private RecognitionLoop.Outcome processLatestDetectFrame(BooleanSupplier isCurrent) {
        if (!isCurrent.getAsBoolean() || !sdkRecognitionReady
                || presenter.state().recognitionLocked || !presenter.state().recognitionActive) {
            return RecognitionLoop.Outcome.IDLE;
        }
        // 上次处理结束后才读取摄像头最新帧，不保留待识别帧队列。
        CameraFrame currentFrame = latestCameraFrame;
        if (!cameraController.isCurrent(currentFrame)) return RecognitionLoop.Outcome.IDLE;
        if (currentFrame == null || currentFrame.getNv() == null
                || currentFrame.getWidth() <= 0 || currentFrame.getHeight() <= 0) {
            return RecognitionLoop.Outcome.IDLE;
        }
        if (requestedFrame == currentFrame) return RecognitionLoop.Outcome.IDLE;
        return processDetectFrame(currentFrame, () -> isCurrent.getAsBoolean() && cameraController.isCurrent(currentFrame));
    }

    private RecognitionLoop.Outcome processDetectFrame(CameraFrame currentFrame, BooleanSupplier isCurrent) {
        long frameStartAt = SystemClock.elapsedRealtime();
        Bitmap bitmap = null;
        Bitmap cropBitmap = null;
        if (presenter.state().recognitionLocked || !presenter.state().recognitionActive) {
            return RecognitionLoop.Outcome.IDLE;
        }
        try {
            bitmap = mNv21ToBitmap.nv21ToBitmap(currentFrame.getNv(), currentFrame.getWidth(), currentFrame.getHeight());
            long convertedAt = SystemClock.elapsedRealtime();
            cropBitmap = corpRecognizeBitmap(bitmap);
            long croppedAt = SystemClock.elapsedRealtime();
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Biz] recognize_prepare_timing frame=" + frameStartAt
                        + " convertMs=" + (convertedAt - frameStartAt)
                        + " cropMs=" + (croppedAt - convertedAt)
                        + " prepareMs=" + (SystemClock.elapsedRealtime() - croppedAt));
            }
            RecognitionLoop.Outcome outcome = sendDetectRequest(cropBitmap, bitmap, frameStartAt, isCurrent);
            // 未就绪或已失效的帧没有发出请求，不提前标记为已消费。
            if (outcome != RecognitionLoop.Outcome.IDLE) requestedFrame = currentFrame;
            return outcome;
        } catch (Exception e) {
            Log.e(LOG_TAG, "processDetectFrame: ", e);
            if (isCurrent.getAsBoolean()) {
                resetStableResultTimeout();
                resetRecognitionResultWindow();
                updateRecognizeStatus(R.string.status_recognition_failed, R.drawable.bg_red_round);
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "[Biz] recognize_loop=paused reason=exception");
                }
            }
            return RecognitionLoop.Outcome.ERROR;
        } finally {
            recycleBitmap(cropBitmap);
            if (bitmap != cropBitmap) {
                recycleBitmap(bitmap);
            }
        }
    }

    /**
     * 识别请求发送（迁移 SDK 2.0 通道 recognize，AIDL/WS
     * 双通道同语义）：图像载体由当前桥构造，WS 可直接从裁剪位图编码 JPEG，
     * AIDL 使用共享内存或小图内联；回执同步
     * 直接组合 JAR 回执与页面选择状态后进入锁定/刷新链路
     * （{@link #handleDetectSuccess}）。rotate=0：NV21→Bitmap 帧已转正，v1 JPEG
     * 链路同样不带旋转。通道未连接/在途断线/载体构造失败时丢弃本帧并提示，
     * <b>不回退 WS v1 旧链路</b>（阶段 F 决策）。同步阻塞调用（AIDL Binder 事务/
     * WS future 等待），在 recognitionLoop 单线程执行，新帧事件合并后取最新帧续发；
     * 错误暂停续发，停止或重启后的旧回执丢弃。
     *
     * @param cropBitmap    识别区裁剪位图；编码后尺寸和方向不变
     * @param sourceBitmap  完整预览帧（识别预览/反馈抓图用，可 null）
     * @param frameStartAt  帧处理起始时间（耗时打点）
     */
    private RecognitionLoop.Outcome sendDetectRequest(Bitmap cropBitmap,
                                   Bitmap sourceBitmap, long frameStartAt, BooleanSupplier isCurrent) {
        if (!isCurrent.getAsBoolean() || presenter.state().recognitionLocked || !presenter.state().recognitionActive) {
            return RecognitionLoop.Outcome.IDLE;
        }
        if (!sdkRecognitionReady) {
            // SDK 尚未完成初始化或模型加载时，识别循环只保留帧，不发起识别请求。
            return RecognitionLoop.Outcome.IDLE;
        }
        long sendStartAt = SystemClock.elapsedRealtime();
        HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
        if (!bridge.isConnected()) {
            // 通道未连接：暂停本轮识别，等待用户重新识别。
            lastDetectRequestAt = sendStartAt;
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Biz] recognize_v2=fail code=disconnected");
            }
            postMainHandler(() -> {
                if (!isCurrent.getAsBoolean() || presenter.state().recognitionLocked || !presenter.state().recognitionActive) return;
                resetStableResultTimeout();
                resetRecognitionResultWindow();
                updateRecognizeStatus(R.string.status_recognition_failed, R.drawable.bg_red_round);
            });
            return RecognitionLoop.Outcome.ERROR;
        }
        int frameWidth = cropBitmap.getWidth();
        int frameHeight = cropBitmap.getHeight();
        RecognizeResult result = bridge.recognize(cropBitmap);
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "[Biz] recognize_response=" + result);
        // 回执守卫（对齐 v1 SDK_DETECT 分支）：称重会话已结束的迟到回执丢弃；
        // 已锁定稳定结果（含 debug 注入）时后续回执不再覆盖
        if (!isCurrent.getAsBoolean() || !presenter.state().recognitionActive || presenter.state().recognitionLocked) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Biz] recognize_v2=fail code=stale_receipt");
            }
            return RecognitionLoop.Outcome.IDLE;
        }
        long sendFinishAt = SystemClock.elapsedRealtime();
        lastDetectRequestAt = sendFinishAt;
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "detectRequest: totalPrepareMs=" + (sendStartAt - frameStartAt)
                + ", sendSdkMs=" + (sendFinishAt - sendStartAt)
                + ", totalMs=" + (sendFinishAt - frameStartAt)
                + ", frame=" + frameStartAt
                + ", frameSize=" + frameWidth + "x" + frameHeight);
        if (result == null) {
            // 通道层未回执（发送失败/在途断线）：暂停，避免连续重发。
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Biz] recognize_v2=fail code=no_receipt");
            }
            postMainHandler(() -> {
                if (!isCurrent.getAsBoolean() || presenter.state().recognitionLocked || !presenter.state().recognitionActive) return;
                resetStableResultTimeout();
                resetRecognitionResultWindow();
                updateRecognizeStatus(R.string.status_recognition_failed, R.drawable.bg_red_round);
            });
            return RecognitionLoop.Outcome.ERROR;
        }
        if (result.getCode() != 0) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Biz] recognize_v2=fail code=" + result.getCode()
                        + " reason=" + result.getReason());
            }
            postMainHandler(() -> {
                if (!isCurrent.getAsBoolean() || presenter.state().recognitionLocked || !presenter.state().recognitionActive) return;
                handleDetectError(result.getCode());
                resetStableResultTimeout();
            });
            return RecognitionLoop.Outcome.ERROR;
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "[Biz] recognize_v2=ok items=" + result.getItems().size()
                    + " candidates=" + countV2Candidates(result));
        }
        long mapStartedAt = SystemClock.elapsedRealtime();
        CheckoutRecognition response = new CheckoutRecognition(result);
        long mappedAt = SystemClock.elapsedRealtime();
        Bitmap responseFrame = sourceBitmap == null || result.getItems().isEmpty() ? null : sourceBitmap.copy(
                sourceBitmap.getConfig() == null ? Bitmap.Config.ARGB_8888 : sourceBitmap.getConfig(), false);
        long postedAt = SystemClock.elapsedRealtime();
        postMainHandler(() -> {
            long dispatchedAt = SystemClock.elapsedRealtime();
            if (!isCurrent.getAsBoolean() || presenter.state().recognitionLocked || !presenter.state().recognitionActive) {
                recycleBitmap(responseFrame);
                return;
            }
            replaceStableRecognizeFrame(responseFrame);
            handleDetectSuccess(response);
            if (BuildConfig.DEBUG) {
                long handledAt = SystemClock.elapsedRealtime();
                Log.d(LOG_TAG, "[Biz] recognize_result_timing frame=" + frameStartAt
                        + " mapMs=" + (mappedAt - mapStartedAt)
                        + " previewCopyMs=" + (postedAt - mappedAt)
                        + " mainQueueMs=" + (dispatchedAt - postedAt)
                        + " handleMs=" + (handledAt - dispatchedAt)
                        + " frameToHandledMs=" + (handledAt - frameStartAt)
                        + " previewCopied=" + (responseFrame != null));
            }
        });
        return RecognitionLoop.Outcome.SUCCESS;
    }

    /** v2 回执同框落选候选总数（[Biz] candidates=N 打点）。 */
    private static int countV2Candidates(RecognizeResult result) {
        int count = 0;
        for (RecognitionItem item : result.getItems()) {
            if (item != null && item.getCandidates() != null) {
                count += item.getCandidates().size();
            }
        }
        return count;
    }

    /** 主线程处理回执，与超时锁定串行，保证锁定结果及图片属于同一帧。 */
    private void handleDetectSuccess(CheckoutRecognition wsResp) {
        detectWsResp = wsResp;
        String setKey = wsResp == null ? null : wsResp.setKey();
        long now = SystemClock.elapsedRealtime();
        boolean lockNow = recognitionResultGate.accept(setKey, now, presenter.state().hasStableWeight);
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "[Biz] recognize_stable=" + (lockNow ? 1 : 0) + " set=" + setKey);
        }
        if (lockNow) {
            completeRecognition(wsResp);
            return;
        }
        presenter.loadGoods(detectWsResp);
        MainHandler.removeCallback(recognitionResultTimeoutRunnable);
        if (setKey == null) {
            updateRecognizeStatus(R.string.status_no_detection, R.drawable.bg_orange_round);
        } else {
            handleUnstableDetectResponse();
            if (!presenter.state().hasStableWeight) {
                MainHandler.postDelay(recognitionResultTimeoutRunnable, recognitionResultGate.remaining(now));
            }
        }
    }

    private void completeRecognition(CheckoutRecognition response) {
        lockStableRecognition(response);
        postMainHandler(() -> ToastEx.showSuccessToast(mContext, getString(R.string.toast_recognition_done)));
    }

    private void resetRecognitionResultWindow() {
        recognitionResultGate.reset();
        MainHandler.removeCallback(recognitionResultTimeoutRunnable);
    }

    /**
     * 识别错误回执统一处理（v2 错误码经 {@link RecognitionMessages#errorTextRes}
     * 映射既有 status_* 文案，清结果/刷新/超时复位行为对齐 v1 错误分支）。
     *
     * @param code v2 回执错误码（非 0；含 LOCAL_DISCONNECTED）
     */
    private void handleDetectError(int code) {
        resetStableResultTimeoutIfNotArmed();
        resetRecognitionResultWindow();
        detectWsResp = null;
        presenter.loadGoods(detectWsResp);
        updateRecognizeStatus(RecognitionMessages.errorTextRes(code), R.drawable.bg_red_round);
    }

    private void replaceStableRecognizeFrame(Bitmap newBitmap) {
        final Bitmap previousBitmap;
        synchronized (stableRecognizeFrameLock) {
            previousBitmap = stableRecognizeFrame;
            stableRecognizeFrame = newBitmap;
        }
        postMainHandler(() -> {
            if (!isFinishing() && !isDestroyed()) {
                updatePreviewFrame();
            }
            if (previousBitmap != null && previousBitmap != newBitmap && !previousBitmap.isRecycled()) {
                synchronized (stableRecognizeFrameLock) {
                    if (!previousBitmap.isRecycled()) {
                        previousBitmap.recycle();
                    }
                }
            }
        });
    }

    private void restartRecognition() {
        MainHandler.removeCallback(printResumeRunnable);
        printResumeAt = 0;
        if (!presenter.restart()) return;
        if (presenter.state().detecting) startSaleRecognition();
        else {
            showEmptySale();
            if (!presenter.state().weighingEnabled) startDetect();
        }
    }

    private void openScale() {
        presenter.scaleDisconnected();
        updateScaleConnectionState(false, false);
        MainHandler.removeCallback(scaleOpenTimeoutRunnable);
        MainHandler.postDelay(scaleOpenTimeoutRunnable, SCALE_OPEN_TIMEOUT_MS);
        String configuredPort = SPManager.getInstance().getString(SP_ACLAS_SCALE_PORT);
        aclasScaleManager.open(configuredPort, new AclasScaleManager.Listener() {
            @Override
            public void onConnected(String port) {
                postMainHandler(() -> {
                    if (!activityResumed) return;
                    presenter.scaleConnected();
                    MainHandler.removeCallback(scaleOpenTimeoutRunnable);
                    updateScaleConnectionState(true, false);
                    updateRecognizeStatus(R.string.status_wait_weighing, R.drawable.bg_orange_round);
                    Logger.logDebug(LOG_TAG, "Aclas scale connected: " + port);
                });
            }

            @Override
            public void onWeightRead(AclasWeightSnapshot snapshot) {
                postMainHandler(() -> handleWeightRead(snapshot));
            }

            @Override
            public void onError(String message) {
                postMainHandler(() -> {
                    if (!activityResumed) return;
                    handleScaleUnavailable();
                    updateScaleConnectionState(false, false);
                    updateRecognizeStatus(R.string.status_scale_error, R.drawable.bg_red_round);
                    Logger.logWarn(LOG_TAG, "Aclas scale: " + message);
                });
            }
        });
    }

    private void handleScaleUnavailable() {
        latestScaleSnapshot = null;
        updateScaleConnectionState(false, false);
        renderScaleReadings();
        presenter.scaleDisconnected();
        resetStableResultTimeout();
        if (isDestroyed()) return;
        if (activityResumed && !presenter.state().recognitionLocked && presenter.state().recognitionActive && hasDetectResults(detectWsResp)) {
            MainHandler.removeCallback(recognitionResultTimeoutRunnable);
            MainHandler.postDelay(recognitionResultTimeoutRunnable,
                    recognitionResultGate.remaining(SystemClock.elapsedRealtime()));
        }
    }

    private void closeScale() {
        MainHandler.removeCallback(scaleOpenTimeoutRunnable);
        handleScaleUnavailable();
        aclasScaleManager.close();
    }

    private void handleWeightRead(AclasWeightSnapshot snapshot) {
        if (!activityResumed || !presenter.state().weighingEnabled) return;
        if (snapshot == null) {
            handleScaleUnavailable();
            updateScaleConnectionState(false, false);
            return;
        }
        latestScaleSnapshot = snapshot;
        renderScaleReadings();
        CheckoutSession.WeightEvent event = presenter.onWeight(snapshot.netKg(), snapshot.tareKg(), snapshot.isStable());
        CheckoutState state = presenter.state();
        weightTv.setText(DisplayFormatUtil.formatWeightKg(state.currentWeightKg.doubleValue(), displayLocale()));

        updateScaleConnectionState(true, snapshot.isStable() && state.currentWeightKg.signum() >= 0);
        switch (event) {
            case STARTED:
            case RESTARTED:
                startSaleRecognition();
                break;
            case CLEARED:
            case NEGATIVE:
                showEmptySale();
                if (event == CheckoutSession.WeightEvent.NEGATIVE)
                    updateRecognizeStatus(R.string.status_weight_error, R.drawable.bg_red_round);
                return;
            case BECAME_STABLE:
                if (!state.recognitionLocked) armStableResultTimeout();
                break;
            case UNSTABLE:
                resetStableResultTimeout();
                if (!state.recognitionLocked && state.recognitionActive && hasDetectResults(detectWsResp)) {
                    MainHandler.removeCallback(recognitionResultTimeoutRunnable);
                    MainHandler.postDelay(recognitionResultTimeoutRunnable,
                            recognitionResultGate.remaining(SystemClock.elapsedRealtime()));
                }
                break;
            default:
                break;
        }
        presenter.renderSaleStatus();
        presenter.render();
    }

    private void startSaleRecognition() {
        saleFlowStartedAt = SystemClock.elapsedRealtime();
        saleFlowDurationLogged = false;
        clearRecognitionData();
        startDetect();
        logSimulatedDuration("下一单识别启动耗时", randomDemoDuration(1L, 31L));
    }

    private void showEmptySale() {
        stopDetect();
        clearRecognitionData();
        saleFlowStartedAt = 0L;
        saleFlowDurationLogged = false;
        CheckoutState state = presenter.state();
        if (state.weighingEnabled) {
            updateRecognizeStatus(state.scaleConnected ? R.string.status_wait_weighing : R.string.status_scale_disconnected,
                    state.scaleConnected ? R.drawable.bg_orange_round : R.drawable.bg_red_round);
        } else {
            sdkResumeInit();
        }
        presenter.render();
    }

    private void clearRecognitionData() {
        resetStableResultTimeout();
        detectWsResp = null;
        resetRecognitionResultWindow();
        replaceStableRecognizeFrame(null);
        goodsColorMap.clear();
        frameChangeGate.reset();
        latestCameraFrame = null;
        presenter.clearRows();
    }

    /** 恢复相机帧调度与识别结果窗口。 */
    @Override public void resumeRecognition() {
        frameChangeGate.reset();
        latestCameraFrame = null;
        resetRecognitionResultWindow();
        drawView.setGoodsVo(presenter.rows(), saveWidth, saveHeight);
        updatePreviewFrame();
        if (activityResumed && sdkRecognitionReady) startDetect();
    }

    private void updateScaleConnectionState(boolean connected, boolean stable) {
        if (connectLine == null) {
            return;
        }
        int color = !connected || presenter.state().currentWeightKg.signum() < 0
                ? 0xFFC93636 : stable ? 0xFF27835B : 0xFFFA6400;
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL); dot.setColor(color);
        connectLine.setBackground(dot);
    }

    private Locale displayLocale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    private void lockStableRecognition(CheckoutRecognition response) {
        recognitionLoop.stop();
        resetRecognitionResultWindow();
        resetStableResultTimeout();
        detectWsResp = response;
        goodsColorMap.clear();
        presenter.lockRecognition(response);
        updateRecognizeStatus(R.string.status_recognition_stable, R.drawable.bg_green_round);
    }

    private void resetStableResultTimeout() {
        stableScaleAt = 0L;
        MainHandler.removeCallback(stableResultTimeoutRunnable);
    }

    private void resetStableResultTimeoutIfNotArmed() {
        if (stableScaleAt <= 0L) {
            resetStableResultTimeout();
        }
    }

    private void armStableResultTimeout() {
        if (presenter.state().recognitionLocked || !presenter.state().detecting || !presenter.state().hasStableWeight) {
            return;
        }
        stableScaleAt = SystemClock.elapsedRealtime();
        MainHandler.removeCallback(stableResultTimeoutRunnable);
        MainHandler.postDelay(stableResultTimeoutRunnable, STABLE_RESULT_TIMEOUT_MS);
        Logger.logDebug(LOG_TAG, "scale stable, arm SDK stable-result timeout: " + STABLE_RESULT_TIMEOUT_MS + "ms");
    }

    private void handleUnstableDetectResponse() {
        if (stableScaleAt > 0L) {
            updateRecognizeStatus(R.string.status_recognition_unstable_wait, R.drawable.bg_orange_round);
        } else {
            updateRecognizeStatus(R.string.status_recognition_unstable, R.drawable.bg_orange_round);
        }
    }

    private boolean hasDetectResults(CheckoutRecognition wsResp) {
        return wsResp != null  && wsResp.getSelections() != null && !wsResp.getSelections().isEmpty();
    }

    /**
     * 返回收银时刷新称重开关与控件，保留当前购物清单。
     *
     */
    private void refreshWeighingModuleState() {
        presenter.setWeighingEnabled(ModuleConfig.isWeighingEnabled(this));
        applyWeighingModuleVisibility();
    }

    private void applyWeighingModuleVisibility() {
        countWeightTypeLayout.setVisibility(presenter.state().weighingEnabled ? View.VISIBLE : View.GONE);
    }

    private void registerDebugRecognizeReceiver() {
        if (!BuildConfig.DEBUG || debugRecognizeReceiver != null) {
            return;
        }
        debugRecognizeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String plu = intent.getStringExtra(DebugInputReceiver.EXTRA_RECOGNIZE_PLU);
                if (!TextUtils.isEmpty(plu)) {
                    handleDebugRecognizeInject(plu.trim());
                }
            }
        };
        IntentFilter filter = new IntentFilter(DebugInputReceiver.ACTION_RECOGNIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugRecognizeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(debugRecognizeReceiver, filter);
        }
    }

    private void unregisterDebugRecognizeReceiver() {
        if (debugRecognizeReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(debugRecognizeReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver may already have been unregistered during activity teardown.
        }
        debugRecognizeReceiver = null;
    }

    /**
     * debug 识别注入（P0 预留接线）：按 PLU 构造稳定识别结果，复用与真实识别完全相同的
     * 锁定→刷新→计价前置校验→计件/称重分发链路。仅 debug 构建可达。
     * 未入库 PLU 不再提示失败，注入为无匹配检测项：
     * 走生产同链路的 refreshGoodsData → unmatchedDetectItems，识别预览仅显示 clazz_name。
     */
    private void handleDebugRecognizeInject(String pluList) {
        List<String> plus = Arrays.stream(pluList.split(","))
                .map(String::trim)
                .filter(plu -> !plu.isEmpty())
                .collect(Collectors.toList());
        if (plus.isEmpty()) {
            return;
        }
        ensureDebugPreviewFrame();
        List<RecognitionItem> injected = new ArrayList<>();
        for (String plu : plus) {
            float[] points = buildDebugInjectPoints();
            injected.add(new RecognitionItem(plu, 0, (int) points[0], (int) points[1],
                    (int) points[2], (int) points[3]));
        }
        CheckoutRecognition resp = new CheckoutRecognition(new RecognizeResult(0, null, injected));
        pendingDebugRecognizePlus = plus.size() > 1 ? new ArrayList<>(plus) : Collections.emptyList();
        pendingDebugRecognizeResp = plus.size() > 1 ? resp : null;
        pendingDebugRecognizeBatch = plus.size() > 1;
        String normalizedPluList = TextUtils.join(",", plus);
        Log.d(LOG_TAG, "[Biz] debug recognize plu=" + normalizedPluList);
        lockStableRecognition(resp);
    }

    /**
     * debug 批量注入计件分发：按注入 PLU 顺序逐个确认，最后一个确认后才恢复识别。
     */
    private void autoDispatchDebugCountConfirms(List<GoodsVo> latestGoodsVos, List<String> pluOrder) {
        List<GoodsVo> countTargets = new ArrayList<>();
        for (String plu : pluOrder) {
            for (GoodsVo goodsVo : latestGoodsVos) {
                GoodsEntity goods = goodsVo == null ? null : goodsVo.getGoods();
                if (goods != null && plu.equals(goods.getPlu())
                        && CheckoutRows.resolvePricingMode(goods) == PricingMode.COUNT
                        && !countTargets.contains(goodsVo)) {
                    countTargets.add(goodsVo);
                    break;
                }
            }
        }
        if (countTargets.isEmpty()) {
            presenter.resumeContinuousRecognition();
            return;
        }
        showDebugCountConfirm(countTargets, 0);
    }

    private void showDebugCountConfirm(List<GoodsVo> countTargets, int index) {
        if (isFinishing() || isDestroyed()) return;
        GoodsVo target = countTargets.get(index);
        if (!presenter.state().canEdit || !presenter.rows().contains(target)) return;
        RecognizeCountKeyboardDialog dialog = new RecognizeCountKeyboardDialog();
        dialog.setCount(target.getCount() == null ? 1 : target.getCount());
        dialog.setListener(count -> {
            if (!presenter.state().canEdit || !presenter.rows().contains(target)) return;
            if (!presenter.confirmCount(target, count)) return;
            int nextIndex = index + 1;
            if (nextIndex >= countTargets.size()) {
                if (BuildConfig.DEBUG && pendingDebugRecognizeBatch) {
                    // debug 批量注入：确认完成后暂停真实识别（帧循环 + 锁定守卫双重隔离），
                    // 防止真实摄像头识别帧覆盖注入的购物车，保证断言/截图/结算稳定。
                    pendingDebugRecognizeBatch = false;
                    stopDetect();
                    return;
                }
                presenter.resumeContinuousRecognition();
                return;
            }
            // Dialog 在 listener 返回后才 dismiss，延后到下一轮主线程再展示后续确认。
            MainHandler.postDelay(() -> showDebugCountConfirm(countTargets, nextIndex), 100L);
        });
        dialog.show(getSupportFragmentManager(), COUNT_KEYBOARD_TAG);
    }

    /**
     * debug 注入兜底帧：无相机帧（无 UVC 相机设备/识别未启动）时识别预览无绘制坐标系，
     * 以纯色占位帧提供（仅 debug 注入链路使用，不影响真实识别帧捕获）。
     */
    private void ensureDebugPreviewFrame() {
        synchronized (stableRecognizeFrameLock) {
            if (stableRecognizeFrame != null && !stableRecognizeFrame.isRecycled()) {
                return;
            }
        }
        Bitmap placeholder = Bitmap.createBitmap(DEBUG_PREVIEW_FRAME_WIDTH, DEBUG_PREVIEW_FRAME_HEIGHT,
                Bitmap.Config.ARGB_8888);
        placeholder.eraseColor(Color.DKGRAY);
        replaceStableRecognizeFrame(placeholder);
    }

    /**
     * debug 注入识别框坐标：识别区域中心小框（OBB 四顶点，左上/右上/右下/左下）。
     * 预览绘制与裁剪链路要求 points 非空，注入无 SDK geometry 时以此兜底（仅 debug 构建可达）。
     */
    private static float[] buildDebugInjectPoints() {
        RecognizeAreaEntity area = SPManager.getInstance().getRecognizeArea();
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = Float.MIN_VALUE;
        float bottom = Float.MIN_VALUE;
        if (area.getPoints() != null) {
            for (PointF point : area.getPoints()) {
                left = Math.min(left, point.x);
                top = Math.min(top, point.y);
                right = Math.max(right, point.x);
                bottom = Math.max(bottom, point.y);
            }
        }
        if (left >= right || top >= bottom) {
            return new float[]{0f, 0f, 1f, 1f};
        }
        // points 为识别区域（裁剪框）坐标系，取中心 10% 小框
        float centerX = (left + right) / 2f - left;
        float centerY = (top + bottom) / 2f - top;
        float halfW = Math.max(1f, (right - left) * 0.05f);
        float halfH = Math.max(1f, (bottom - top) * 0.05f);
        return new float[]{centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH};
    }

    /** 将 Presenter 提供的页面状态写入控件。 */
    @Override public void render(CheckoutViewState page) {
        CheckoutState state = page.sale;
        showAutomaticTareLoading(activityResumed && state.automaticTarePending);
        resultAdapter.setCurrentWeightKg(state.currentWeightKg.doubleValue());
        Locale locale = displayLocale();
        List<GoodsVo> latestGoodsVos = page.rows;
        int goodsKind = latestGoodsVos.size();
        int itemCount = page.itemCount;
        summaryKinds.setText(getString(R.string.checkout_kind_value, goodsKind));
        summaryCount.setText(getString(R.string.checkout_count_value, itemCount));
        summaryAmount.setText(getString(R.string.amount_currency_format, state.receipt != null
                ? DisplayFormatUtil.formatAmountCny(state.receipt.getTotalAmountCent(), locale)
                : (latestGoodsVos.isEmpty() ? "0.00" : "—")));
        renderEmptyGuide(page);
        modifyGoods.setText(getString(R.string.button_modify_goods,
                DisplayFormatUtil.formatQuantity(page.selected.size(), locale)));
        resultAdapter.setEditingEnabled(state.canEdit);
        resultAdapter.setDeletionVisible(presenter.isDeletionEnabled() && state.recognitionLocked);
        boolean modifyEnabled = page.modifyEnabled;
        modifyGoods.setEnabled(modifyEnabled);
        modifyGoods.setAlpha(modifyEnabled ? 1f : 0.45f);

        // 确认资格与打印开关独立；打印关闭时沿用同一结算和反馈链路。
        boolean canConfirm = state.canPrint;
        showConfirmationLoading(state.printing && activityResumed);
        retryRecognize.setEnabled(!state.printing && !state.automaticTarePending);

        confirmSale.setEnabled(canConfirm || (!state.printing && (state.phase == CheckoutState.Phase.LOCKED_PRICE_MISSING
                || state.phase == CheckoutState.Phase.LOCKED_PRICE_CONFLICT)));
        confirmSale.setAlpha(canConfirm ? 1f : 0.45f);
        confirmSale.setText(state.phase == CheckoutState.Phase.LOCKED_PRICE_MISSING ? R.string.checkout_price_required
                : page.priceConflict
                ? R.string.button_price_conflict : (ModuleConfig.isPrintEnabled(this)
                        ? R.string.button_print_ticket : R.string.button_confirm_results));
        previewView.setPreviewState(goodsColorMap, null, page.selected);
        if (state.recognitionLocked) {
            resultAdapter.setPreviewState(goodsColorMap, null, page.selected);
            resultAdapter.setPriceConflictPlus(state.priceConflictPlus);
        }
    }

    /** 列表空态与动画随内容和系统减少动态效果设置切换。 */
    private void renderEmptyGuide(CheckoutViewState page) {
        boolean empty = page.rows.isEmpty();
        emptyPanel.setVisibility(page.waitingVisible || page.emptyVisible ? View.VISIBLE : View.GONE);
        resultWatermark.setLocked(!empty);
        boolean motion = android.provider.Settings.Global.getFloat(getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        int mode = page.emptyVisible ? 1 : page.waitingVisible ? (motion ? 2 : 3) : 0;
        if (mode == guideMode) return;
        guideMode = mode;
        com.bumptech.glide.Glide.with(this).clear(trayGuide);
        trayGuide.setVisibility(mode >= 2 ? View.VISIBLE : View.GONE);
        emptyTitle.setText(mode == 1 ? R.string.checkout_no_match : R.string.checkout_place_tray);
        emptyHint.setText(mode == 1 ? R.string.checkout_no_match_hint : R.string.checkout_wait_hint);
        if (mode == 2) com.bumptech.glide.Glide.with(this).asGif().load(R.raw.recognition_tray_guide).into(trayGuide);
        else if (mode == 3) trayGuide.setImageResource(R.drawable.recognition_tray_still);
    }

    /** 三次连续点击版本号才请求退出，间隔超过1秒重新计数。 */
    private void onVersionTap() {
        long now = SystemClock.elapsedRealtime();
        versionTapCount = now - versionTapAt <= 1000 ? versionTapCount + 1 : 1;
        versionTapAt = now;
        if (versionTapCount == 3) { versionTapCount = 0; requestExit(); }
    }

    /** 系统返回和隐藏入口执行相同的本地密码验证。 */
    private void requestExit() {
        if (!cc.huoyan.retail.settings.ExitPasswordStore.isEnabled(this)) { returnToHome(); return; }
        if (getSupportFragmentManager().findFragmentByTag("exitPassword") == null)
            cc.huoyan.retail.settings.ExitPasswordDialog.showVerify(getSupportFragmentManager());
    }

    /** 显示设备称重操作，复用本页秤连接而不另开串口。 */
    private void showScaleDialog() {
        if (presenter.state().automaticTarePending) return;
        if (getSupportFragmentManager().findFragmentByTag("scaleOperations") == null)
            new ScaleOperationsDialog().show(getSupportFragmentManager(), "scaleOperations");
    }

    /** 设备读数同时更新三个重量卡片与已打开的弹窗。 */
    private void renderScaleReadings() {
        if (weightTv == null) return;
        AclasWeightSnapshot snapshot = latestScaleSnapshot;
        weightTv.setText(snapshot == null ? "—" : snapshot.netKg().toPlainString());
        tareWeightTv.setText(snapshot == null || snapshot.tareKg() == null ? "—" : snapshot.tareKg().toPlainString());
        grossWeightTv.setText(snapshot == null || snapshot.grossKg() == null ? "—" : snapshot.grossKg().toPlainString());
        androidx.fragment.app.Fragment dialog = getSupportFragmentManager().findFragmentByTag("scaleOperations");
        if (dialog instanceof ScaleOperationsDialog) ((ScaleOperationsDialog) dialog).render(snapshot);
    }

    /** 弹窗读取当前设备快照，未知值不替换成零。 */
    AclasWeightSnapshot scaleSnapshot() { return latestScaleSnapshot; }

    /** 主线程发送命令；设备管理器以读数确认结果。 */
    void commandScale(BigDecimal tare, AclasScaleManager.CommandListener callback) {
        if (tare == null) aclasScaleManager.setZero(callback);
        else aclasScaleManager.setPreTare(tare.movePointRight(3).intValueExact(), callback);
    }

    /** 自动去皮沿用设备确认接口；回调不强持有页面，退出后只完成业务状态。 */
    @Override public void applyAutomaticTare(BigDecimal tareKg, Consumer<Boolean> completion) {
        final int grams;
        try { grams = tareKg.movePointRight(3).intValueExact(); }
        catch (ArithmeticException error) {
            completion.accept(false);
            ToastEx.showFailureToast(this, getString(R.string.checkout_tare_invalid));
            return;
        }
        java.lang.ref.WeakReference<MainActivity> page = new java.lang.ref.WeakReference<>(this);
        aclasScaleManager.setPreTare(grams, (success, message) -> MainHandler.get().post(() -> {
            completion.accept(success);
            MainActivity activity = page.get();
            if (activity == null || !activity.activityResumed || activity.isDestroyed()) return;
            if (success) ToastEx.showSuccessToast(activity, message);
            else ToastEx.showFailureToast(activity, message);
        }));
    }

    /** 按在途状态恢复或关闭 Loading，页面暂停时立即移除遮罩。 */
    private void showAutomaticTareLoading(boolean busy) {
        androidx.fragment.app.DialogFragment existing = (androidx.fragment.app.DialogFragment)
                getSupportFragmentManager().findFragmentByTag("automaticTareLoading");
        if (existing == null) existing = automaticTareLoading;
        if (busy && existing == null && !getSupportFragmentManager().isStateSaved()) {
            automaticTareLoading = new cc.huoyan.retail.common.ui.LoadingDialog(getString(R.string.checkout_scale_working));
            automaticTareLoading.setCancelable(false);
            automaticTareLoading.show(getSupportFragmentManager(), "automaticTareLoading");
        } else if (!busy && existing != null) {
            existing.dismissAllowingStateLoss();
            automaticTareLoading = null;
        }
    }

    /** 根据在途确认状态展示 Loading；保留待添加实例，暂停或完成时关闭。 */
    private void showConfirmationLoading(boolean busy) {
        androidx.fragment.app.DialogFragment existing = (androidx.fragment.app.DialogFragment)
                getSupportFragmentManager().findFragmentByTag("confirmationLoading");
        if (existing == null) existing = confirmationLoading;
        if (busy && existing == null && !getSupportFragmentManager().isStateSaved()) {
            confirmationLoading = new cc.huoyan.retail.common.ui.LoadingDialog(getString(R.string.checkout_confirming_results));
            confirmationLoading.setCancelable(false);
            confirmationLoading.show(getSupportFragmentManager(), "confirmationLoading");
        } else if (!busy && existing != null) {
            existing.dismissAllowingStateLoss();
            confirmationLoading = null;
        }
    }

    /** 创建缩略图并把页面清单交给现有适配器和预览控件。 */
    @Override public void renderRows(CheckoutViewState page, boolean updateList) {
        for (GoodsVo row : page.rows) {
            if (row.getRecognizeBitmap() == null && row.getRecognitionSelection() != null
                    && !row.getRecognitionSelection().isEmpty())
                row.setRecognizeBitmap(buildRecognizeBitmap(row.getRecognitionSelection().get(0)));
        }
        RecognizeColorUtil.syncGoodsColorMap(goodsColorMap, page.rows);
        if (updateList) {
            resultAdapter.setCurrentWeightKg(page.sale.currentWeightKg.doubleValue());
            resultAdapter.setList(new ArrayList<>(page.rows));
            resultAdapter.setBlockedPlus(page.blockedPlus);
        }
        drawView.setGoodsVo(page.sale.recognitionLocked ? new ArrayList<>() : page.rows, saveWidth, saveHeight);
        updatePreviewFrame();
    }

    /** 展示数量键盘，确认结果交回 Presenter。 */
    @Override public void showCountInput(int initial, IntConsumer confirm) {
        RecognizeCountKeyboardDialog dialog = new RecognizeCountKeyboardDialog();
        dialog.setCount(initial);
        dialog.setListener(confirm::accept);
        dialog.show(getSupportFragmentManager(), COUNT_KEYBOARD_TAG);
    }

    /** 展示重量键盘，保持现有三位小数输入。 */
    @Override public void showWeightInput(BigDecimal initial, Consumer<BigDecimal> confirm) {
        WeightInputDialog dialog = new WeightInputDialog();
        dialog.setWeight(initial.doubleValue());
        dialog.setListener(weight -> confirm.accept(BigDecimal.valueOf(weight)));
        dialog.show(getSupportFragmentManager(), WEIGHT_INPUT_TAG);
    }

    /** 展示改品选择框与当前选择的缩略图。 */
    @Override public void showGoodsPicker(GoodsVo selected, Predicate<GoodsEntity> confirm) {
        selected.setRecognizeBitmap(buildRecognizeBitmap(selected.getRecognitionSelection().get(0)));
        RecognizeModifyGoodsDialog dialog = new RecognizeModifyGoodsDialog();
        dialog.setGoodsVo(selected);
        dialog.setCancelListener(presenter::render);
        dialog.setListener(confirm::test);
        dialog.show(getSupportFragmentManager(), "mainRecognizeModify");
    }

    /** 回显手改重量。 */
    @Override public void showManualWeight(BigDecimal weight) {
        weightTv.setText(DisplayFormatUtil.formatWeightKg(weight.doubleValue(), displayLocale()));
    }

    /** 记录平台依赖的业务日志。 */
    @Override public void logBusiness(String message) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "[Biz] " + message);
    }

    /** 将交互结果映射为已有文案和 Toast 样式。 */
    @Override public void showMessage(CheckoutPresenter.Message message, String detail) {
        int textRes;
        switch (message) {
            case SELECT_BOX: textRes = R.string.toast_select_modify_box_first; break;
            case NO_GOODS: textRes = R.string.toast_no_modifiable_goods; break;
            case MODIFY_EXPIRED: textRes = R.string.toast_modify_result_expired; break;
            case WEIGHING_DISABLED: textRes = R.string.toast_modify_weighing_disabled; break;
            case SPLIT_COUNT: textRes = R.string.toast_modify_split_count; break;
            case PRICE_MISSING: textRes = R.string.checkout_price_required; break;
            case PRICE_CONFLICT: textRes = R.string.price_conflict_message; break;
            case GOODS_REMOVED: textRes = R.string.toast_goods_removed_please_reweigh; break;
            case NOT_READY: textRes = R.string.toast_recognition_or_weight_unstable; break;
            case RESULT_DELETED:
                ToastEx.showSuccessToast(mContext, R.string.toast_recognition_result_deleted);
                return;
            case CONFIRM_DONE:
                ToastEx.showSuccessToast(mContext, R.string.toast_results_confirmed);
                return;
            case CONFIRM_FAILED:
                ToastEx.showFailureToast(mContext, getString(R.string.toast_confirm_results_failed, detail));
                return;
            case PRINT_DONE:
                ToastEx.showSuccessToast(mContext, getString(R.string.toast_print_done));
                return;
            case PRINT_FAILED:
                ToastEx.showFailureToast(mContext, getString(R.string.toast_print_failed, detail));
                return;
            default: throw new IllegalArgumentException("Unknown checkout message: " + message);
        }
        if (message == CheckoutPresenter.Message.SELECT_BOX || message == CheckoutPresenter.Message.NO_GOODS) {
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "[Toast] " + getString(textRes));
            Toast.makeText(this, textRes, Toast.LENGTH_SHORT).show();
        } else ToastEx.showFailureToast(mContext, getString(textRes));
    }

    /** 加载完成后触发 debug 批量计件弹窗，生产识别不自动弹窗。 */
    @Override public void onGoodsLoaded(CheckoutRecognition source, List<GoodsVo> rows) {
        List<String> plus = BuildConfig.DEBUG && source == pendingDebugRecognizeResp
                ? pendingDebugRecognizePlus : Collections.emptyList();
        if (!plus.isEmpty()) {
            pendingDebugRecognizePlus = Collections.emptyList();
            pendingDebugRecognizeResp = null;
            if (presenter.state().recognitionLocked) autoDispatchDebugCountConfirms(rows, plus);
        }
    }

    /** 查询页面当前的数量弹窗。 */
    @Override public boolean isCountInputShowing() {
        return getSupportFragmentManager().findFragmentByTag(COUNT_KEYBOARD_TAG) != null;
    }

    /** 保留商品查询错误日志。 */
    @Override public void showLoadingFailure(Exception error) { Log.e(LOG_TAG, "refreshGoodsData", error); }

    private String formatCent(long amountCent) {
        return BigDecimal.valueOf(amountCent).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void updatePreviewFrame() {
        if (presenter.state().recognitionLocked) {
            previewView.setPreviewData(stableRecognizeFrame, presenter.rows());
            previewView.setVisibility(View.VISIBLE);
            surfaceView.setVisibility(View.GONE);
            drawView.setVisibility(View.GONE);
            recognizeStatus.bringToFront();
            versionTv.bringToFront();
        } else {
            previewView.setPreviewData(null, null);
            previewView.setVisibility(View.GONE);
            surfaceView.setVisibility(View.VISIBLE);
            drawView.setVisibility(View.VISIBLE);
            cameraController.resumePreview();
            recognizeStatus.bringToFront();
            versionTv.bringToFront();
        }
    }

    /** 展示收银状态并保留平台日志、语音与计时出口。 */
    @Override public void showSaleStatus(CheckoutPresenter.SaleStatus status, CheckoutState state) {
        if (status == CheckoutPresenter.SaleStatus.PRINTED || status == CheckoutPresenter.SaleStatus.CONFIRMED) {
            if (!state.weighingEnabled) {
                printResumeAt = SystemClock.elapsedRealtime() + 10_000L;
                schedulePrintResume();
            }
            boolean printed = status == CheckoutPresenter.SaleStatus.PRINTED;
            updateRecognizeStatus(state.weighingEnabled
                    ? (printed ? R.string.status_print_done_wait_remove : R.string.status_confirmed_wait_remove)
                    : (printed ? R.string.status_print_done_auto_resume : R.string.status_confirmed_auto_resume),
                    R.drawable.bg_green_round);
            return;
        }
        if (status == CheckoutPresenter.SaleStatus.PRICE_MISSING) {
            updateRecognizeStatus(R.string.checkout_price_required, R.drawable.bg_orange_round);
            return;
        }
        if (status == CheckoutPresenter.SaleStatus.PRICE_CONFLICT) {
            updateRecognizeStatus(R.string.status_price_conflict_retry, R.drawable.bg_orange_round);
            postMainHandler(() -> voicePromptManager.speak(getString(R.string.price_conflict_message)));
            return;
        }
        if (state.receipt != null && BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "[Biz] item_count=" + state.receipt.getDetectedPieceCount());
            Log.d(LOG_TAG, "[Biz] amount=" + formatCent(state.receipt.getTotalAmountCent()));
        }
        if (status == CheckoutPresenter.SaleStatus.READY) {
            if (!saleFlowDurationLogged && saleFlowStartedAt > 0L) {
                saleFlowDurationLogged = true;
                if (BuildConfig.DEBUG) Log.i(BUSINESS_TIMING_LOG_TAG,
                        "重量=" + state.saleWeightKg.toPlainString() + "kg, 含称重全流程耗时="
                                + randomDemoDuration(1200L, 2901L) + "ms");
            }
            updateRecognizeStatus(R.string.status_recognition_stable, R.drawable.bg_green_round);
        }
    }

    private Bitmap copyStableRecognizeFrame() {
        synchronized (stableRecognizeFrameLock) {
            Bitmap frameBitmap = stableRecognizeFrame;
            if (frameBitmap == null || frameBitmap.isRecycled()) {
                return null;
            }
            try {
                return frameBitmap.copy(frameBitmap.getConfig() != null ? frameBitmap.getConfig() : Bitmap.Config.ARGB_8888, false);
            } catch (Exception e) {
                Log.e(LOG_TAG, "copyStableRecognizeFrame: ", e);
                return null;
            }
        }
    }

    private static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private Bitmap buildRecognizeBitmap(RecognitionSelection detectItem) {
        synchronized (stableRecognizeFrameLock) {
            Bitmap frameBitmap = stableRecognizeFrame;
            if (frameBitmap == null || frameBitmap.isRecycled() || detectItem == null) {
                return null;
            }
            try {
                float[] reversedPoints = invertPoints(detectItem, frameBitmap.getWidth(), frameBitmap.getHeight());
                if (reversedPoints == null || reversedPoints.length < 4) {
                    return null;
                }
                // OBB 为 4 顶点 8 值，AABB 为 4 值，统一对全部顶点取 min/max
                float minX = Float.MAX_VALUE;
                float minY = Float.MAX_VALUE;
                float maxX = Float.MIN_VALUE;
                float maxY = Float.MIN_VALUE;
                for (int i = 0; i < reversedPoints.length; i += 2) {
                    minX = Math.min(minX, reversedPoints[i]);
                    maxX = Math.max(maxX, reversedPoints[i]);
                    minY = Math.min(minY, reversedPoints[i + 1]);
                    maxY = Math.max(maxY, reversedPoints[i + 1]);
                }
                int left = Math.max(0, Math.round(minX));
                int top = Math.max(0, Math.round(minY));
                int right = Math.min(frameBitmap.getWidth(), Math.round(maxX));
                int bottom = Math.min(frameBitmap.getHeight(), Math.round(maxY));
                int width = right - left;
                int height = bottom - top;
                if (width <= 0 || height <= 0) {
                    return null;
                }
                Bitmap cropped = cropBitmap(frameBitmap, left, top, width, height, 0);
                return cropped == frameBitmap ? frameBitmap.copy(Bitmap.Config.ARGB_8888, false) : cropped;
            } catch (Exception e) {
                Log.e(LOG_TAG, "buildRecognizeBitmap: ", e);
                return null;
            }
        }
    }

    private void updateRecognizeStatus(String text, int drawableRes) {
        postMainHandler(() -> {
            if (!sdkRecognitionReady && sdkStatusTextRes != 0) {
                recognizeText.setText(sdkStatusTextRes);
                recognizeIcon.setBackground(AppCompatResources.getDrawable(mContext, R.drawable.bg_red_round));
                return;
            }
            recognizeText.setText(text);
            recognizeIcon.setBackground(AppCompatResources.getDrawable(mContext, drawableRes));
        });
    }

    private void updateRecognizeStatus(int textRes, int drawableRes) {
        postMainHandler(() -> {
            if (!sdkRecognitionReady && sdkStatusTextRes != 0) {
                recognizeText.setText(sdkStatusTextRes);
                recognizeIcon.setBackground(AppCompatResources.getDrawable(mContext, R.drawable.bg_red_round));
                return;
            }
            recognizeText.setText(textRes);
            recognizeIcon.setBackground(AppCompatResources.getDrawable(mContext, drawableRes));
        });
    }

    private void logSimulatedDuration(String node, long durationMs) {
        if (BuildConfig.DEBUG) {
            Log.i(BUSINESS_TIMING_LOG_TAG, node + "=" + durationMs + "ms");
        }
    }

    private long randomDemoDuration(long originMs, long boundMs) {
        return ThreadLocalRandom.current().nextLong(originMs, boundMs);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(EventBusModel event) {
        switch (event.getCode()) {
            case EVENT_CAMERA_INIT: {
                switch (event.getStatus()) {
                    case EVENT_CAMERA_STATUS_SUCCESS:
                        break;
                    case EVENT_CAMERA_STATUS_ERROR:
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "[Toast] " + event.getMsg());
                        }
                        Toast.makeText(mContext, event.getMsg(), Toast.LENGTH_LONG).show();
                        break;
                    case EVENT_CAMERA_STATUS_SWITCH:
                        break;
                }
                break;
            }
            case EVENT_GOODS_TASK: {
                if (event.getStatus() == EVENT_GOODS_TASK_FALSE) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "[Toast] " + getString(R.string.toast_ftp_start_failed, event.getMsg()));
                    }
                    Toast.makeText(mContext, getString(R.string.toast_ftp_start_failed, event.getMsg()), Toast.LENGTH_LONG).show();
                }
                break;
            }
            case EVENT_WS_CONNECT: {
                switch (event.getStatus()) {
                    case EVENT_WS_CONNECT_OPENED:
                        if (!presenter.state().recognitionLocked) sdkResumeInit();
                        break;
                    case EVENT_WS_CONNECT_CLOSE:
                        updateRecognizeStatus(R.string.status_sdk_disconnected, R.drawable.bg_red_round);
                        break;
                }
                break;
            }
        }
    }

}
