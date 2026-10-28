package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 设备绑定请求载体（bindDevice 入参）：accountId/shopId 必填
 * （缺参在 SDK 侧编排协作层前置拒绝，得 code=ERR_ARGS + reason 而非
 * RemoteException）、shopName 可选（缺省/空白经请求 DTO 构造期
 * 以 shopId 回填）、shopNo 可选（null = 不传，后端自动生成；传了且同账号
 * 重复后端 409）。shopId 语义为 app_shop_id（绑已有门店——命中官方直绑、
 * 未命中按第三方新建）。契约校验不在此层（本包为 AIDL 对外契约，
 * 方案 A 纯度豁免边界）。
 */
public final class BindDeviceParcel implements Parcelable {

    private final long accountId;
    private final String shopId;
    private final String shopName;
    private final String shopNo;

    public BindDeviceParcel(long accountId, String shopId, String shopName, String shopNo) {
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

    /** 门店名称（可选）；null/空白经请求 DTO 构造期以 shopId 回填。 */
    public String getShopName() {
        return shopName;
    }

    /** 门店编号（可选）；null = 不传（后端自动生成）。 */
    public String getShopNo() {
        return shopNo;
    }

    private BindDeviceParcel(Parcel parcel) {
        accountId = parcel.readLong();
        shopId = parcel.readString();
        shopName = parcel.readString();
        shopNo = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLong(accountId);
        parcel.writeString(shopId);
        parcel.writeString(shopName);
        parcel.writeString(shopNo);
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
        if (!(other instanceof BindDeviceParcel)) {
            return false;
        }
        BindDeviceParcel that = (BindDeviceParcel) other;
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
        return "BindDeviceParcel(accountId=" + accountId + ", shopId=" + shopId
                + ", shopName=" + shopName + ", shopNo=" + shopNo + ")";
    }

    public static final Creator<BindDeviceParcel> CREATOR = new Creator<BindDeviceParcel>() {
        @Override
        public BindDeviceParcel createFromParcel(Parcel parcel) {
            return new BindDeviceParcel(parcel);
        }

        @Override
        public BindDeviceParcel[] newArray(int size) {
            return new BindDeviceParcel[size];
        }
    };
}
