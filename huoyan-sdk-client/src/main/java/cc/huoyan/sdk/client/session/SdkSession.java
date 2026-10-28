package cc.huoyan.sdk.client.session;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import java.util.Objects;
import java.util.function.Function;

/** 线程安全的 SDK 会话；配置变化释放旧客户端，不重放业务请求。 */
public final class SdkSession implements AutoCloseable {
    /** 客户端工厂，不持有页面。 */
    private final Function<SdkConfig, HuoyanSdkClient> factory;
    /** 当前配置，由锁保护。 */
    private SdkConfig config;
    /** 当前客户端，关闭或切换后丢弃。 */
    private HuoyanSdkClient client;
    /** 注入初始配置与工厂，延迟创建连接。 */
    public SdkSession(SdkConfig config, Function<SdkConfig, HuoyanSdkClient> factory) {
        this.config = Objects.requireNonNull(config);
        this.factory = Objects.requireNonNull(factory);
    }
    /** 取得当前配置，不触发连接。 */
    public synchronized SdkConfig getConfig() { return config; }
    /** 按通道和端点切换配置；同配置复用连接。 */
    public synchronized void configure(SdkConfig next) {
        Objects.requireNonNull(next);
        boolean changed = config.getChannel() != next.getChannel()
                || !Objects.equals(config.getWsEndpoint(), next.getWsEndpoint());
        config = next;
        if (changed) close();
    }
    /** 懒建当前客户端，由调用方决定连接和请求时机。 */
    public synchronized HuoyanSdkClient getClient() {
        if (client == null) client = Objects.requireNonNull(factory.apply(config));
        return client;
    }
    /** 释放当前连接，下一次取客户端时可重新创建。 */
    @Override public synchronized void close() {
        HuoyanSdkClient old = client;
        client = null;
        if (old != null) old.close();
    }
}
