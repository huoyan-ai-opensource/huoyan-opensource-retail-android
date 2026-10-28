package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 通用动作结果回执（统一错误模型）：activate/renewCert/setBaseUrl/
 * itemForget/itemForgetByUid 等无载荷动作的统一形态——code=0 成功，非 0 时
 * reason 携带错误说明；业务 App 不解 native 细节。
 */
public final class BaseResult {

    private final int code;
    private final String reason;

    /** 构造数据对象。 */
    public BaseResult(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    /** 结果码；0 表示请求成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseResult)) {
            return false;
        }
        BaseResult that = (BaseResult) other;
        return code == that.code && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return 31 * code + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return "BaseResult(code=" + code + ", reason=" + reason + ")";
    }

}
