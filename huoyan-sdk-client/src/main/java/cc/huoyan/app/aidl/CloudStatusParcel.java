package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 云端托管开关回执：code=0 时 {@code enabled} 为开关终态；
 * 非 0 携带 reason（如 true→false 拒绝的「不可关闭」说明）——此时 enabled
 * 不代表真实状态，业务端以 code 判读。单向开关语义见
 * {@code CloudStatusStore}（缺省 false；开启后不可关闭；重启读同一持久化状态）。
 */
public final class CloudStatusParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final boolean enabled;

    public CloudStatusParcel(int code, String reason, boolean enabled) {
        this.code = code;
        this.reason = reason;
        this.enabled = enabled;
    }

    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 开关终态（code=0 有效）。 */
    public boolean isEnabled() {
        return enabled;
    }

    private CloudStatusParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        enabled = parcel.readInt() != 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeInt(enabled ? 1 : 0);
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
        if (!(other instanceof CloudStatusParcel)) {
            return false;
        }
        CloudStatusParcel that = (CloudStatusParcel) other;
        return code == that.code && enabled == that.enabled
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * code + Boolean.hashCode(enabled)) + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return "CloudStatusParcel(code=" + code + ", reason=" + reason
                + ", enabled=" + enabled + ")";
    }

    public static final Creator<CloudStatusParcel> CREATOR = new Creator<CloudStatusParcel>() {
        @Override
        public CloudStatusParcel createFromParcel(Parcel parcel) {
            return new CloudStatusParcel(parcel);
        }

        @Override
        public CloudStatusParcel[] newArray(int size) {
            return new CloudStatusParcel[size];
        }
    };
}
