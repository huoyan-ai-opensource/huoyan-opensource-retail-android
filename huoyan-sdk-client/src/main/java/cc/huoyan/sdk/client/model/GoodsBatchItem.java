package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 本地批量操作单条结果（batchUpdateGoods/batchDeleteGoods 回执
 * items[] 元素）：goodsId（回显目标）+ code（0=成功；-1=失败）+ reason（失败
 * 原因——「商品不存在或已软删」/「goods_id 非法（空）」等，成功为 null）。
 * 与入参 goodsIds 等长同序（部分成功语义）。
 */
public final class GoodsBatchItem {

    private final String goodsId;
    private final int code;
    private final String reason;

    /** 构造数据对象。 */
    public GoodsBatchItem(String goodsId, int code, String reason) {
        this.goodsId = goodsId;
        this.code = code;
        this.reason = reason;
    }

    /** 目标商品 id（回显入参元素）。 */
    public String getGoodsId() {
        return goodsId;
    }

    /** 0=成功；非 0=失败（reason 携带说明）。 */
    public int getCode() {
        return code;
    }

    /** 读取 getReason 对应数据。 */
    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoodsBatchItem)) {
            return false;
        }
        GoodsBatchItem that = (GoodsBatchItem) other;
        return code == that.code
                && Objects.equals(goodsId, that.goodsId)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goodsId, code, reason);
    }

    @Override
    public String toString() {
        return "GoodsBatchItem(goodsId=" + goodsId + ", code=" + code
                + ", reason=" + reason + ")";
    }

}
