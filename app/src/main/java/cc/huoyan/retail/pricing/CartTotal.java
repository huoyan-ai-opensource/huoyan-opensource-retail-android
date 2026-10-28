package cc.huoyan.retail.pricing;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 购物车结算合计结果（不可变），由 {@link CartCalculator#computeTotal} 产出。
 * <p>口径说明：
 * <ul>
 *     <li>{@code totalAmountCent}：折扣前合计 = Σ条目金额。舍入策略 A（Issue 决策 1）：
 *     每条目经对应 {@link PricingStrategy} 以 HALF_UP 舍入到分后累加，
 *     故合计恒等于 Σ条目金额（含折扣前/后口径一致，折扣亦按合计一次舍入）；</li>
 *     <li>{@code totalPieceCount}（Issue 决策 3）：称重行每行计 1 件 + 计件行计其数量；</li>
 *     <li>{@code totalWeightKg}（Issue 决策 3）：Σ称重条目重量，计件条目不计入；</li>
 *     <li>{@code discountAmountCent}（Issue 决策 5）：折扣金额 = 折扣前合计 − 折后合计；
 *     {@code discountRate} 为 null（不折扣）时恒为 0；</li>
 *     <li>{@code payableAmountCent}（Issue 决策 5）：折后应付合计 =
 *     {@code totalAmountCent} − {@code discountAmountCent}，
 *     即「折扣前合计 × 折扣率」按 HALF_UP 到分的直接结果；</li>
 *     <li>{@code items}：与输入顺序一致、已回填 {@code amountCent} 的条目列表（金额回填）。</li>
 * </ul>
 */
public final class CartTotal {

    /** 与输入顺序一致、已回填计算后金额的条目列表。 */
    private final List<CartItem> items;

    /** 折扣前合计金额（单位：分）= Σ条目金额（决策 1）。 */
    private final long totalAmountCent;

    /** 总件数 = 称重行每行 1 件 + 计件行数量（决策 3）。 */
    private final long totalPieceCount;

    /** 总重量（kg）= Σ称重条目重量（决策 3）。 */
    private final BigDecimal totalWeightKg;

    /** 折扣金额（单位：分）= totalAmountCent − payableAmountCent（决策 5）；无折扣时为 0。 */
    private final long discountAmountCent;

    /** 折后应付合计（单位：分）；无折扣时等于 totalAmountCent（决策 5）。 */
    private final long payableAmountCent;

    CartTotal(List<CartItem> items, long totalAmountCent, long totalPieceCount,
              BigDecimal totalWeightKg, long discountAmountCent, long payableAmountCent) {
        this.items = Collections.unmodifiableList(items);
        this.totalAmountCent = totalAmountCent;
        this.totalPieceCount = totalPieceCount;
        this.totalWeightKg = totalWeightKg;
        this.discountAmountCent = discountAmountCent;
        this.payableAmountCent = payableAmountCent;
    }

    /** @return 与输入顺序一致、已回填 amountCent 的条目列表（不可变） */
    public List<CartItem> getItems() {
        return items;
    }

    /** @return 折扣前合计金额（单位：分）= Σ条目金额 */
    public long getTotalAmountCent() {
        return totalAmountCent;
    }

    /** @return 总件数（称重行每行 1 件 + 计件行数量） */
    public long getTotalPieceCount() {
        return totalPieceCount;
    }

    /** @return 总重量（kg），计件条目不计入 */
    public BigDecimal getTotalWeightKg() {
        return totalWeightKg;
    }

    /** @return 折扣金额（单位：分）；无折扣时为 0 */
    public long getDiscountAmountCent() {
        return discountAmountCent;
    }

    /** @return 折后应付合计（单位：分）；无折扣时等于折扣前合计 */
    public long getPayableAmountCent() {
        return payableAmountCent;
    }
}
