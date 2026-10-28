package cc.huoyan.retail.settings;

import static cc.huoyan.retail.RetailConstants.SCAN_WE_CHART_QRCODE_DIALOG_TYPE_LOGIN;
import static cc.huoyan.retail.RetailConstants.SCAN_WE_CHART_QRCODE_DIALOG_TYPE_SERIAL;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import cc.huoyan.retail.R;
import cc.huoyan.retail.device.camera.AutoFitTextureView;
import cc.huoyan.retail.device.camera.CameraFrame;
import cc.huoyan.retail.device.camera.CameraController;
import cc.huoyan.retail.device.camera.CameraUtils;
import cc.huoyan.retail.common.ui.WindowUtil;

/** 扫码预览视图；会话节流及解码交由 Presenter，宿主继续处理二维码业务。 */
public class ScanWechatQRCodeDialog extends DialogFragment implements QrScanPresenter.View {
    /** 相机预览控件，仅在主线程访问。 */
    private AutoFitTextureView surfaceView;
    /** 保持相机画面比例的布局容器。 */
    private View previewContainer;
    /** 当前布局对应的源图宽度。 */
    private int previewImageWidth;
    /** 当前布局对应的源图高度。 */
    private int previewImageHeight;
    /** 宿主扫码结果及预览恢复回调。 */
    private ScanWechatQRCodeDialogListener listener;
    /** 共享相机；视图销毁时移除当前监听。 */
    private CameraController cameraController;
    /** 页面实例独立的订阅键，旧页面销毁不能移除新订阅。 */
    private final String cameraListenerKey = getClass().getSimpleName() + "@" + System.identityHashCode(this);
    /** 最新规范化帧，扫码与识别共享同一图像方向。 */
    private volatile CameraFrame latestFrame;
    /** 当前视图的独立扫码会话。 */
    private QrScanPresenter presenter;
    /** 截图解码队列，有序关闭以保证排队图片最终释放。 */
    private java.util.concurrent.ExecutorService worker;
    /** 不捕获弹窗的主线程投递器。 */
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    /** 用参数保存扫码类型，允许 FragmentManager 通过无参构造重建。 */
    public static ScanWechatQRCodeDialog create(int type) {
        ScanWechatQRCodeDialog dialog = new ScanWechatQRCodeDialog();
        Bundle args = new Bundle(); args.putInt("type", type); dialog.setArguments(args);
        return dialog;
    }

    /** 设置业务结果回调，二维码内容由宿主解析。 */
    public void setListener(ScanWechatQRCodeDialogListener listener) {
        this.listener = listener;
    }

