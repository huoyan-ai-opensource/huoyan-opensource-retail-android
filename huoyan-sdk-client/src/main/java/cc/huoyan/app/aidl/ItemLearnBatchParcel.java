package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 批量学习回执：{@code batchLearn()} 的载荷——code=0 时 boxes 为
 * 每框结果列表（与 detect 框序下标一致，无检测框为空列表，属正常结果）；非 0
 * 时 reason 携带整体错误说明（detect 失败/参数非法等——一次都没学）、boxes 为
 * 空。boxes 元素携带每框独立学习结果（code≠0 带原因不阻断其他框），结构见
 * {@link ItemLearnBoxParcel}。
 */
public final class ItemLearnBatchParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final List<ItemLearnBoxParcel> boxes;

    public ItemLearnBatchParcel(int code, String reason, List<ItemLearnBoxParcel> boxes) {
        this.code = code;
        this.reason = reason;
        this.boxes = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(boxes, "boxes 不能为空")));
    }

    public int getCode() {
        return code;
    }

    /** 整体错误说明；code=0 时为 null（每框错误说明在 boxes 元素内）。 */
    public String getReason() {
        return reason;
    }

    /** 每框结果列表（code=0 时有效；与 detect 框序一致，无框为空列表）。 */
    public List<ItemLearnBoxParcel> getBoxes() {
        return boxes;
    }

    private ItemLearnBatchParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        List<ItemLearnBoxParcel> read = parcel.createTypedArrayList(ItemLearnBoxParcel.CREATOR);
        boxes = read == null ? Collections.<ItemLearnBoxParcel>emptyList() : read;
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
        if (!(other instanceof ItemLearnBatchParcel)) {
            return false;
        }
        ItemLearnBatchParcel that = (ItemLearnBatchParcel) other;
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
        return "ItemLearnBatchParcel(code=" + code + ", reason=" + reason + ", boxes=" + boxes + ")";
    }

    public static final Creator<ItemLearnBatchParcel> CREATOR = new Creator<ItemLearnBatchParcel>() {
        @Override
        public ItemLearnBatchParcel createFromParcel(Parcel parcel) {
            return new ItemLearnBatchParcel(parcel);
        }

        @Override
        public ItemLearnBatchParcel[] newArray(int size) {
            return new ItemLearnBatchParcel[size];
        }
    };
}
