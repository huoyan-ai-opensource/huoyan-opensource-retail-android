package cc.huoyan.retail.goods;

import cc.huoyan.sdk.client.model.GoodsDownloadResult;
import org.junit.Test;
import static org.junit.Assert.*;

/** 商品同步只采信 SDK 最终统计，部分失败不能提示全部成功。 */
public class CloudGoodsSyncTest {
    @Test public void onlyCompleteSdkSuccessCountsAsSuccess() {
        assertTrue(CloudGoodsSync.complete(result(0, "SUCCESS", 0)));
        assertFalse(CloudGoodsSync.complete(result(0, "SUCCESS", 2)));
        assertFalse(CloudGoodsSync.complete(result(500, "FAILED", 0)));
        assertFalse(CloudGoodsSync.complete(result(0, "RUNNING", 0)));
        assertFalse(CloudGoodsSync.complete(null));
    }
    /** 构造 SDK 商品下载终态，无宿主目录合并。 */
    private GoodsDownloadResult result(int code, String phase, long failures) {
        return new GoodsDownloadResult(code, null, phase, "full", 0, 3, 3, 0, 0, 3, failures, true, 0, null);
    }
}
