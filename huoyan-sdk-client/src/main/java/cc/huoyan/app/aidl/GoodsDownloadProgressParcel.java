package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 商品同步进度快照（getDownloadGoodsProgress 回执）：code/reason
 * 为查询回执信封（0 = 查询成功）；同步状态在字段——active（无任务恒 false）、
 * phase（IDLE/FETCHING/APPLYING/SUCCESS/FAILED，终态停留可查）、current/total
 * （已处理商品行数/本次商品总数——**非页码**，current 按行计含跨页重复行与
 * 无效行）、successCount/failCount（行级累计）、addedCount/updatedCount/
 * deletedCount（本地库变更计数）、hasChanges、syncMode（full/incremental）、
 * syncTimestamp（已提交游标——进行中/失败保留旧值）、failReason（仅 FAILED
 * 非 null）。字段独立于图片流程（getImageDownloadProgress/
 * getImageLearnProgress 语义不变——两进度互不感知）。
 */
public final class GoodsDownloadProgressParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final boolean active;
    private final String phase;
    private final long current;
    private final long total;
    private final long successCount;
    private final long failCount;
    private final long addedCount;
    private final long updatedCount;
    private final long deletedCount;
    private final boolean hasChanges;
    private final String syncMode;
    private final long syncTimestamp;
    private final String failReason;

    public GoodsDownloadProgressParcel(int code, String reason, boolean active, String phase,
            long current, long total, long successCount, long failCount, long addedCount,
            long updatedCount, long deletedCount, boolean hasChanges, String syncMode,
            long syncTimestamp, String failReason) {
        this.code = code;
        this.reason = reason;
        this.active = active;
        this.phase = phase;
        this.current = current;
        this.total = total;
        this.successCount = successCount;
        this.failCount = failCount;
        this.addedCount = addedCount;
        this.updatedCount = updatedCount;
        this.deletedCount = deletedCount;
        this.hasChanges = hasChanges;
        this.syncMode = syncMode;
        this.syncTimestamp = syncTimestamp;
        this.failReason = failReason;
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    /** 同步进行中（FETCHING/APPLYING）；无任务（IDLE）恒 false。 */
    public boolean isActive() {
        return active;
    }

    /** IDLE/FETCHING/APPLYING/SUCCESS/FAILED（终态停留可查）。 */
    public String getPhase() {
        return phase;
    }

    /** 已处理商品行数（含跨页重复与无效行；非页码）。 */
    public long getCurrent() {
        return current;
    }

    /** 本次商品总数（0 = 未知/未开始）。 */
    public long getTotal() {
        return total;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailCount() {
        return failCount;
    }

    public long getAddedCount() {
        return addedCount;
    }

    public long getUpdatedCount() {
        return updatedCount;
    }

    public long getDeletedCount() {
        return deletedCount;
    }

    /** 本次本地库是否有变更。 */
    public boolean isHasChanges() {
        return hasChanges;
    }

    /** 同步模式（full/incremental；未开始为 null）。 */
    public String getSyncMode() {
        return syncMode;
    }

    /** 已提交游标（进行中/失败保留旧值；成功为本次回执值）。 */
    public long getSyncTimestamp() {
        return syncTimestamp;
    }

    /** 失败原因（仅 FAILED 非 null）。 */
    public String getFailReason() {
        return failReason;
    }

    private GoodsDownloadProgressParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        active = parcel.readByte() != 0;
        phase = parcel.readString();
        current = parcel.readLong();
        total = parcel.readLong();
        successCount = parcel.readLong();
        failCount = parcel.readLong();
        addedCount = parcel.readLong();
        updatedCount = parcel.readLong();
        deletedCount = parcel.readLong();
        hasChanges = parcel.readByte() != 0;
        syncMode = parcel.readString();
        syncTimestamp = parcel.readLong();
        failReason = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeByte((byte) (active ? 1 : 0));
        parcel.writeString(phase);
        parcel.writeLong(current);
        parcel.writeLong(total);
        parcel.writeLong(successCount);
        parcel.writeLong(failCount);
        parcel.writeLong(addedCount);
        parcel.writeLong(updatedCount);
        parcel.writeLong(deletedCount);
        parcel.writeByte((byte) (hasChanges ? 1 : 0));
        parcel.writeString(syncMode);
        parcel.writeLong(syncTimestamp);
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
        if (!(other instanceof GoodsDownloadProgressParcel)) {
            return false;
        }
        GoodsDownloadProgressParcel that = (GoodsDownloadProgressParcel) other;
        return code == that.code
                && active == that.active
                && current == that.current
                && total == that.total
                && successCount == that.successCount
                && failCount == that.failCount
                && addedCount == that.addedCount
                && updatedCount == that.updatedCount
                && deletedCount == that.deletedCount
                && hasChanges == that.hasChanges
                && syncTimestamp == that.syncTimestamp
                && Objects.equals(reason, that.reason)
                && Objects.equals(phase, that.phase)
                && Objects.equals(syncMode, that.syncMode)
                && Objects.equals(failReason, that.failReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, active, phase, current, total, successCount,
                failCount, addedCount, updatedCount, deletedCount, hasChanges, syncMode,
                syncTimestamp, failReason);
    }

    @Override
    public String toString() {
        return "GoodsDownloadProgressParcel(code=" + code + ", reason=" + reason
                + ", active=" + active + ", phase=" + phase + ", current=" + current
                + ", total=" + total + ", successCount=" + successCount
                + ", failCount=" + failCount + ", addedCount=" + addedCount
                + ", updatedCount=" + updatedCount + ", deletedCount=" + deletedCount
                + ", hasChanges=" + hasChanges + ", syncMode=" + syncMode
                + ", syncTimestamp=" + syncTimestamp + ", failReason=" + failReason + ")";
    }

    public static final Creator<GoodsDownloadProgressParcel> CREATOR =
            new Creator<GoodsDownloadProgressParcel>() {
        @Override
        public GoodsDownloadProgressParcel createFromParcel(Parcel parcel) {
            return new GoodsDownloadProgressParcel(parcel);
        }

        @Override
        public GoodsDownloadProgressParcel[] newArray(int size) {
            return new GoodsDownloadProgressParcel[size];
        }
    };
}
