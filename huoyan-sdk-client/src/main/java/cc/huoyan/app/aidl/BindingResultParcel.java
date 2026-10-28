package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 设备绑定回执（Issue #73，bindDevice 出参）：code=0 成功，两组字段区分
 * 「客户端传入」与「后端实际落库」——
 *
 * <ul>
 *   <li>{@link #getRequest()}：客户端传入回显（全字段，没传置空——shopNo
 *       未传为 null；错误回执同样保留回执时刻可得的回显，供客户端对账）；</li>
 *   <li>response 组（{@code accountId/accountName/shopId/shopName/shopNo/shopAddress/shopPhone}）：
 *       后端实际落库终态（bind-shop 回执快照，雪花 ID 字符串化原样保持）；
 *       仅 code=0 时有效，错误时全 null。</li>
 * </ul>
 *
 * <p>错误（含 409 shop_no 已存在）：code≠0 + reason 携带步骤与服务端说明
 * （原样透传）。权限拒绝：code=40004 + reason（request 为 null）。与 WS
 * 通道 bindDevice 响应 data 同构（键 snake_case）。</p>
 */
public final class BindingResultParcel implements Parcelable {

    private final int code;
    private final String reason;
    private final BindDeviceParcel request;
    private final String accountId;
    private final String accountName;
    private final String shopId;
    private final String shopName;
    private final String shopNo;

    /** 后端门店地址，未填写为空字符串。 */
    private final String shopAddress;

    /** 后端门店电话，未填写为空字符串，不使用联系人手机号替代。 */
    private final String shopPhone;

    public BindingResultParcel(int code, String reason, BindDeviceParcel request,
            String accountId, String accountName, String shopId, String shopName, String shopNo, String shopAddress, String shopPhone) {
        this.code = code;
        this.reason = reason;
        this.request = request;
        this.accountId = accountId;
        this.accountName = accountName;
        this.shopId = shopId;
        this.shopName = shopName;
        this.shopNo = shopNo;
        this.shopAddress = shopAddress;
        this.shopPhone = shopPhone;
    }

    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 客户端传入回显（全字段，没传置空）；守卫拒绝/空请求时为 null。 */
    public BindDeviceParcel getRequest() {
        return request;
    }

    /** 后端实际：归属账号标识（字符串化）；错误时 null。 */
    public String getAccountId() {
        return accountId;
    }

    /** 后端实际：归属账号名称；错误时 null。 */
    public String getAccountName() {
        return accountName;
    }

    /** 后端实际：门店标识（wujing 内部门店 ID）；错误时 null。 */
    public String getShopId() {
        return shopId;
    }

    /** 后端实际：门店名称；错误时 null。 */
    public String getShopName() {
        return shopName;
    }

    /** 后端实际：门店编号（自动生成时为 SH+雪花）；错误时 null。 */
    public String getShopNo() {
        return shopNo;
    }

    /** 返回后端门店地址。 */
    public String getShopAddress() {
        return shopAddress;
    }

    /** 返回后端门店电话。 */
    public String getShopPhone() {
        return shopPhone;
    }

    private BindingResultParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        request = (BindDeviceParcel) parcel.readValue(BindingResultParcel.class.getClassLoader());
        accountId = parcel.readString();
        accountName = parcel.readString();
        shopId = parcel.readString();
        shopName = parcel.readString();
        shopNo = parcel.readString();
        shopAddress = parcel.readString();
        shopPhone = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeValue(request);
        parcel.writeString(accountId);
        parcel.writeString(accountName);
        parcel.writeString(shopId);
        parcel.writeString(shopName);
        parcel.writeString(shopNo);
        parcel.writeString(shopAddress);
        parcel.writeString(shopPhone);
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
        if (!(other instanceof BindingResultParcel)) {
            return false;
        }
        BindingResultParcel that = (BindingResultParcel) other;
        return code == that.code
                && Objects.equals(reason, that.reason)
                && Objects.equals(request, that.request)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(accountName, that.accountName)
                && Objects.equals(shopId, that.shopId)
                && Objects.equals(shopName, that.shopName)
                && Objects.equals(shopNo, that.shopNo)
                && Objects.equals(shopAddress, that.shopAddress)
                && Objects.equals(shopPhone, that.shopPhone);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        result = 31 * result + Objects.hashCode(request);
        result = 31 * result + Objects.hashCode(accountId);
        result = 31 * result + Objects.hashCode(accountName);
        result = 31 * result + Objects.hashCode(shopId);
        result = 31 * result + Objects.hashCode(shopName);
        result = 31 * result + Objects.hashCode(shopNo);
        result = 31 * result + Objects.hashCode(shopAddress);
        return 31 * result + Objects.hashCode(shopPhone);
    }

    @Override
    public String toString() {
        return "BindingResultParcel(code=" + code + ", reason=" + reason
                + ", request=" + request + ", accountId=" + accountId
                + ", accountName=" + accountName + ", shopId=" + shopId
                + ", shopName=" + shopName + ", shopNo=" + shopNo + ")";
    }

    public static final Creator<BindingResultParcel> CREATOR = new Creator<BindingResultParcel>() {
        @Override
        public BindingResultParcel createFromParcel(Parcel parcel) {
            return new BindingResultParcel(parcel);
        }

        @Override
        public BindingResultParcel[] newArray(int size) {
            return new BindingResultParcel[size];
        }
    };
}
