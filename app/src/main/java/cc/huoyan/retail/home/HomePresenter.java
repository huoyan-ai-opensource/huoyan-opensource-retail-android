package cc.huoyan.retail.home;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** 首页状态协调器；入口及回调均在主线程，读取在后台串行执行。 */
final class HomePresenter {
    /** 首页渲染能力，不持有控件或 Android 生命周期对象。 */
    interface View {
        /** 每次查询前刷新当前 SDK 通道名称。 */
        void showConnection();
        /** 展示云状态及 SDK 版本快照。 */
        void showStatus(Status status);
        /** 展示连接等待态；连接成功后恢复版本号。 */
        void showSdkStartup(boolean loading);
        /** 初始化等待与授权一起更新，避免连接成功后提前开放入口。 */
        default void showCheckoutStartup(boolean starting, HomeCheckoutState state) { }
        /** 独立展示版本，不能被慢速云查询覆盖。 */
        default void showSdkVersion(String version) { }
        /** 展示临期提醒；页面忙或不可提交弹窗时返回 false，后续轮询重试。 */
        default boolean showActivationExpiry(ActivationExpiry expiry) { return false; }
    }
    /** 一次查询的不可变展示数据。 */
    static final class Status {
        /** 云状态接口是否返回成功。 */
        final boolean cloudValid;
        /** 云服务是否已开启；无有效回执时为 false。 */
        final boolean cloudEnabled;
        /** SDK 版本名；未知时为破折号。 */
        final String version;
        /** 授权是否有效，null 表示状态暂不可用。 */
        final Boolean activated;
        /** 当前快照对应的入口状态，不丢弃轮换状态。 */
        final HomeCheckoutState checkoutState;
        /** 有效证书的到期秒数，0 表示未知或永久。 */
        final long expiresAt;
        /** 尚未取得有效快照时入口不可用。 */
        Status(boolean cloudValid, boolean cloudEnabled, String version) {
            this(cloudValid, cloudEnabled, version, HomeCheckoutState.UNAVAILABLE);
        }
        /** 固定云状态、版本与授权状态。 */
        Status(boolean cloudValid, boolean cloudEnabled, String version, HomeCheckoutState state) {
            this(cloudValid, cloudEnabled, version, state, 0);
        }
        /** 固定状态与同一通道读取的证书到期时间。 */
        Status(boolean cloudValid, boolean cloudEnabled, String version, HomeCheckoutState state, long expiresAt) {
            this.expiresAt = expiresAt;
            this.checkoutState = state;
            this.activated = state.authorization();
            this.cloudValid = cloudValid;
            this.cloudEnabled = cloudEnabled;
            this.version = version;
        }
    }
    /** 最近收到的入口状态；延迟结束启动等待时不能覆盖更新的轮换快照。 */
    private HomeCheckoutState latestCheckoutState = HomeCheckoutState.UNAVAILABLE;
    /** 当前页面；销毁时置空，迟到结果不能访问页面。 */
    private View view;
    /** 后台串行查询执行器，由 Activity 负责关闭。 */
    private final Executor worker;
    /** 主线程投递器，不捕获 Activity。 */
    private final Executor ui;
    /** 延迟主线程投递器，暂停时由宿主清除已排队任务。 */
    private final BiConsumer<Runnable, Long> delay;
    /** SDK 查询边界，便于独立验证轮询时序。 */
    private final Supplier<Status> source;
    /** 生命周期代次，暂停或重启后使旧回调失效。 */
    private int generation;
    /** 仅前台可见期间允许轮询。 */
    private boolean active;
    /** 只读取通道连接标记，不等待激活、初始化或模型状态。 */
    private final BooleanSupplier connected;
    /** 真实启动结束后停止首页启动观察，不断开全局 SDK 连接。 */
    private final Runnable stopSdkStartup;
    /** 本轮已取得版本，停止页脚连接观察；初始化与云状态观察独立执行。 */
    private boolean sdkStatusLoaded;
    /** 本页已确认过启动结果；返回时先静默检查，断连或初始化回执仍可重新进入等待。 */
    private boolean startupResolved;
    /** 当前入口是否展示等待，跨前后台保留，避免重复计时。 */
    private boolean checkoutWaiting;
    /** 本次实际展示等待的单调时间，仅用于等待态的最短展示时间。 */
    private long checkoutWaitStartedAt;
    /** 版本使用独立后台执行器，避免排在云接口之后。 */
    private Executor versionWorker;
    /** 仅查询能力接口版本，不查询云服务。 */
    private Supplier<String> versionSource;
    /** 单调时钟，测试可替换以验证最小展示时间。 */
    java.util.function.LongSupplier clock = () -> System.nanoTime() / 1_000_000L;
    /** 授权日期使用墙上时钟，不能与启动等待的单调时钟混用。 */
    java.util.function.LongSupplier epochSeconds = () -> System.currentTimeMillis() / 1000L;
    /** 本次进入首页是否已经展示提醒；轮询不能重复消费。 */
    private boolean expiryShown;
    /** 重建后的首次恢复沿用已消费状态，普通重新进入首页重新判断。 */
    private boolean restoreExpiryVisit;

