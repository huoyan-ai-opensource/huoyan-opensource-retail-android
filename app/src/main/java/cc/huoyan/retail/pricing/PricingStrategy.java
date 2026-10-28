package cc.huoyan.retail.pricing;

import java.math.BigDecimal;

/**
 * 计价策略接口。
 * <p>金额一律以「分」为单位（long 返回），避免浮点精度误差。
 */
public interface PricingStrategy {

    /**
     * 计算应收金额（单位：分）。
     *
     * @param quantity      数量：计件为件数（整数），称重为重量（kg）
     * @param unitPriceCent 单价（单位：分/件 或 分/kg）
     * @return 金额（单位：分）
     */
    long calculateAmountCent(BigDecimal quantity, long unitPriceCent);
}
