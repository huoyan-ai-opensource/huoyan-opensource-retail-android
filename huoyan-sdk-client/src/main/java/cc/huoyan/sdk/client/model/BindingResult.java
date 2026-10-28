package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 设备绑定回执（bindDevice 出参）：code=0 成功，两组字段区分
 * 「JAR 发出的请求」与「后端实际落库」——
 *
 * <ul>
 *   <li>{@link #getRequest()}：请求回显（accountId 由 JAR 内置，shopId 来自客户端；全字段，没传置空——shopNo
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
public final class BindingResult {

    private final int code;
    private final String reason;
    private final BindDevice request;
    private final String accountId;
    private final String accountName;
    private final String shopId;
    private final String shopName;
    private final String shopNo;

    /** 后端门店地址，未填写为空字符串。 */
    private final String shopAddress;

    /** 后端门店电话，未填写为空字符串，不使用联系人手机号替代。 */
    private final String shopPhone;

    /** 构造数据对象。 */
    public BindingResult(int code, String reason, BindDevice request,
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

    /** 结果码；0 表示请求成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 请求回显（accountId 由 JAR 内置，shopId 来自客户端；全字段，没传置空）；守卫拒绝/空请求时为 null。 */
    public BindDevice getRequest() {
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

    /** 回执是否含可用的账号/门店归属；是否保存由宿主决定。 */
    public boolean hasBindingScope() {
        return code == 0 && accountId != null && !accountId.trim().isEmpty()
                && shopId != null && !shopId.trim().isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BindingResult)) {
            return false;
        }
        BindingResult that = (BindingResult) other;
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
        return "BindingResult(code=" + code + ", reason=" + reason
                + ", request=" + request + ", accountId=" + accountId
                + ", accountName=" + accountName + ", shopId=" + shopId
                + ", shopName=" + shopName + ", shopNo=" + shopNo + ")";
    }

}
