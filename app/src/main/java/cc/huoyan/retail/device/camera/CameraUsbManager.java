package cc.huoyan.retail.device.camera;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import cc.huoyan.retail.data.SPManager;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;


public class CameraUsbManager {

    public interface CameraUsbManagerListener {
        void onFrame(CameraUsbEntity entity);
    }

    private static Map<String, CameraUsbManagerListener> listenerMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String TAG = CameraUsbManager.class.getSimpleName();
    public volatile static CameraUsbManager INSTANCE;

    private ICameraHelper mCameraHelper;

    private AutoFitTextureView mCameraViewMain;

    private TextureView mCameraView;

    private final Map<String, UsbDevice> usbDeviceHashMap = new LinkedHashMap<>();
    private String currentCameraId;

    public Size cameraSize;

    private final List<Size> cameraSizeList = new ArrayList<>();

    public int rotation;
    private boolean previewPaused = false;
    private volatile boolean mirrorEnabled = SPManager.getInstance().getBoolean("cameraMirror");
    // 相机重开在途标志（2026-08-27 用户反馈① 振荡根因）：startCameraPreview 发起
    // 重开到 onCameraOpen 完成之间相机处于"未开"态，看门狗/巡检若据此再次发起
    // 重开会周期性关开相机（黑 1-2s→恢复→再黑）；在途期间调用方应跳过重复发起
    private volatile boolean cameraRestarting = false;

