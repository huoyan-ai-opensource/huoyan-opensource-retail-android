package cc.huoyan.retail.pricing;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 购物车条目（不可变）。
 * <p>数量与重量按计价方式二选一语义：
 * <ul>
 *     <li>{@link PricingMode#COUNT}：使用 {@code quantity}（整数件），{@code weightKg} 被忽略（可为 null）；</li>
 *     <li>{@link PricingMode#WEIGHT}：使用 {@code weightKg}（kg），{@code quantity} 被忽略（可为 null）。</li>
 * </ul>
 * 防御策略（Issue 决策 7）：必需字段（COUNT 的 quantity / WEIGHT 的 weightKg）为 null 或为负时，
 * 构造即抛 {@link IllegalArgumentException} 拒绝该条目（负数入车属上游错误，不静默按 0 计掩盖）；
 * 数量/重量为 0 允许构造，由 {@link CartCalculator} 按金额 0 计。
 * <p>{@code amountCent} 为计算后金额（单位：分）：入车时默认 0，
 * 由 {@link CartCalculator#computeTotal} 复用 {@link PricingStrategy} 计算并通过
 * {@link #withAmountCent(long)} 回填，本模型自身不做任何金额计算。
 * <p>同 SKU 允许多条独立条目（含 COUNT/WEIGHT 双形态同存），是否合并由调用方决定，
 * 计算器不合并（Issue 决策 4）。
 */
public final class CartItem {

    /** 商品 PLU 编码（同 SKU 多条目不合并，仅作展示/对账标识）。 */
    private final String plu;

    /** 商品名称（展示用）。 */
    private final String name;

    /** 单价（单位：分/件 或 分/kg，按 {@link #pricingMode} 解释）。 */
    private final long unitPriceCent;

    /** 计价方式。 */
    private final PricingMode pricingMode;

    /** 数量（件）：仅 COUNT 条目使用，非负整数值。 */
    private final BigDecimal quantity;

    /** 重量（kg）：仅 WEIGHT 条目使用，非负。 */
    private final BigDecimal weightKg;

    /** 计算后金额（单位：分）：入车时为 0，由 {@link CartCalculator} 回填。 */
    private final long amountCent;

    /**
     * 构造入车条目（amountCent 初始为 0，等待计算器回填）。
     *
     * @param plu           商品 PLU 编码
     * @param name          商品名称
     * @param unitPriceCent 单价（单位：分）
     * @param pricingMode   计价方式（不能为 null）
     * @param quantity      数量（件）：COUNT 必填非负，WEIGHT 忽略（可传 null）
     * @param weightKg      重量（kg）：WEIGHT 必填非负，COUNT 忽略（可传 null）
     * @throws IllegalArgumentException 必需的数量/重量为 null 或为负（Issue 决策 7 拒绝策略）
     */
    public CartItem(String plu, String name, long unitPriceCent, PricingMode pricingMode,
                    BigDecimal quantity, BigDecimal weightKg) {
        this(plu, name, unitPriceCent, pricingMode, quantity, weightKg, 0L);
    }

    private CartItem(String plu, String name, long unitPriceCent, PricingMode pricingMode,
                     BigDecimal quantity, BigDecimal weightKg, long amountCent) {
        this.plu = plu;
        this.name = name;
        this.unitPriceCent = unitPriceCent;
        this.pricingMode = Objects.requireNonNull(pricingMode, "pricingMode 不能为 null");
        if (pricingMode == PricingMode.COUNT) {
            this.quantity = requireNonNegative(quantity, "计件条目 quantity");
            this.weightKg = weightKg;
        } else {
            this.quantity = quantity;
            this.weightKg = requireNonNegative(weightKg, "称重条目 weightKg");
        }
        this.amountCent = amountCent;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为 null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " 不能为负数: " + value);
        }
        return value;
    }

    /**
     * 回填计算后金额，返回携带新金额的条目副本（本条目不可变，原条目不变）。
     *
     * @param amountCent 计算后金额（单位：分）
     * @return 携带新金额的条目副本
     */
    public CartItem withAmountCent(long amountCent) {
        return new CartItem(plu, name, unitPriceCent, pricingMode, quantity, weightKg, amountCent);
    }

    /** @return 商品 PLU 编码 */
    public String getPlu() {
        return plu;
    }

    /** @return 商品名称 */
    public String getName() {
        return name;
    }

    /** @return 单价（单位：分/件 或 分/kg） */
    public long getUnitPriceCent() {
        return unitPriceCent;
    }

    /** @return 计价方式 */
    public PricingMode getPricingMode() {
        return pricingMode;
    }

    /** @return 数量（件），仅 COUNT 条目有效；WEIGHT 条目为 null */
    public BigDecimal getQuantity() {
        return quantity;
    }

    /** @return 重量（kg），仅 WEIGHT 条目有效；COUNT 条目为 null */
    public BigDecimal getWeightKg() {
        return weightKg;
    }

    /** @return 计算后金额（单位：分）；入车未计算时为 0 */
    public long getAmountCent() {
        return amountCent;
    }
}
