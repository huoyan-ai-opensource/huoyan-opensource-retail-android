package cc.huoyan.sdk.client.session;


import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkLog;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.StatusSnapshot;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** 首页提前拉起 SDK，功能页复用同一连接；退出页面时仅取消该页的初始化轮询。 */
public final class SdkStartupController {
    /** 宿主指定的观察间隔。 */
    private final long pollMillis;
    /** 当前连接来源，切换通道后重新取得。 */
    private final Supplier<HuoyanSdkClient> clients;
    /** 执行阻塞调用的后台线程。 */
    private final Executor worker;
    /** 宿主提供的回调线程。 */
    private final Executor callbacks;
    /** 宿主日志出口。 */
    private final SdkLog log;
    /** 观察代次；停止后旧回调丢弃。 */
    private volatile Object session;

    /** SDK 状态监听，在宿主注入的回调执行器上运行。 */
    public interface Listener {
        /** 返回初始化结果、状态快照及模型是否已就绪。 */
        void onStatus(int initCode, StatusSnapshot status, boolean modelReady);
    }

    /** 注入客户端来源、后台线程、回调线程和日志；默认每 200ms 观察一次。 */
    public SdkStartupController(Supplier<HuoyanSdkClient> clients, Executor worker,
                         Executor callbacks, SdkLog log) {
        this(clients, worker, callbacks, log, 200L);
    }

    /** 注入观察间隔；停止观察不撤销已经提交的 SDK 请求。 */
    public SdkStartupController(Supplier<HuoyanSdkClient> clients, Executor worker,
                         Executor callbacks, SdkLog log, long pollMillis) {
        if (pollMillis <= 0) throw new IllegalArgumentException("pollMillis 必须为正数");
        this.pollMillis = pollMillis;
        this.clients = clients;
        this.worker = worker;
        this.callbacks = callbacks;
        this.log = log;
    }

    /** 进入前台时连接并初始化；同一前台会话内重复调用不重复启动。 */
    public synchronized void start(Listener listener) {
        if (session != null) return;
        Object current = new Object();
        session = current;
        worker.execute(() -> poll(current, listener));
    }

    /** 退出前台时取消轮询和待执行回调，保留全局 SDK 连接供其他页面使用。 */
    public synchronized void stop() {
        session = null;
    }

    /** 后台观察连接与模型，回调时再检查观察代次。 */
    private void poll(Object current, Listener listener) {
        HuoyanSdkClient initialized = null;
        int initializationCode = HuoyanSdkClient.LOCAL_DISCONNECTED;
        while (session == current) {
            int initCode = HuoyanSdkClient.LOCAL_DISCONNECTED;
            StatusSnapshot status = null;
            try {
                HuoyanSdkClient client = clients.get();
                client.connect();
                if (client.isConnected()) {
                    if (session != current) return;
                    if (initialized != client) {
                        // 调用前记录本次尝试；丢失回执不在观察循环里重放。
                        initialized = client;
                        BaseResult result = client.initialize();
                        initializationCode = result == null ? HuoyanSdkClient.RESULT_UNKNOWN : result.getCode();
                        if (initializationCode == HuoyanSdkClient.LOCAL_DISCONNECTED
                                || initializationCode == HuoyanSdkClient.NOT_EXECUTED) initialized = null;
                    }
                    initCode = initializationCode;
                    status = client.getStatus();
                } else {
                    initialized = null;
                }
            } catch (RuntimeException error) {
                initializationCode = HuoyanSdkClient.RESULT_UNKNOWN;
                initCode = initializationCode;
            }
            boolean ready = cc.huoyan.sdk.client.status.SdkStatus.modelReady(status);
            if (session != current) return;
            if (listener != null) {
                final int code = initCode;
                final StatusSnapshot snapshot = status;
                callbacks.execute(() -> {
                    if (session == current) listener.onStatus(code, snapshot, ready);
                });
            }
            if (ready) return;
            try { log.log("SdkStartup", "[Biz] sdk_status=" + initCode
                    + " status=" + (status == null ? "na" : status.getCode())
                    + " sdk_state=" + (status == null ? "na" : status.getSdkState())
                    + " model_ready=" + ready
                    + " recent_error=" + (status == null ? "na" : status.getRecentError()));
            } catch (RuntimeException ignored) { /* 宿主日志不能终止观察。 */ }
            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
