package cc.huoyan.retail.pricing;

import java.math.BigDecimal;

/**
 * 按件/按份计价策略（{@link PricingMode#COUNT}）。
 * <p>数量为整数件，金额（分）= unitPriceCent × quantity 的整数值，
 * 使用 BigDecimal 计算后取精确整数值。
 */
public class CountPricingStrategy implements PricingStrategy {

    @Override
    public long calculateAmountCent(BigDecimal quantity, long unitPriceCent) {
        return BigDecimal.valueOf(unitPriceCent).multiply(quantity).longValueExact();
    }
}
