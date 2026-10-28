package cc.huoyan.retail.goods;

/**
 * sale_mode 计价归一化（Issue D13，纯逻辑可单测）。
 * <p>v2 商品 sale_mode 整数：0 按个 / 1 按重量。
 * 本地计价链路以 GoodsEntity.unit 判断（PricingMode.fromUnit：unit == 5 为计件，其余为称重），
 * 本类负责两套语义的双向映射：拉取由 goods 列表解析处调用 toLocalUnit，上传由新增商品链路调用 toSaleMode。
 */
public final class SaleModeMapper {

    /** v2 销售方式：按个（计件） */
    public static final int SALE_MODE_COUNT = 0;
    /** v2 销售方式：按重量（称重） */
    public static final int SALE_MODE_WEIGHT = 1;
    /** 本地计件 unit 语义（PricingMode.fromUnit：unit == 5 → COUNT） */
    public static final int LOCAL_UNIT_COUNT = 5;
    /** 本地称重 unit 语义（GoodsEntity 默认值，非 5 即称重 → WEIGHT） */
    public static final int LOCAL_UNIT_WEIGHT = 0;
    /** 计价单位标签：按个（计件商品，上传 v2 unit 字段与价格展示共用） */
    public static final String UNIT_LABEL_COUNT = "个";
    /** 计价单位标签：kg（称重商品，本地称重链路按 kg 计价，见 WeightPricingStrategy） */
    public static final String UNIT_LABEL_WEIGHT = "kg";

    private SaleModeMapper() {
    }

    /**
     * v2 sale_mode 映射为本地计价 unit 值。
     * <p>0 按个 → 5（计件，对应现有 unit 5 语义）；1 按重量 → 0（称重）；
     * 契约枚举外的未知值按称重兜底（不进计件键盘链路）。
     *
     * @param saleMode v2 销售方式（0 按个 / 1 按重量）
     * @return 本地 unit 值（5 = 计件，0 = 称重）
     */
    public static int toLocalUnit(int saleMode) {
        if (saleMode == SALE_MODE_COUNT) {
            return LOCAL_UNIT_COUNT;
        }
        return LOCAL_UNIT_WEIGHT;
    }

    /**
     * 本地计价 unit 值反向映射为 v2 sale_mode（Issue D18）。
     * <p>5（计件）→ 0 按个；其余（含 0 称重）→ 1 按重量，与
     * {@link cc.huoyan.retail.pricing.PricingMode#fromUnit(int)} 的计件判定口径一致。
     *
     * @param localUnit 本地 unit 值（5 = 计件，其余 = 称重）
     * @return v2 销售方式（0 按个 / 1 按重量）
     */
    public static int toSaleMode(int localUnit) {
        if (localUnit == LOCAL_UNIT_COUNT) {
            return SALE_MODE_COUNT;
        }
        return SALE_MODE_WEIGHT;
    }

    /**
     * 本地计价 unit 值映射为展示/上传用单位标签：5（计件）→ 个，其余（含 0 称重）→ kg。
     * <p>与 {@link cc.huoyan.retail.pricing.PricingMode#fromUnit(int)} 的
     * 计件判定口径一致（非 5 一律按称重标签展示）。
     *
     * @param localUnit 本地 unit 值（5 = 计件，其余 = 称重）
     * @return 单位标签（个 / kg）
     */
    public static String toUnitLabel(int localUnit) {
        if (localUnit == LOCAL_UNIT_COUNT) {
            return UNIT_LABEL_COUNT;
        }
        return UNIT_LABEL_WEIGHT;
    }
}
