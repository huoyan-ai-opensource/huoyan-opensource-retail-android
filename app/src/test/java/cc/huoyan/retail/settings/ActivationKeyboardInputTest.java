package cc.huoyan.retail.settings;

import org.junit.Test;
import static org.junit.Assert.*;

/** 手输字符边界及历史回填兼容，不改变扫码规范化测试。 */
public class ActivationKeyboardInputTest {
    /** 大写 L 与 1 合法，易混字符及小写不能混入。 */
    @Test public void rejectsInvalidCharactersWithoutConvertingCase() {
        assertTrue(ActivationKeyboardInput.accepts("L1AZ29"));
        for (String value : new String[]{"0", "I", "O", "l", "a", " ", "-", "１", "A\n"})
            assertFalse(value, ActivationKeyboardInput.accepts(value));
    }
    /** 长度满十六位才能提交，拒绝第十七位。 */
    @Test public void enforcesLength() {
        assertTrue(ActivationKeyboardInput.complete("Z2FBW9L7M78R586H"));
        assertFalse(ActivationKeyboardInput.complete("Z2FB"));
        assertFalse(ActivationKeyboardInput.accepts("Z2FBW9L7M78R586H1"));
        assertFalse(ActivationKeyboardInput.complete(null));
    }
    /** 只恢复合法手输码，不将其他扫码协议转换成不同串号。 */
    @Test public void restoresOnlyCompatibleStoredCodes() {
        assertEquals("Z2FBW9L7M78R586H", ActivationKeyboardInput.restore("Z2FB-W9L7-M78R-586H"));
        assertEquals("", ActivationKeyboardInput.restore("HY2-87IA-VY86-QZXY"));
        assertEquals("", ActivationKeyboardInput.restore("abcDEF"));
        assertEquals("", ActivationKeyboardInput.restore(null));
    }
}
