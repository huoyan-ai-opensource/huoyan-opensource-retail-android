package cc.huoyan.retail.sdk;
import cc.huoyan.retail.goods.SdkClassMapping;
import cc.huoyan.sdk.client.SdkConfig.Channel;
import cc.huoyan.sdk.client.session.SdkSession;
import cc.huoyan.sdk.client.samples.LearnedClazzPages;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.retail.RetailApplication;
import cc.huoyan.retail.BuildConfig;

import cc.huoyan.sdk.client.model.CertInfo;
import cc.huoyan.sdk.client.model.ItemClazzList;
import cc.huoyan.sdk.client.model.StatusSnapshot;

/** 零售应用 SDK 装配入口；仅保存偏好、注入应用资源并持有 JAR 会话。 */
public final class SdkChannelManager {

    /** 通道值的持久化读写抽象（注入以便纯 JUnit 单测）。 */
    public interface ChannelStore {

        /** 读通道持久化值（无记录返回 null 或空串）。 */
        String get();

        /** 写通道持久化值。 */
        void put(String value);
    }

    /** 生产环境存储：SPManager（"HuoYanApplication" SP 文件）。 */
    private static final class SpChannelStore implements ChannelStore {

        @Override
        public String get() {
            return SdkConfig.supportedChannel(Channel.fromValue(
                    SPManager.getInstance().getString(SPManager.SP_SDK_CHANNEL)),
                    android.os.Build.VERSION.SDK_INT).value();
        }

        @Override
        public void put(String value) {
            SPManager.getInstance().putString(SPManager.SP_SDK_CHANNEL, value);
        }
    }

    /** 桥构造缝（生产为真实 AIDL/WS 实现，单测注入替身）。 */
    interface BridgeFactory {

        /**
         * 建指定通道的桥。
         *
         * @param channel 目标通道
         * @return 桥实现
         */
        HuoyanSdkClient create(Channel channel);
    }

