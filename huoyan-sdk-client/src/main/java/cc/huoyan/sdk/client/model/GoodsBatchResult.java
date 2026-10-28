package cc.huoyan.sdk.client.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 本地商品批量操作回执（batchUpdateGoods/batchDeleteGoods）：
 * code/reason + items[]（逐条 goods_id/code/reason——与入参 goodsIds 等长
 * 同序）。**部分成功语义**：code=0 表示调用执行完毕，条目级成败看 items；
 * code≠0（空数组/空 changes 参数错、存储事务失败整体回滚）时 items 空列表。
 */
public final class GoodsBatchResult {

    private final int code;
    private final String reason;
    private final List<GoodsBatchItem> items;

    /** 构造数据对象。 */
    public GoodsBatchResult(int code, String reason, List<GoodsBatchItem> items) {
        this.code = code;
        this.reason = reason;
        this.items = items == null ? Collections.<GoodsBatchItem>emptyList() : items;
    }

    /** 读取 getCode 对应数据。 */
    public int getCode() {
        return code;
    }

    /** 读取 getReason 对应数据。 */
    public String getReason() {
        return reason;
    }

    /** 逐条结果（与入参 goodsIds 等长同序；错误回执空列表）。 */
    public List<GoodsBatchItem> getItems() {
        return items;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoodsBatchResult)) {
            return false;
        }
        GoodsBatchResult that = (GoodsBatchResult) other;
        return code == that.code
                && Objects.equals(reason, that.reason)
                && Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, items);
    }

    @Override
    public String toString() {
        return "GoodsBatchResult(code=" + code + ", reason=" + reason
                + ", items=" + items + ")";
    }

}
