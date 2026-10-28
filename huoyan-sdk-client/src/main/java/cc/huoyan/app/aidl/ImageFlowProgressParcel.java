package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 下载图片并学习流程进度快照（getImageDownloadProgress/
 * getImageLearnProgress 回执）：code/reason 为查询回执信封（0 = 查询成功）；
 * 流程状态在字段——active（无流程恒 false）、phase（IDLE/DOWNLOADING/
 * LEARNING/SUCCESS/FAILED）、current/total（本阶段 1/N）、successCount/
 * failCount（本阶段累计）、failStats（按原因聚合「原因=次数」串）、failCode/
 * failReason（仅 FAILED 非 0/非 null）。
 *
 * <p>尾插 skippedCount/skipReasonStats（下载学习两入口回执——
 * downloadImagesAndLearn/downloadImagesAndLearnByGoodsId 同构携带；进度查询
 * 快照恒 0/null）：被跳过的图片张数与按原因聚合（商品不在本地库/旧数据清理
 * 失败——与失败统计分立，不影响终态）。既有 11/12 参构造委托缺省（0/null），
 * 旧调用点语义不变。</p>
 *
 * <p>下载学习终态同时携带 downloadSuccess/downloadFail/downloadFailStats 与
 * failedImages，避免 AIDL 丢失 WS 已有的下载阶段统计和失败明细。
 * 终态的 successCount/failCount/failStats 是学习阶段统计；进度查询仍表示本阶段。</p>
 */
