package cc.huoyan.sdk.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 图片流程回执。终态含下载、学习、跳过及失败明细；进度查询的 successCount/failCount 表示当前阶段。 */
public final class ImageFlowProgress {

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
    private final List<FailedImage> failedImages;

    /** 构造图片流程回执。 */
    public ImageFlowProgress(int code, String reason, boolean active, String phase,
            long current, long total, long successCount, long failCount, String failStats,
            int failCode, String failReason) {
        this(code, reason, active, phase, current, total, successCount, failCount, failStats,
                failCode, failReason, null);
    }

    /** 构造图片流程回执。 */
    public ImageFlowProgress(int code, String reason, boolean active, String phase,
            long current, long total, long successCount, long failCount, String failStats,
            int failCode, String failReason, String publicDir) {
        this(code, reason, active, phase, current, total, successCount, failCount, failStats,
                failCode, failReason, publicDir, 0L, null);
    }

    /** 构造图片流程回执。 */
    public ImageFlowProgress(int code, String reason, boolean active, String phase,
            long current, long total, long successCount, long failCount, String failStats,
            int failCode, String failReason, String publicDir, long skippedCount,
            String skipReasonStats) {
        this(code, reason, active, phase, current, total, successCount, failCount,
                failStats, failCode, failReason, publicDir, skippedCount, skipReasonStats,
                0L, 0L, null, Collections.emptyList());
    }

    /** 下载学习终态完整回执；调用端须使用同版本客户端契约。 */
    public ImageFlowProgress(int code, String reason, boolean active, String phase,
            long current, long total, long successCount, long failCount, String failStats,
            int failCode, String failReason, String publicDir, long skippedCount,
            String skipReasonStats, long downloadSuccess, long downloadFail,
            String downloadFailStats, List<FailedImage> failedImages) {
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
    public List<FailedImage> getFailedImages() { return failedImages; }

    /** 图片流程阶段，未知新增阶段保留原 phase 字段供诊断。 */
    public enum Phase {
        /** 尚未开始。 */ IDLE,
        /** 正在下载。 */ DOWNLOADING,
        /** 正在学习。 */ LEARNING,
        /** 流程完成。 */ SUCCESS,
        /** 流程失败。 */ FAILED,
        /** 无法识别或缺失阶段。 */ UNKNOWN
    }
    /** 综合流程回执的终态。 */
    public enum Outcome {
        /** 全部成功。 */ SUCCESS,
        /** 部分图片失败。 */ PARTIAL_SUCCESS,
        /** 明确失败。 */ FAILURE,
        /** 请求未发送。 */ NOT_EXECUTED,
        /** 没有可确认的最终回执。 */ UNKNOWN
    }
    /** 将 SDK 阶段翻译为类型，同时保留 getPhase 原始字符串。 */
    public Phase phase() {
        try { return Phase.valueOf(phase == null ? "UNKNOWN" : phase); }
        catch (IllegalArgumentException ignored) { return Phase.UNKNOWN; }
    }
    /** 已开始学习或学习完成，供宿主选择进度条。 */
    public boolean learningStarted() { return code == 0 && (phase() == Phase.LEARNING || phase() == Phase.SUCCESS || current > 0); }
    /** 最终操作回执解释；不能以轮询快照代替操作回执。 */
    public Outcome outcome() {
        cc.huoyan.sdk.client.operation.OperationOutcome execution = cc.huoyan.sdk.client.operation.OperationOutcome.fromCode(code);
        if (execution == cc.huoyan.sdk.client.operation.OperationOutcome.UNKNOWN) return Outcome.UNKNOWN;
        if (execution == cc.huoyan.sdk.client.operation.OperationOutcome.NOT_EXECUTED) return Outcome.NOT_EXECUTED;
        if (code != 0 || failCode != 0) return Outcome.FAILURE;
        if (phase() != Phase.SUCCESS) return active ? Outcome.UNKNOWN : Outcome.FAILURE;
        return failCount > 0 || downloadFail > 0 ? Outcome.PARTIAL_SUCCESS : Outcome.SUCCESS;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ImageFlowProgress)) {
            return false;
        }
        ImageFlowProgress that = (ImageFlowProgress) other;
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
        return "ImageFlowProgress(code=" + code + ", reason=" + reason
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

}
