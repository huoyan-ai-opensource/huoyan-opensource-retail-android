package cc.huoyan.retail.device.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import cc.huoyan.retail.RetailApplication;
import cc.huoyan.retail.data.SPManager;

/** Camera2 预览与分析后端；设备状态在主线程串行，像素转换在工作线程执行。 */
final class Camera2Source implements CameraSource {
    /** 应用上下文，不保留 Activity。 */
    private final Context context = RetailApplication.getInstance();
    /** 系统相机服务。 */
    private final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    /** 串行设备与会话回调。 */
    private final Handler main = new Handler(Looper.getMainLooper());
    /** 当前像素转换线程，停止时退出。 */
    private HandlerThread worker;
    /** 当前硬件句柄。 */
    private CameraDevice device;
    /** 当前捕获会话。 */
    private CameraCaptureSession session;
    /** 当前分析帧读取器。 */
    private ImageReader reader;
    /** 本后端创建并负责释放的预览 Surface。 */
    private Surface surface;
    /** 当前页面预览，停止时断开。 */
    private AutoFitTextureView preview;
    /** 当前帧和失败出口。 */
    private Callback callback;
    /** 每次关闭递增，跨线程过滤旧帧。 */
    private volatile int generation;
    /** 已收到有效帧。 */
    private volatile boolean opened;
    /** 打开及配置会话在途。 */
    private volatile boolean starting;
    /** 规范化图像镜像标志。 */
    private volatile boolean mirrored;
    /** 当前原始输出尺寸。 */
    private CameraSize size;
    /** 当前原始帧到显示图像的顺时针角度。 */
    private int rotation;
    /** 抵消系统 Surface 方向后的预览补偿角。 */
    private int previewRotation;
    /** 系统前置预览自带镜像，统一输出前先抵消。 */
    private boolean frontFacing;
    /** 当前选择的系统设备标识。 */
    private String cameraId;
    /** 视图尺寸变化时重算像素变换。 */
    private final android.view.View.OnLayoutChangeListener layoutListener = (v,l,t,r,b,ol,ot,or,ob) -> transform();