    /** 从已保存的端点装配配置；非法旧值回退 SDK 默认地址。 */
    private static SdkConfig savedConfig(Channel channel) {
        SdkConfig config = SdkConfig.aidl();
        if (channel == Channel.WS) {
            try { config = SdkConfig.websocket(SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT)); }
            catch (IllegalArgumentException | NullPointerException ignored) {
                config = SdkConfig.websocket(SdkConfig.DEFAULT_WS_ENDPOINT);
            }
        }
        return config.withLog((tag, message) -> { if (BuildConfig.DEBUG) android.util.Log.d(tag, message); });
    }

    /** 应用级装配单例。 */
    private static volatile SdkChannelManager instance;

    /** 宿主配置持久化，不进入 JAR。 */
    private final ChannelStore store;
    /** JAR 会话拥有连接及切换生命周期。 */
    private final SdkSession session;

    /** 装配 JAR 会话；测试工厂不读取 Android 配置。 */
    private SdkChannelManager(ChannelStore store, BridgeFactory bridgeFactory) {
        this.store = store;
        Channel channel = Channel.fromValue(store.get());
        this.session = new SdkSession(bridgeFactory == null ? savedConfig(channel)
                : channel == Channel.AIDL ? SdkConfig.aidl() : SdkConfig.websocket(SdkConfig.DEFAULT_WS_ENDPOINT),
                config -> bridgeFactory == null ? HuoyanSdkClient.create(RetailApplication.getInstance(), config)
                        : bridgeFactory.create(config.getChannel()));
    }

    /**
     * 取单例（生产环境，SPManager 存储）。
     *
     * @return 通道管理器单例
     */
    public static SdkChannelManager getInstance() {
        if (instance == null) {
            synchronized (SdkChannelManager.class) {
                if (instance == null) {
                    instance = new SdkChannelManager(new SpChannelStore(),
                            null);
                }
            }
        }
        return instance;
    }

    /**
     * 构造指定存储的管理器（仅单测注入内存存储用）。
     *
     * @param store 通道值存储
     * @return 新实例（不触碰单例与 SPManager）
     */
    static SdkChannelManager createForTest(ChannelStore store) {
        return createForTest(store, channel -> { throw new IllegalStateException("测试须注入客户端工厂"); });
    }

    /**
     * 构造指定存储 + 桥工厂的管理器（单测注入替身桥验证透传语义用）。
     *
     * @param store        通道值存储
     * @param bridgeFactory 桥构造缝
     * @return 新实例（不触碰单例与 SPManager）
     */
    static SdkChannelManager createForTest(ChannelStore store, BridgeFactory bridgeFactory) {
        return new SdkChannelManager(store, bridgeFactory);
    }

    /**
     * 当前通道（读 SP）。
     *
     * @return 当前通道；无记录或值非法时回退默认 {@link Channel#AIDL}
     */
    public Channel getChannel() {
        return Channel.fromValue(store.get());
    }

    /** 保存支持的通道并交给 JAR 切换连接，清理图片展示缓存。 */
    public synchronized void setChannel(Channel channel) {
        if (channel == null) return;
        if (store instanceof SpChannelStore) channel = SdkConfig.supportedChannel(channel, android.os.Build.VERSION.SDK_INT);
        if (channel == getChannel()) return;
        store.put(channel.value());
        SdkConfig old = session.getConfig();
        session.configure(channel == Channel.AIDL ? SdkConfig.aidl().withLog(old.getLog())
                : SdkConfig.websocket(savedEndpoint()).withLog(old.getLog()));
        if (store instanceof SpChannelStore) SdkGoodsImages.invalidate();
    }

    /** 保存值读取仅属于宿主；测试存储不触碰 Android。 */
    private String savedEndpoint() {
        return store instanceof SpChannelStore ? savedConfig(Channel.WS).getWsEndpoint() : SdkConfig.DEFAULT_WS_ENDPOINT;
    }

    /**
     * 取当前通道的桥实现（同一通道复用同一实例，桥内持有连接状态；通道切换时
     * 旧桥由 {@link #setChannel} 断开并丢弃，切换后首次调用建新通道桥）。
     *
     * @return 当前通道对应的 {@link HuoyanSdkClient}
     */
    public HuoyanSdkClient getClient() { return session.getClient(); }

    /** 查询当前连接是否需要本机 SDK。 */
    public boolean requiresLocalShell() { return session.getConfig().requiresLocalShell(); }

    /** 注入零售应用的线程和日志策略，启动规则由 JAR 持有。 */
    public cc.huoyan.sdk.client.session.SdkStartupController newStartupController() {
        return new cc.huoyan.sdk.client.session.SdkStartupController(this::getClient,
                command -> cc.huoyan.retail.common.IOThreadHelper.getInstance().post(command),
                cc.huoyan.retail.common.MainHandler::runOnUiThread,
                (tag, message) -> { if (BuildConfig.DEBUG) android.util.Log.d(tag, message); });
    }

    /** 保存 WS 地址并丢弃旧连接，下一次连接使用新地址。 */
    public synchronized void setWsEndpoint(String endpoint) {
        String normalized = SdkConfig.websocket(endpoint).getWsEndpoint();
        if (normalized.equals(SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT))) return;
        SPManager.getInstance().putString(SPManager.SP_WS_ENDPOINT, normalized);
        if (getChannel() == Channel.WS) session.configure(
                SdkConfig.websocket(normalized).withLog(session.getConfig().getLog()));
        if (store instanceof SpChannelStore) SdkGoodsImages.invalidate();
    }

    /**
     * 查询 SDK 只读状态快照（未初始化判定新路径）：经当前通道
     * 桥透传 getStatus。同步阻塞调用（AIDL 为 Binder 同步事务 / WS 为 future
     * 等待），线程模型由调用方决定——须在后台线程执行。
     *
     * @return 状态快照；通道未连接返回 null（本方法不触发连接）
     */
    public StatusSnapshot getSdkStatus() {
        HuoyanSdkClient bridge = getClient();
        return bridge.isConnected() ? bridge.getStatus() : null;
    }

    /** 后台查询证书资料；激活与模型可用以 JAR 状态解释为准。 */
    public CertInfo getCertInfo() {
        HuoyanSdkClient bridge = getClient();
        return bridge.isConnected() ? bridge.getCertInfo() : null;
    }

    /**
     * 查询已学类别列表（v1 getStudiedClazzNameList 迁移新
     * 路径）：经当前通道桥逐页汇总 getItemClazzList，clazz 数组由调用方按
     * {@code SdkClassMapping.clazzFromPlu} 逆映射回 PLU 文本。同步阻塞调用，
     * 须在后台线程执行。
     *
     * @return 已学类别列表；通道未连接返回 null（本方法不触发连接）
     */
    public ItemClazzList listClasses() {
        HuoyanSdkClient bridge = getClient();
        return bridge.isConnected() ? LearnedClazzPages.loadAll(bridge) : null;
    }
}
