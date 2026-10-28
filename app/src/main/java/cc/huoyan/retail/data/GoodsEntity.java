package cc.huoyan.retail.data;

/** SDK 商品的页面内存快照；不具备数据库或文件持久化能力。 */
public class GoodsEntity {
    /** SDK 商品 ID；仅随页面快照保留，不落盘。 */
    private String goodsId;
    /** 商品 PLU。 */
    private String plu;
    /** 商品名称。 */
    private String name;
    /** SDK 商品图片地址。 */
    private String image;
    /** 商品单价，单位为分。 */
    private long price;
    /** 商品皮重，SDK 使用整数克；页面快照不落盘。 */
    private long tareWeight;
    /** 搜索拼音。 */
    private String pinyin;
    /** 页面计价类型：5 计件，0 称重。 */
    private int unit;
    /** SDK 商品所属账号。 */
    private String account_id;
    /** SDK 商品所属门店。 */
    private String shop_id;
    /** SDK 建档时间。 */
    private String create_time;
    /** SDK 更新时间。 */
    private String update_time;

    /** 创建空白表单或未匹配商品快照。 */
    public GoodsEntity() { }

    /** 读取SDK 商品 ID；仅随页面快照保留，不落盘。 */
    public String getGoodsId() { return goodsId; }

    /** 设置SDK 商品 ID；仅随页面快照保留，不落盘。 */
    public void setGoodsId(String value) { goodsId = value; }

    /** 读取商品 PLU。 */
    public String getPlu() { return plu; }

    /** 设置商品 PLU。 */
    public void setPlu(String value) { plu = value; }

    /** 读取商品名称。 */
    public String getName() { return name; }

    /** 设置商品名称。 */
    public void setName(String value) { name = value; }

    /** 读取SDK 商品图片地址。 */
    public String getImage() { return image; }

    /** 设置SDK 商品图片地址。 */
    public void setImage(String value) { image = value; }

    /** 读取商品单价，单位为分。 */
    public long getPrice() { return price; }

    /** 设置商品单价，单位为分。 */
    public void setPrice(long value) { price = value; }

    /** 返回商品皮重，单位克。 */
    public long getTareWeight() { return tareWeight; }

    /** 设置商品皮重，单位克。 */
    public void setTareWeight(long grams) { tareWeight = grams; }

    /** 读取搜索拼音。 */
    public String getPinyin() { return pinyin; }

    /** 设置搜索拼音。 */
    public void setPinyin(String value) { pinyin = value; }

    /** 读取页面计价类型：5 计件，0 称重。 */
    public int getUnit() { return unit; }

    /** 设置页面计价类型：5 计件，0 称重。 */
    public void setUnit(int value) { unit = value; }

    /** 读取SDK 商品所属账号。 */
    public String getAccount_id() { return account_id; }

    /** 设置SDK 商品所属账号。 */
    public void setAccount_id(String value) { account_id = value; }

    /** 读取SDK 商品所属门店。 */
    public String getShop_id() { return shop_id; }

    /** 设置SDK 商品所属门店。 */
    public void setShop_id(String value) { shop_id = value; }

    /** 读取SDK 建档时间。 */
    public String getCreate_time() { return create_time; }

    /** 设置SDK 建档时间。 */
    public void setCreate_time(String value) { create_time = value; }

    /** 读取SDK 更新时间。 */
    public String getUpdate_time() { return update_time; }

    /** 设置SDK 更新时间。 */
    public void setUpdate_time(String value) { update_time = value; }
}
