package cc.huoyan.retail.recognition;

import org.junit.Test;
import static org.junit.Assert.*;

/** 验证五档在真实帧门控中的触发边界，防止灵敏度方向反转。 */
public class RecognitionSensitivityTest {
    /** 逐档验证距离等于阈值不触发，超过一位才触发。 */
    @Test public void levelsControlStrictHammingBoundary() {
        int[] thresholds = {8, 6, 4, 2, 1};
        for (int level = 1; level <= 5; level++) {
            int threshold = RecognitionSensitivity.threshold(level);
            assertEquals(thresholds[level - 1], threshold);
            for (int distance = 0; distance <= 12; distance++) {
                FrameChangeGate gate = new FrameChangeGate();
                gate.accept("0000", threshold);
                String changed = String.format(java.util.Locale.ROOT, "%04x", (1 << distance) - 1);
                assertEquals("档位 " + level + " 距离 " + distance,
                        distance > thresholds[level - 1], gate.accept(changed, threshold));
            }
        }
    }

    /** 缺省和异常配置保持标准档，与设置页默认展示一致。 */
    @Test public void invalidLevelsUseStandardDefault() {
        assertEquals(3, RecognitionSensitivity.DEFAULT_LEVEL);
        assertEquals(4, RecognitionSensitivity.threshold(RecognitionSensitivity.DEFAULT_LEVEL));
        for (int level : new int[]{Integer.MIN_VALUE, 0, 6, Integer.MAX_VALUE}) {
            assertEquals(3, RecognitionSensitivity.validLevel(level));
            assertEquals(4, RecognitionSensitivity.threshold(level));
        }
    }
}
