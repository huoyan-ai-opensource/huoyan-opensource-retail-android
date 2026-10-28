package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 学习结果回执：{@code itemLearn()} 的载荷——code=0 时 uid 为新
 * 学习条目的持久化句柄（&gt;0；0=无检测框的正常结果，业务侧提示重试）；
 * <b>UID 持有契约：业务端必须持久化持有 uid</b>，itemForgetByUid 依赖它做单条遗忘。
 * 非 0 时 reason 携带错误说明。
 */
public final class LearnResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final long uid;

    public LearnResultParcel(int code, String reason, long uid) {
        this.code = code;
        this.reason = reason;
        this.uid = uid;
    }

    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 新学习条目 UID（code=0 时有效；0=无检测框）。 */
    public long getUid() {
        return uid;
    }

    private LearnResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        uid = parcel.readLong();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeLong(uid);
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
        if (!(other instanceof LearnResultParcel)) {
            return false;
        }
        LearnResultParcel that = (LearnResultParcel) other;
        return code == that.code && uid == that.uid && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        return 31 * result + (int) (uid ^ (uid >>> 32));
    }

    @Override
    public String toString() {
        return "LearnResultParcel(code=" + code + ", reason=" + reason + ", uid=" + uid + ")";
    }

    public static final Creator<LearnResultParcel> CREATOR = new Creator<LearnResultParcel>() {
        @Override
        public LearnResultParcel createFromParcel(Parcel parcel) {
            return new LearnResultParcel(parcel);
        }

        @Override
        public LearnResultParcel[] newArray(int size) {
            return new LearnResultParcel[size];
        }
    };
}
