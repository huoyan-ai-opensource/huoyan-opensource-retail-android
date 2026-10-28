package cc.huoyan.sdk.client.model;

/** 宿主提供的商品快照；商品 ID 必须是当前绑定下的 SDK 映射，不是本地数据库主键。 */
public final class FeedbackProduct {
    /** 可选的 SDK 商品 ID。 */
    private final String goodsId;
    /** 商品名称，缺省时以 PLU 作为反馈名称。 */
    private final String name;
    /** 宿主商品标识。 */
    private final String plu;

    /** 固定商品参数，不保留宿主实体引用。 */
    public FeedbackProduct(String goodsId, String name, String plu) {
        this.goodsId = goodsId;
        this.name = name == null || name.trim().isEmpty() ? plu : name;
        this.plu = plu;
    }

    /** 返回可选 SDK 商品 ID。 */
    public String getGoodsId() { return goodsId; }
    /** 返回反馈名称。 */
    public String getName() { return name; }
    /** 返回宿主商品标识。 */
    public String getPlu() { return plu; }
}
