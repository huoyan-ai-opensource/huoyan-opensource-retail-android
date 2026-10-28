package cc.huoyan.retail.common;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Localized DISPLAY formatting helpers for the recognition / weighing flow.
 *
 * <p>These helpers format numeric values that are shown to the user. They never
 * affect any API, AIDL, WebSocket, SDK, database or printer protocol value —
 * callers must keep using raw numeric values for all numeric business logic and
 * only invoke these helpers at the display boundary.
 *
 * <p>To stay behaviourally compatible with the previous {@code BigDecimal#toPlainString()}
 * output (no thousands grouping, half-up rounding), the formatters explicitly
 * disable grouping and use {@link RoundingMode#HALF_UP}.
 */
public final class DisplayFormatUtil {

    private DisplayFormatUtil() {
    }

    /**
     * Formats a weight in kilograms with three decimal places using a
     * locale-aware decimal separator. e.g. {@code 0.5} -> {@code "0.500"} in
     * {@link Locale#US}, {@code "0,500"} in {@link Locale#GERMANY}.
     */
    public static String formatWeightKg(double weightKg, Locale locale) {
        return formatNumber(weightKg, 3, locale);
    }

    /**
     * Formats an amount given in CNY cents (1 yuan = 100 cents) with two
     * decimal places using a locale-aware decimal separator. Grouping is
     * disabled so output stays compatible with the previous
     * {@code BigDecimal#toPlainString()} behaviour.
     */
    public static String formatAmountCny(long amountCent, Locale locale) {
        return formatNumber(java.math.BigDecimal.valueOf(amountCent).movePointLeft(2), 2, locale);
    }

    /**
     * Formats an integer quantity (e.g. piece count, category count) using the
     * locale-aware integer representation. Grouping is disabled to keep layouts
     * stable on narrow POS screens.
     */
    public static String formatQuantity(int count, Locale locale) {
        return formatNumber((long) count, 0, locale);
    }

    private static String formatNumber(Number value, int fractionDigits, Locale locale) {
        NumberFormat nf = NumberFormat.getNumberInstance(safeLocale(locale));
        nf.setMinimumFractionDigits(fractionDigits);
        nf.setMaximumFractionDigits(fractionDigits);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        nf.setGroupingUsed(false);
        return nf.format(value);
    }

    private static String formatNumber(long value, int fractionDigits, Locale locale) {
        NumberFormat nf = NumberFormat.getNumberInstance(safeLocale(locale));
        nf.setMinimumFractionDigits(fractionDigits);
        nf.setMaximumFractionDigits(fractionDigits);
        nf.setGroupingUsed(false);
        return nf.format(value);
    }

    private static Locale safeLocale(Locale locale) {
        return locale != null ? locale : Locale.getDefault();
    }
}
