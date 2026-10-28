package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 商品单条回执（createGoods/updateGoods）：code/reason + record
 * （CRUD 条目全字段——含门店/客户冗余列与建档时间）。错误回执 record=null
 * （不假装成功）。
 */
public final class GoodsResult {

    private final int code;
    private final String reason;
    private final Goods record;

    /** 构造数据对象。 */
    public GoodsResult(int code, String reason, Goods record) {
        this.code = code;
        this.reason = reason;
        this.record = record;
    }

    /** 结果码；0 表示请求成功。 */
    public int getCode() {
        return code;
    }

    /** getReason 字段值。 */
    public String getReason() {
        return reason;
    }

    /** 创建/更新后的商品终态；错误时 null。 */
    public Goods getRecord() {
        return record;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoodsResult)) {
            return false;
        }
        GoodsResult that = (GoodsResult) other;
        return code == that.code
                && Objects.equals(reason, that.reason)
                && Objects.equals(record, that.record);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, record);
    }

    @Override
    public String toString() {
        return "GoodsResult(code=" + code + ", reason=" + reason
                + ", record=" + record + ")";
    }

}
