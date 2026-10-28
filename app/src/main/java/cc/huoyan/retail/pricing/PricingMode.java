package cc.huoyan.retail.pricing;

/**
 * 商品计价方式。
 * <p>通用零售客户端（生鲜/零食/烘焙/小碗菜）中，商品计价分为两种：
 * <ul>
 *     <li>{@link #COUNT}：按件/按份计价，金额 = 数量 × 单价；</li>
 *     <li>{@link #WEIGHT}：称重计价，金额 = 重量(kg) × 单价。</li>
 * </ul>
 * Issue 决策：计价方式暂以现有 {@code GoodsEntity.unit} 字段判断，
 * unit == 5 视为计件/按份（{@link #COUNT}），其余取值一律视为称重（{@link #WEIGHT}），
 * 后续增加分类字段后再演进。
 */
public enum PricingMode {

    /** 按件/按份计价：数量（整数件）× 单价。 */
    COUNT,

    /** 称重计价：重量(kg) × 单价。 */
    WEIGHT;

    /**
     * 由商品 unit 字段映射计价方式。
     *
     * @param unit 商品单位字段（Issue 决策：5 = 计件/按份，其余 = 称重）
     * @return unit == 5 返回 {@link #COUNT}，否则返回 {@link #WEIGHT}
     */
    public static PricingMode fromUnit(int unit) {
        return unit == 5 ? COUNT : WEIGHT;
    }
}
