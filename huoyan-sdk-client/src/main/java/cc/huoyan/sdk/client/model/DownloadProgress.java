package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 模型下载进度回执（）：{@code getDownloadProgress()} 的载荷——
 * downloaded 已收字节、total manifest 声明总字节（0=未知）。code=0 时字段有效；
 * 非 0 时 reason 携带说明（如无进行中的下载，字段语义见 reason）。
 */
public final class DownloadProgress {

    private final int code;
    private final String reason;
    private final long downloaded;
    private final long total;

    /** 构造数据对象。 */
    public DownloadProgress(int code, String reason, long downloaded, long total) {
        this.code = code;
        this.reason = reason;
        this.downloaded = downloaded;
        this.total = total;
    }

    /** 结果码；0 表示请求成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 已收字节。 */
    public long getDownloaded() {
        return downloaded;
    }

    /** manifest 声明总字节；0=未知。 */
    public long getTotal() {
        return total;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DownloadProgress)) {
            return false;
        }
        DownloadProgress that = (DownloadProgress) other;
        return code == that.code
                && downloaded == that.downloaded
                && total == that.total
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        result = 31 * result + (int) (downloaded ^ (downloaded >>> 32));
        return 31 * result + (int) (total ^ (total >>> 32));
    }

    @Override
    public String toString() {
        return "DownloadProgress(code=" + code + ", reason=" + reason
                + ", downloaded=" + downloaded + ", total=" + total + ")";
    }

}
