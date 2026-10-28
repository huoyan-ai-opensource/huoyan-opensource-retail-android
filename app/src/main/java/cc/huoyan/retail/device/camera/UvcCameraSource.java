package cc.huoyan.retail.device.camera;

import android.hardware.usb.UsbDevice;
import java.util.ArrayList;
import java.util.List;
import cc.huoyan.retail.common.MainHandler;

/** 现有 UVC 驱动适配，保留 USB 权限与设备兼容逻辑。 */
final class UvcCameraSource implements CameraSource {
    /** 进程内唯一 UVC 管理器。 */
    private final CameraUsbManager camera = CameraUsbManager.getInstance();
    /** 打开代次，用于过滤延迟协商。 */
    private int generation;
    /** 当前页面的预览。 */
    private AutoFitTextureView preview;
    /** 枚举设备，屏蔽 UsbDevice 类型。 */
    @Override public List<CameraDeviceInfo> devices() {
        List<CameraDeviceInfo> result = new ArrayList<>();
        for (UsbDevice device : camera.getUsbDeviceList()) {
            result.add(new CameraDeviceInfo(device.getDeviceId() + "_" + device.getVendorId() + "_" + device.getProductId(),
                    (device.getProductName() == null ? "USB Camera" : device.getProductName()) + " " + device.getDeviceId()));
        }
        return result;
    }
    /** 转换 UVC 尺寸，不暴露驱动类型。 */
    @Override public List<CameraSize> sizes() {
        List<CameraSize> result = new ArrayList<>();
        for (com.serenegiant.usb.Size size : camera.getCameraSize()) result.add(new CameraSize(size.width, size.height));
        return result;
    }
    /** 返回已选择的 USB 标识。 */
    @Override public String id() { return camera.getCurrentCameraId(); }
    /** 挂接预览，在设备枚举后应用原有分辨率配置。 */
    @Override public void start(AutoFitTextureView view, Callback callback) {
        int token = ++generation;
        if (preview != null && preview != view) camera.removePreview(preview.getSurfaceTexture());
        preview = view;
        view.setTransform(new android.graphics.Matrix());
        camera.addListener("camera_source", frame -> callback.frame(frame.getNv(), frame.getWidth(), frame.getHeight()));
        camera.setErrorListener(callback::error);
        if (camera.isCameraOpen()) { camera.cameraPreview(view); return; }
        if (camera.isCameraRestarting()) { camera.cameraPreview(view); return; }
        camera.setCameraStateListener(id -> MainHandler.postDelay(() -> {
            if (token != generation) return;
            camera.setCameraStateListener(null);
            camera.getCameraSize();
            camera.setCamaraSize();
        }, 100));
        camera.startCameraPreview(view);
    }
    /** 关闭 USB 监控，防止切到原生后再次抢占设备。 */
    @Override public void stop() {
        generation++; camera.removeListener("camera_source"); camera.releaseCamera(); preview = null;
    }
    /** 返回驱动打开状态。 */
    @Override public boolean open() { return camera.isCameraOpen(); }
    /** 返回设备选择或重开状态。 */
    @Override public boolean opening() { return camera.isCameraRestarting(); }
    /** 沿用 UVC 像素及预览同步镜像。 */
    @Override public void mirror(boolean enabled) { camera.setMirrorEnabled(enabled); }
}
