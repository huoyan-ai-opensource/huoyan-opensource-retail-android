package cc.huoyan.retail.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 称重计价策略（{@link PricingMode#WEIGHT}）。
 * <p>重量单位 kg，金额（分）= quantity(kg) × unitPriceCent，
 * BigDecimal 计算后四舍五入（HALF_UP）保留到分，
 * 与 MainActivity 现有称重计金额额语义一致。
 */
public class WeightPricingStrategy implements PricingStrategy {

    @Override
    public long calculateAmountCent(BigDecimal quantity, long unitPriceCent) {
        return BigDecimal.valueOf(unitPriceCent)
                .multiply(quantity)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
