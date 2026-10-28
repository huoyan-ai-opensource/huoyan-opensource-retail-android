package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 本地商品批量操作回执（batchUpdateGoods/batchDeleteGoods）：
 * code/reason + items[]（逐条 goods_id/code/reason——与入参 goodsIds 等长
 * 同序）。**部分成功语义**：code=0 表示调用执行完毕，条目级成败看 items；
 * code≠0（空数组/空 changes 参数错、存储事务失败整体回滚）时 items 空列表。
 */
public final class GoodsBatchResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final List<GoodsBatchItemParcel> items;

    public GoodsBatchResultParcel(int code, String reason, List<GoodsBatchItemParcel> items) {
        this.code = code;
        this.reason = reason;
        this.items = items == null ? Collections.<GoodsBatchItemParcel>emptyList() : items;
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    /** 逐条结果（与入参 goodsIds 等长同序；错误回执空列表）。 */
    public List<GoodsBatchItemParcel> getItems() {
        return items;
    }

    private GoodsBatchResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        items = parcel.createTypedArrayList(GoodsBatchItemParcel.CREATOR);
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
        if (!(other instanceof GoodsBatchResultParcel)) {
            return false;
        }
        GoodsBatchResultParcel that = (GoodsBatchResultParcel) other;
        return code == that.code
                && Objects.equals(reason, that.reason)
                && Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, items);
    }

    @Override
    public String toString() {
        return "GoodsBatchResultParcel(code=" + code + ", reason=" + reason
                + ", items=" + items + ")";
    }

    public static final Creator<GoodsBatchResultParcel> CREATOR =
            new Creator<GoodsBatchResultParcel>() {
        @Override
        public GoodsBatchResultParcel createFromParcel(Parcel parcel) {
            return new GoodsBatchResultParcel(parcel);
        }

        @Override
        public GoodsBatchResultParcel[] newArray(int size) {
            return new GoodsBatchResultParcel[size];
        }
    };
}
