package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 图片上传回执（两段式）：uploadPhoto 的字节上传结果——
 * code=0 成功时 photoFileUrl 为可访问 object URL（http(s)），供
 * submitFeedback 的 photo_file_url 使用；非 0 时 reason 携带错误说明。
 */
public final class PhotoUploadResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final String photoFileUrl;

    public PhotoUploadResultParcel(int code, String reason, String photoFileUrl) {
        this.code = code;
        this.reason = reason;
        this.photoFileUrl = photoFileUrl;
    }

    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 上传成功后返回的 object URL；code≠0 时为 null。 */
    public String getPhotoFileUrl() {
        return photoFileUrl;
    }

    private PhotoUploadResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        photoFileUrl = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeString(photoFileUrl);
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
        if (!(other instanceof PhotoUploadResultParcel)) {
            return false;
        }
        PhotoUploadResultParcel that = (PhotoUploadResultParcel) other;
        return code == that.code
                && Objects.equals(reason, that.reason)
                && Objects.equals(photoFileUrl, that.photoFileUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, photoFileUrl);
    }

    @Override
    public String toString() {
        return "PhotoUploadResultParcel(code=" + code + ", reason=" + reason
                + ", photoFileUrl=" + photoFileUrl + ")";
    }

    public static final Creator<PhotoUploadResultParcel> CREATOR =
            new Creator<PhotoUploadResultParcel>() {
                @Override
                public PhotoUploadResultParcel createFromParcel(Parcel parcel) {
                    return new PhotoUploadResultParcel(parcel);
                }

                @Override
                public PhotoUploadResultParcel[] newArray(int size) {
                    return new PhotoUploadResultParcel[size];
                }
            };
}
