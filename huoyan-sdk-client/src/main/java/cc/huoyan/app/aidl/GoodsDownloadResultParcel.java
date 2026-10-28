package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

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
public final class GoodsDownloadResultParcel implements Parcelable {

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

    public GoodsDownloadResultParcel(int code, String reason, String phase, String syncMode,
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

    public int getCode() {
        return code;
    }

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

    public long getAdded() {
        return added;
    }

    public long getUpdated() {
        return updated;
    }

    public long getDeleted() {
        return deleted;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailCount() {
        return failCount;
    }

    /** 本次本地库是否有变更。 */
    public boolean isHasChanges() {
        return hasChanges;
    }

    public int getFailCode() {
        return failCode;
    }

    public String getFailReason() {
        return failReason;
    }

    private GoodsDownloadResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        phase = parcel.readString();
        syncMode = parcel.readString();
        syncTimestamp = parcel.readLong();
        total = parcel.readLong();
        added = parcel.readLong();
        updated = parcel.readLong();
        deleted = parcel.readLong();
        successCount = parcel.readLong();
        failCount = parcel.readLong();
        hasChanges = parcel.readByte() != 0;
        failCode = parcel.readInt();
        failReason = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeString(phase);
        parcel.writeString(syncMode);
        parcel.writeLong(syncTimestamp);
        parcel.writeLong(total);
        parcel.writeLong(added);
        parcel.writeLong(updated);
        parcel.writeLong(deleted);
        parcel.writeLong(successCount);
        parcel.writeLong(failCount);
        parcel.writeByte((byte) (hasChanges ? 1 : 0));
        parcel.writeInt(failCode);
        parcel.writeString(failReason);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoodsDownloadResultParcel)) {
            return false;
        }
        GoodsDownloadResultParcel that = (GoodsDownloadResultParcel) other;
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
        return "GoodsDownloadResultParcel(code=" + code + ", reason=" + reason
                + ", phase=" + phase + ", syncMode=" + syncMode
                + ", syncTimestamp=" + syncTimestamp + ", total=" + total
                + ", added=" + added + ", updated=" + updated + ", deleted=" + deleted
                + ", successCount=" + successCount + ", failCount=" + failCount
                + ", hasChanges=" + hasChanges + ", failCode=" + failCode
                + ", failReason=" + failReason + ")";
    }

    public static final Creator<GoodsDownloadResultParcel> CREATOR =
            new Creator<GoodsDownloadResultParcel>() {
        @Override
        public GoodsDownloadResultParcel createFromParcel(Parcel parcel) {
            return new GoodsDownloadResultParcel(parcel);
        }

        @Override
        public GoodsDownloadResultParcel[] newArray(int size) {
            return new GoodsDownloadResultParcel[size];
        }
    };
}
