package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 失败图片查询信封：只有 code=0 的空列表才表示当前没有失败记录。 */
public final class FailedImagesResultParcel implements Parcelable {
    private final int code;
    private final String reason;
    private final List<FailedImageParcel> items;

    /** 构造失败图片查询回执。 */
    public FailedImagesResultParcel(int code, String reason, List<FailedImageParcel> items) {
        this.code = code;
        this.reason = reason;
        this.items = Collections.unmodifiableList(new ArrayList<>(
                items == null ? Collections.emptyList() : items));
    }

    /** 取得回执码，0 表示成功。 */
    public int getCode() { return code; }
    /** 取得错误说明。 */
    public String getReason() { return reason; }
    /** 取得失败图片记录。 */
    public List<FailedImageParcel> getItems() { return items; }

    private FailedImagesResultParcel(Parcel parcel) {
        this(parcel.readInt(), parcel.readString(),
                parcel.createTypedArrayList(FailedImageParcel.CREATOR));
    }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeTypedList(items);
    }

    @Override public int describeContents() { return 0; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FailedImagesResultParcel)) return false;
        FailedImagesResultParcel that = (FailedImagesResultParcel) other;
        return code == that.code && Objects.equals(reason, that.reason)
                && Objects.equals(items, that.items);
    }

    @Override public int hashCode() { return Objects.hash(code, reason, items); }

    public static final Creator<FailedImagesResultParcel> CREATOR =
            new Creator<FailedImagesResultParcel>() {
                @Override public FailedImagesResultParcel createFromParcel(Parcel parcel) {
                    return new FailedImagesResultParcel(parcel);
                }
                @Override public FailedImagesResultParcel[] newArray(int size) {
                    return new FailedImagesResultParcel[size];
                }
            };
}
