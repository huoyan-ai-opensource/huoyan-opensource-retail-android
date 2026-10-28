package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 商品创建/更新请求载体（Issue #74，createGoods/updateGoods 入参）：字符串可选项
 * null = 不传；数值可选项经哨兵 + hasX 模式（-1 = 未传）。创建语义 name/plu 必填（空白在壳侧拦截得 ERR_ARGS）；更新
 * 全可选（仅传入字段生效，name/plu 传则不可空白）。契约校验不在此层（本包为
 * AIDL 对外契约，Issue #46 方案 A 纯度豁免边界）。
 */
public final class GoodsSaveParcel implements Parcelable {

    private final String name;
    private final String plu;
    private final String image;
    private final String ingredientsList;
    private final String shelfLife;
    private final String pinyin;
    private final String categoryName;
    private final String unit;
    private final int category;
    private final int saleMode;
    private final long price;
    private final long tareWeight;
    private final int sort;
    private final int status;
    private final boolean hasCategory;
    private final boolean hasSaleMode;
    private final boolean hasPrice;
    private final boolean hasTareWeight;
    private final boolean hasSort;
    private final boolean hasStatus;

    /**
     * 全参构造（数值可选项盒装：null → 哨兵 -1 + hasX=false）。
     *
     * @param category 商品类别（0-8 枚举）
     * @param saleMode 销售方式（0 按个 / 1 按重量）
     * @param price    单价（整数分）
     * @param tareWeight 皮重（克）
     * @param sort     排序（小在前）
     * @param status   状态（1 上架 / 2 下架）
     */
    public GoodsSaveParcel(String name, String plu, String image,
            String ingredientsList, String shelfLife, String pinyin,
            Integer category, String categoryName, String unit, Integer saleMode,
            Long tareWeight, Long price, Integer sort, Integer status) {
        this.name = name;
        this.plu = plu;
        this.image = image;
        this.ingredientsList = ingredientsList;
        this.shelfLife = shelfLife;
        this.pinyin = pinyin;
        this.categoryName = categoryName;
        this.unit = unit;
        this.category = category == null ? -1 : category.intValue();
        this.hasCategory = category != null;
        this.saleMode = saleMode == null ? -1 : saleMode.intValue();
        this.hasSaleMode = saleMode != null;
        this.price = price == null ? -1L : price.longValue();
        this.hasPrice = price != null;
        this.tareWeight = tareWeight == null ? -1L : tareWeight.longValue();
        this.hasTareWeight = tareWeight != null;
        this.sort = sort == null ? -1 : sort.intValue();
        this.hasSort = sort != null;
        this.status = status == null ? -1 : status.intValue();
        this.hasStatus = status != null;
    }

    public String getName() {
        return name;
    }

    /** 称重/店内码；创建必填，更新 null = 不更新。 */
    public String getPlu() {
        return plu;
    }

    public String getImage() {
        return image;
    }

    public String getIngredientsList() {
        return ingredientsList;
    }

    public String getShelfLife() {
        return shelfLife;
    }

