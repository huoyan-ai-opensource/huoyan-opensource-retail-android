package cc.huoyan.retail.device.camera;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 相机重开判定纯逻辑单测（2026-08-27 用户反馈①③）：shouldReopenCamera 覆盖
 * 幂等（相机已开不重开）/ 首次关闭（无历史节流直接重开）/ 节流窗内抑制（重开在途
 * 不重复发起）/ 节流边界（窗满放行）/ 重开在途防排队（cameraRestarting，2026-08-27
 * 反馈① 振荡根因：看门狗在重开在途观察到"关闭"再次发起 → 周期性关开黑屏）。
 * shouldNegotiatePreviewSize 覆盖协商仅首次初始化执行（重开场景跳过，防 onConnect
 * setCamaraSize 关开振荡）。
 * <p>setPreview→startCameraPreview 的相机链路依赖 Android 运行时与 UVC 硬件，
 * 不在纯 JUnit 覆盖范围（真机黑屏复验由平台场景/人工验收）。
 */
public class CameraUtilsTest {

    private static final long THROTTLE_MS = 5000L;

    @Test
    public void cameraOpen_neverReopens() {
        // 幂等：相机已开不重复开（正常预览路径零扰动）
        assertFalse(CameraUtils.shouldReopenCamera(true, false, 0L, THROTTLE_MS + 1, THROTTLE_MS));
        assertFalse(CameraUtils.shouldReopenCamera(true, false, 0L, Long.MAX_VALUE, THROTTLE_MS));
        assertFalse(CameraUtils.shouldReopenCamera(true, true, 0L, Long.MAX_VALUE, THROTTLE_MS));
    }

    @Test
    public void cameraClosed_noRestartHistory_reopensImmediately() {
        // 首次发现关闭（lastReopen=0，elapsedRealtime 远超节流窗）→ 立即重开（跳过观察窗语义）
        assertTrue(CameraUtils.shouldReopenCamera(false, false, 0L, 60000L, THROTTLE_MS));
    }

    @Test
    public void cameraClosed_withinThrottleWindow_suppressed() {
        // 重开在途（距上次发起 < 节流窗）：相机仍未开也不重复发起，防反复 new CameraHelper
        assertFalse(CameraUtils.shouldReopenCamera(false, false, 60000L, 60000L + THROTTLE_MS - 1, THROTTLE_MS));
    }

    @Test
    public void cameraClosed_throttleWindowElapsed_reopens() {
        // 节流边界：距上次发起 ≥ 节流窗（含恰好等于）→ 放行重开（此前重开失败的兜底重试）
        assertTrue(CameraUtils.shouldReopenCamera(false, false, 60000L, 60000L + THROTTLE_MS, THROTTLE_MS));
        assertTrue(CameraUtils.shouldReopenCamera(false, false, 60000L, 60000L + THROTTLE_MS + 1, THROTTLE_MS));
    }

    @Test
    public void cameraClosed_restartInFlight_neverReopens() {
        // 重开在途防排队（2026-08-27 反馈① 振荡根因）：节流已满甚至无历史节流，
        // 只要重开在途（发起后未收到 onCameraOpen）就不重复发起——在途期间相机
        // 恒为"未开"，放行会二次 new CameraHelper 周期性关开（黑 1-2s→恢复→再黑）
        assertFalse(CameraUtils.shouldReopenCamera(false, true, 0L, 60000L, THROTTLE_MS));
        assertFalse(CameraUtils.shouldReopenCamera(false, true, 60000L, 60000L + THROTTLE_MS, THROTTLE_MS));
        assertFalse(CameraUtils.shouldReopenCamera(false, true, 60000L, 60000L + THROTTLE_MS + 1, THROTTLE_MS));
        assertFalse(CameraUtils.shouldReopenCamera(false, true, 0L, Long.MAX_VALUE, THROTTLE_MS));
    }

    @Test
    public void negotiatePreviewSize_firstInitOnly() {
        // 首次初始化（相机未开、无在途协商、进程内未完成过协商）→ 注册 onConnect 协商
        // （首次打开 cameraSizeList 为空，需 getCameraSize+setCamaraSize 二次应用 SP 分辨率）
        assertTrue(CameraUtils.shouldNegotiatePreviewSize(false, false, false));
    }

    @Test
    public void negotiatePreviewSize_reopenScenario_skipped() {
        // 重开场景（进程内已完成过协商）→ 跳过：相机分辨率已按 SP 在
        // onDeviceOpen→getPreviewSize 应用，再协商 setCamaraSize 会 close+reopen
        // 造成 1-2s 黑屏并触发看门狗周期性振荡（2026-08-27 反馈① 根因）
        assertFalse(CameraUtils.shouldNegotiatePreviewSize(false, false, true));
        assertFalse(CameraUtils.shouldNegotiatePreviewSize(true, false, true));
    }

    @Test
    public void negotiatePreviewSize_inFlightOrOpen_skipped() {
        // 协商在途不重复注册；相机已开无需协商（挂 surface 即可）
        assertFalse(CameraUtils.shouldNegotiatePreviewSize(false, true, false));
        assertFalse(CameraUtils.shouldNegotiatePreviewSize(true, false, false));
        assertFalse(CameraUtils.shouldNegotiatePreviewSize(true, true, false));
    }
}
