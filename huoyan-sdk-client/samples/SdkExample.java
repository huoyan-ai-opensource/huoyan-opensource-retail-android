package example;

import android.content.Context;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.sdk.client.SdkImage;
import cc.huoyan.sdk.client.session.SdkConnection;
import cc.huoyan.sdk.client.status.SdkStatus;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.RecognizeResult;
import cc.huoyan.sdk.client.model.StatusSnapshot;
import java.io.IOException;

/** 第三方最小接入示例；所有方法由业务应用工作线程调用，硬件由业务端管理。 */
public final class SdkExample {
    private SdkExample() { }

    /** 在工作线程等待最多 15 秒建连并初始化；初始化 RPC 本身仍遵循通道等待规则。 */
    public static HuoyanSdkClient open(Context context, SdkConfig config)
            throws IOException, InterruptedException {
        HuoyanSdkClient client = HuoyanSdkClient.create(context, config);
        try {
            if (!SdkConnection.await(client, 15000, 50, () -> true)) throw new IOException("连接 SDK 超时");
            BaseResult init = client.initialize();
            if (init == null) throw new IOException("SDK 初始化未回执");
            if (init.getCode() != 0) throw new IOException("SDK 初始化失败：" + init.getReason());
            StatusSnapshot status = client.getStatus();
            if (!SdkStatus.modelReady(status)) {
                throw new IOException("SDK 未就绪，请由业务端处理激活或模型准备状态");
            }
            return client;
        } catch (IOException | RuntimeException failure) {
            client.close();
            throw failure;
        }
    }

    /** 在工作线程绑定第三方店铺或账号唯一标识；accountId 已内置于发行 JAR。 */
    public static cc.huoyan.sdk.client.model.BindingResult bindShop(
            HuoyanSdkClient client, String externalShopId) throws IOException {
        cc.huoyan.sdk.client.model.BindingResult result = client.bindDevice(externalShopId);
        if (result == null) throw new IOException("绑定结果未知，请核对火眼 SDK 状态后处理");
        if (result.getCode() != 0) throw new IOException("绑定失败：" + result.getReason());
        return result;
    }

    /** 识别业务端提供的已裁剪、转正 RGB888 帧；失败由业务端决定如何展示。 */
    public static RecognizeResult recognize(HuoyanSdkClient client, byte[] rgb, int width, int height)
            throws IOException {
        RecognizeResult result = client.recognize(SdkImage.rgb888(rgb, width, height));
        if (result == null) throw new IOException("SDK 未返回结果");
        if (result.getCode() != 0) throw new IOException("识别失败：" + result.getReason());
        return result;
    }
}
