package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

/**
 * 类别下样本 UID 明细回执：{@code getItemClazzDetail()} 的载荷——
 * code=0 时 uid 为该类别下样本 UID 升序列表（对齐桥接层 nativeGetItemClazzDetail
 * 语义；clazz 不存在返回空列表，空明细非异常）。非 0 时 reason 携带错误说明、
 * 数组为空。
 */
public final class ItemClazzDetailParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final long[] uid;

    public ItemClazzDetailParcel(int code, String reason, long[] uid) {
        this.code = code;
        this.reason = reason;
        this.uid = uid == null ? new long[0] : uid.clone();
    }

    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 样本 UID 数组（防御性拷贝；升序，空=无样本/类别不存在）。 */
    public long[] getUid() {
        return uid.clone();
    }

    private ItemClazzDetailParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        long[] read = parcel.createLongArray();
        uid = read == null ? new long[0] : read;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeLongArray(uid);
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
        if (!(other instanceof ItemClazzDetailParcel)) {
            return false;
        }
        ItemClazzDetailParcel that = (ItemClazzDetailParcel) other;
        return code == that.code && Objects.equals(reason, that.reason) && Arrays.equals(uid, that.uid);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        return 31 * result + Arrays.hashCode(uid);
    }

    @Override
    public String toString() {
        return "ItemClazzDetailParcel(code=" + code + ", reason=" + reason
                + ", uid=" + Arrays.toString(uid) + ")";
    }

    public static final Creator<ItemClazzDetailParcel> CREATOR = new Creator<ItemClazzDetailParcel>() {
        @Override
        public ItemClazzDetailParcel createFromParcel(Parcel parcel) {
            return new ItemClazzDetailParcel(parcel);
        }

        @Override
        public ItemClazzDetailParcel[] newArray(int size) {
            return new ItemClazzDetailParcel[size];
        }
    };
}
