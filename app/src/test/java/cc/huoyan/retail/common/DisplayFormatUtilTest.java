package cc.huoyan.retail.common;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Test;

public class DisplayFormatUtilTest {
    @Test public void largeCentAmountsRetainExactDecimalDigits() {
        assertEquals("40000000.00", DisplayFormatUtil.formatAmountCny(4_000_000_000L, Locale.US));
        assertEquals("92233720368547758.07", DisplayFormatUtil.formatAmountCny(Long.MAX_VALUE, Locale.US));
    }


    @Test
    public void formatWeightKg_zero_us_returnsThreeDecimals() {
        assertEquals("0.000", DisplayFormatUtil.formatWeightKg(0d, Locale.US));
    }

    @Test
    public void formatWeightKg_positiveValue_us_returnsThreeDecimals() {
        assertEquals("0.500", DisplayFormatUtil.formatWeightKg(0.5d, Locale.US));
    }

    @Test
    public void formatWeightKg_moreDigits_us_roundsHalfUp() {
        // 1.2346 -> 1.235 with HALF_UP at 3rd decimal
        assertEquals("1.235", DisplayFormatUtil.formatWeightKg(1.2346d, Locale.US));
    }

    @Test
    public void formatWeightKg_roundsDown_us() {
        // 1.2344 -> 1.234
        assertEquals("1.234", DisplayFormatUtil.formatWeightKg(1.2344d, Locale.US));
    }

    @Test
    public void formatWeightKg_zero_china_returnsThreeDecimals() {
        assertEquals("0.000", DisplayFormatUtil.formatWeightKg(0d, Locale.CHINA));
    }

    @Test
    public void formatWeightKg_china_usesPeriodDecimalSeparator() {
        // Chinese number format uses U+002E (period) as decimal separator per CLDR.
        assertEquals("1.234", DisplayFormatUtil.formatWeightKg(1.2344d, Locale.CHINA));
    }

    @Test
    public void formatWeightKg_germany_usesCommaDecimalSeparator() {
        // Locale-aware behavior: Germany uses comma as decimal separator.
        assertEquals("1,500", DisplayFormatUtil.formatWeightKg(1.5d, Locale.GERMANY));
    }

    @Test
    public void formatWeightKg_doesNotGroupThousands() {
        // 12345.6780 -> "12345.678" (no thousands grouping, matches BigDecimal#toPlainString())
        assertEquals("12345.678", DisplayFormatUtil.formatWeightKg(12345.678d, Locale.US));
    }

    @Test
    public void formatAmountCny_zeroCents_us_returnsTwoDecimals() {
        assertEquals("0.00", DisplayFormatUtil.formatAmountCny(0, Locale.US));
    }

    @Test
    public void formatAmountCny_lessThanOneYuan_us_returnsTwoDecimals() {
        assertEquals("0.99", DisplayFormatUtil.formatAmountCny(99, Locale.US));
    }

    @Test
    public void formatAmountCny_oneYuanTwentyThree_us_returnsTwoDecimals() {
        assertEquals("1.23", DisplayFormatUtil.formatAmountCny(123, Locale.US));
    }

    @Test
    public void formatAmountCny_largeValue_us_returnsTwoDecimalsNoGrouping() {
        // Don't insert thousands grouping; current code uses BigDecimal#toPlainString()
        // which never inserts grouping separators.
        assertEquals("12345.67", DisplayFormatUtil.formatAmountCny(1234567, Locale.US));
    }

    @Test
    public void formatAmountCny_china_usesPeriodDecimalSeparator() {
        assertEquals("12.34", DisplayFormatUtil.formatAmountCny(1234, Locale.CHINA));
    }

    @Test
    public void formatAmountCny_negativeCentArgument_us_returnsTwoDecimals() {
        // Price-conflict path may pass negative sentinel; still must format cleanly.
        assertEquals("-1.00", DisplayFormatUtil.formatAmountCny(-100, Locale.US));
    }

    @Test
    public void formatQuantity_zero_us_returnsZero() {
        assertEquals("0", DisplayFormatUtil.formatQuantity(0, Locale.US));
    }

    @Test
    public void formatQuantity_positive_us_returnsInteger() {
        assertEquals("5", DisplayFormatUtil.formatQuantity(5, Locale.US));
    }

    @Test
    public void formatQuantity_china_returnsInteger() {
        assertEquals("3", DisplayFormatUtil.formatQuantity(3, Locale.CHINA));
    }

    @Test
    public void formatQuantity_large_us_noGrouping() {
        // No thousands grouping; keeps layout stable.
        assertEquals("12345", DisplayFormatUtil.formatQuantity(12345, Locale.US));
    }
}
