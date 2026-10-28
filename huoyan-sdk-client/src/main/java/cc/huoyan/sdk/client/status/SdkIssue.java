package cc.huoyan.sdk.client.status;

/** SDK 问题类型及默认说明；宿主可映射自己的本地化资源。 */
public enum SdkIssue {
    /** 模型可用。 */
    NONE("模型可用"),
    /** SDK 未连接。 */
    NOT_CONNECTED("SDK 未连接"),
    /** SDK 状态不可用。 */
    STATUS_UNAVAILABLE("SDK 状态不可用"),
    /** 模型尚未确认可用。 */
    MODEL_NOT_READY("模型尚未确认可用"),
    /** SDK 未激活。 */
    NOT_ACTIVATED("SDK 未激活"),
    /** SDK 已停止。 */
    STOPPED("SDK 已停止"),
    /** SDK 正在清理。 */
    CLEANUP_PENDING("SDK 正在清理"),
    /** SDK 尚未初始化。 */
    NOT_INIT("SDK 尚未初始化"),
    /** SDK 正在初始化。 */
    INITIALIZING("SDK 正在初始化"),
    /** 模型正在准备。 */
    MODEL_PREPARING("模型正在准备"),
    /** 密钥轮换等待批准。 */
    KEY_ROTATION_PENDING("密钥轮换等待批准"),
    /** 密钥轮换被拒绝。 */
    KEY_ROTATION_REJECTED("密钥轮换被拒绝"),
    /** 密钥轮换请求过于频繁。 */
    KEY_ROTATION_RATE_LIMITED("密钥轮换请求过于频繁"),
    /** 密钥轮换正在恢复。 */
    KEY_ROTATION_RESTORING("密钥轮换正在恢复"),
    /** 证书已过期。 */
    CERTIFICATE_EXPIRED("证书已过期"),
    /** 证书无效。 */
    CERTIFICATE_INVALID("证书无效"),
    /** 后端暂不可用。 */
    BACKEND_UNAVAILABLE("后端暂不可用"),
    /** SDK 启动失败。 */
    STARTUP_FAILED("SDK 启动失败"),
    /** SDK 已禁用。 */
    DISABLED("SDK 已禁用");
    /** 与问题类型对应的默认中文说明。 */
    private final String message;
    /** 固定问题类型的说明，不引用宿主资源。 */
    SdkIssue(String message) { this.message = message; }
    /** 取得默认说明；原始 code/reason 仍保留在回执中。 */
    public String defaultMessage() { return message; }
}