public final class ImageFlowProgressParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final boolean active;
    private final String phase;
    private final long current;
    private final long total;
    private final long successCount;
    private final long failCount;
    private final String failStats;
    private final int failCode;
    private final String failReason;
    private final String publicDir;
    private final long skippedCount;
    private final String skipReasonStats;
    private final long downloadSuccess;
    private final long downloadFail;
    private final String downloadFailStats;
    private final List<FailedImageParcel> failedImages;

    /** 构造图片流程回执。 */
    public ImageFlowProgressParcel(int code, String reason, boolean active, String phase,
            long current, long total, long successCount, long failCount, String failStats,
            int failCode, String failReason) {
        this(code, reason, active, phase, current, total, successCount, failCount, failStats,
                failCode, failReason, null);
    }

    /** 构造图片流程回执。 */
    public ImageFlowProgressParcel(int code, String reason, boolean active, String phase,
            long current, long total, long successCount, long failCount, String failStats,
            int failCode, String failReason, String publicDir) {
        this(code, reason, active, phase, current, total, successCount, failCount, failStats,
                failCode, failReason, publicDir, 0L, null);
    }

    /** 构造图片流程回执。 */
    public ImageFlowProgressParcel(int code, String reason, boolean active, String phase,
            long current, long total, long successCount, long failCount, String failStats,
            int failCode, String failReason, String publicDir, long skippedCount,
            String skipReasonStats) {
        this(code, reason, active, phase, current, total, successCount, failCount,
                failStats, failCode, failReason, publicDir, skippedCount, skipReasonStats,
                0L, 0L, null, Collections.emptyList());
    }

    /** 下载学习终态完整回执；调用端须使用同版本 Parcelable 契约。 */
    public ImageFlowProgressParcel(int code, String reason, boolean active, String phase,
            long current, long total, long successCount, long failCount, String failStats,
            int failCode, String failReason, String publicDir, long skippedCount,
            String skipReasonStats, long downloadSuccess, long downloadFail,
            String downloadFailStats, List<FailedImageParcel> failedImages) {
        this.code = code;
        this.reason = reason;
        this.active = active;
        this.phase = phase;
        this.current = current;
        this.total = total;
        this.successCount = successCount;
        this.failCount = failCount;
        this.failStats = failStats;
        this.failCode = failCode;
        this.failReason = failReason;
        this.publicDir = publicDir;
        this.skippedCount = skippedCount;
        this.skipReasonStats = skipReasonStats;
        this.downloadSuccess = downloadSuccess;
        this.downloadFail = downloadFail;
        this.downloadFailStats = downloadFailStats;
        this.failedImages = Collections.unmodifiableList(new ArrayList<>(
                failedImages == null ? Collections.emptyList() : failedImages));
    }

    /** 取得回执码，0 表示成功。 */
    public int getCode() {
        return code;
    }

    /** 取得错误说明。 */
    public String getReason() {
        return reason;
    }

    /** 流程进行中（DOWNLOADING/LEARNING）；无流程（IDLE）恒 false。 */
    public boolean isActive() {
        return active;
    }

    /** 流程阶段：IDLE/DOWNLOADING/LEARNING/SUCCESS/FAILED（终态停留可查）。 */
    public String getPhase() {
        return phase;
    }

    /** 本阶段已处理张数（失败也计）。 */
    public long getCurrent() {
        return current;
    }

    /** 本阶段总张数（0 = 未知/未开始）。 */
    public long getTotal() {
        return total;
    }

    /** 取得成功数量。 */
    public long getSuccessCount() {
        return successCount;
    }

    /** 取得失败数量。 */
    public long getFailCount() {
        return failCount;
    }

    /** 按原因聚合的「原因=次数」串（无失败为 null）。 */
    public String getFailStats() {
        return failStats;
    }

    /** 流程失败码（仅 FAILED 非 0）。 */
    public int getFailCode() {
        return failCode;
    }

    /** 流程失败聚合原因（仅 FAILED 非 null）。 */
    public String getFailReason() {
        return failReason;
    }

    /** 公共外部存储目录（huoyan/goods/...，downloadImagesAndLearn 回执携带）。 */
    public String getPublicDir() {
        return publicDir;
    }

    /** 被跳过的图片张数（商品不在本地库/旧数据清理失败；进度查询恒 0）。 */
    public long getSkippedCount() {
        return skippedCount;
    }

    /** 跳过按原因聚合「原因=次数」串（无跳过为 null）。 */
    public String getSkipReasonStats() {
        return skipReasonStats;
    }

    /** 取得下载成功张数。 */
    public long getDownloadSuccess() { return downloadSuccess; }

    /** 取得下载失败张数。 */
    public long getDownloadFail() { return downloadFail; }

    /** 取得下载失败原因统计。 */
    public String getDownloadFailStats() { return downloadFailStats; }

    /** 取得本次流程失败图片明细。 */
    public List<FailedImageParcel> getFailedImages() { return failedImages; }

    private ImageFlowProgressParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        active = parcel.readByte() != 0;
        phase = parcel.readString();
        current = parcel.readLong();
        total = parcel.readLong();
        successCount = parcel.readLong();
        failCount = parcel.readLong();
        failStats = parcel.readString();
        failCode = parcel.readInt();
        failReason = parcel.readString();
        publicDir = parcel.readString();
        skippedCount = parcel.readLong();
        skipReasonStats = parcel.readString();
        downloadSuccess = parcel.readLong();
        downloadFail = parcel.readLong();
        downloadFailStats = parcel.readString();
        List<FailedImageParcel> images = parcel.createTypedArrayList(FailedImageParcel.CREATOR);
        failedImages = Collections.unmodifiableList(images == null
                ? Collections.emptyList() : images);
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
        parcel.writeString(failStats);
        parcel.writeInt(failCode);
        parcel.writeString(failReason);
        parcel.writeString(publicDir);
        parcel.writeLong(skippedCount);
        parcel.writeString(skipReasonStats);
        parcel.writeLong(downloadSuccess);
        parcel.writeLong(downloadFail);
        parcel.writeString(downloadFailStats);
        parcel.writeTypedList(failedImages);
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
        if (!(other instanceof ImageFlowProgressParcel)) {
            return false;
        }
        ImageFlowProgressParcel that = (ImageFlowProgressParcel) other;
        return code == that.code
                && active == that.active
                && current == that.current
                && total == that.total
                && successCount == that.successCount
                && failCount == that.failCount
                && failCode == that.failCode
                && skippedCount == that.skippedCount
                && downloadSuccess == that.downloadSuccess
                && downloadFail == that.downloadFail
                && Objects.equals(reason, that.reason)
                && Objects.equals(phase, that.phase)
                && Objects.equals(failStats, that.failStats)
                && Objects.equals(failReason, that.failReason)
                && Objects.equals(publicDir, that.publicDir)
                && Objects.equals(skipReasonStats, that.skipReasonStats)
                && Objects.equals(downloadFailStats, that.downloadFailStats)
                && Objects.equals(failedImages, that.failedImages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, active, phase, current, total,
                successCount, failCount, failStats, failCode, failReason, publicDir,
                skippedCount, skipReasonStats, downloadSuccess, downloadFail,
                downloadFailStats, failedImages);
    }

    @Override
    public String toString() {
        return "ImageFlowProgressParcel(code=" + code + ", reason=" + reason
                + ", active=" + active + ", phase=" + phase + ", current=" + current
                + ", total=" + total + ", successCount=" + successCount
                + ", failCount=" + failCount + ", failStats=" + failStats
                + ", failCode=" + failCode + ", failReason=" + failReason
                + ", publicDir=" + publicDir + ", skippedCount=" + skippedCount
                + ", skipReasonStats=" + skipReasonStats
                + ", downloadSuccess=" + downloadSuccess + ", downloadFail=" + downloadFail
                + ", downloadFailStats=" + downloadFailStats
                + ", failedImages=" + failedImages + ")";
    }

    public static final Creator<ImageFlowProgressParcel> CREATOR = new Creator<ImageFlowProgressParcel>() {
        @Override
        public ImageFlowProgressParcel createFromParcel(Parcel parcel) {
            return new ImageFlowProgressParcel(parcel);
        }

        @Override
        public ImageFlowProgressParcel[] newArray(int size) {
            return new ImageFlowProgressParcel[size];
        }
    };
}
