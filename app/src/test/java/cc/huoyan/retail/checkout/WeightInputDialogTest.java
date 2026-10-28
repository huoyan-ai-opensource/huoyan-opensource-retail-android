package cc.huoyan.retail.checkout;

import static org.junit.Assert.assertEquals;

import cc.huoyan.retail.common.DisplayFormatUtil;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * WeightInputDialog 小键盘输入纯逻辑单测（用户 2026-08-25 指示：重量小键盘 + 3 位小数限制）：
 * 数字追加与 3 位小数上限、点按钮行为（重复/空输入忽略）、退格、预填格式与秤读数口径一致。
 * 弹窗视图与点击交互依赖 Android 视图，留真机验证。
 */
public class WeightInputDialogTest {

    @Test
    public void decimalPlaces_noDot_returnsZero() {
        assertEquals(0, WeightInputDialog.decimalPlaces("2"));
        assertEquals(0, WeightInputDialog.decimalPlaces("0"));
    }

    @Test
    public void decimalPlaces_withDot_returnsFractionLength() {
        assertEquals(1, WeightInputDialog.decimalPlaces("0.5"));
        assertEquals(3, WeightInputDialog.decimalPlaces("0.123"));
        assertEquals(4, WeightInputDialog.decimalPlaces("1.2345"));
    }

    @Test
    public void decimalPlaces_nullOrEmpty_returnsZero() {
        assertEquals(0, WeightInputDialog.decimalPlaces(null));
        assertEquals(0, WeightInputDialog.decimalPlaces(""));
    }

    @Test
    public void appendDigit_integerPart_appendsNormally() {
        assertEquals("2", WeightInputDialog.appendDigit("", "2"));
        assertEquals("25", WeightInputDialog.appendDigit("2", "5"));
        assertEquals("0.5", WeightInputDialog.appendDigit("0.", "5"));
    }

    @Test
    public void appendDigit_threeDecimalDigits_fourthIgnored() {
        // 小数位已达 3 位上限，第 4 位输入被忽略（输入时即限制，不到确定校验）
        assertEquals("2.500", WeightInputDialog.appendDigit("2.500", "1"));
        assertEquals("0.123", WeightInputDialog.appendDigit("0.123", "9"));
    }

    @Test
    public void appendDigit_belowLimit_stillAppends() {
        assertEquals("2.50", WeightInputDialog.appendDigit("2.5", "0"));
        assertEquals("2.501", WeightInputDialog.appendDigit("2.50", "1"));
    }

    @Test
    public void appendDigit_exceedsMaxInputLength_ignored() {
        StringBuilder longInput = new StringBuilder("1234567890");
        assertEquals(longInput.toString(), WeightInputDialog.appendDigit(longInput.toString(), "5"));
    }

    @Test
    public void appendDigit_nullInput_treatedAsEmpty() {
        assertEquals("7", WeightInputDialog.appendDigit(null, "7"));
    }

    @Test
    public void appendDot_normalInput_appendsDot() {
        assertEquals("2.", WeightInputDialog.appendDot("2"));
        assertEquals("0.", WeightInputDialog.appendDot("0"));
    }

    @Test
    public void appendDot_duplicateDot_ignored() {
        assertEquals("2.5", WeightInputDialog.appendDot("2.5"));
        assertEquals("2.", WeightInputDialog.appendDot("2."));
    }

    @Test
    public void appendDot_emptyOrNullInput_returnsZeroDot() {
        // 空内容输入点：默认显示 0.（用户 2026-08-25 指示）
        assertEquals("0.", WeightInputDialog.appendDot(""));
        assertEquals("0.", WeightInputDialog.appendDot(null));
    }

    @Test
    public void backspace_removesLastChar() {
        assertEquals("2.5", WeightInputDialog.backspace("2.50"));
        assertEquals("2.", WeightInputDialog.backspace("2.5"));
        assertEquals("2", WeightInputDialog.backspace("2."));
    }

    @Test
    public void backspace_singleCharOrEmpty_becomesEmpty() {
        assertEquals("", WeightInputDialog.backspace("2"));
        assertEquals("", WeightInputDialog.backspace(""));
        assertEquals("", WeightInputDialog.backspace(null));
    }

    @Test
    public void keyboardInputFlow_fullThreeDecimalEntry() {
        // 模拟键盘连击链路：2 → . → 5 → 0 → 0 → 多余的 1 被忽略
        String input = WeightInputDialog.appendDigit("", "2");
        input = WeightInputDialog.appendDot(input);
        input = WeightInputDialog.appendDigit(input, "5");
        input = WeightInputDialog.appendDigit(input, "0");
        input = WeightInputDialog.appendDigit(input, "0");
        input = WeightInputDialog.appendDigit(input, "1");
        assertEquals("2.500", input);
        assertEquals(true, WeightInputDialog.decimalPlaces(input) <= WeightInputDialog.MAX_DECIMAL_PLACES);
    }

    @Test
    public void prefillFormat_alignsWithScaleWeightDisplay() {
        String prefill = BigDecimal.valueOf(2.5d)
                .setScale(WeightInputDialog.MAX_DECIMAL_PLACES, RoundingMode.HALF_UP).toPlainString();
        assertEquals(DisplayFormatUtil.formatWeightKg(2.5d, Locale.US), prefill);
    }
}
