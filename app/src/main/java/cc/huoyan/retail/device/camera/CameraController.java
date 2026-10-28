package cc.huoyan.retail.device.camera;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.RetailApplication;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.retail.data.SPManager;

/** 应用级相机入口；主线程管理唯一预览所有者，后台仅交付当前代次帧。 */
public final class CameraController {
    /** USB 模式持久化值，兼容旧安装默认值。 */
    public static final String UVC = "UVC";
    /** 系统 Camera2 模式持久化值。 */
    public static final String CAMERA2 = "CAMERA2";
    /** 进程级唯一入口。 */
    private static final CameraController INSTANCE = new CameraController();
    /** 主线程错误提示调度。 */
    private final Handler main = new Handler(Looper.getMainLooper());
    /** 跨线程安全的帧订阅，页面退出时移除。 */
    private final Map<String, FrameListener> listeners = new ConcurrentHashMap<>();
    /** 当前类型对应的驱动。 */
    private CameraSource source;
    /** 当前预览所有者，解绑后清除。 */
    private AutoFitTextureView preview;
    /** 切换与暂停都会使旧帧失效。 */
    private volatile long generation;
    /** 最近一次失败后停止自动重开，用户重新进入或选择后重试。 */
    private boolean failed;
    /** 是否由页面暂停。 */
    private boolean paused;
    /** 当前会话是否已记录首帧。 */
    private volatile boolean firstFrame;
    /** 帧监听器不得修改像素，调用线程为后端工作线程。 */
    public interface FrameListener {
        /** 接收独立帧快照。 */
        void onFrame(CameraFrame frame);
    }
    /** 延迟创建具体驱动，避免未使用 UVC 时注册 USB 监控。 */
    private CameraController() { }
    /** 返回统一入口。 */
    public static CameraController getInstance() { return INSTANCE; }
    /** 返回保存的类型，旧用户默认使用 UVC。 */
    public String type() { return CAMERA2.equals(SPManager.getInstance().getString("camera_source", UVC)) ? CAMERA2 : UVC; }
    /** 按当前配置创建唯一后端。 */
    private CameraSource source() {
        if (source == null) source = CAMERA2.equals(type()) ? new Camera2Source() : new UvcCameraSource();
        return source;
    }
    /** 枚举当前类型设备。 */
    public List<CameraDeviceInfo> getDevices() { return source().devices(); }
    /** 返回当前类型支持的原始尺寸。 */
    public List<CameraSize> getCameraSize() { return source().sizes(); }
    /** 返回当前设备标识。 */
    public String getCurrentCameraId() { return source().id(); }
    /** 查询驱动是否已供帧。 */
    public boolean isCameraOpen() { return source != null && source.open(); }
    /** 打开在途或失败后等待用户处理时，禁止看门狗重复重开。 */
    public boolean isCameraRestarting() { return failed || (source != null && source.opening()); }
    /** 注册页面帧监听。 */
    public void addListener(String key, FrameListener listener) { listeners.put(key, listener); }
    /** 页面销毁或暂停时移除监听。 */
    public void removeListener(String key) { listeners.remove(key); }
    /** 判断识别、学习缓存的帧是否仍属于当前相机会话。 */
    public boolean isCurrent(CameraFrame frame) { return frame != null && frame.generation == generation; }
    /** 返回当前相机的镜像配置。 */
    public boolean mirrorEnabled() { return SPManager.getInstance().getBoolean(mirrorKey()); }
    /** 原生设备独立保存镜像，UVC 保留历史键。 */
    private String mirrorKey() { return CAMERA2.equals(type()) ? "camera2_mirror_" + getCurrentCameraId() : "cameraMirror"; }
    /** 更新镜像并重建会话，使缓存帧失效。 */
    public void setMirrorEnabled(boolean enabled) {
        SPManager.getInstance().putBoolean(mirrorKey(), enabled);
        restart();
    }
    /** 持久化类型并释放旧后端；不会自动回退另一类型。 */
    public void selectType(String type) {
        if (!UVC.equals(type) && !CAMERA2.equals(type)) throw new IllegalArgumentException("未知相机类型");
        if (type.equals(type())) return;
        halt(); source = null;
        SPManager.getInstance().putString("camera_source", type);
        failed = false; paused = false; start();
    }
    /** 保存设备并使用其分辨率配置重新打开。 */
    public void selectCamera(String id) {
        halt();
        SPManager.getInstance().putString(CAMERA2.equals(type()) ? "camera2_device" : "usb_camera", id);
        failed = false; start();
    }
    /** 保存当前设备原始输出尺寸，重新协商会话。 */
    public void selectResolution(String resolution) {
        SPManager.getInstance().putString(CAMERA2.equals(type()) ? "camera2_resolution_" + getCurrentCameraId() : "fbl_" + getCurrentCameraId(), resolution);
        CameraConfigEntity config = SPManager.getInstance().getCameraConfig();
        config.setCamera_resolution(resolution); SPManager.getInstance().putCameraConfig(config);
        restart();
    }
    /** 返回当前设备保存的原始输出尺寸。 */
    public String resolution() {
        return SPManager.getInstance().getString(CAMERA2.equals(type()) ? "camera2_resolution_" + getCurrentCameraId() : "fbl_" + getCurrentCameraId(), "1280*720");
    }
    /** 绑定当前页面，Surface 销毁仅影响自身绑定。 */
    public void bind(AutoFitTextureView view) {
        if (preview == view && !paused && !failed && (isCameraOpen() || isCameraRestarting())) return;
        if (preview != view) {
            halt();
            if (preview != null) preview.setSurfaceTextureListener(null);
            preview = view;
        }
        failed = false; paused = false;
        view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            /** 当前纹理就绪后打开相机。 */
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { if (preview == view) start(); }
            /** 视图变换由对应后端的布局监听维护。 */
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { }
            /** 先释放硬件输出，再由 TextureView 回收纹理。 */
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { unbind(view); return true; }
            /** 帧处理使用后端的分析流。 */
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
        });
        start();
    }
    /** 只解绑属于调用页面的预览，旧 Activity 销毁不能关闭新页面。 */
    public void unbind(AutoFitTextureView view) {
        if (preview != view) return;
        halt(); preview.setSurfaceTextureListener(null); preview = null;
    }
    /** 暂停当前相机并使旧帧失效，保留视图供恢复。 */
    public void pausePreview() { paused = true; halt(); }
    /** 恢复当前页面预览。 */
    public void resumePreview() { if (!paused) return; paused = false; failed = false; start(); }
    /** 释放所有硬件资源，主要用于设备验收和应用退出。 */
    public void stopCamera() { halt(); if (preview != null) preview.setSurfaceTextureListener(null); preview = null; }
    /** 用户更改配置时重启当前视图，不创建新的页面控件。 */
    private void restart() { halt(); failed = false; start(); }
    /** 统一使旧回调失效，后端释放在主线程执行。 */
    private void halt() { generation++; if (source != null) source.stop(); }
    /** 为一次会话生成代次，首帧记录真实类型和尺寸。 */
    private void start() {
        if (preview == null || !preview.isAvailable() || paused || failed) return;
        CameraSource active = source();
        long token = ++generation; firstFrame = true;
        active.mirror(mirrorEnabled());
        active.start(preview, new CameraSource.Callback() {
            /** 当前代次独立像素交给页面，旧输出直接丢弃。 */
            @Override public void frame(byte[] nv, int width, int height) {
                if (token != generation) return;
                if (firstFrame) {
                    firstFrame = false;
                    if (BuildConfig.DEBUG) android.util.Log.d("CameraController", "[Biz] camera_source=" + type() + " frame=" + width + "x" + height);
                }
                CameraFrame frame = new CameraFrame(nv, width, height, token);
                for (FrameListener listener : listeners.values()) listener.onFrame(frame);
            }
            /** 失败保留选择，并通过项目 Toast 单次提示。 */
            @Override public void error(String message) { main.post(() -> {
                if (token != generation) return;
                halt(); failed = true;
                ToastEx.createToast(RetailApplication.getInstance(), message);
            }); }
        });
    }
}
