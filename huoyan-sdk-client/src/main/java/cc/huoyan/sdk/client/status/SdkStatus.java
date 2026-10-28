package cc.huoyan.sdk.client.status;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.StatusSnapshot;

/** 解释有效快照的 SDK 启动与模型状态，不用历史快照推断当前连接。 */
public final class SdkStatus {
    /** 静态契约工具。 */
    private SdkStatus() {}
    /** READY 和 OFFLINE 表示 SDK 已启动；本地能力还需确认授权与模型事实。 */
    public static boolean shellStarted(StatusSnapshot status) {
        return status != null && status.getCode() == 0
                && ("READY".equals(status.getSdkState()) || "OFFLINE".equals(status.getSdkState()));
    }
    /** 有效回执确认模型可用；OFFLINE 还须有有效证书且未丢失私钥。 */
    public static boolean modelReady(StatusSnapshot status) {
        if (status == null || status.getCode() != 0 || !Boolean.TRUE.equals(status.getModelReady())) return false;
        return !"OFFLINE".equals(status.getSdkState())
                || (Boolean.TRUE.equals(status.getCertificateValid()) && !Boolean.TRUE.equals(status.getKeyLost()));
    }
    /** 返回识别入口的问题；连接状态优先，模型就绪不再附加 READY 条件。 */
    public static SdkIssue issue(int initCode, StatusSnapshot status) {
        if (initCode == HuoyanSdkClient.LOCAL_DISCONNECTED || initCode == HuoyanSdkClient.NOT_EXECUTED)
            return SdkIssue.NOT_CONNECTED;
        if (modelReady(status)) return SdkIssue.NONE;
        if (status == null || status.getCode() != 0 || status.getSdkState() == null)
            return initCode == -111 ? SdkIssue.STARTUP_FAILED : SdkIssue.STATUS_UNAVAILABLE;
        switch (status.getSdkState()) {
            case "OFFLINE":
                if (Boolean.TRUE.equals(status.getKeyLost()) || status.getCertificateValid() == null)
                    return SdkIssue.STATUS_UNAVAILABLE;
                return Boolean.FALSE.equals(status.getCertificateValid())
                        ? SdkIssue.CERTIFICATE_INVALID : SdkIssue.MODEL_NOT_READY;
            case "READY": return SdkIssue.MODEL_NOT_READY;
            case "REGISTERED": return SdkIssue.NOT_ACTIVATED;
            case "STOPPED": return SdkIssue.STOPPED;
            case "CLEANUP_PENDING": return SdkIssue.CLEANUP_PENDING;
            case "NOT_INIT": return SdkIssue.NOT_INIT;
            case "INITIALIZING": return SdkIssue.INITIALIZING;
            case "NOT_ACTIVATED": return SdkIssue.NOT_ACTIVATED;
            case "MODEL_PREPARING": return SdkIssue.MODEL_PREPARING;
            case "MODEL_NOT_READY": return SdkIssue.MODEL_NOT_READY;
            case "KEY_ROTATION_PENDING": return SdkIssue.KEY_ROTATION_PENDING;
            case "KEY_ROTATION_REJECTED": return SdkIssue.KEY_ROTATION_REJECTED;
            case "KEY_ROTATION_RATE_LIMITED": return SdkIssue.KEY_ROTATION_RATE_LIMITED;
            case "KEY_ROTATION_RESTORING": return SdkIssue.KEY_ROTATION_RESTORING;
            case "CERTIFICATE_EXPIRED": return SdkIssue.CERTIFICATE_EXPIRED;
            case "CERTIFICATE_INVALID": return SdkIssue.CERTIFICATE_INVALID;
            case "BACKEND_UNAVAILABLE": return SdkIssue.BACKEND_UNAVAILABLE;
            case "STARTUP_FAILED": return SdkIssue.STARTUP_FAILED;
            case "DISABLED": return SdkIssue.DISABLED;
            default: return SdkIssue.STATUS_UNAVAILABLE;
        }
    }
}