    /** 创建预览及本次视图专属的扫码队列。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_scan_wechat_qrcode, container, false);
        int type = getArguments() == null ? SCAN_WE_CHART_QRCODE_DIALOG_TYPE_LOGIN : getArguments().getInt("type");
        ((TextView) view.findViewById(R.id.text)).setText(type == SCAN_WE_CHART_QRCODE_DIALOG_TYPE_SERIAL
                ? R.string.scan_qrcode_activate_hint : R.string.scan_qrcode_hint);
        surfaceView = view.findViewById(R.id.surfaceView);
        surfaceView.setFitInside(true);
        previewContainer = view.findViewById(R.id.scanPreview);
        cameraController = CameraController.getInstance();
        previewImageWidth = 0; previewImageHeight = 0;
        worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        presenter = new QrScanPresenter(this, worker, ui::post, android.os.SystemClock::elapsedRealtime);
        QrScanPresenter session = presenter;
        cameraController.addListener(cameraListenerKey, new CameraController.FrameListener() {
            /** 相机线程只交付尺寸，不访问预览控件。 */
            @Override public void onFrame(CameraFrame entity) {
                latestFrame = entity;
                if (entity != null) session.frameAvailable(entity.getWidth(), entity.getHeight());
            }
        });
        registerCameraSelection(view);
        CameraUtils.getInstance().setPreview(surfaceView);
        return view;
    }

    /** 切换入口复用项目设备列表，不在扫码过程中更改摄像头类型。 */
    private void registerCameraSelection(View view) {
        view.findViewById(R.id.scanSwitchCamera).setOnClickListener(v -> {
            java.util.List<String> labels = new java.util.ArrayList<>();
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (cc.huoyan.retail.device.camera.CameraDeviceInfo device : cameraController.getDevices()) {
                labels.add(device.label);
                ids.add(device.id);
            }
            cc.huoyan.retail.common.ui.SelectOptionDialog.show(getChildFragmentManager(), "scanCamera",
                    getString(R.string.scan_switch_camera), labels, ids, cameraController.getCurrentCameraId());
        });
        cc.huoyan.retail.common.ui.SelectOptionDialog.listen(getChildFragmentManager(), this,
                "scanCamera", (position, selected) -> {
                    for (cc.huoyan.retail.device.camera.CameraDeviceInfo device : cameraController.getDevices()) {
                        if (!device.id.equals(selected)) continue;
                        if (device.id.equals(cameraController.getCurrentCameraId())) return;
                        presenter.cameraChanged();
                        latestFrame = null;
                        previewImageWidth = 0;
                        previewImageHeight = 0;
                        cameraController.selectCamera(device.id);
                        return;
                    }
                    cc.huoyan.retail.common.ui.ToastEx.showFailureToast(requireContext(),
                            getString(R.string.select_option_unavailable));
                });
    }

    /** 在主线程调整预览并截取独立 Bitmap，交给解码任务管理。 */
    @Override public QrScanPresenter.Frame capture(int width, int height) {
        if (width != previewImageWidth || height != previewImageHeight) updatePreviewBounds(width, height);
        CameraFrame frame = latestFrame;
        return cameraController.isCurrent(frame) ? new QrNv21Frame(frame) : null;
    }

    /** 成功后只通知一次宿主，关闭操作也在主线程执行。 */
    @Override public void scanned(String result) {
        try { if (listener != null) listener.onScan(result); }
        finally { dismissAllowingStateLoss(); }
    }

    /** 按窗口可用空间限制扫码区域，保持相机原始比例。 */
    @Override
    public void onStart() {
        super.onStart();
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels - Math.round(32 * metrics.density),
                Math.round(520 * metrics.density));
        requireDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        updatePreviewBounds(surfaceView.getRatioWidth(), surfaceView.getRatioHeight());
    }

    /** 按相机比例和窗口可用尺寸更新预览区域。 */
    private void updatePreviewBounds(int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) { imageWidth = 4; imageHeight = 3; }
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int maxWidth = Math.min(metrics.widthPixels - Math.round(32 * metrics.density),
                Math.round(520 * metrics.density)) - Math.round(40 * metrics.density);
        float scale = Math.min((float) maxWidth / imageWidth, Math.min(metrics.heightPixels * 0.55f,
                Math.max(1, metrics.heightPixels - Math.round(180 * metrics.density))) / imageHeight);
        int width = Math.round(imageWidth * scale);
        int height = Math.round(imageHeight * scale);
        ViewGroup.LayoutParams bounds = previewContainer.getLayoutParams();
        if (bounds.width != width || bounds.height != height) {
            bounds.width = width;
            bounds.height = height;
            previewContainer.setLayoutParams(bounds);
        }
        surfaceView.setAspectRatio(imageWidth, imageHeight);
        previewImageWidth = imageWidth;
        previewImageHeight = imageHeight;
    }

    /** 用户关闭窗口即停止交付，不等待异步 Fragment 移除完成。 */
    @Override public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        if (presenter != null) presenter.detach();
        super.onDismiss(dialog);
    }

    /** 视图关闭时停止扫码回调，并通知宿主恢复预览。 */
    @Override
    public void onDestroyView() {
        cameraController.removeListener(cameraListenerKey);
        presenter.detach();
        worker.shutdown();
        cameraController.unbind(surfaceView);
        latestFrame = null;
        surfaceView = null; previewContainer = null;
        if (listener != null) listener.onDismiss();
        super.onDestroyView();
    }

    /** 业务扫码处理和相机预览恢复的宿主出口。 */
    public interface ScanWechatQRCodeDialogListener {
        /** 接收本会话唯一扫码结果。 */
        void onScan(String result);
        /** 视图销毁后恢复宿主预览或补光状态。 */
        void onDismiss();
    }
}
