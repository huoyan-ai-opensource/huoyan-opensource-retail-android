package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 商品列表回执（listGoods）：code/reason + 分页元数据（total/pages/
 * current/size）+ 同步契约（游标/模式）+ records[]（同步条目形态，含 isDeleted
 * 增量判据）。错误回执 records 空列表、数值字段 0（不假装成功）。
 */
public final class GoodsListResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final String shopId;
    private final String syncMode;
    private final long syncTimestamp;
    private final String serverTime;
    private final long total;
    private final long pages;
    private final int current;
    private final int size;
    private final List<GoodsParcel> records;

    public GoodsListResultParcel(int code, String reason, String shopId, String syncMode,
            long syncTimestamp, String serverTime, long total, long pages, int current,
            int size, List<GoodsParcel> records) {
        this.code = code;
        this.reason = reason;
        this.shopId = shopId;
        this.syncMode = syncMode;
        this.syncTimestamp = syncTimestamp;
        this.serverTime = serverTime;
        this.total = total;
        this.pages = pages;
        this.current = current;
        this.size = size;
        this.records = records == null ? Collections.<GoodsParcel>emptyList() : records;
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    public String getShopId() {
        return shopId;
    }

    /** 同步模式：full（全量）/ incremental（增量）。 */
    public String getSyncMode() {
        return syncMode;
    }

    /** 下次增量游标（epoch 秒）。 */
    public long getSyncTimestamp() {
        return syncTimestamp;
    }

    public String getServerTime() {
        return serverTime;
    }

    /** 总条数（分页元数据）。 */
    public long getTotal() {
        return total;
    }

    /** 总页数。 */
    public long getPages() {
        return pages;
    }

    /** 当前页。 */
    public int getCurrent() {
        return current;
    }

    /** 每页大小。 */
    public int getSize() {
        return size;
    }

    /** 本页条目（错误回执空列表）。 */
    public List<GoodsParcel> getRecords() {
        return records;
    }

    private GoodsListResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        shopId = parcel.readString();
        syncMode = parcel.readString();
        syncTimestamp = parcel.readLong();
        serverTime = parcel.readString();
        total = parcel.readLong();
        pages = parcel.readLong();
        current = parcel.readInt();
        size = parcel.readInt();
        records = parcel.createTypedArrayList(GoodsParcel.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeString(shopId);
        parcel.writeString(syncMode);
        parcel.writeLong(syncTimestamp);
        parcel.writeString(serverTime);
        parcel.writeLong(total);
        parcel.writeLong(pages);
        parcel.writeInt(current);
        parcel.writeInt(size);
        parcel.writeTypedList(records);
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
        if (!(other instanceof GoodsListResultParcel)) {
            return false;
        }
        GoodsListResultParcel that = (GoodsListResultParcel) other;
        return code == that.code
                && syncTimestamp == that.syncTimestamp
                && total == that.total
                && pages == that.pages
                && current == that.current
                && size == that.size
                && Objects.equals(reason, that.reason)
                && Objects.equals(shopId, that.shopId)
                && Objects.equals(syncMode, that.syncMode)
                && Objects.equals(serverTime, that.serverTime)
                && Objects.equals(records, that.records);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, shopId, syncMode, syncTimestamp, serverTime,
                total, pages, current, size, records);
    }

    @Override
    public String toString() {
        return "GoodsListResultParcel(code=" + code + ", reason=" + reason
                + ", shopId=" + shopId + ", syncMode=" + syncMode
                + ", syncTimestamp=" + syncTimestamp + ", serverTime=" + serverTime
                + ", total=" + total + ", pages=" + pages + ", current=" + current
                + ", size=" + size + ", records=" + records + ")";
    }

    public static final Creator<GoodsListResultParcel> CREATOR = new Creator<GoodsListResultParcel>() {
        @Override
        public GoodsListResultParcel createFromParcel(Parcel parcel) {
            return new GoodsListResultParcel(parcel);
        }

        @Override
        public GoodsListResultParcel[] newArray(int size) {
            return new GoodsListResultParcel[size];
        }
    };
}
