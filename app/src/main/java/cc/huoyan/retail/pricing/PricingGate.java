package cc.huoyan.retail.pricing;

/**
 * 计价前置校验（Issue 决策 9）。
 * <p>识别、点选两条加购路径统一在入车前做计价校验，拦截规则：
 * <ul>
 *     <li>称重计价（{@link PricingMode#WEIGHT}）商品且称重模块关闭 → 拦截（BLOCK），
 *     由调用方提示「该商品为称重计价，未开启称重模块」，商品不入车；</li>
 *     <li>计件商品（{@link PricingMode#COUNT}）恒放行（PASS），与称重开关无关；</li>
 *     <li>称重模块开启时，WEIGHT 商品同样放行。</li>
 * </ul>
 * 本类为纯函数工具类，不含业务接线；称重开关的配置来源由后续任务提供，
 * 当前以 {@code weighingEnabled} 布尔参数注入。
 */
public final class PricingGate {

    private PricingGate() {
    }

    /**
     * 拦截原因。
     * <p>当前唯一取值 {@link #WEIGHING_DISABLED}，后续新增拦截场景
     * （如称重设备未连接）时可在此扩展，并相应演进结果模型。
     */
    public enum BlockReason {

        /** 称重模块未开启，无法对 WEIGHT 商品计价。 */
        WEIGHING_DISABLED
    }

    /**
     * 校验结果。
     * <ul>
     *     <li>{@link #PASS}：放行，可继续入车；</li>
     *     <li>{@link #BLOCK}：拦截，携带原因 {@link BlockReason}，由调用方按原因提示。</li>
     * </ul>
     */
    public enum GateResult {

        /** 放行。 */
        PASS(null),

        /** 拦截，原因为 {@link BlockReason#WEIGHING_DISABLED}。 */
        BLOCK(BlockReason.WEIGHING_DISABLED);

        private final BlockReason reason;

        GateResult(BlockReason reason) {
            this.reason = reason;
        }

        /**
         * 获取拦截原因。
         *
         * @return 仅 {@link #BLOCK} 携带原因；{@link #PASS} 返回 {@code null}
         */
        public BlockReason getReason() {
            return reason;
        }
    }

    /**
     * 计价前置校验（决策 9 拦截规则）。
     *
     * @param mode            商品计价方式
     * @param weighingEnabled 称重模块是否开启（配置来源由后续任务提供）
     * @return WEIGHT 且称重模块关闭返回 {@link GateResult#BLOCK}
     * （reason={@link BlockReason#WEIGHING_DISABLED}）；其余情况（WEIGHT 且开关开、COUNT 任意）返回 {@link GateResult#PASS}
     */
    public static GateResult check(PricingMode mode, boolean weighingEnabled) {
        if (mode == PricingMode.WEIGHT && !weighingEnabled) {
            return GateResult.BLOCK;
        }
        return GateResult.PASS;
    }
}
