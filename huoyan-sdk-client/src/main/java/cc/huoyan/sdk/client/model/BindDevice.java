package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 绑定回执中的请求快照，仅用于读取与对账，不作为公共绑定接口入参。
 * accountId 由 JAR 内置；shopId 是客户端传入的外部唯一标识。
 * shopName 缺省时由 SDK 以 shopId 回填；shopNo 可选。
 */
public final class BindDevice {

    private final long accountId;
    private final String shopId;
    private final String shopName;
    private final String shopNo;

    /** 构造数据对象。 */
    public BindDevice(long accountId, String shopId, String shopName, String shopNo) {
        this.accountId = accountId;
        this.shopId = shopId;
        this.shopName = shopName;
        this.shopNo = shopNo;
    }

    /** 归属账号标识（必填，正整数）。 */
    public long getAccountId() {
        return accountId;
    }

    /** 门店标识（必填，app_shop_id）。 */
    public String getShopId() {
        return shopId;
    }

    /** 门店名称（可选）；null/空白经请求 DTO 构造期以 shopId 回填（）。 */
    public String getShopName() {
        return shopName;
    }

    /** 门店编号（可选）；null = 不传（后端自动生成）。 */
    public String getShopNo() {
        return shopNo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BindDevice)) {
            return false;
        }
        BindDevice that = (BindDevice) other;
        return accountId == that.accountId
                && Objects.equals(shopId, that.shopId)
                && Objects.equals(shopName, that.shopName)
                && Objects.equals(shopNo, that.shopNo);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(accountId);
        result = 31 * result + Objects.hashCode(shopId);
        result = 31 * result + Objects.hashCode(shopName);
        return 31 * result + Objects.hashCode(shopNo);
    }

    @Override
    public String toString() {
        return "BindDevice(accountId=" + accountId + ", shopId=" + shopId
                + ", shopName=" + shopName + ", shopNo=" + shopNo + ")";
    }

}