    public String getPinyin() {
        return pinyin;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getUnit() {
        return unit;
    }

    /** 商品类别（0-8）；仅 {@link #hasCategory()}=true 时有效。 */
    public int getCategory() {
        return category;
    }

    /** 销售方式（0 按个 / 1 按重量）；仅 {@link #hasSaleMode()}=true 时有效。 */
    public int getSaleMode() {
        return saleMode;
    }

    /** 单价（整数分）；仅 {@link #hasPrice()}=true 时有效。 */
    public long getPrice() {
        return price;
    }

    /** 皮重（克）；仅 {@link #hasTareWeight()}=true 时有效。 */
    public long getTareWeight() {
        return tareWeight;
    }

    /** 排序（小在前）；仅 {@link #hasSort()}=true 时有效。 */
    public int getSort() {
        return sort;
    }

    /** 状态（1 上架 / 2 下架）；仅 {@link #hasStatus()}=true 时有效。 */
    public int getStatus() {
        return status;
    }

    public boolean hasCategory() {
        return hasCategory;
    }

    public boolean hasSaleMode() {
        return hasSaleMode;
    }

    public boolean hasPrice() {
        return hasPrice;
    }

    public boolean hasTareWeight() {
        return hasTareWeight;
    }

    public boolean hasSort() {
        return hasSort;
    }

    public boolean hasStatus() {
        return hasStatus;
    }

    private GoodsSaveParcel(Parcel parcel) {
        name = parcel.readString();
        plu = parcel.readString();
        image = parcel.readString();
        ingredientsList = parcel.readString();
        shelfLife = parcel.readString();
        pinyin = parcel.readString();
        categoryName = parcel.readString();
        unit = parcel.readString();
        category = parcel.readInt();
        saleMode = parcel.readInt();
        price = parcel.readLong();
        tareWeight = parcel.readLong();
        sort = parcel.readInt();
        status = parcel.readInt();
        hasCategory = parcel.readByte() != 0;
        hasSaleMode = parcel.readByte() != 0;
        hasPrice = parcel.readByte() != 0;
        hasTareWeight = parcel.readByte() != 0;
        hasSort = parcel.readByte() != 0;
        hasStatus = parcel.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(name);
        parcel.writeString(plu);
        parcel.writeString(image);
        parcel.writeString(ingredientsList);
        parcel.writeString(shelfLife);
        parcel.writeString(pinyin);
        parcel.writeString(categoryName);
        parcel.writeString(unit);
        parcel.writeInt(category);
        parcel.writeInt(saleMode);
        parcel.writeLong(price);
        parcel.writeLong(tareWeight);
        parcel.writeInt(sort);
        parcel.writeInt(status);
        parcel.writeByte((byte) (hasCategory ? 1 : 0));
        parcel.writeByte((byte) (hasSaleMode ? 1 : 0));
        parcel.writeByte((byte) (hasPrice ? 1 : 0));
        parcel.writeByte((byte) (hasTareWeight ? 1 : 0));
        parcel.writeByte((byte) (hasSort ? 1 : 0));
        parcel.writeByte((byte) (hasStatus ? 1 : 0));
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
        if (!(other instanceof GoodsSaveParcel)) {
            return false;
        }
        GoodsSaveParcel that = (GoodsSaveParcel) other;
        return category == that.category
                && saleMode == that.saleMode
                && price == that.price
                && tareWeight == that.tareWeight
                && sort == that.sort
                && status == that.status
                && hasCategory == that.hasCategory
                && hasSaleMode == that.hasSaleMode
                && hasPrice == that.hasPrice
                && hasTareWeight == that.hasTareWeight
                && hasSort == that.hasSort
                && hasStatus == that.hasStatus
                && Objects.equals(name, that.name)
                && Objects.equals(plu, that.plu)
                && Objects.equals(image, that.image)
                && Objects.equals(ingredientsList, that.ingredientsList)
                && Objects.equals(shelfLife, that.shelfLife)
                && Objects.equals(pinyin, that.pinyin)
                && Objects.equals(categoryName, that.categoryName)
                && Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, plu, image, ingredientsList, shelfLife, pinyin,
                categoryName, unit, category, saleMode, price, tareWeight, sort, status,
                hasCategory, hasSaleMode, hasPrice, hasTareWeight, hasSort, hasStatus);
    }

    @Override
    public String toString() {
        return "GoodsSaveParcel(name=" + name + ", plu=" + plu + ", image=" + image
                + ", ingredientsList=" + ingredientsList + ", shelfLife=" + shelfLife
                + ", pinyin=" + pinyin + ", categoryName=" + categoryName + ", unit=" + unit
                + ", category=" + category + ", saleMode=" + saleMode + ", price=" + price
                + ", tareWeight=" + tareWeight + ", sort=" + sort + ", status=" + status + ")";
    }

    public static final Creator<GoodsSaveParcel> CREATOR = new Creator<GoodsSaveParcel>() {
        @Override
        public GoodsSaveParcel createFromParcel(Parcel parcel) {
            return new GoodsSaveParcel(parcel);
        }

        @Override
        public GoodsSaveParcel[] newArray(int size) {
            return new GoodsSaveParcel[size];
        }
    };
}
