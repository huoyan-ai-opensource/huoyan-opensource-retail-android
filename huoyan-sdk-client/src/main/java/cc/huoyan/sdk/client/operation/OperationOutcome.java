package cc.huoyan.sdk.client.operation;
import cc.huoyan.sdk.client.HuoyanSdkClient;
/** 单次调用执行确定性；未知结果不能当作未执行，也不能自动重放。 */
public enum OperationOutcome {
    /** 明确成功。 */ SUCCESS,
    /** 收到失败回执。 */ FAILURE,
    /** 请求没有发送。 */ NOT_EXECUTED,
    /** 已调用但未能确认结果。 */ UNKNOWN;
    /** 从保留的原始结果码归类。 */
    public static OperationOutcome fromCode(int code) {
        if (code == 0) return SUCCESS;
        if (code == HuoyanSdkClient.LOCAL_DISCONNECTED || code == HuoyanSdkClient.NOT_EXECUTED) return NOT_EXECUTED;
        if (code == HuoyanSdkClient.RESULT_UNKNOWN) return UNKNOWN;
        return FAILURE;
    }
}
