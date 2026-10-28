package cc.huoyan.retail.sdk;

import cc.huoyan.sdk.client.model.Capability;

/** 首页与设备信息页共用的 SDK 版本展示规则，不依赖授权或模型状态。 */
public final class SdkVersionDisplay {
    /** 仅提供纯函数，不保存页面或连接。 */
    private SdkVersionDisplay() { }

    /** 返回能力接口携带的有效版本；缺失时返回 null，由页面选择占位文案。 */
    public static String name(Capability capability) {
        if (capability == null) return null;
        String version = capability.getSdkVersionName();
        if (version == null) return null;
        version = version.trim();
        return version.isEmpty() || "unknown".equalsIgnoreCase(version) || "—".equals(version)
                ? null : version;
    }
}
