package cc.huoyan.sdk.client.internal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import cc.huoyan.sdk.client.SdkLog;

import cc.huoyan.app.aidl.ISdkCommService;
import cc.huoyan.app.aidl.BaseResultParcel;

/**
 * SDK AIDL 绑定客户端（语义对齐 SDK 契约文档
 * 「业务客户端接入与重连」第 3/4/5 节模板）：
 * {@code bindService(BIND_AUTO_CREATE)} 拉起 SDK（action
 * {@code cc.huoyan.action.BIND_SDK}、包 {@code cc.huoyan}）+ 断线指数退避
 * 重连（500ms 起步每次翻倍封顶 30s，连接成功清零，叠加断线事件只留一个
 * 在途任务）+ {@link #close()} 永不再连。回调与重连统一串行在主线程；
 * 跨进程 Binder 事务为同步调用，调用方须自行切后台线程。
 *
 * <p>绑定操作、主线程调度与打点经 {@link BindOps}/{@link Scheduler}/
 * {@link EventLog} 注入（生产为 Context.bindService / 主线程 Handler /
 * android Log），状态机保持纯 Java 可单测（项目约定：单测路径不触 android 类）。
 */
public final class SdkCommBinding {

    private static final String LOG_TAG = "SdkCommBinding";
    private static final String ACTION_BIND_SDK = "cc.huoyan.action.BIND_SDK";
    private static final String SDK_PACKAGE = "cc.huoyan";

    /** 指数退避：500ms 起步，每失败一次翻倍，封顶 30s。 */
    static final long BACKOFF_FIRST_MS = 500L;
    static final long BACKOFF_MAX_MS = 30_000L;

    /** 本地哨兵：未连接/连接断开（非服务端回执，不与服务码段 0/负值/40004 冲突）。 */
    public static final int LOCAL_DISCONNECTED = Integer.MIN_VALUE;

    /**
     * 绑定操作抽象：生产为 {@code Context.bindService/unbindService}
     * （BIND_AUTO_CREATE + 显式包名 Intent），单测注入记录式实现。
     */
    interface BindOps {

        /** 发起绑定（BIND_AUTO_CREATE）；false=SDK 未安装/组件不可用/包不可见；实现抛出的运行时异常由状态机折算为拒绝。 */
        boolean bind(ServiceConnection connection);

        /** 解绑（连接未注册时由调用方吞 IllegalArgumentException）。 */
        void unbind(ServiceConnection connection);
    }

    /** 主线程调度抽象：生产为主线程 Handler，单测注入捕获式实现。 */
    interface Scheduler {

        /** 主线程即时执行。 */
        void post(Runnable task);

        /** 主线程延迟执行。 */
        void postDelayed(Runnable task, long delayMs);

        /** 取消全部在途任务（close 时停掉退避重连）。 */
        void cancelPending();
    }

    /** [Biz] 打点出口：生产为 android Log（由调用方控制调试日志，平台 wait_log/assert_value 依赖），单测注入记录实现。 */
    interface EventLog {

        /** 打一条业务打点（消息形如 {@code [Biz] key=value}）。 */
        void biz(String message);
    }

    /** 生产绑定操作：applicationContext + 显式包名隐式 Intent + BIND_AUTO_CREATE。 */
    private static final class ContextBindOps implements BindOps {

        private final Context appContext;

        ContextBindOps(Context context) {
            this.appContext = context.getApplicationContext();
        }

        @Override
        public boolean bind(ServiceConnection connection) {
            Intent intent = new Intent(ACTION_BIND_SDK).setPackage(SDK_PACKAGE);
            return appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }

        @Override
        public void unbind(ServiceConnection connection) {
            appContext.unbindService(connection);
        }
    }

    /** 生产主线程调度：私有 Handler 仅承载本类的重连任务（cancelPending 可全量清）。 */
    private static final class HandlerScheduler implements Scheduler {

        private final Handler main = new Handler(Looper.getMainLooper());

