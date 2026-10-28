package cc.huoyan.retail.recognition;
import cc.huoyan.retail.R;
/** SDK 问题到客户端本地化文案的 UI 适配。 */
public final class RecognitionMessages {
    /** 仅提供文案映射。 */
    private RecognitionMessages() {}
    /** 返回客户端识别错误文案。 */
    public static int errorTextRes(int code) {
        switch (cc.huoyan.sdk.client.status.SdkError.fromCode(code)) {
            case NOT_CONNECTED: return R.string.status_sdk_not_started;
            case NOT_INITIALIZED: return R.string.status_sdk_uninitialized;
            case NOT_ACTIVATED: return R.string.status_sdk_not_activated;
            default: return R.string.status_recognition_failed;
        }
    }

}
