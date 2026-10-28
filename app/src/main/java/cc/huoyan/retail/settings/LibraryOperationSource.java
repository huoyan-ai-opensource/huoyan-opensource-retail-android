package cc.huoyan.retail.settings;

import android.util.Log;
import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.diagnostics.Logger;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.BaseResult;

/** SDK 识别库备份恢复适配，保留测试平台依赖的操作日志。 */
final class LibraryOperationSource {
    /** 执行一次库操作；未连接和异常均返回明确回执，不自动重试。 */
    static BaseResult run(boolean restore) {
        BaseResult result;
        try {
            HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
            result = bridge.isConnected()
                    ? (restore ? bridge.restoreRecognitionData() : bridge.backupRecognitionData())
                    : new BaseResult(HuoyanSdkClient.LOCAL_DISCONNECTED, "SDK 未连接，请求未发送");
        } catch (Exception e) {
            Logger.logError("GoodsFragment", e);
            result = new BaseResult(HuoyanSdkClient.RESULT_UNKNOWN, e.getMessage());
        }
        BaseResult operationResult = result == null
                ? new BaseResult(HuoyanSdkClient.RESULT_UNKNOWN, null) : result;
        if (BuildConfig.DEBUG) {
            Log.d("GoodsFragment", "[Biz] sdk_method="
                    + (restore ? "restoreRecognitionData" : "backupRecognitionData")
                    + " result=" + result);
            Log.d("GoodsFragment", "[Biz] library_" + (restore ? "restore" : "backup")
                    + "=" + operationResult.getCode());
        }
        return operationResult;
    }
    /** 读取识别库备份元信息；异常记录后交由页面显示不可用。 */
    static cc.huoyan.sdk.client.model.RecognitionDataInfo info() {
        try { return SdkChannelManager.getInstance().getClient().getRecognitionDataInfo(); }
        catch (RuntimeException error) { Logger.logError("GoodsFragment", error); return null; }
    }
    /** 读取学习分类数量，非成功回执返回不可用标记。 */
    static int count() {
        try {
            cc.huoyan.sdk.client.model.ItemClazzList result = SdkChannelManager.getInstance().listClasses();
            return result != null && result.getCode() == 0 && result.getClazz() != null ? result.getClazz().length : -1;
        } catch (RuntimeException error) { Logger.logError("GoodsFragment", error); return -1; }
    }
}
