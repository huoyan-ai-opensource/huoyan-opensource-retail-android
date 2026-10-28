package cc.huoyan.retail.home;

import cc.huoyan.retail.R;
import cc.huoyan.sdk.client.model.StatusSnapshot;

/** 首页收银入口的当前状态；轮换状态优先于授权和模型字段，未知状态禁止收银。 */
enum HomeCheckoutState {
    /** 初始化尚未完成。 */
    STARTING(R.string.home_checkout_starting_hint, R.string.home_checkout_starting),
    /** 已确认可以进入收银。 */
    READY(R.string.home_checkout_subtitle, R.string.home_enter_checkout),
    /** 需要用户激活设备。 */
    ACTIVATION_REQUIRED(R.string.home_activation_required, R.string.home_activate_now),
    /** 等待后台审核密钥轮换。 */
    ROTATION_PENDING(R.string.home_rotation_pending, R.string.home_waiting_review),
    /** 后台已拒绝本次轮换。 */
    ROTATION_REJECTED(R.string.status_sdk_key_rotation_rejected, R.string.home_checkout_unavailable),
    /** 轮换请求处于限频窗口。 */
    ROTATION_RATE_LIMITED(R.string.status_sdk_key_rotation_rate_limited, R.string.home_checkout_unavailable),
    /** 审核通过，正在恢复识别服务。 */
    ROTATION_RESTORING(R.string.status_sdk_key_rotation_restoring, R.string.home_restoring_service),
    /** 没有有效的可用状态，不能沿用之前的收银入口。 */
    UNAVAILABLE(R.string.home_service_unavailable, R.string.home_checkout_unavailable);

    /** 卡片说明的本地化资源。 */
    final int hint;
    /** 卡片按钮的本地化资源。 */
    final int action;

    /** 固定该状态的显示文案。 */
    HomeCheckoutState(int hint, int action) { this.hint = hint; this.action = action; }

    /** 从当前有效快照映射，不根据 reason 文案或过去的激活结果猜测。 */
    static HomeCheckoutState from(StatusSnapshot status) {
        if (status == null || status.getCode() != 0 || status.getSdkState() == null)
            return UNAVAILABLE;
        switch (status.getSdkState()) {
            case "KEY_ROTATION_PENDING": return ROTATION_PENDING;
            case "KEY_ROTATION_REJECTED": return ROTATION_REJECTED;
            case "KEY_ROTATION_RATE_LIMITED": return ROTATION_RATE_LIMITED;
            case "KEY_ROTATION_RESTORING": return ROTATION_RESTORING;
            case "NOT_INIT":
            case "INITIALIZING":
            case "MODEL_PREPARING":
            case "CLEANUP_PENDING": return STARTING;
            case "REGISTERED":
            case "NOT_ACTIVATED":
            case "CERTIFICATE_EXPIRED":
            case "CERTIFICATE_INVALID": return ACTIVATION_REQUIRED;
            case "READY":
            case "OFFLINE":
                // 离线未校准不影响本地收银；授权、模型和私钥事实仍需分别核实。
                if (Boolean.TRUE.equals(status.getKeyLost())) return UNAVAILABLE;
                if (Boolean.FALSE.equals(status.getCertificateValid())) return ACTIVATION_REQUIRED;
                return Boolean.TRUE.equals(status.getCertificateValid())
                        && Boolean.TRUE.equals(status.getModelReady()) ? READY : UNAVAILABLE;
            default: return UNAVAILABLE;
        }
    }

    /** 轮换各阶段统一使用警示色，均不开放收银或激活入口。 */
    boolean isRotation() {
        return this == ROTATION_PENDING || this == ROTATION_REJECTED
                || this == ROTATION_RATE_LIMITED || this == ROTATION_RESTORING;
    }

    /** 只有收银和激活状态可操作；安装入口由页面单独处理。 */
    boolean actionable() { return this == READY || this == ACTIVATION_REQUIRED; }

    /** 临期提醒仅在已确认可用时查询证书，轮换和未知状态不沿用旧授权。 */
    Boolean authorization() {
        return this == READY ? Boolean.TRUE : this == ACTIVATION_REQUIRED ? Boolean.FALSE : null;
    }
}
