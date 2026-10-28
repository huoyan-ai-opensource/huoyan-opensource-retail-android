package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 本地商品查询分页回执（queryGoods）：code/reason + 分页元数据
 * （total/pages/current/size）+ records[]（含 isDeleted 字段——isDeleted=1
 * 筛选时记录携带软删状态）。错误回执 records 空列表、数值字段 0（不假装
 * 成功）。空结果为 total=0/pages=0/records 空的正常回执（code=0）。
 */
public final class GoodsQueryResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final long total;
    private final long pages;
    private final int current;
    private final int size;
    private final List<GoodsParcel> records;

    public GoodsQueryResultParcel(int code, String reason, long total, long pages,
            int current, int size, List<GoodsParcel> records) {
        this.code = code;
        this.reason = reason;
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

    /** 过滤后总条数（分页元数据）。 */
    public long getTotal() {
        return total;
    }

    /** 总页数（ceil(total/size)）。 */
    public long getPages() {
        return pages;
    }

    /** 当前页（生效页码——缺省/0 归一后）。 */
    public int getCurrent() {
        return current;
    }

    /** 页大小（生效值——缺省/0 归一后）。 */
    public int getSize() {
        return size;
    }

    /** 本页条目（越界页为空列表；错误回执空列表）。 */
    public List<GoodsParcel> getRecords() {
        return records;
    }

    private GoodsQueryResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
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
        if (!(other instanceof GoodsQueryResultParcel)) {
            return false;
        }
        GoodsQueryResultParcel that = (GoodsQueryResultParcel) other;
        return code == that.code
                && total == that.total
                && pages == that.pages
                && current == that.current
                && size == that.size
                && Objects.equals(reason, that.reason)
                && Objects.equals(records, that.records);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, total, pages, current, size, records);
    }

    @Override
    public String toString() {
        return "GoodsQueryResultParcel(code=" + code + ", reason=" + reason
                + ", total=" + total + ", pages=" + pages + ", current=" + current
                + ", size=" + size + ", records=" + records + ")";
    }

    public static final Creator<GoodsQueryResultParcel> CREATOR =
            new Creator<GoodsQueryResultParcel>() {
        @Override
        public GoodsQueryResultParcel createFromParcel(Parcel parcel) {
            return new GoodsQueryResultParcel(parcel);
        }

        @Override
        public GoodsQueryResultParcel[] newArray(int size) {
            return new GoodsQueryResultParcel[size];
        }
    };
}
