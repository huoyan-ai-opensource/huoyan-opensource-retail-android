package cc.huoyan.sdk.client;

import cc.huoyan.sdk.client.internal.ws.WsV2Codec;
import java.util.Objects;

/** 客户端连接配置；不读取应用 SharedPreferences，不自动跨通道重试请求。 */
public final class SdkConfig {
    /** SDK 支持的通信方式。 */
    public enum Channel {
        /** Binder 通道。 */ AIDL,
        /** WebSocket 通道。 */ WS;
        /** 配置持久化值。 */
        public String value() { return name().toLowerCase(java.util.Locale.ROOT); }
        /** 未识别配置回退 AIDL，API 支持条件另行检查。 */
        public static Channel fromValue(String value) { return "ws".equals(value) ? WS : AIDL; }
    }
    /** 本机 WS 默认地址。 */
    public static final String DEFAULT_WS_ENDPOINT = "ws://127.0.0.1:18086/ws";
    /** 返回平台支持的通道，API 27 以下使用 WS。 */
    public static Channel supportedChannel(Channel channel, int apiLevel) {
        return apiLevel < 27 ? Channel.WS : channel;
    }
    /** 是否依赖本机 SDK；远程 WS 地址不要求本机安装。 */
    public boolean requiresLocalShell() {
        if (channel == Channel.AIDL) return true;
        String host = java.net.URI.create(getWsEndpoint()).getHost();
        return cc.huoyan.sdk.client.internal.LocalSdkEndpoint.isLocal(host);
    }

    private final Channel channel;
    private final WsV2Codec.Endpoint endpoint;
    private final SdkLog log;

    private SdkConfig(Channel channel, WsV2Codec.Endpoint endpoint, SdkLog log) {
        this.channel = channel;
        this.endpoint = endpoint;
        this.log = log;
    }

    /** 使用 AIDL 连接本机已安装的 SDK，创建客户端时要求 API 27+。 */
    public static SdkConfig aidl() {
        return new SdkConfig(Channel.AIDL, null, (tag, message) -> { });
    }

    /** 使用 WS 连接指定地址，格式为 ws://host:port/path，不接受无效地址。 */
    public static SdkConfig websocket(String url) {
        java.net.URI uri;
        try { uri = new java.net.URI(Objects.requireNonNull(url, "url 不能为空")); }
        catch (java.net.URISyntaxException e) { throw new IllegalArgumentException("WS 地址无效", e); }
        if (!"ws".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1
                || uri.getPort() > 65535 || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("WS 地址须为 ws://host:port/path");
        }
        String path = uri.getRawPath();
        return new SdkConfig(Channel.WS, new WsV2Codec.Endpoint(uri.getHost(), uri.getPort(),
                path == null || path.isEmpty() ? "/ws" : path), (tag, message) -> { });
    }

    /** 返回使用指定日志出口的新配置；业务端可仅在 DEBUG 构建输出。 */
    public SdkConfig withLog(SdkLog log) {
        return new SdkConfig(channel, endpoint, Objects.requireNonNull(log, "log 不能为空"));
    }

    /** 查询配置的通道。 */
    public Channel getChannel() { return channel; }

    /** 查询日志出口。 */
    public SdkLog getLog() { return log; }

    /** 查询 WS 地址文本；AIDL 配置返回 null。 */
    public String getWsEndpoint() { return endpoint == null ? null : endpoint.toUrl(); }
}
