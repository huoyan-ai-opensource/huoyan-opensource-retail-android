package cc.huoyan.retail.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import cc.huoyan.sdk.client.SdkConfig.Channel;

import org.junit.Test;

/** SDK 通道映射与门店绑定校验。 */
public class SystemFragmentTest {

    @Test
    public void sdkChannelPosition_roundTripsAllChannels() {
        for (Channel channel : Channel.values()) {
            assertEquals(channel,
                    SystemFragment.sdkChannelFromPosition(SystemFragment.sdkChannelPosition(channel)));
        }
    }

    @Test
    public void sdkChannelPosition_followsFixedItemOrder() {
        assertEquals(0, SystemFragment.sdkChannelPosition(Channel.AIDL));
        assertEquals(1, SystemFragment.sdkChannelPosition(Channel.WS));
    }

    @Test
    public void sdkChannelPosition_null_fallsBackToAidlPosition() {
        // null 通道按默认通道 AIDL 回退选中 AIDL 条目
        assertEquals(0, SystemFragment.sdkChannelPosition(null));
    }

    @Test
    public void sdkChannelFromPosition_outOfBounds_returnsNull() {
        assertNull(SystemFragment.sdkChannelFromPosition(-1));
        assertNull(SystemFragment.sdkChannelFromPosition(2));
        assertNull(SystemFragment.sdkChannelFromPosition(Integer.MAX_VALUE));
    }

    // ---- 扫码绑定：门店二维码内容校验（原样 shop_id，非法不发起绑定） ----

    @Test
    public void parseShopQrShopId_digitsReturnedAsIs() {
        assertSame("123456", ShopBindingPresenter.parseShopQrShopId("123456"));
        assertSame("7", ShopBindingPresenter.parseShopQrShopId("7"));
    }

    @Test
    public void parseShopQrShopId_leadingZerosAndLongStringsPreserved() {
        assertSame("007", ShopBindingPresenter.parseShopQrShopId("007"));
        assertSame("0000", ShopBindingPresenter.parseShopQrShopId("0000"));
        String longId = "92233720368547758071234567890";
        assertSame("超 long 范围长串同样接受且不转数值", longId,
                ShopBindingPresenter.parseShopQrShopId(longId));
    }

    @Test
    public void parseShopQrShopId_nullOrEmpty_rejected() {
        assertNull(ShopBindingPresenter.parseShopQrShopId(null));
        assertNull(ShopBindingPresenter.parseShopQrShopId(""));
    }

    @Test
    public void parseShopQrShopId_blankRejectedButIdentifierPreserved() {
        assertNull(ShopBindingPresenter.parseShopQrShopId("   "));
        assertNull(ShopBindingPresenter.parseShopQrShopId("\t\n"));
        for (String id : new String[]{"erp-shop-007", "account:user_123", "１２３", " 123 ", "12 3"}) {
            assertSame(id, ShopBindingPresenter.parseShopQrShopId(id));
        }
    }

    @Test
    public void bindingScopeRequiresSuccessfulInternalIds() {
        cc.huoyan.sdk.client.model.BindDevice echo =
                new cc.huoyan.sdk.client.model.BindDevice(42, "external-user", null, null);
        assertEquals(true, ShopBindingPresenter.hasBindingScope(new cc.huoyan.sdk.client.model.BindingResult(
                0, null, echo, "42", null, "internal-shop-9", null, null, null, null)));
        assertEquals(false, ShopBindingPresenter.hasBindingScope(new cc.huoyan.sdk.client.model.BindingResult(
                0, null, echo, "42", null, null, null, null, null, null)));
        assertEquals(false, ShopBindingPresenter.hasBindingScope(new cc.huoyan.sdk.client.model.BindingResult(
                409, "失败", echo, "42", null, "internal-shop-9", null, null, null, null)));
        assertEquals(false, ShopBindingPresenter.hasBindingScope(null));
    }
}
