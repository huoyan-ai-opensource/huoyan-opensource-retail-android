package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 本地商品计数回执（getGoodsCount）：code/reason + count（过滤后
 * 总条数——keyword/name/plu/isDeleted 四参过滤，不含分页）。错误回执 count=0
 * （不代表真实计数——以 code 判读）。
 */
public final class GoodsCountResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final long count;

    public GoodsCountResultParcel(int code, String reason, long count) {
        this.code = code;
        this.reason = reason;
        this.count = count;
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    /** 过滤后总条数（code=0 时有效）。 */
    public long getCount() {
        return count;
    }

    private GoodsCountResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        count = parcel.readLong();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeLong(count);
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
        if (!(other instanceof GoodsCountResultParcel)) {
            return false;
        }
        GoodsCountResultParcel that = (GoodsCountResultParcel) other;
        return code == that.code
                && count == that.count
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, count);
    }

    @Override
    public String toString() {
        return "GoodsCountResultParcel(code=" + code + ", reason=" + reason
                + ", count=" + count + ")";
    }

    public static final Creator<GoodsCountResultParcel> CREATOR =
            new Creator<GoodsCountResultParcel>() {
        @Override
        public GoodsCountResultParcel createFromParcel(Parcel parcel) {
            return new GoodsCountResultParcel(parcel);
        }

        @Override
        public GoodsCountResultParcel[] newArray(int size) {
            return new GoodsCountResultParcel[size];
        }
    };
}