    /** 枚举系统可见相机，优先后置。 */
    @Override public List<CameraDeviceInfo> devices() {
        List<CameraDeviceInfo> result = new ArrayList<>();
        try {
            for (String id : manager.getCameraIdList()) {
                Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                String label = facing != null && facing == CameraCharacteristics.LENS_FACING_BACK ? "后置摄像头"
                        : facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT ? "前置摄像头" : "系统摄像头";
                CameraDeviceInfo info = new CameraDeviceInfo(id, label + " " + id);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) result.add(0, info); else result.add(info);
            }
        } catch (CameraAccessException e) { android.util.Log.e("Camera2Source", "枚举相机失败", e); }
        return result;
    }
    /** 恢复用户选择；仅尚未配置时选首个后置设备。 */
    @Override public String id() {
        String saved = SPManager.getInstance().getString("camera2_device", "");
        if (!saved.isEmpty()) return saved;
        List<CameraDeviceInfo> available = devices();
        return available.isEmpty() ? "" : available.get(0).id;
    }
    /** 同时支持 SurfaceTexture 和 YUV 的尺寸；限制到 1080p 像素量控制识别内存。 */
    @Override public List<CameraSize> sizes() {
        List<CameraSize> result = new ArrayList<>();
        try {
            if (id().isEmpty()) return result;
            StreamConfigurationMap map = manager.getCameraCharacteristics(id()).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return result;
            Size[] yuv = map.getOutputSizes(ImageFormat.YUV_420_888);
            Size[] texture = map.getOutputSizes(SurfaceTexture.class);
            if (yuv == null || texture == null) return result;
            List<Size> previews = Arrays.asList(texture);
            for (Size item : yuv) if (previews.contains(item) && item.getWidth() * (long)item.getHeight() <= 1920L * 1080
                    && ((item.getWidth() | item.getHeight()) & 1) == 0)
                result.add(new CameraSize(item.getWidth(), item.getHeight()));
            result.sort((a,b) -> Long.compare((long)b.width*b.height, (long)a.width*a.height));
        } catch (CameraAccessException | IllegalArgumentException e) { android.util.Log.e("Camera2Source", "读取尺寸失败", e); }
        return result;
    }
    /** 根据保存配置选择实际支持的尺寸，默认优先 720p。 */
    private CameraSize chooseSize() {
        List<CameraSize> available = sizes();
        String saved = SPManager.getInstance().getString("camera2_resolution_" + cameraId, "1280*720");
        for (CameraSize item : available) if (item.key().equals(saved)) return item;
        for (CameraSize item : available) if (item.width <= 1280 && item.height <= 720) return item;
        return available.isEmpty() ? null : available.get(available.size()-1);
    }
    /** 打开一个预览与一个 YUV 输出，旧打开回调不能恢复已停止的会话。 */
    @Override public void start(AutoFitTextureView view, Callback target) {
        stop();
        preview = view; callback = target; cameraId = id();
        if (!cameraId.isEmpty()) SPManager.getInstance().putString("camera2_device", cameraId);
        int token = generation;
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { fail(token, "请在系统设置中允许相机权限"); return; }
        size = chooseSize();
        if (size == null || view.getSurfaceTexture() == null) { fail(token, "所选原生相机不可用"); return; }
        try {
            CameraCharacteristics info = manager.getCameraCharacteristics(cameraId);
            Integer sensor = info.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Integer facing = info.get(CameraCharacteristics.LENS_FACING);
            int display = view.getDisplay() == null ? 0 : view.getDisplay().getRotation() * 90;
            rotation = YuvFrames.rotation(sensor == null ? 0 : sensor, display,
                    facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT);
            frontFacing = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
            previewRotation = (frontFacing ? display : 360 - display) % 360;
            if (cc.huoyan.retail.BuildConfig.DEBUG) android.util.Log.d("Camera2Source", "[Biz] camera_orientation sensor=" + sensor + " display=" + display + " frame=" + rotation + " preview=" + previewRotation);
            view.setScaleX(1f);
            view.addOnLayoutChangeListener(layoutListener);
            view.setAspectRatio(rotation % 180 == 0 ? size.width : size.height, rotation % 180 == 0 ? size.height : size.width);
            view.getSurfaceTexture().setDefaultBufferSize(size.width, size.height);
            transform();
            surface = new Surface(view.getSurfaceTexture());
            worker = new HandlerThread("Camera2Frames"); worker.start();
            reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 3);
            final int angle = rotation;
            reader.setOnImageAvailableListener(input -> receive(input, token, angle, target), new Handler(worker.getLooper()));
            starting = true;
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                /** 过期设备立即关闭，当前设备创建捕获会话。 */
                @Override public void onOpened(CameraDevice camera) {
                    if (token != generation) { camera.close(); return; }
                    device = camera; configure(token);
                }
                /** 系统断连时释放资源并单次提示。 */
                @Override public void onDisconnected(CameraDevice camera) { camera.close(); fail(token, "原生相机连接已断开"); }
                /** 权限或占用等硬件错误交给统一提示。 */
                @Override public void onError(CameraDevice camera, int error) { camera.close(); fail(token, "原生相机打开失败（" + error + "）"); }
            }, main);
            main.postDelayed(() -> { if (token == generation && starting) fail(token, "原生相机启动超时"); }, 10000);
        } catch (CameraAccessException | RuntimeException e) { fail(token, "原生相机打开失败：" + e.getMessage()); }
    }
    /** 在主线程建立预览和分析流，自动对焦仅在硬件支持时启用。 */
    private void configure(int token) {
        try {
            device.createCaptureSession(Arrays.asList(surface, reader.getSurface()), new CameraCaptureSession.StateCallback() {
                /** 会话就绪后开始连续采集。 */
                @Override public void onConfigured(CameraCaptureSession capture) {
                    if (token != generation) { capture.close(); return; }
                    session = capture;
                    try {
                        CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(surface); request.addTarget(reader.getSurface());
                        int[] modes = manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                        if (modes != null) for (int mode : modes) if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            request.set(CaptureRequest.CONTROL_AF_MODE, mode);
                        session.setRepeatingRequest(request.build(), null, main);
                    } catch (CameraAccessException | RuntimeException e) { fail(token, "原生相机采集失败：" + e.getMessage()); }
                }
                /** 不支持的输出组合不保留黑屏会话。 */
                @Override public void onConfigureFailed(CameraCaptureSession capture) { capture.close(); fail(token, "原生相机不支持此预览组合，请调整分辨率"); }
            }, main);
        } catch (CameraAccessException | RuntimeException e) { fail(token, "原生相机会话创建失败：" + e.getMessage()); }
    }
    /** 工作线程只处理最新帧，所有分支都关闭 Image。 */
    private void receive(ImageReader input, int token, int angle, Callback target) {
        try (Image image = input.acquireLatestImage()) {
            if (image == null || token != generation) return;
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer[] buffers = new ByteBuffer[3]; int[] rows = new int[3]; int[] strides = new int[3];
            for (int i=0;i<3;i++) { buffers[i]=planes[i].getBuffer(); rows[i]=planes[i].getRowStride(); strides[i]=planes[i].getPixelStride(); }
            Rect crop = image.getCropRect();
            byte[] nv = YuvFrames.nv21(buffers, rows, strides, crop.left, crop.top, crop.width(), crop.height());
            nv = YuvFrames.rotate(nv, crop.width(), crop.height(), angle);
            int width = angle % 180 == 0 ? crop.width() : crop.height();
            int height = angle % 180 == 0 ? crop.height() : crop.width();
            if (mirrored) Nv21Mirror.mirror(nv, width, height);
            if (token != generation) return;
            opened = true; starting = false; target.frame(nv, width, height);
        } catch (RuntimeException e) { main.post(() -> fail(token, "原生相机图像读取失败：" + e.getMessage())); }
    }
    /** 将 TextureView 默认拉伸的四角映射到规范化图像，镜像在旋转后应用。 */
    private void transform() {
        if (preview == null || preview.getWidth() == 0 || preview.getHeight() == 0) return;
        if (size != null && preview.getSurfaceTexture() != null) preview.getSurfaceTexture().setDefaultBufferSize(size.width, size.height);
        float w = preview.getWidth(), h = preview.getHeight();
        float[] source = {0,0,w,0,w,h,0,h};
        float[] dest = previewRotation == 90 ? new float[]{w,0,w,h,0,h,0,0}
                : previewRotation == 180 ? new float[]{w,h,0,h,0,0,w,0}
                : previewRotation == 270 ? new float[]{0,h,0,0,w,0,w,h} : source.clone();
        if (mirrored != frontFacing) for (int i=0;i<8;i+=2) dest[i] = w - dest[i];
        Matrix matrix = new Matrix(); matrix.setPolyToPoly(source,0,dest,0,4); preview.setTransform(matrix);
    }
    /** 当前代次失败只报告一次，关闭后自动重试由入口控制。 */
    private void fail(int token, String message) {
        if (token != generation) return;
        Callback target = callback; stop(); if (target != null) target.error(message);
    }
    /** 释放本次会话、输出、设备和线程，不释放视图拥有的 SurfaceTexture。 */
    @Override public void stop() {
        generation++; opened = false; starting = false;
        if (preview != null) preview.removeOnLayoutChangeListener(layoutListener);
        preview = null; callback = null;
        if (session != null) { session.close(); session = null; }
        if (device != null) { device.close(); device = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (surface != null) { surface.release(); surface = null; }
        if (worker != null) { worker.quitSafely(); worker = null; }
    }
    /** 返回真实供帧状态。 */
    @Override public boolean open() { return opened; }
    /** 返回打开在途状态。 */
    @Override public boolean opening() { return starting; }
    /** 镜像同时应用到下一分析帧与当前预览。 */
    @Override public void mirror(boolean enabled) { mirrored = enabled; transform(); }
}
