package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 本地商品计数回执（getGoodsCount）：code/reason + count（过滤后
 * 总条数——keyword/name/plu/isDeleted 四参过滤，不含分页）。错误回执 count=0
 * （不代表真实计数——以 code 判读）。
 */
public final class GoodsCountResult {

    private final int code;
    private final String reason;
    private final long count;

    /** 构造数据对象。 */
    public GoodsCountResult(int code, String reason, long count) {
        this.code = code;
        this.reason = reason;
        this.count = count;
    }

    /** 读取 getCode 对应数据。 */
    public int getCode() {
        return code;
    }

    /** 读取 getReason 对应数据。 */
    public String getReason() {
        return reason;
    }

    /** 过滤后总条数（code=0 时有效）。 */
    public long getCount() {
        return count;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoodsCountResult)) {
            return false;
        }
        GoodsCountResult that = (GoodsCountResult) other;
        return code == that.code
                && count == that.count
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, count);
    }

    @Override
    public String toString() {
        return "GoodsCountResult(code=" + code + ", reason=" + reason
                + ", count=" + count + ")";
    }

}
