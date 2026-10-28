package cc.huoyan.retail.device.camera;

import cc.huoyan.retail.common.MainHandler;

import android.graphics.SurfaceTexture;
import android.view.TextureView;

import androidx.annotation.NonNull;


public class CameraUtils {
    private volatile static CameraUtils INSTANCE;
    /** 返回原管理器，仅供 UVC 专项兼容测试使用。 */
    public CameraUsbManager getCameraUsbManager() { return CameraUsbManager.getInstance(); }
    /** 返回进程级预览入口。 */
    public static synchronized CameraUtils getInstance() {
        if (INSTANCE == null) INSTANCE = new CameraUtils();
        return INSTANCE;
    }
    /** 页面绑定由统一入口选择对应后端。 */
    public void setPreview(AutoFitTextureView view) { CameraController.getInstance().bind(view); }

    /**
     * 判断是否应发起相机重开（2026-08-27 用户反馈①③ 共用纯逻辑）。
     * <p>相机已开不重复开（幂等）；重开在途（cameraRestarting，发起方尚未收到
     * onCameraOpen）不重复发起，否则看门狗在途期间再次 new CameraHelper 反复
     * 关开相机（振荡根源）；相机关闭且距上次发起重开超过节流间隔才放行：
     * setPreview→开相机实测 ≤3s，重开在途期间相机仍未开，不节流会重复发起。
     *
     * @param cameraOpen       相机当前是否已开
     * @param cameraRestarting 相机重开是否在途（发起后未收到 onCameraOpen）
     * @param lastReopenMs     上次发起重开时刻（SystemClock.elapsedRealtime；0=尚未发起过）
     * @param nowMs            当前时刻（SystemClock.elapsedRealtime）
     * @param throttleMs       重开节流间隔（毫秒）
     * @return true=应发起重开
     */
    public static boolean shouldReopenCamera(boolean cameraOpen, boolean cameraRestarting,
                                             long lastReopenMs, long nowMs, long throttleMs) {
        return !cameraOpen && !cameraRestarting && nowMs - lastReopenMs >= throttleMs;
    }

    /**
     * 判断 setPreview 是否应注册 onConnect 分辨率协商（2026-08-27 用户反馈① 纯逻辑）。
     * <p>仅首次初始化（进程内尚未完成过协商且无在途协商）且相机未开时执行：
     * 首次打开 cameraSizeList 为空，onDeviceOpen 可能以默认分辨率开相机，需
     * onConnect 后 getCameraSize+setCamaraSize 二次协商应用 SP 分辨率；重开场景
     * cameraSizeList 已在，onDeviceOpen→getPreviewSize 直接按 SP 分辨率开，
     * 再协商会 close+reopen 造成 1-2s 黑屏并触发看门狗周期性振荡。
     *
     * @param cameraOpen            相机当前是否已开
     * @param previewInitPending    是否已有协商在途
     * @param previewSizeNegotiated 进程内是否已完成过协商
     * @return true=应注册协商回调
     */
    public static boolean shouldNegotiatePreviewSize(boolean cameraOpen, boolean previewInitPending,
                                                     boolean previewSizeNegotiated) {
        return !cameraOpen && !previewInitPending && !previewSizeNegotiated;
    }
}
