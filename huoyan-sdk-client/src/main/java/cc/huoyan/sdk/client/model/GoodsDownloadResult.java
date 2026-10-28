package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 商品同步执行结果（downloadGoods(size) 回执）：code/reason 为
 * 回执信封（0 = 同步成功；≠0 携带原因——分页失败/本地事务失败/参数非法/
 * 进行中重复触发，此时统计字段无效，已处理统计经 getDownloadGoodsProgress
 * 查询）；code=0 时统计字段有效——phase=SUCCESS、syncMode（full/incremental）、
 * syncTimestamp（本次提交游标）、total（本次商品总数）、added/updated/deleted
 * （本地库变更计数，按 goods_id 终态计一次）、successCount/failCount（行级
 * 累计——无效记录计 fail 不阻断）、hasChanges = added+updated+deleted &gt; 0。
 */
public final class GoodsDownloadResult {

    private final int code;
    private final String reason;
    private final String phase;
    private final String syncMode;
    private final long syncTimestamp;
    private final long total;
    private final long added;
    private final long updated;
    private final long deleted;
    private final long successCount;
    private final long failCount;
    private final boolean hasChanges;
    private final int failCode;
    private final String failReason;

    /** 构造数据对象。 */
    public GoodsDownloadResult(int code, String reason, String phase, String syncMode,
            long syncTimestamp, long total, long added, long updated, long deleted,
            long successCount, long failCount, boolean hasChanges, int failCode,
            String failReason) {
        this.code = code;
        this.reason = reason;
        this.phase = phase;
        this.syncMode = syncMode;
        this.syncTimestamp = syncTimestamp;
        this.total = total;
        this.added = added;
        this.updated = updated;
        this.deleted = deleted;
        this.successCount = successCount;
        this.failCount = failCount;
        this.hasChanges = hasChanges;
        this.failCode = failCode;
        this.failReason = failReason;
    }

    /** 读取 getCode 对应数据。 */
    public int getCode() {
        return code;
    }

    /** 读取 getReason 对应数据。 */
    public String getReason() {
        return reason;
    }

    /** 终态：SUCCESS（失败经 code≠0 返回，见类注释）。 */
    public String getPhase() {
        return phase;
    }

    /** 同步模式（full/incremental）。 */
    public String getSyncMode() {
        return syncMode;
    }

    /** 本次提交游标（epoch 秒；空商品也提交）。 */
    public long getSyncTimestamp() {
        return syncTimestamp;
    }

    /** 本次商品总数（非页码）。 */
    public long getTotal() {
        return total;
    }

    /** 读取 getAdded 对应数据。 */
    public long getAdded() {
        return added;
    }

    /** 读取 getUpdated 对应数据。 */
    public long getUpdated() {
        return updated;
    }

    /** 读取 getDeleted 对应数据。 */
    public long getDeleted() {
        return deleted;
    }

    /** 读取 getSuccessCount 对应数据。 */
    public long getSuccessCount() {
        return successCount;
    }

    /** 读取 getFailCount 对应数据。 */
    public long getFailCount() {
        return failCount;
    }

    /** 本次本地库是否有变更。 */
    public boolean isHasChanges() {
        return hasChanges;
    }

    /** 读取 getFailCode 对应数据。 */
    public int getFailCode() {
        return failCode;
    }

    /** 读取 getFailReason 对应数据。 */
    public String getFailReason() {
        return failReason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoodsDownloadResult)) {
            return false;
        }
        GoodsDownloadResult that = (GoodsDownloadResult) other;
        return code == that.code
                && syncTimestamp == that.syncTimestamp
                && total == that.total
                && added == that.added
                && updated == that.updated
                && deleted == that.deleted
                && successCount == that.successCount
                && failCount == that.failCount
                && hasChanges == that.hasChanges
                && failCode == that.failCode
                && Objects.equals(reason, that.reason)
                && Objects.equals(phase, that.phase)
                && Objects.equals(syncMode, that.syncMode)
                && Objects.equals(failReason, that.failReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, phase, syncMode, syncTimestamp, total, added,
                updated, deleted, successCount, failCount, hasChanges, failCode, failReason);
    }

    @Override
    public String toString() {
        return "GoodsDownloadResult(code=" + code + ", reason=" + reason
                + ", phase=" + phase + ", syncMode=" + syncMode
                + ", syncTimestamp=" + syncTimestamp + ", total=" + total
                + ", added=" + added + ", updated=" + updated + ", deleted=" + deleted
                + ", successCount=" + successCount + ", failCount=" + failCount
                + ", hasChanges=" + hasChanges + ", failCode=" + failCode
                + ", failReason=" + failReason + ")";
    }

}
