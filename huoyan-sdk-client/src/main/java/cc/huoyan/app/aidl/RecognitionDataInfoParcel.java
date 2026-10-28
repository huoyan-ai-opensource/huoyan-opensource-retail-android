package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 识别数据备份信息回执（getRecognitionDataInfo）：code=0 时四字段
 * 有效——{@code backupTimeEpochMs}（epoch 毫秒；0 = 从未备份，属正常空态非
 * 错误）、{@code backupTimeText}（yyyy-MM-dd HH:mm:ss；未备份为空串）、
 * {@code schemaVersion}/{@code fileFormatVersion}（当前库两版本号；未知/未
 * 备份为 0）——与 WS 载荷 {@code data.backup_time_epoch_ms} 等蛇形键同构。
 *
 * <p>code≠0 为失败（引擎/执行错误）——四字段为空态值，调用方以 code 判读。</p>
 */
public final class RecognitionDataInfoParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final long backupTimeEpochMs;
    private final String backupTimeText;
    private final long schemaVersion;
    private final long fileFormatVersion;

    /** 构造识别数据备份信息。 */
    public RecognitionDataInfoParcel(int code, String reason, long backupTimeEpochMs,
            String backupTimeText, long schemaVersion, long fileFormatVersion) {
        this.code = code;
        this.reason = reason;
        this.backupTimeEpochMs = backupTimeEpochMs;
        this.backupTimeText = backupTimeText;
        this.schemaVersion = schemaVersion;
        this.fileFormatVersion = fileFormatVersion;
    }

    /** 取得回执码，0 表示成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 备份时间点（epoch 毫秒）；0 = 从未备份（正常空态）。 */
    public long getBackupTimeEpochMs() {
        return backupTimeEpochMs;
    }

    /** 备份时间点格式文本（yyyy-MM-dd HH:mm:ss）；未备份为空串。 */
    public String getBackupTimeText() {
        return backupTimeText;
    }

    /** 当前库 schema 版本；未知/未备份为 0。 */
    public long getSchemaVersion() {
        return schemaVersion;
    }

    /** 当前库文件格式版本；未知/未备份为 0。 */
    public long getFileFormatVersion() {
        return fileFormatVersion;
    }

    private RecognitionDataInfoParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        backupTimeEpochMs = parcel.readLong();
        backupTimeText = parcel.readString();
        schemaVersion = parcel.readLong();
        fileFormatVersion = parcel.readLong();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeLong(backupTimeEpochMs);
        parcel.writeString(backupTimeText);
        parcel.writeLong(schemaVersion);
        parcel.writeLong(fileFormatVersion);
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
        if (!(other instanceof RecognitionDataInfoParcel)) {
            return false;
        }
        RecognitionDataInfoParcel that = (RecognitionDataInfoParcel) other;
        return code == that.code
                && backupTimeEpochMs == that.backupTimeEpochMs
                && schemaVersion == that.schemaVersion
                && fileFormatVersion == that.fileFormatVersion
                && Objects.equals(reason, that.reason)
                && Objects.equals(backupTimeText, that.backupTimeText);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * (31 * (31 * code + Long.hashCode(backupTimeEpochMs))
                + Long.hashCode(schemaVersion)) + Long.hashCode(fileFormatVersion))
                + Objects.hashCode(backupTimeText)) + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return "RecognitionDataInfoParcel(code=" + code + ", reason=" + reason
                + ", backupTimeEpochMs=" + backupTimeEpochMs
                + ", backupTimeText=" + backupTimeText + ", schemaVersion=" + schemaVersion
                + ", fileFormatVersion=" + fileFormatVersion + ")";
    }

    public static final Creator<RecognitionDataInfoParcel> CREATOR =
            new Creator<RecognitionDataInfoParcel>() {
                @Override
                public RecognitionDataInfoParcel createFromParcel(Parcel parcel) {
                    return new RecognitionDataInfoParcel(parcel);
                }

                @Override
                public RecognitionDataInfoParcel[] newArray(int size) {
                    return new RecognitionDataInfoParcel[size];
                }
            };
}
