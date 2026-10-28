package cc.huoyan.retail.home;

import cc.huoyan.sdk.client.model.CloudStatus;
import cc.huoyan.sdk.client.model.Capability;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.retail.sdk.SdkVersionDisplay;
import cc.huoyan.retail.diagnostics.Logger;

/** 在后台读取 SDK 状态；版本由能力接口提供，不依赖证书可用性。 */
final class HomeStatusSource {
    /** 独立读取当前通道的版本；切换通道时丢弃旧通道回执。 */
    static String readVersion() {
        HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
        String version = readVersion(client);
        return client == SdkChannelManager.getInstance().getClient() ? version : null;
    }

    /** 后台只访问能力接口，云端不可用不影响版本显示。 */
    static String readVersion(HuoyanSdkClient client) {
        try {
            return client.isConnected() ? SdkVersionDisplay.name(client.getCapabilities()) : null;
        } catch (RuntimeException error) {
            Logger.logError("HomeVersion", error);
            return null;
        }
    }
    /** 返回本次读取快照，未连接或读取失败对应不可用状态。 */
    static HomePresenter.Status read() {
        HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
        HomePresenter.Status status = read(client, false);
        return client == SdkChannelManager.getInstance().getClient() ? status
                : new HomePresenter.Status(false, false, "—");
    }

    /** 读取指定客户端，云接口失败仍继续查询 SDK 版本。 */
    static HomePresenter.Status read(HuoyanSdkClient client) {
        return read(client, true);
    }

    /** 生产轮询不重复读版本，版本由专用通道立即推送。 */
    private static HomePresenter.Status read(HuoyanSdkClient client, boolean includeVersion) {
        CloudStatus cloud = null;
        Capability capability = null;
        cc.huoyan.sdk.client.model.StatusSnapshot status = null;
        cc.huoyan.sdk.client.model.CertInfo certificate = null;
        try {
            if (client.isConnected()) {
                try { cloud = client.getCloudStatus(); }
                catch (RuntimeException error) { Logger.logError("HomeCloudStatus", error); }
                try { if (includeVersion) capability = client.getCapabilities(); }
                catch (RuntimeException error) { Logger.logError("HomeVersion", error); }
                try { status = client.getStatus(); }
                catch (RuntimeException error) { Logger.logError("HomeActivation", error); }
                if (Boolean.TRUE.equals(activation(status))) {
                    try { certificate = client.getCertInfo(); }
                    catch (RuntimeException error) { Logger.logError("HomeExpiry", error); }
                }
            }
        } catch (RuntimeException error) {
            Logger.logError("HomeStatus", error);
        }
        String version = SdkVersionDisplay.name(capability);
        return new HomePresenter.Status(cloud != null && cloud.getCode() == 0,
                cloud != null && cloud.getCode() == 0 && cloud.isEnabled(),
                version != null ? version : "—", HomeCheckoutState.from(status),
                certificate != null && certificate.getCode() == 0 ? certificate.getExpiresAt() : 0);
    }
    /** 通道连通、版本可读和初始化成功回执均不能单独结束等待。 */
    static boolean starting(int code, cc.huoyan.sdk.client.model.StatusSnapshot status) {
        if (code == HuoyanSdkClient.LOCAL_DISCONNECTED || code == HuoyanSdkClient.NOT_EXECUTED) return true;
        if (status == null || status.getCode() != 0) return code != -111;
        String state = status.getSdkState();
        if (state == null) return !Boolean.TRUE.equals(status.getModelReady());
        switch (state) {
            case "NOT_INIT":
            case "INITIALIZING":
            case "MODEL_PREPARING":
            case "CLEANUP_PENDING":
            case "KEY_ROTATION_RESTORING":
                return true;
            default:
                return false;
        }
    }

    /** 只用有效授权回执确认未激活，连接失败不伪装成未激活。 */
    static Boolean activation(cc.huoyan.sdk.client.model.StatusSnapshot status) {
        return HomeCheckoutState.from(status).authorization();
    }
}
