package cc.huoyan.retail.home;

import cc.huoyan.retail.R;
import cc.huoyan.retail.home.CloudServicePresenter.Result;
import cc.huoyan.retail.common.LocalizedText;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.goods.CloudGoodsSync;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.retail.sdk.SdkGoodsImages;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.BindingResult;
import cc.huoyan.sdk.client.model.CloudStatus;
import cc.huoyan.sdk.client.model.ImageFlowProgress;

/** 云端服务后台访问；使用 SDK 的真实回执，不持久化猜测的开关值。 */
final class CloudServiceSource implements CloudServicePresenter.Source {
    /** 查询当前连接对应的持久化开关；切换通道时丢弃旧客户端回执。 */
    @Override public CloudStatus cloud() {
        HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
        CloudStatus result = client.isConnected() ? client.getCloudStatus() : null;
        return client == SdkChannelManager.getInstance().getClient() ? result : null;
    }
    /** 执行一次绑定、开启或同步操作；所有分支都由回执决定成功。 */
    @Override public Result run(CloudServicePresenter.Action action, String input) {
        SPManager store = SPManager.getInstance();
        if (action != CloudServicePresenter.Action.BIND && !store.hasBoundShop())
            return new Result(false, LocalizedText.get(action == CloudServicePresenter.Action.ENABLE
                    ? R.string.cloud_bind_shop_first : R.string.cloud_sync_bind_shop_first));
        HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
        if (!client.isConnected()) return new Result(false, LocalizedText.get(R.string.sdk_error_not_connected));
        if (action == CloudServicePresenter.Action.BIND) {
            if (input == null || input.trim().isEmpty()) return new Result(false, LocalizedText.get(R.string.bind_id_required));
            long generation = store.getBindGeneration();
            BindingResult result = client.bindDevice(input);
            if (client != SdkChannelManager.getInstance().getClient() || generation != store.getBindGeneration())
                return new Result(false, LocalizedText.get(R.string.cloud_scope_changed));
            if (result == null) return new Result(false, LocalizedText.get(R.string.sdk_no_response));
            if (!result.hasBindingScope()) return new Result(false, result.getCode() == 0
                    ? LocalizedText.get(R.string.bind_incomplete) : failure(result.getReason()));
            store.saveBinding(result, Math.max(0, store.getBindGeneration()) + 1);
            return new Result(true, LocalizedText.get(R.string.bind_success));
        }
        if (action == CloudServicePresenter.Action.ENABLE) {
            CloudStatus result = client.setCloudStatus(true);
            boolean success = result != null && result.getCode() == 0 && result.isEnabled();
            return new Result(success, success ? LocalizedText.get(R.string.cloud_enabled)
                    : failure(result == null ? null : result.getReason()));
        }
        CloudStatus current = client.getCloudStatus();
        if (current == null || current.getCode() != 0 || !current.isEnabled())
            return new Result(false, LocalizedText.get(R.string.cloud_state_retry));
        if (action == CloudServicePresenter.Action.GOODS) {
            CloudGoodsSync.Result result = CloudGoodsSync.run(client);
            return new Result(result.success, result.message);
        }
        ImageFlowProgress result = client.downloadImagesAndLearn();
        SdkGoodsImages.invalidate();
        if (result == null) return new Result(false, LocalizedText.get(R.string.sdk_no_response));
        if (result.outcome() == ImageFlowProgress.Outcome.SUCCESS && result.getSkippedCount() == 0)
            return new Result(true, LocalizedText.get(R.string.cloud_sync_complete));
        if (result.outcome() == ImageFlowProgress.Outcome.PARTIAL_SUCCESS || result.getSkippedCount() > 0)
            return new Result(false, LocalizedText.get(R.string.cloud_sync_partial, result.getSuccessCount(),
                    result.getFailCount() + result.getDownloadFail() + result.getSkippedCount()));
        return new Result(false, failure(result.getFailReason() == null ? result.getReason() : result.getFailReason()));
    }
    /** 使用当前语言展示后台异常。 */
    @Override public String failure(RuntimeException error) { return failure(error.getMessage()); }
    /** SDK 未给出原因时仍显示明确失败，不能以空消息当作成功。 */
    private static String failure(String reason) {
        return LocalizedText.get(R.string.panel_cloud_failed,
                reason == null || reason.isEmpty() ? LocalizedText.get(R.string.sdk_no_response) : reason);
    }
}
