package cc.huoyan.retail.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 混合购物车结算计算器（纯函数、无 Android 依赖）。
 * <p>计价复用 P2 交付的策略：COUNT 条目走 {@link CountPricingStrategy}，
 * WEIGHT 条目走 {@link WeightPricingStrategy}（含逐条目 HALF_UP 到分，即舍入策略 A，Issue 决策 1），
 * 合计 = Σ条目金额（决策 1/3）。
 * <p>同 SKU 双形态（Issue 决策 4）：计算器不合并条目，逐条独立计价后累加，
 * 合并/去重策略是调用方的事。
 * <p>折扣（Issue 决策 5）：{@code discountRate} 非空时，折后应付合计 =
 * 折扣前合计 × 折扣率（HALF_UP 到分），折扣金额 = 折扣前合计 − 折后合计；
 * 传 null 不折扣。折扣率语义约定为 [0, 1]，越界值由调用方负责。
 * <p>防御（Issue 决策 7）：
 * <ul>
 *     <li>items 为 null 或空列表 → 全 0 结果；</li>
 *     <li>数量/重量为 0 的条目按金额 0 计（负数已在 {@link CartItem} 构造时拒绝）；</li>
 *     <li>计件条目数量必须为整数值，小数数量抛 {@link IllegalArgumentException}；</li>
 *     <li>金额/件数累加使用 {@link Math#addExact}，溢出抛 {@link IllegalStateException}（含清晰信息）。</li>
 * </ul>
 */
public final class CartCalculator {

    private static final CountPricingStrategy COUNT_STRATEGY = new CountPricingStrategy();
    private static final WeightPricingStrategy WEIGHT_STRATEGY = new WeightPricingStrategy();

    private CartCalculator() {
    }

    /**
     * 计算混合购物车结算合计（纯函数，不修改入参）。
     *
     * @param items        购物车条目列表；null 或空列表返回全 0 结果（决策 7）
     * @param discountRate 折扣率（如 0.9 表示九折）；null 表示不折扣（决策 5）
     * @return 合计结果，items 为输入顺序的金额回填条目列表
     * @throws IllegalArgumentException 计件条目数量为小数
     * @throws IllegalStateException    合计金额或件数累加溢出 long 范围（决策 7）
     */
    public static CartTotal computeTotal(List<CartItem> items, BigDecimal discountRate) {
        if (items == null || items.isEmpty()) {
            return new CartTotal(Collections.<CartItem>emptyList(), 0L, 0L, BigDecimal.ZERO, 0L, 0L);
        }

        List<CartItem> resultItems = new ArrayList<>(items.size());
        long totalAmountCent = 0L;
        long totalPieceCount = 0L;
        BigDecimal totalWeightKg = BigDecimal.ZERO;
        try {
            for (CartItem item : items) {
                long amountCent;
                long pieceCount;
                if (item.getPricingMode() == PricingMode.COUNT) {
                    requireIntegerCount(item);
                    amountCent = COUNT_STRATEGY.calculateAmountCent(item.getQuantity(), item.getUnitPriceCent());
                    pieceCount = item.getQuantity().longValueExact();
                } else {
                    amountCent = WEIGHT_STRATEGY.calculateAmountCent(item.getWeightKg(), item.getUnitPriceCent());
                    pieceCount = 1L;
                    totalWeightKg = totalWeightKg.add(item.getWeightKg());
                }
                resultItems.add(item.withAmountCent(amountCent));
                totalAmountCent = Math.addExact(totalAmountCent, amountCent);
                totalPieceCount = Math.addExact(totalPieceCount, pieceCount);
            }
        } catch (ArithmeticException e) {
            throw new IllegalStateException(
                    "购物车合计计算溢出（金额或件数超出 long 范围），条目数=" + items.size(), e);
        }

        long payableAmountCent;
        if (discountRate == null) {
            payableAmountCent = totalAmountCent;
        } else {
            payableAmountCent = BigDecimal.valueOf(totalAmountCent)
                    .multiply(discountRate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        }
        long discountAmountCent = totalAmountCent - payableAmountCent;
        return new CartTotal(resultItems, totalAmountCent, totalPieceCount,
                totalWeightKg, discountAmountCent, payableAmountCent);
    }

    private static void requireIntegerCount(CartItem item) {
        BigDecimal quantity = item.getQuantity();
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "计件条目数量必须为整数值: plu=" + item.getPlu() + ", quantity=" + quantity);
        }
    }
}
