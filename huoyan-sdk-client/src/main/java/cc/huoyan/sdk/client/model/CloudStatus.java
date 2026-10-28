package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 云端托管开关回执（）：code=0 时 {@code enabled} 为开关终态；
 * 非 0 携带 reason（如 true→false 拒绝的「不可关闭」说明）——此时 enabled
 * 不代表真实状态，业务端以 code 判读。单向开关语义见
 * {@code CloudStatusStore}（缺省 false；开启后不可关闭；重启读同一持久化状态）。
 */
public final class CloudStatus {

    private final int code;
    private final String reason;
    private final boolean enabled;

    /** 构造数据对象。 */
    public CloudStatus(int code, String reason, boolean enabled) {
        this.code = code;
        this.reason = reason;
        this.enabled = enabled;
    }

    /** 结果码；0 表示请求成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 开关终态（code=0 有效）。 */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CloudStatus)) {
            return false;
        }
        CloudStatus that = (CloudStatus) other;
        return code == that.code && enabled == that.enabled
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * code + Boolean.hashCode(enabled)) + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return "CloudStatus(code=" + code + ", reason=" + reason
                + ", enabled=" + enabled + ")";
    }

}
