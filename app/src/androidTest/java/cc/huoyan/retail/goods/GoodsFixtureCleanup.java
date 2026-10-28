package cc.huoyan.retail.goods;

import android.content.Context;
import cc.huoyan.retail.data.GoodsEntity;

/** 跨业务 UI 测试的商品清理入口，使生产仓库继续保持包内可见。 */
public final class GoodsFixtureCleanup {
    private GoodsFixtureCleanup() {}

    /** 商品设备测试开始前连接 SDK，不能再依赖宿主数据库离线造数。 */
    public static void awaitSdk() {
        cc.huoyan.sdk.client.HuoyanSdkClient client = cc.huoyan.retail.sdk.SdkChannelManager.getInstance().getClient();
        client.connect();
        long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
        while (!client.isConnected() && android.os.SystemClock.elapsedRealtime() < deadline)
            android.os.SystemClock.sleep(100);
        org.junit.Assert.assertTrue("SDK 商品接口必须已连接", client.isConnected());
    }

    /** 按生产删除链路清理测试商品及其 SDK 样本。 */
    public static void delete(Context context, GoodsEntity goods) {
        new GoodsRepository().delete(goods);
    }
    /** 测试结束后只删除 SDK 中对应的测试商品。 */
    public static void deleteByPlu(String plu) {
        SdkGoodsSource source = SdkGoodsSource.getInstance();
        GoodsEntity goods = source.getGoodsByPlu(plu);
        if (goods != null) source.delete(goods);
    }
}
