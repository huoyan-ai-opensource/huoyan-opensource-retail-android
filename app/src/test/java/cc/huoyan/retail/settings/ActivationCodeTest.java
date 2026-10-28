package cc.huoyan.retail.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 激活码纯逻辑单测（用户 2026-08-27）：normalizeActivateCode 规范化带连字符
 * （后端存储带连字符 HY2-87IA-VY86-QZXY，提交/SP 存储需同格式才能匹配）；
 * segmentCode 分段——16 位激活码 4-4-4-4、其余长度（输入
 * 前缀等）每 4 位一段，且兼容带连字符输入（先去连字符再分段）。
 * 弹窗 setText 的控件渲染依赖 Android 运行时，不在纯 JUnit 覆盖范围。
 */
public class ActivationCodeTest {

    @Test
    public void normalizeActivateCode_dashedInput_idempotent() {
        // 带连字符输入幂等：去连字符重新分组后结果不变
        assertEquals("HY28-7IAV-Y86Q-ZXYA",
                ActivationCode.normalize("HY28-7IAV-Y86Q-ZXYA"));
    }

    @Test
    public void normalizeActivateCode_sixteenChar_groupsFourFourFourFour() {
        // 16 位旧码纯码 → 4+4+4+4 带连字符
        assertEquals("HY28-7IAV-Y86Q-ZXYA",
                ActivationCode.normalize("HY287IAVY86QZXYA"));
    }

    @Test
    public void normalizeActivateCode_abnormalLength_returnsAsIs() {
        // 异常长度不猜测分组，原样返回（含 null/空串）
        assertEquals("HY287IA", ActivationCode.normalize("HY287IA"));
        assertEquals("HY2-87I", ActivationCode.normalize("HY2-87I"));
        assertEquals("", ActivationCode.normalize(""));
        assertEquals(null, ActivationCode.normalize(null));
    }

    @Test
    public void segmentCode_sixteenChar_keepsFourFourFourFour() {
        // 16 位旧码行为不变：4-4-4-4
        assertEquals(Arrays.asList("HY28", "7IAV", "Y86Q", "ZXYA"),
                ActivationCode.segmentCode("HY287IAVY86QZXYA"));
    }

    @Test
    public void segmentCode_partialInput_chunksByFour() {
        // 输入过程中的前缀沿用每 4 位一段
        assertEquals(Collections.singletonList("HY2"), ActivationCode.segmentCode("HY2"));
        assertEquals(Arrays.asList("HY28", "7"), ActivationCode.segmentCode("HY287"));
        assertEquals(Arrays.asList("HY28", "7IAV", "Y86Q", "ZX"),
                ActivationCode.segmentCode("HY287IAVY86QZX"));
    }

    @Test
    public void segmentCode_dashedInput_stripsDashesFirst() {
        // SP 回填带连字符（用户 2026-08-27）：先去连字符再分段，与纯码结果一致
        assertEquals(Arrays.asList("HY28", "7IAV", "Y86Q", "ZXYA"),
                ActivationCode.segmentCode("HY28-7IAV-Y86Q-ZXYA"));
        assertEquals(Collections.singletonList("HY2"),
                ActivationCode.segmentCode("HY2-"));
    }

    @Test
    public void segmentCode_emptyOrNull_returnsEmpty() {
        assertEquals(Collections.emptyList(), ActivationCode.segmentCode(""));
        assertEquals(Collections.emptyList(), ActivationCode.segmentCode(null));
        assertEquals(Collections.emptyList(), ActivationCode.segmentCode("-"));
    }
}
