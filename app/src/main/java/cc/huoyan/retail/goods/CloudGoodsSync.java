package cc.huoyan.retail.goods;

import cc.huoyan.retail.R;
import cc.huoyan.retail.common.LocalizedText;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.GoodsDownloadResult;

/** 云端商品同步只交给 SDK 执行，宿主不复制商品目录。 */
public final class CloudGoodsSync {
    /** SDK 同步的最终回执。 */
    public static final class Result {
        /** 无失败条目时为 true。 */
        public final boolean success;
        /** 当前语言的同步结果。 */
        public final String message;
        /** 固定本次同步回执。 */
        private Result(boolean success, String message) { this.success = success; this.message = message; }
    }
    /** 仅提供后台同步入口。 */
    private CloudGoodsSync() { }
    /** 等待 SDK 完成云端同步；换绑或断连均不返回成功，不自动重试。 */
    public static Result run(HuoyanSdkClient client) {
        if (!SPManager.getInstance().hasBoundShop())
            return new Result(false, LocalizedText.get(R.string.cloud_sync_bind_shop_first));
        long generation = SPManager.getInstance().getBindGeneration();
        GoodsDownloadResult result = client.downloadGoods(100);
        if (generation != SPManager.getInstance().getBindGeneration()
                || client != SdkChannelManager.getInstance().getClient())
            throw new IllegalStateException(LocalizedText.get(R.string.cloud_scope_changed));
        if (result == null || result.getCode() != 0 || !"SUCCESS".equals(result.getPhase()))
            throw new IllegalStateException(result == null || result.getReason() == null
                    ? LocalizedText.get(R.string.sdk_no_response) : result.getReason());
        boolean success = complete(result);
        return new Result(success, success ? LocalizedText.get(R.string.cloud_sync_complete)
                : LocalizedText.get(R.string.cloud_sync_partial, result.getSuccessCount(), result.getFailCount()));
    }
    /** SDK 完成且没有失败条目才认定为完整成功。 */
    static boolean complete(GoodsDownloadResult result) {
        return result != null && result.getCode() == 0 && "SUCCESS".equals(result.getPhase()) && result.getFailCount() == 0;
    }
}
