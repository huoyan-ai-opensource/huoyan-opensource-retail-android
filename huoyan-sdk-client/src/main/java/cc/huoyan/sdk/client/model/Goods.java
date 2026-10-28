package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 商品条目载体（商品 CRUD/列表）：CRUD 条目全字段（同步条目 + 门店/
 * 客户冗余列 + 建档时间）。列表（listGoods）为同步条目形态（isDeleted 增量判据，
 * 审计四字段 null）；create/update 回执为 CRUD 形态（isDeleted 恒 0）。
 */
public final class Goods {

    private final String goodsId;
    private final String shopId;
    private final String name;
    private final String plu;
    private final String baseGoodsId;
    private final String image;
    private final int category;
    private final String categoryName;
    private final String unit;
    private final int saleMode;
    private final long price;
    private final long tareWeight;
    private final String ingredientsList;
    private final String shelfLife;
    private final String pinyin;
    private final int status;
    private final int sort;
    private final int isDeleted;
    private final String updateTime;
    private final String shopName;
    private final String accountId;
    private final String accountName;
    private final String createTime;

    /** 构造数据对象。 */
    public Goods(String goodsId, String shopId, String name, String plu,
            String baseGoodsId, String image, int category, String categoryName,
            String unit, int saleMode, long price, long tareWeight,
            String ingredientsList, String shelfLife, String pinyin, int status,
            int sort, int isDeleted, String updateTime, String shopName,
            String accountId, String accountName, String createTime) {
        this.goodsId = goodsId;
        this.shopId = shopId;
        this.name = name;
        this.plu = plu;
        this.baseGoodsId = baseGoodsId;
        this.image = image;
        this.category = category;
        this.categoryName = categoryName;
        this.unit = unit;
        this.saleMode = saleMode;
        this.price = price;
        this.tareWeight = tareWeight;
        this.ingredientsList = ingredientsList;
        this.shelfLife = shelfLife;
        this.pinyin = pinyin;
        this.status = status;
        this.sort = sort;
        this.isDeleted = isDeleted;
        this.updateTime = updateTime;
        this.shopName = shopName;
        this.accountId = accountId;
        this.accountName = accountName;
        this.createTime = createTime;
    }

    /** 商品标识（十进制字符串，雪花 ID）。 */
    public String getGoodsId() {
        return goodsId;
    }

    /** getShopId 字段值。 */
    public String getShopId() {
        return shopId;
    }

    /** 商品名称。 */
    public String getName() {
        return name;
    }

    /** 称重/店内码。 */
    public String getPlu() {
        return plu;
    }

    /** getBaseGoodsId 字段值。 */
    public String getBaseGoodsId() {
        return baseGoodsId;
    }

    /** 商品图片地址。 */
    public String getImage() {
        return image;
    }

    /** 商品类别（0-8 枚举）。 */
    public int getCategory() {
        return category;
    }

    /** 商品分类名称。 */
    public String getCategoryName() {
        return categoryName;
    }

    /** 计价单位（如 500g/个/份）。 */
    public String getUnit() {
        return unit;
    }

    /** 销售方式（0 按个 / 1 按重量）。 */
    public int getSaleMode() {
        return saleMode;
    }

    /** 单价（整数分）。 */
    public long getPrice() {
        return price;
    }

    /** 皮重（克）。 */
    public long getTareWeight() {
        return tareWeight;
    }

    /** 配料表。 */
    public String getIngredientsList() {
        return ingredientsList;
    }

    /** 保质期。 */
    public String getShelfLife() {
        return shelfLife;
    }

    /** 商品拼音。 */
    public String getPinyin() {
        return pinyin;
    }

    /** 状态（1 上架 / 2 下架）。 */
    public int getStatus() {
        return status;
    }

    /** 排序（小在前）。 */
    public int getSort() {
        return sort;
    }

    /** 软删标志（1 = 已删——增量列表据此删本地；CRUD 回执恒 0）。 */
    public int getIsDeleted() {
        return isDeleted;
    }

    /** getUpdateTime 字段值。 */
    public String getUpdateTime() {
        return updateTime;
    }

    /** 门店名称（CRUD 回执冗余；列表形态 null）。 */
    public String getShopName() {
        return shopName;
    }

    /** 归属客户标识（CRUD 回执；列表形态 null）。 */
    public String getAccountId() {
        return accountId;
    }

    /** 归属客户名称（CRUD 回执；列表形态 null）。 */
    public String getAccountName() {
        return accountName;
    }

    /** 建档时间（ISO-8601；CRUD 回执；列表形态 null）。 */
    public String getCreateTime() {
        return createTime;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Goods)) {
            return false;
        }
        Goods that = (Goods) other;
        return category == that.category
                && saleMode == that.saleMode
                && price == that.price
                && tareWeight == that.tareWeight
                && status == that.status
                && sort == that.sort
                && isDeleted == that.isDeleted
                && Objects.equals(goodsId, that.goodsId)
                && Objects.equals(shopId, that.shopId)
                && Objects.equals(name, that.name)
                && Objects.equals(plu, that.plu)
                && Objects.equals(baseGoodsId, that.baseGoodsId)
                && Objects.equals(image, that.image)
                && Objects.equals(categoryName, that.categoryName)
                && Objects.equals(unit, that.unit)
                && Objects.equals(ingredientsList, that.ingredientsList)
                && Objects.equals(shelfLife, that.shelfLife)
                && Objects.equals(pinyin, that.pinyin)
                && Objects.equals(updateTime, that.updateTime)
                && Objects.equals(shopName, that.shopName)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(accountName, that.accountName)
                && Objects.equals(createTime, that.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goodsId, shopId, name, plu, baseGoodsId, image, category,
                categoryName, unit, saleMode, price, tareWeight, ingredientsList,
                shelfLife, pinyin, status, sort, isDeleted, updateTime, shopName,
                accountId, accountName, createTime);
    }

    @Override
    public String toString() {
        return "Goods(goodsId=" + goodsId + ", shopId=" + shopId + ", name=" + name
                + ", plu=" + plu + ", baseGoodsId=" + baseGoodsId + ", image=" + image
                + ", category=" + category + ", categoryName=" + categoryName
                + ", unit=" + unit + ", saleMode=" + saleMode + ", price=" + price
                + ", tareWeight=" + tareWeight + ", ingredientsList=" + ingredientsList
                + ", shelfLife=" + shelfLife + ", pinyin=" + pinyin + ", status=" + status
                + ", sort=" + sort + ", isDeleted=" + isDeleted + ", updateTime=" + updateTime
                + ", shopName=" + shopName + ", accountId=" + accountId
                + ", accountName=" + accountName + ", createTime=" + createTime + ")";
    }

}
