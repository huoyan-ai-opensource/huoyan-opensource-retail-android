package cc.huoyan.retail.recognition;

import org.junit.Test;
import static org.junit.Assert.*;

/** 验证启动阈值、轮次持续放行、基线重置及称重优先。 */
public class FrameChangeGateTest {
    /** 没有上一帧时不识别，等于阈值也不识别，超过才识别。 */
    @Test public void firstFrameAndThresholdBoundary() {
        FrameChangeGate gate = new FrameChangeGate();
        assertFalse(gate.accept("00", 4));
        assertFalse(gate.accept("0f", 4));
        assertTrue(gate.accept("f0", 4));
        assertTrue("已启动后静止新帧仍须放行", gate.accept("f0", 4));
    }
    /** 未通过阈值的帧也必须成为下一次比较的基线。 */
    @Test public void comparesPreviousFrameInsteadOfLastRecognizedFrame() {
        FrameChangeGate gate = new FrameChangeGate();
        assertFalse(gate.accept("0", 2));
        assertFalse(gate.accept("3", 2));
        assertFalse(gate.accept("7", 2));
        assertTrue(gate.accept("0", 2));
    }
    /** 汉明距离按位计数，不把一个十六进制字符误当成一位。 */
    @Test public void hexNibbleCanContainFourChangedBits() {
        FrameChangeGate gate = new FrameChangeGate();
        gate.accept("0", 3);
        assertTrue(gate.accept("f", 3));
    }
    /** 重开会话及尺寸变化建立新基线，不与旧画面触发比较。 */
    @Test public void resetAndChangedHashSizeEstablishBaseline() {
        FrameChangeGate gate = new FrameChangeGate();
        gate.accept("0", 0);
        gate.reset();
        assertFalse(gate.accept("f", 0));
        assertFalse(gate.accept("00", 0));
        assertFalse(gate.accept(null, 0));
        assertFalse(gate.accept("ff", 0));
    }
    /** 所有开关组合均以称重流程为优先。 */
    @Test public void weighingAlwaysBypassesImageGate() {
        assertTrue(FrameChangeGate.enabled(true, false));
        assertFalse(FrameChangeGate.enabled(true, true));
        assertFalse(FrameChangeGate.enabled(false, true));
        assertFalse(FrameChangeGate.enabled(false, false));
    }
    /** 五个档位均只需触发一次，静止后继续取得三次一致结果，不依赖超时。 */
    @Test public void singleChangeAtEverySensitivityCanCompleteResultConfirmation() {
        for (int level = 1; level <= 5; level++) {
            FrameChangeGate frames = new FrameChangeGate();
            RecognitionResultGate results = new RecognitionResultGate();
            int threshold = RecognitionSensitivity.threshold(level);
            String boundary = String.format(java.util.Locale.ROOT, "%04x", (1 << threshold) - 1);
            String changed = String.format(java.util.Locale.ROOT, "%04x", 1 << threshold);
            assertFalse(frames.accept("0000", threshold));
            assertFalse(frames.accept(boundary, threshold));
            // 与上一帧恰好相差 threshold + 1 位。
            assertTrue(frames.accept(changed, threshold));
            // 放入过程中可能先得到空结果，不能因此结束本轮或再次要求画面变化。
            assertFalse(results.accept(null, 0, false));
            for (int i = 1; i <= 3; i++) {
                assertTrue("档位 " + level + " 静止新帧继续放行", frames.accept(changed, threshold));
                assertEquals(i == 3, results.accept("tray:goods*2", i * 100L, false));
            }
            frames.reset();
            assertFalse("新一轮必须重新建立基线", frames.accept(changed, threshold));
            assertFalse("上一轮触发状态不得泄漏", frames.accept(changed, threshold));
        }
    }

}
