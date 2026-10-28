package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 识别请求回执：code=0 时 items 有效；非 0 时 reason 携带错误说明、items 为空
 * ——与 WS 通道 recognize 响应同语义（C4 两通道语义一致）。
 */
public final class RecognizeResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final List<RecognitionItemParcel> items;

    public RecognizeResultParcel(int code, String reason, List<RecognitionItemParcel> items) {
        this.code = code;
        this.reason = reason;
        this.items = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(items, "items 不能为空")));
    }

    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    public List<RecognitionItemParcel> getItems() {
        return items;
    }

    private RecognizeResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        List<RecognitionItemParcel> read = parcel.createTypedArrayList(RecognitionItemParcel.CREATOR);
        items = read == null ? Collections.<RecognitionItemParcel>emptyList() : read;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeTypedList(items);
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
        if (!(other instanceof RecognizeResultParcel)) {
            return false;
        }
        RecognizeResultParcel that = (RecognizeResultParcel) other;
        return code == that.code && Objects.equals(reason, that.reason) && items.equals(that.items);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        return 31 * result + items.hashCode();
    }

    @Override
    public String toString() {
        return "RecognizeResultParcel(code=" + code + ", reason=" + reason + ", items=" + items + ")";
    }

    public static final Creator<RecognizeResultParcel> CREATOR = new Creator<RecognizeResultParcel>() {
        @Override
        public RecognizeResultParcel createFromParcel(Parcel parcel) {
            return new RecognizeResultParcel(parcel);
        }

        @Override
        public RecognizeResultParcel[] newArray(int size) {
            return new RecognizeResultParcel[size];
        }
    };
}