    private boolean selected = false;
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (!usbDeviceHashMap.keySet().isEmpty()) {
                if (!selected) {
                    UsbDevice usbDevice = usbDeviceHashMap.get(usbDeviceHashMap.keySet().iterator().next());
                    currentCameraId = usbDevice.getDeviceId() + "_" + usbDevice.getVendorId() + "_" + usbDevice.getProductId();
                    Log.d(TAG, "CameraId: " + currentCameraId);
                    SPManager.getInstance().putString("usb_camera", currentCameraId);
                    mCameraHelper.selectDevice(usbDevice);
                    selected = true;
                } else {
                    handler.sendEmptyMessageDelayed(1, 200);
                }
            }
        }
    };


    private CameraUsbManager() {
    }

    /** USB 会话代次，过滤释放后已投递的回调。 */
    private int sessionGeneration;
    private CameraStateListener cameraStateListener;
    /** 当前后端错误出口，释放时清除。 */
    private java.util.function.Consumer<String> errorListener;
    /** 设置统一入口的错误出口。 */
    public void setErrorListener(java.util.function.Consumer<String> listener) { errorListener = listener; }
    /** 完全释放旧 USB 会话与监控，供相机类型切换使用。 */
    public void releaseCamera() {
        sessionGeneration++;
        handler.removeCallbacksAndMessages(null);
        cameraRestarting = false;
        cameraStateListener = null;
        errorListener = null;
        selected = false;
        previewPaused = false;
        if (mCameraHelper != null) {
            mCameraHelper.setStateCallback(null);
            mCameraHelper.closeCamera();
            mCameraHelper.release();
            mCameraHelper = null;
        }
        mCameraViewMain = null;
        mCameraView = null;
        cameraSize = null;
        cameraSizeList.clear();
        usbDeviceHashMap.clear();
    }


    public void addListener(String key, CameraUsbManagerListener listener) {
        listenerMap.put(key, listener);
    }

    public void removeListener(String key) {
        listenerMap.remove(key);
    }

    public static CameraUsbManager getInstance() {
        if (INSTANCE == null) {
            synchronized (CameraUsbManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CameraUsbManager();
                }
            }
        }
        return INSTANCE;
    }

    private void selectDevice(UsbDevice device) {
        String cameraId = device.getDeviceId() + "_" + device.getVendorId() + "_" + device.getProductId();
        Log.d(TAG, "cameraId：" + cameraId);
        Log.d(TAG, "currentCameraId：" + currentCameraId);
        if (!TextUtils.isEmpty(currentCameraId) && cameraId.contains(currentCameraId)) {
            mCameraHelper.selectDevice(device);
            Log.d(TAG, "CameraId: " + currentCameraId);
            selected = true;
        }
    }

    public static String getDefaultId(int type) {
        Log.d("getDefaultId", "getPreviewSize:" + Build.MODEL);
        if (Build.MODEL.equals("C50")) {
            if (type == 1) {
                return "2008_3034_20871";
            } else if (type == 2) {
                return "2007_2316_13179";
            }
        } else {
            return "";
        }
        return "";
    }

    private final ICameraHelper.StateCallback mStateListener = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {
            Log.d(TAG, "onAttach:");
            usbDeviceHashMap.put(device.getDeviceName(), device);
            selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            Log.d(TAG, "onDeviceOpen:");
            getPreviewSize();
            if (cameraSize != null) {
                mCameraHelper.openCamera(cameraSize);
            } else {
                mCameraHelper.openCamera();
            }

        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            Log.d(TAG, "onCameraOpen:");
            cameraRestarting = false;

            mCameraHelper.startPreview();
            Size size = mCameraHelper.getPreviewSize();
            if (size != null) {
                if (cameraSize == null) {
                    cameraSize = size;
                }
                int width = size.width;
                int height = size.height;
                //auto aspect ratio
                // 崩溃修复（2026-08-27，C50 真机）：切换摄像头时 startCameraPreview(mCameraViewMain)
                // 传入的 surfaceView 可能未 attach（mCameraViewMain 为 null），此处空引用导致 FATAL NPE。
                if (mCameraViewMain != null) {
                    mCameraViewMain.setAspectRatio(width, height);
                }

            }

            if (mCameraViewMain != null && mCameraViewMain.isAvailable() && mCameraViewMain.getSurfaceTexture() != null) {
                mCameraHelper.addSurface(mCameraViewMain.getSurfaceTexture(), false);
            }

            if (mCameraView != null && mCameraView.isAvailable() && mCameraView.getSurfaceTexture() != null) {
                mCameraHelper.addSurface(mCameraView.getSurfaceTexture(), false);
            }
            mCameraHelper.setFrameCallback(frame -> {

                byte[] nv21 = new byte[frame.remaining()];
                frame.get(nv21, 0, nv21.length);
                if (mirrorEnabled) Nv21Mirror.mirror(nv21, size.width, size.height);
                listenerMap.forEach(new BiConsumer<String, CameraUsbManagerListener>() {
                    @Override
                    public void accept(String s, CameraUsbManagerListener listener) {
                        listener.onFrame(new CameraUsbEntity(nv21, size.width, size.height));
                    }
                });
            }, UVCCamera.PIXEL_FORMAT_NV21);

            if (cameraStateListener != null) {
                cameraStateListener.onConnect(currentCameraId);
            }
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            Log.d(TAG, "onCameraClose:");

            removeSurfaceQuietly(mCameraViewMain == null ? null : mCameraViewMain.getSurfaceTexture());
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            Log.d(TAG, "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            Log.d(TAG, "onDetach:");
        }

        @Override
        public void onCancel(UsbDevice device) {
            cameraRestarting = false;
            if (errorListener != null) errorListener.accept("USB 相机授权已取消");
        }

    };

    /** 将库内已排队的状态事件限制在本次 USB 会话内。 */
    private ICameraHelper.StateCallback guardedCallback(int token) {
        return new ICameraHelper.StateCallback() {
            /** 设备接入只交给当前会话。 */
            @Override public void onAttach(UsbDevice d) { if (token == sessionGeneration) mStateListener.onAttach(d); }
            /** 设备授权后打开当前相机。 */
            @Override public void onDeviceOpen(UsbDevice d, boolean first) { if (token == sessionGeneration) mStateListener.onDeviceOpen(d, first); }
            /** 当前相机就绪后挂接预览。 */
            @Override public void onCameraOpen(UsbDevice d) { if (token == sessionGeneration) mStateListener.onCameraOpen(d); }
            /** 旧会话关闭不能移除新预览。 */
            @Override public void onCameraClose(UsbDevice d) { if (token == sessionGeneration) mStateListener.onCameraClose(d); }
            /** 当前设备关闭事件。 */
            @Override public void onDeviceClose(UsbDevice d) { if (token == sessionGeneration) mStateListener.onDeviceClose(d); }
            /** 拔出当前设备后由统一入口提示。 */
            @Override public void onDetach(UsbDevice d) {
                if (token != sessionGeneration) return;
                mStateListener.onDetach(d);
                String id = d.getDeviceId() + "_" + d.getVendorId() + "_" + d.getProductId();
                if (id.equals(currentCameraId) && errorListener != null) errorListener.accept("USB 相机已断开");
            }
            /** 用户取消授权后停止在途标志。 */
            @Override public void onCancel(UsbDevice d) { if (token == sessionGeneration) mStateListener.onCancel(d); }
            /** 驱动错误统一使用项目提示。 */
            @Override public void onError(UsbDevice d, com.herohan.uvcapp.CameraException e) {
                if (token != sessionGeneration) return;
                cameraRestarting = false;
                if (errorListener != null) errorListener.accept("USB 相机打开失败：" + e.getMessage());
            }
        };
    }

    public void startCameraPreview(AutoFitTextureView cameraViewMain) {
        handler.removeCallbacksAndMessages(null);
        if (mCameraHelper != null) {
            mCameraHelper.setStateCallback(null);
            mCameraHelper.release();
        }
        selected = false;
        mCameraHelper = new CameraHelper();
        this.currentCameraId = SPManager.getInstance().getString("usb_camera", getDefaultId(1));
        mCameraViewMain = cameraViewMain;
        applyMirror(mCameraViewMain);
        previewPaused = false;
        usbDeviceHashMap.clear();
        // 重开在途置位（onCameraOpen 清零）：设备选择/打开全程相机未开，调用方
        // 据此跳过重复发起（2026-08-27 用户反馈① 振荡根因）
        cameraRestarting = true;
        mCameraHelper.setStateCallback(guardedCallback(++sessionGeneration));
        handler.sendEmptyMessageDelayed(1, 500);
    }

    /**
     * 次级相机页面（学习页/相机设置页/扫码弹窗等）挂接预览 surface。
     * <p>2026-08-27 根因修复：MainActivity.onPause → {@link #pausePreview()} 置位
     * previewPaused 并摘除全部 surface 后进入次级页面，本方法仅 addSurface 新视图而
     * 未恢复标记，导致预览链路停摆（黑屏、帧回调停、拍照帧无效）。此处对齐
     * {@link #startCameraPreview(AutoFitTextureView)} / {@link #resumePreview()} 语义：
     * 恢复标记并幂等重启预览（CameraInternal.startPreview 为 synchronized 且重设
     * display，预览运行中重复调用安全；相机未开时为 no-op）。
     *
     * @param cameraView 次级页面预览视图（可用状态时挂接 surface）
     */
    public void cameraPreview(TextureView cameraView) {
        if (mCameraHelper == null) {
            return;
        }
        this.mCameraView = cameraView;
        applyMirror(mCameraView);

        if (previewPaused) {
            previewPaused = false;
            if (mCameraHelper.isCameraOpened()) {
                mCameraHelper.startPreview();
            }
        }
        if (mCameraView != null && mCameraView.isAvailable() && mCameraView.getSurfaceTexture() != null) {
            mCameraHelper.addSurface(mCameraView.getSurfaceTexture(), false);
        }
    }

    /**
     * 切换摄像头。
     * <p>mCameraViewMain 允许为 null（surfaceView 未 attach 时切换）：摄像头枚举/打开照常进行，
     * onCameraOpen 已对 mCameraViewMain 判空（2026-08-27 崩溃修复），预览 surface 由后续
     * attach 后的 cameraPreview/resumePreview 补挂。
     *
     * @param camaraId 目标摄像头 id
     */
    public void switchCamara(String camaraId) {
        if (mCameraHelper != null && mCameraHelper.isCameraOpened() && camaraId.equals(currentCameraId)) {
            return;
        }
        mCameraHelper.closeCamera();
        startCameraPreview(mCameraViewMain);
    }

    /**
     * 重设预览尺寸后重开摄像头（分辨率切换）。
     * <p>同 {@link #switchCamara(String)}：mCameraViewMain 为 null 时允许重开，判空见 onCameraOpen。
     */
    public void setCamaraSize() {
        if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
            mCameraHelper.closeCamera();
            startCameraPreview(mCameraViewMain);
        }
    }


    public List<UsbDevice> getUsbDeviceList() {
        List<UsbDevice> usbDeviceList = new ArrayList<>();
        usbDeviceHashMap.forEach((s, usbDevice) -> usbDeviceList.add(usbDevice));
        return usbDeviceList;
    }

    public List<Size> getCameraSize() {
        List<Size> sizeList = mCameraHelper == null ? null : mCameraHelper.getSupportedSizeList();
        if (sizeList == null || sizeList.isEmpty()) {
            return cameraSizeList;
        }
        cameraSizeList.clear();
        int type = 0;
        if (cameraSize == null) {
            type = sizeList.get(0).type;
        } else {
            type = cameraSize.type;
        }
        for (Size size : sizeList) {
            if (size.type == type) {
                cameraSizeList.add(size);
            }
        }
        return cameraSizeList;
    }

    public void getPreviewSize() {
        String fbl = SPManager.getInstance().getString("fbl_" + currentCameraId, "0");
        int width = CameraPreviewSize.width;
        int height = CameraPreviewSize.height;
        if (!TextUtils.isEmpty(fbl)) {
            String[] fblwh = fbl.split("\\*");
            if (fblwh.length > 1) {
                width = Integer.parseInt(fblwh[0]);
                height = Integer.parseInt(fblwh[1]);
            }
        }
        for (Size size : cameraSizeList) {
            if (size.width == width && size.height == height) {
                cameraSize = size;
                return;
            }
        }
    }

    public void stopCamera() {
        cameraStateListener = null;
        cameraSize = null;
        previewPaused = false;
        // 主动关相机不是重开在途：在途重开若被本方法打断，onCameraOpen 不会到来，
        // 标志不清零会永久阻塞后续重开（学习页/相机页看门狗）
        cameraRestarting = false;
        if (mCameraHelper != null) {
            mCameraHelper.closeCamera();
            if (mCameraViewMain != null) {
                removeSurfaceQuietly(mCameraViewMain.getSurfaceTexture());
                mCameraViewMain = null;
            }
        }
    }

    public void pausePreview() {
        if (mCameraHelper == null || previewPaused) {
            return;
        }
        if (mCameraViewMain != null) {
            removeSurfaceQuietly(mCameraViewMain.getSurfaceTexture());
        }
        if (mCameraView != null) {
            removeSurfaceQuietly(mCameraView.getSurfaceTexture());
        }
        previewPaused = true;
    }

    public void resumePreview() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened() || !previewPaused) {
            return;
        }
        if (mCameraViewMain != null && mCameraViewMain.isAvailable() && mCameraViewMain.getSurfaceTexture() != null) {
            mCameraHelper.addSurface(mCameraViewMain.getSurfaceTexture(), false);
        }
        if (mCameraView != null && mCameraView.isAvailable() && mCameraView.getSurfaceTexture() != null) {
            mCameraHelper.addSurface(mCameraView.getSurfaceTexture(), false);
        }
        previewPaused = false;
    }

    /**
     * 防御性摘除预览 surface（2026-08-27 C50 崩溃根源修复）。
     * <p>崩溃链路：识别页进设置页触发 MainActivity 销毁 → stopCamera/closeCamera →
     * onCameraClose 对已销毁页面的 TextureView 取 {@code getSurfaceTexture()} 得
     * null 仍调 {@code CameraHelper.removeSurface(null)}——库侧 removeSurface 仅把
     * 摘除投递到相机 HandlerThread，其 {@code fetchSurface(null)} 因 instanceof 全
     * 不命中抛 {@link UnsupportedOperationException} 且异常表未覆盖该段（调用点
     * try-catch 无法捕获异步异常），未捕获即进程崩溃（tombstone 实证：崩溃时刻
     * 12:33:15.400 = onCameraClose 回调后 16ms）。此处统一空值跳过 + 同步异常吞掉，
     * 从源头保证不向库传入非法 surface；null 摘除本就无意义，跳过不影响正常摘除。
     *
     * @param surfaceTexture 预览 surface；null/已失效安全跳过
     */
    private void removeSurfaceQuietly(SurfaceTexture surfaceTexture) {
        if (mCameraHelper == null || surfaceTexture == null) {
            return;
        }
        try {
            mCameraHelper.removeSurface(surfaceTexture);
        } catch (Exception e) {
            Log.e(TAG, "removeSurface: " + e.getMessage());
        }
    }

    public void removePreview(SurfaceTexture surfaceTexture) {
        removeSurfaceQuietly(surfaceTexture);
    }

    public void setCameraStateListener(CameraStateListener cameraStateListener) {
        this.cameraStateListener = cameraStateListener;
    }

    public String getCurrentCameraId() {
        return currentCameraId;
    }

    //旋转摄像头
    public void rotateBy() {
        rotation = SPManager.getInstance().getInt("cameraRotation", 180);
        if (mCameraHelper != null) {
            mCameraHelper.setPreviewConfig(
                    mCameraHelper.getPreviewConfig().setRotation(rotation));
        }
    }

    /** 同步预览和识别输入的镜像方向，保证检测坐标与图像一致。 */
    public void setMirrorEnabled(boolean enabled) {
        mirrorEnabled = enabled;
        SPManager.getInstance().putBoolean("cameraMirror", enabled);
        applyMirror(mCameraViewMain);
        applyMirror(mCameraView);
    }

    private void applyMirror(TextureView view) {
        if (view != null) view.setScaleX(mirrorEnabled ? -1f : 1f);
    }

    public boolean isCameraOpen() {
        return mCameraHelper != null && mCameraHelper.isCameraOpened();
    }

    /**
     * 相机重开是否在途（startCameraPreview 发起后尚未收到 onCameraOpen）。
     * <p>2026-08-27 用户反馈①：学习页/相机页看门狗在重开在途期间观察到相机
     * "未开"会再次发起重开，造成周期性关开振荡；重开决策前应检查本标志。
     *
     * @return true=重开在途（不应再发起重开）
     */
    public boolean isCameraRestarting() {
        return cameraRestarting;
    }
}