    /** 页面重建前保存本次提醒是否已展示。 */
    boolean hasShownExpiry() { return expiryShown; }

    /** 页面重建后恢复本次进入的提醒状态。 */
    void restoreExpiryVisit(boolean shown) { expiryShown = shown; restoreExpiryVisit = true; }

    /** 页面创建时注入版本专用读取通道。 */
    void configureVersionLoader(Executor executor, Supplier<String> source) {
        versionWorker = executor;
        versionSource = source;
    }

    /** 注入读取与调度能力；所有 View 调用均经主线程。 */
    HomePresenter(View view, Executor worker, Executor ui,
                  BiConsumer<Runnable, Long> delay, Supplier<Status> source, BooleanSupplier connected,
                  Runnable stopSdkStartup) {
        this.view = view;
        this.worker = worker;
        this.ui = ui;
        this.delay = delay;
        this.source = source;
        this.connected = connected;
        this.stopSdkStartup = stopSdkStartup;
    }
    /** 恢复后每五秒刷新云端与授权状态，版本就绪不结束初始化等待。 */
    void resume() {
        if (!restoreExpiryVisit) expiryShown = false;
        restoreExpiryVisit = false;
        active = true; sdkStatusLoaded = false; query(++generation);
    }
    /** 使后台在途结果和已排队轮询失效。 */
    void pause() { active = false; ++generation; }
    /** 永久释放页面引用。 */
    void detach() { pause(); view = null; }
    /** 启动时独立观察连接，避免初始化或云状态请求阻塞加载提示。 */
    cc.huoyan.sdk.client.session.SdkStartupController.Listener beginSdkStartup() {
        int request = generation;
        if (active && view != null && (!startupResolved || !connected.getAsBoolean()))
            showCheckoutWaiting();
        observeSdkConnection(request);
        if (versionSource != null) queryVersion(request);
        return (code, status, ready) -> {
            if (!active || view == null || request != generation) return;
            latestCheckoutState = HomeCheckoutState.from(status);
            boolean starting = HomeStatusSource.starting(code, status);
            if (starting) {
                showCheckoutWaiting();
                view.showCheckoutStartup(true, HomeCheckoutState.from(status));
                return;
            }
            stopSdkStartup.run();
            Runnable finish = () -> {
                if (active && view != null && request == generation) {
                    startupResolved = true;
                    checkoutWaiting = false;
                    view.showCheckoutStartup(false, latestCheckoutState);
                }
            };
            long remaining = checkoutWaiting
                    ? 600L - (clock.getAsLong() - checkoutWaitStartedAt) : 0L;
            if (remaining > 0) delay.accept(finish, remaining);
            else finish.run();
        };
    }

    /** 主线程仅在首次等待或真实启动状态变化时更新入口并开始计时。 */
    private void showCheckoutWaiting() {
        startupResolved = false;
        if (checkoutWaiting) return;
        checkoutWaiting = true;
        checkoutWaitStartedAt = clock.getAsLong();
        view.showCheckoutStartup(true, HomeCheckoutState.STARTING);
    }

    /** 连接后立即读版本；未连接每 200ms 检查，空回执每秒重试，不等待云轮询。 */
    private void queryVersion(int request) {
        if (!active || view == null || request != generation) return;
        if (!connected.getAsBoolean()) {
            delay.accept(() -> queryVersion(request), 200L);
            return;
        }
        versionWorker.execute(() -> {
            String version = versionSource.get();
            ui.execute(() -> {
                if (!active || view == null || request != generation) return;
                if (version != null && !version.trim().isEmpty() && !"—".equals(version)) {
                    view.showSdkVersion(version);
                } else delay.accept(() -> queryVersion(request), 1000L);
            });
        });
    }
    /** 主线程读取非阻塞连接标记；离开页面后丢弃旧轮询，连接成功即结束等待。 */
    private void observeSdkConnection(int request) {
        if (!active || view == null || request != generation || sdkStatusLoaded) return;
        boolean loading = !connected.getAsBoolean();
        view.showSdkStartup(loading);
        if (loading) delay.accept(() -> observeSdkConnection(request), 200L);
    }
    /** 查询指定代次，结果过期时不渲染也不继续轮询。 */
    private void query(int request) {
        if (!active || view == null || request != generation) return;
        view.showConnection();
        worker.execute(() -> {
            Status result = source.get();
            ui.execute(() -> {
                if (!active || view == null || request != generation) return;
                latestCheckoutState = result.checkoutState;
                view.showStatus(result);
                if (!expiryShown && connected.getAsBoolean()) {
                    ActivationExpiry expiry = ActivationExpiry.check(result.activated, result.expiresAt,
                            epochSeconds.getAsLong());
                    if (expiry != null) expiryShown = view.showActivationExpiry(expiry);
                }
                if (connected.getAsBoolean() && result.version != null
                        && !result.version.trim().isEmpty() && !"—".equals(result.version)) {
                    sdkStatusLoaded = true;
                    view.showSdkStartup(false);
                }
                delay.accept(() -> query(request), 5000L);
            });
        });
    }
}
