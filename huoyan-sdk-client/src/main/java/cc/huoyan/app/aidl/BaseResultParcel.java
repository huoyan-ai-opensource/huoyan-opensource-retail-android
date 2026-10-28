package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 通用动作结果回执（统一错误模型）：activate/renewCert/setBaseUrl/
 * itemForget/itemForgetByUid 等无载荷动作的统一形态——code=0 成功，非 0 时
 * reason 携带错误说明；业务 App 不解 native 细节。
 */
public final class BaseResultParcel implements Parcelable {

    private final int code;
    private final String reason;

    public BaseResultParcel(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    private BaseResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
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
        if (!(other instanceof BaseResultParcel)) {
            return false;
        }
        BaseResultParcel that = (BaseResultParcel) other;
        return code == that.code && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return 31 * code + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return "BaseResultParcel(code=" + code + ", reason=" + reason + ")";
    }

    public static final Creator<BaseResultParcel> CREATOR = new Creator<BaseResultParcel>() {
        @Override
        public BaseResultParcel createFromParcel(Parcel parcel) {
            return new BaseResultParcel(parcel);
        }

        @Override
        public BaseResultParcel[] newArray(int size) {
            return new BaseResultParcel[size];
        }
    };
}
