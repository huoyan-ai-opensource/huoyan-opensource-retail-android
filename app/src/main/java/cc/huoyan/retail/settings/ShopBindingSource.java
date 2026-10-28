package cc.huoyan.retail.settings;

import cc.huoyan.retail.R;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.diagnostics.Logger;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.BindingResult;

/** 门店绑定 SDK 与本地归属存储适配。 */
final class ShopBindingSource implements ShopBindingPresenter.Data {
    /** 等待连接最多五秒，仅调用一次 bindDevice。 */
    @Override public ShopBindingPresenter.Result bind(String shopId) {
        try {
            HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
            if (!cc.huoyan.sdk.client.session.SdkConnection.await(bridge, 5000L, 100L, () -> true))
                return new ShopBindingPresenter.Result(null, R.string.sdk_error_not_connected);
            BindingResult receipt = bridge.bindDevice(shopId);
            return new ShopBindingPresenter.Result(receipt, receipt == null ? R.string.sdk_no_response : 0);
        } catch (RuntimeException error) { return new ShopBindingPresenter.Result(null, R.string.sdk_channel_error); }
    }
    /** 原子保存成功归属并递增代次，使旧归属查询失效。 */
    @Override public void save(BindingResult receipt) {
        long current = SPManager.getInstance().getBindGeneration();
        long generation = current < 0 ? 1L : current + 1L;
        SPManager.getInstance().saveBinding(receipt, generation);
        Logger.logDebug("SystemFragment", "[Biz] device_bind=ok account_id=" + receipt.getAccountId()
                + " shop_id=" + receipt.getShopId() + " generation=" + generation);
    }
}