        @Override
        public void post(Runnable task) {
            main.post(task);
        }

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            main.postDelayed(task, delayMs);
        }

        @Override
        public void cancelPending() {
            main.removeCallbacksAndMessages(null);
        }
    }

    private final BindOps bindOps;
    private final Scheduler main;
    private final EventLog eventLog;
    private final ServiceConnection connection = new SdkConnection();

    private ISdkCommService sdk;      // null = 当前断开
    private boolean bindRequested;    // bindService 已发起且未 unbind
    private boolean reconnectPending; // 已有退避重连任务在途
    private boolean closed;           // close()/onNullBinding 后永不再连
    private int backoffAttempt;

    /**
     * 生产构造（Context 经 application 上下文绑定）。
     *
     * @param context 任意上下文（内部取 applicationContext）
     */
    public SdkCommBinding(Context context, SdkLog log) {
        this(new ContextBindOps(context), new HandlerScheduler(), message -> log.log(LOG_TAG, message));
    }

    /**
     * 注入式构造（单测：绑定操作/调度/打点均为内存实现）。
     *
     * @param bindOps  绑定操作
     * @param main     主线程调度
     * @param eventLog 打点出口
     */
    SdkCommBinding(BindOps bindOps, Scheduler main, EventLog eventLog) {
        this.bindOps = bindOps;
        this.main = main;
        this.eventLog = eventLog;
    }

    /**
     * 发起绑定（幂等；重复调用与 close 后调用均为空操作）。绑定操作抛
     * {@code SecurityException}（signature 级权限拒绝，客户端与 SDK 签名不符）/
     * {@code IllegalArgumentException}（Intent 非法）等运行时异常时折算为拒绝，
     * 不向调用方上抛——走退避重连，不崩溃。
     */
    public synchronized void bind() {
        if (closed || bindRequested) {
            return;
        }
        bindRequested = true;
        boolean accepted;
        try {
            accepted = bindOps.bind(connection);
        } catch (RuntimeException e) {
            // 签名不符/SDK 未安装等准入失败不应崩溃（2026-08-26 C50 真机实测
            // bindService 抛 SecurityException 启动崩溃）：折算为 bind 拒绝，由
            // 退避重连承载；bind 抛异常时连接未注册，同 false 路径无需解绑
            eventLog.biz("[Biz] aidl_bind_error=" + e.getClass().getSimpleName());
            accepted = false;
        }
        eventLog.biz("[Biz] aidl_connect=" + (accepted ? "1" : "0"));
        if (!accepted) {
            bindRequested = false;   // SDK 未安装/组件不可用/包不可见
            scheduleReconnect();
        }
    }

    /**
     * 当前是否已连接（连接成功 ≠ 初始化完成，调用前先 {@link #init}）。
     *
     * @return true=已连接
     */
    public synchronized boolean isConnected() {
        return sdk != null;
    }

    /**
     * 在后台线程触发/复用 SDK 初始化；连接成功不代表初始化完成。
     * @return 非空动作回执；断连或 Binder 失败返回本地哨兵并按需重连。
     */
    public BaseResultParcel init(String appVersion) {
        ISdkCommService current;
        synchronized (this) {
            current = sdk;
        }
        if (current == null) {
            return localDisconnected("本地未连接（bind 未完成或已断开）");
        }
        try {
            BaseResultParcel result = current.init(appVersion);
            eventLog.biz("[Biz] aidl_init=" + (result != null ? result.getCode() : "null"));
            return result != null ? result
                    : new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "SDK 未返回初始化回执");
        } catch (RemoteException e) {
            markDisconnectedAndReconnect();
            return new BaseResultParcel(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, "Binder 事务失败，初始化结果未知");
        }
    }

    /** 本地断连动作回执。 */
    private static BaseResultParcel localDisconnected(String reason) {
        return new BaseResultParcel(LOCAL_DISCONNECTED, reason);
    }

    /** 停止使用并永不再连：取消退避任务 + 解绑。 */
    public synchronized void close() {
        closed = true;
        main.cancelPending();
        if (bindRequested) {
            bindRequested = false;
            bindOps.unbind(connection);
        }
        sdk = null;
        eventLog.biz("[Biz] aidl_disconnect=close");
    }

    /** 当前服务代理（null=未连接）；Binder 同步事务，调用方须在后台线程执行。 */
    synchronized ISdkCommService service() {
        return sdk;
    }

    /** 桥接层 Binder 调用抛 RemoteException 后回调：标记断开 + 退避重连。 */
    void onRemoteException() {
        eventLog.biz("[Biz] aidl_remote_exception=1");
        markDisconnectedAndReconnect();
    }

    /** 连接建立（onServiceConnected 回调）：缓存代理 + 退避计数清零。 */
    synchronized void connected(ISdkCommService service) {
        if (closed) {
            return;
        }
        sdk = service;
        backoffAttempt = 0;
        eventLog.biz("[Biz] aidl_connected=1");
    }

    /** 服务进程死亡（onServiceDisconnected）：清空死代理 + 退避重连兜底。 */
    void serviceDisconnected() {
        synchronized (this) {
            sdk = null;
        }
        eventLog.biz("[Biz] aidl_disconnected=1");
        markDisconnectedAndReconnect();
    }

    /** 宿主更新/卸载等导致绑定死亡（onBindingDied，API 28+）：同断线处理。 */
    void bindingDied() {
        serviceDisconnected();
    }

    /** onBind 返回 null（契约级故障）：重连无意义，解绑并停止重连、上报人工介入。 */
    void nullBinding() {
        synchronized (this) {
            sdk = null;
            bindRequested = false;
            closed = true;
        }
        eventLog.biz("[Biz] aidl_null_binding=1");
        bindOps.unbind(connection);
    }

    /**
     * 指数退避延迟（纯函数）：500ms 起步每次翻倍封顶 30s（位移上限 6 防溢出，
     * 500&lt;&lt;6=32000 已达封顶）。
     *
     * @param attempt 失败次数（连接成功清零）
     * @return 下次重连延迟毫秒数
     */
    static long backoffDelayMs(int attempt) {
        int shift = Math.min(attempt, 6);
        return Math.min(BACKOFF_FIRST_MS << shift, BACKOFF_MAX_MS);
    }

    private void markDisconnectedAndReconnect() {
        main.post(this::scheduleReconnect); // 统一回主线程串行化
    }

    private synchronized void scheduleReconnect() {
        if (closed) {
            return;
        }
        if (bindRequested) {  // onServiceDisconnected/onBindingDied 场景：旧绑定先解掉再重绑
            bindRequested = false;
            try {
                bindOps.unbind(connection);
            } catch (IllegalArgumentException ignored) {
                // 连接本就未注册（如 bindService 曾返回 false）：忽略
            }
        }
        if (reconnectPending) {
            return;           // 已有退避任务在途，不叠加
        }
        reconnectPending = true;
        long delayMs = backoffDelayMs(backoffAttempt);
        backoffAttempt++;
        eventLog.biz("[Biz] aidl_reconnect delay_ms=" + delayMs + " attempt=" + backoffAttempt);
        main.postDelayed(() -> {
            reconnectPending = false;
            bind();
        }, delayMs);
    }

    /** SDK 契约 ServiceConnection 四回调（第 4 节表语义，逻辑移交上方包级方法以便单测直连）。 */
    private final class SdkConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            connected(ISdkCommService.Stub.asInterface(binder));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // 服务进程死亡；BIND_AUTO_CREATE 下系统会尝试重建，
            // 客户端仍主动解绑 + 退避重绑兜底（重建失败/被系统放弃的场景）
            serviceDisconnected();
        }

        @Override
        public void onBindingDied(ComponentName name) {  // API 28+ 才会回调
            bindingDied();
        }

        @Override
        public void onNullBinding(ComponentName name) {  // API 26+ 才会回调
            nullBinding();
        }
    }
}
