package cc.huoyan.sdk.client.operation;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.*;
/** SDK 长操作与进度访问；每次显式调用只提交一次，不自动重放。 */
public final class SdkOperations {
    /** 静态操作工具。 */
    private SdkOperations() {}
    /** 图片下载学习操作回执；回执丢失时表达未知结果。 */
    public static ImageFlowProgress learnImages(HuoyanSdkClient client) {
        if (!client.isConnected()) return imageFailure(HuoyanSdkClient.LOCAL_DISCONNECTED, "SDK 未连接");
        try {
            ImageFlowProgress result = client.downloadImagesAndLearn();
            return result == null ? imageFailure(HuoyanSdkClient.RESULT_UNKNOWN, "图片学习结果未知") : result;
        } catch (RuntimeException error) { return imageFailure(HuoyanSdkClient.RESULT_UNKNOWN, error.getMessage()); }
    }
    /** 查询某一阶段进度；查询失败不覆盖最终操作回执。 */
    public static ImageFlowProgress imageProgress(HuoyanSdkClient client, boolean download) {
        try { return download ? client.getImageDownloadProgress() : client.getImageLearnProgress(); }
        catch (RuntimeException error) { return imageFailure(HuoyanSdkClient.RESULT_UNKNOWN, error.getMessage()); }
    }
    /** 激活入口规范化输入并保留结果确定性。 */
    public static BaseResult activate(HuoyanSdkClient client, String code, String modelCode) {
        if (!client.isConnected()) return new BaseResult(HuoyanSdkClient.LOCAL_DISCONNECTED, "SDK 未连接");
        try {
            BaseResult result = client.activate(cc.huoyan.sdk.client.activation.ActivationCode.normalize(code), modelCode);
            return result == null ? new BaseResult(HuoyanSdkClient.RESULT_UNKNOWN, "激活结果未知") : result;
        } catch (RuntimeException error) { return new BaseResult(HuoyanSdkClient.RESULT_UNKNOWN, error.getMessage()); }
    }
    /** 查询激活下载进度，缺回执保留为空。 */
    public static DownloadProgress activationProgress(HuoyanSdkClient client) {
        try { return client.isConnected() ? client.getDownloadProgress() : null; }
        catch (RuntimeException ignored) { return null; }
    }
    /** 构造没有阶段进度的本地结果。 */
    private static ImageFlowProgress imageFailure(int code, String reason) {
        return new ImageFlowProgress(code, reason, false, null, 0, 0, 0, 0, null, 0, null);
    }
}
