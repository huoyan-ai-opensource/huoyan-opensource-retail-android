package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 模型下载进度回执：{@code getDownloadProgress()} 的载荷——
 * downloaded 已收字节、total manifest 声明总字节（0=未知）。code=0 时字段有效；
 * 非 0 时 reason 携带说明（如无进行中的下载，字段语义见 reason）。
 */
public final class DownloadProgressParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final long downloaded;
    private final long total;

    public DownloadProgressParcel(int code, String reason, long downloaded, long total) {
        this.code = code;
        this.reason = reason;
        this.downloaded = downloaded;
        this.total = total;
    }

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

    private DownloadProgressParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        downloaded = parcel.readLong();
        total = parcel.readLong();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeLong(downloaded);
        parcel.writeLong(total);
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
        if (!(other instanceof DownloadProgressParcel)) {
            return false;
        }
        DownloadProgressParcel that = (DownloadProgressParcel) other;
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
        return "DownloadProgressParcel(code=" + code + ", reason=" + reason
                + ", downloaded=" + downloaded + ", total=" + total + ")";
    }

    public static final Creator<DownloadProgressParcel> CREATOR = new Creator<DownloadProgressParcel>() {
        @Override
        public DownloadProgressParcel createFromParcel(Parcel parcel) {
            return new DownloadProgressParcel(parcel);
        }

        @Override
        public DownloadProgressParcel[] newArray(int size) {
            return new DownloadProgressParcel[size];
        }
    };
}
