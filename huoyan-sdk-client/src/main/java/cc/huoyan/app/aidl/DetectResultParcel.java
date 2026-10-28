package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 纯检测回执：{@code detect()} 的载荷——code=0 时 boxes 为画框
 * 列表（无目标为空列表，属正常结果）；非 0 时 reason 携带错误说明、boxes 为空。
 */
public final class DetectResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final List<DetectBoxParcel> boxes;

    public DetectResultParcel(int code, String reason, List<DetectBoxParcel> boxes) {
        this.code = code;
        this.reason = reason;
        this.boxes = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(boxes, "boxes 不能为空")));
    }

    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 检测框列表（code=0 时有效；无目标为空列表）。 */
    public List<DetectBoxParcel> getBoxes() {
        return boxes;
    }

    private DetectResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        List<DetectBoxParcel> read = parcel.createTypedArrayList(DetectBoxParcel.CREATOR);
        boxes = read == null ? Collections.<DetectBoxParcel>emptyList() : read;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeTypedList(boxes);
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
        if (!(other instanceof DetectResultParcel)) {
            return false;
        }
        DetectResultParcel that = (DetectResultParcel) other;
        return code == that.code && Objects.equals(reason, that.reason) && boxes.equals(that.boxes);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        return 31 * result + boxes.hashCode();
    }

    @Override
    public String toString() {
        return "DetectResultParcel(code=" + code + ", reason=" + reason + ", boxes=" + boxes + ")";
    }

    public static final Creator<DetectResultParcel> CREATOR = new Creator<DetectResultParcel>() {
        @Override
        public DetectResultParcel createFromParcel(Parcel parcel) {
            return new DetectResultParcel(parcel);
        }

        @Override
        public DetectResultParcel[] newArray(int size) {
            return new DetectResultParcel[size];
        }
    };
}
