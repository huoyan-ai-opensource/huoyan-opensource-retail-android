package example;

import android.content.Context;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.sdk.client.SdkLog;
import cc.huoyan.sdk.client.session.SdkSession;
import cc.huoyan.sdk.client.session.SdkStartupController;
import java.util.concurrent.Executor;

/** 无零售应用依赖的会话装配；UI 自己提供执行器及状态监听。 */
public final class SessionIntegrationExample implements AutoCloseable {
    /** JAR 管理当前连接，客户端决定此对象的生命周期。 */
    private final SdkSession session;
    /** 只观察当前页面需要的启动/模型状态。 */
    private final SdkStartupController startup;

    /** 注入应用 Context、后台/回调执行器及日志，未读取任何应用偏好。 */
    public SessionIntegrationExample(Context context, SdkConfig config,
            Executor worker, Executor ui, SdkLog log) {
        Context application = context.getApplicationContext();
        session = new SdkSession(config.withLog(log), next -> HuoyanSdkClient.create(application, next));
        startup = new SdkStartupController(session::getClient, worker, ui, log, 200L);
    }

    /** 前台开始观察；监听器可将 JAR 问题类型映射为自己的文案。 */
    public void start(SdkStartupController.Listener listener) { startup.start(listener); }

    /** 离开页面停止观察，保留共享连接；不会撤销已提交的学习/反馈。 */
    public void stopObserving() { startup.stop(); }

    /** 明确切换通道/地址；JAR 释放旧连接，客户端再启动新观察。 */
    public void configure(SdkConfig config) { startup.stop(); session.configure(config); }

    /** 只读查询当前是否需要引导安装本机火眼 SDK。 */
    public boolean requiresLocalShell() { return session.getConfig().requiresLocalShell(); }

    /** 会话所有者退出时释放连接，业务方应先等待已提交写操作完成。 */
    @Override public void close() { startup.stop(); session.close(); }
}
