package example;

import android.content.Context;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.sdk.client.activation.ActivationCode;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.session.SdkConnection;
import java.io.IOException;

/** 激活页面接入示例；方法在后台执行，调用方管理连接、状态观察及页面生命周期。 */
public final class ActivationStatusExample {
    /** 仅提供静态示例方法。 */
    private ActivationStatusExample() {}

    /** 等待通信连接，不要求已激活；调用方随后初始化并查询状态，结束使用时关闭客户端。 */
    public static HuoyanSdkClient openConnected(Context context, SdkConfig config)
            throws IOException {
        HuoyanSdkClient client = HuoyanSdkClient.create(context, config);
        try {
            if (!SdkConnection.await(client, 15000, 50, () -> true)) {
                throw new IOException("连接未完成：请检查超时、取消或线程中断");
            }
            return client;
        } catch (IOException | RuntimeException error) {
            client.close();
            throw error;
        }
    }

    /** 用户确认后提交一次激活；输入为激活码或扫码提取的 serial，原始结果交由调用方处理。 */
    public static BaseResult activateOnce(HuoyanSdkClient client, String input, String modelCode) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("激活码不能为空");
        }
        if (!client.isConnected()) {
            return new BaseResult(HuoyanSdkClient.LOCAL_DISCONNECTED, "火眼 SDK 未连接");
        }
        return client.activate(ActivationCode.normalize(input), modelCode);
    }
}
