package cc.huoyan.sdk.client.status;
import cc.huoyan.sdk.client.HuoyanSdkClient;
/** SDK 原生错误分类，保留调用结果中的原始 code/reason。 */
public enum SdkError {
    /** 通道尚未连接。 */ NOT_CONNECTED,
    /** SDK 未初始化。 */ NOT_INITIALIZED,
    /** 授权不可用。 */ NOT_ACTIVATED,
    /** 无法确认执行结果。 */ RESULT_UNKNOWN,
    /** 其他错误。 */ OTHER;
    /** 按 SDK 错误码解释，不解析诊断文案。 */
    public static SdkError fromCode(int code) {
        if (code == HuoyanSdkClient.LOCAL_DISCONNECTED || code == HuoyanSdkClient.NOT_EXECUTED) return NOT_CONNECTED;
        if (code == HuoyanSdkClient.RESULT_UNKNOWN) return RESULT_UNKNOWN;
        if (code == -3) return NOT_INITIALIZED;
        if (code == -42 || code == -50) return NOT_ACTIVATED;
        return OTHER;
    }
    /** 默认错误说明；具体原因优先使用原始回执。 */
    public String defaultMessage() {
        switch (this) {
            case NOT_CONNECTED: return "SDK 未连接，请求未发送";
            case NOT_INITIALIZED: return "SDK 尚未初始化";
            case NOT_ACTIVATED: return "SDK 授权不可用";
            case RESULT_UNKNOWN: return "调用已发出，结果未知";
            default: return "SDK 调用失败";
        }
    }
}
