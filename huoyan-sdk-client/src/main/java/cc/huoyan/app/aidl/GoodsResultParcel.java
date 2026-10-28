package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 商品单条回执（createGoods/updateGoods）：code/reason + record
 * （CRUD 条目全字段——含门店/客户冗余列与建档时间）。错误回执 record=null
 * （不假装成功）。
 */
public final class GoodsResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final GoodsParcel record;

    public GoodsResultParcel(int code, String reason, GoodsParcel record) {
        this.code = code;
        this.reason = reason;
        this.record = record;
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    /** 创建/更新后的商品终态；错误时 null。 */
    public GoodsParcel getRecord() {
        return record;
    }

    private GoodsResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        record = (GoodsParcel) parcel.readValue(GoodsResultParcel.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeValue(record);
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
        if (!(other instanceof GoodsResultParcel)) {
            return false;
        }
        GoodsResultParcel that = (GoodsResultParcel) other;
        return code == that.code
                && Objects.equals(reason, that.reason)
                && Objects.equals(record, that.record);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, record);
    }

    @Override
    public String toString() {
        return "GoodsResultParcel(code=" + code + ", reason=" + reason
                + ", record=" + record + ")";
    }

    public static final Creator<GoodsResultParcel> CREATOR = new Creator<GoodsResultParcel>() {
        @Override
        public GoodsResultParcel createFromParcel(Parcel parcel) {
            return new GoodsResultParcel(parcel);
        }

        @Override
        public GoodsResultParcel[] newArray(int size) {
            return new GoodsResultParcel[size];
        }
    };
}
