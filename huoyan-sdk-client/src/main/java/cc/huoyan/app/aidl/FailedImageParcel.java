package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 失败图片记录回执（Issue #110）：{@code getFailedImages} 的单条失败记录——clazz +
 * 后端图片 ID + 失败原因 + 局域网 URL（有完整图片时非空，无完整图片为空）。
 */
public final class FailedImageParcel implements Parcelable {

    private final String clazz;
    private final String imageId;
    private final String failureReason;
    private final String imageUrl;

    public FailedImageParcel(String clazz, String imageId, String failureReason, String imageUrl) {
        this.clazz = clazz;
        this.imageId = imageId;
        this.failureReason = failureReason;
        this.imageUrl = imageUrl;
    }

    public String getClazz() {
        return clazz;
    }

    public String getImageId() {
        return imageId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    private FailedImageParcel(Parcel parcel) {
        clazz = parcel.readString();
        imageId = parcel.readString();
        failureReason = parcel.readString();
        imageUrl = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(clazz);
        parcel.writeString(imageId);
        parcel.writeString(failureReason);
        parcel.writeString(imageUrl);
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
        if (!(other instanceof FailedImageParcel)) {
            return false;
        }
        FailedImageParcel that = (FailedImageParcel) other;
        return Objects.equals(clazz, that.clazz)
                && Objects.equals(imageId, that.imageId)
                && Objects.equals(failureReason, that.failureReason)
                && Objects.equals(imageUrl, that.imageUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, imageId, failureReason, imageUrl);
    }

    @Override
    public String toString() {
        return "FailedImageParcel(clazz=" + clazz + ", imageId=" + imageId
                + ", failureReason=" + failureReason + ", imageUrl=" + imageUrl + ")";
    }

    public static final Creator<FailedImageParcel> CREATOR =
            new Creator<FailedImageParcel>() {
                @Override
                public FailedImageParcel createFromParcel(Parcel parcel) {
                    return new FailedImageParcel(parcel);
                }

                @Override
                public FailedImageParcel[] newArray(int size) {
                    return new FailedImageParcel[size];
                }
            };
}
