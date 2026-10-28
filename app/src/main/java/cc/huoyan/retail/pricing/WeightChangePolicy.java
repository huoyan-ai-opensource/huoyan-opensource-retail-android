package cc.huoyan.retail.pricing;

import java.math.BigDecimal;

/** 识别锁定后相对固定基线的称重变化判断，与秤稳定标识和计价重量分离。 */
public final class WeightChangePolicy {
    private static final BigDecimal TOLERANCE_KG = new BigDecimal("0.005");

    private WeightChangePolicy() { }

    /** 重量相对本轮识别基线变化超过 5g 才需重新识别，含 5g 的抖动保留结果。 */
    public static boolean requiresRecognition(BigDecimal baselineKg, BigDecimal currentKg) {
        return baselineKg != null && currentKg != null
                && currentKg.subtract(baselineKg)
                .abs().compareTo(TOLERANCE_KG) > 0;
    }
}
