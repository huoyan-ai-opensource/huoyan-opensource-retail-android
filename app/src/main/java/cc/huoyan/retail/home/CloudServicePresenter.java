package cc.huoyan.retail.home;

import cc.huoyan.sdk.client.model.CloudStatus;
import java.util.concurrent.Executor;

/** 云端弹窗状态协调；主线程接收操作，已提交写操作在关闭弹窗后继续完成。 */
final class CloudServicePresenter {
    /** 弹窗只负责把状态绘制为控件。 */
    interface View {
        /** 主线程渲染当前快照。 */ void render();
        /** 主线程展示一次性操作回执，不把登录和开关结果留在弹窗底部。 */
        default void notifyResult(Result receipt) { }
    }
    /** SDK 操作回执，成功标记独立于展示文案，供 ToastEx 选择图标。 */
    static final class Result {
        /** 是否收到完整成功的回执。 */ final boolean success;
        /** 当前语言的结果说明。 */ final String message;
        /** 固定本次操作结果。 */
        Result(boolean success, String message) { this.success = success; this.message = message; }
    }
    /** SDK 与绑定信息访问，方法均在后台执行。 */
    interface Source {
        /** 查询真实云状态，失败保留 null。 */ CloudStatus cloud();
        /** 执行一次写操作，不自动重试。 */ Result run(Action action, String input);
        /** 格式化异常，保证没有异常消息时仍展示失败。 */
        default String failure(RuntimeException error) {
            return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        }
    }
    /** 可执行操作；识别库同步为云端图片下载学习，不做备份恢复。 */
    enum Action { BIND, ENABLE, GOODS, IMAGES }
    /** 当前弹窗视图，关闭后释放。 */
    private View view;
    /** 首页已销毁时停止提交后续查询，已入队写操作仍完成。 */
    private boolean disposed;
    /** 后台串行执行器，不持有界面。 */
    private final Executor worker;
    /** 主线程回调边界。 */
    private final Executor ui;
    /** 无页面引用的数据源。 */
    private final Source source;
    /** 最新可信开关，null 表示未知；失败不能解释为关闭。 */
    Boolean enabled;
    /** 当前查询或写入是否进行中，重开弹窗也保持互斥。 */
    boolean busy;
    /** 当前写操作，用于显示具体进度文案。 */
    Action action;
    /** 弹窗关闭期间完成的操作回执，下次显示时仅交付一次。 */
    private Result pendingNotice;
    /** 注入调度和数据访问。 */
    CloudServicePresenter(Executor worker, Executor ui, Source source) {
        this.worker = worker; this.ui = ui; this.source = source;
    }
    /** 重新绑定弹窗，恢复在途操作。 */
    void attach(View view) { this.view = view; changed(); deliverNotice(); }
    /** 关闭弹窗不撤销 SDK 操作。 */
    void detach() { view = null; }
    /** 首页销毁后停止续查，避免向已关闭线程池提交任务。 */
    void close() { disposed = true; view = null; }
    /** 查询真实开关；写操作进行中时不排入额外查询。 */
    void refresh() {
        if (busy || disposed) return;
        busy = true; action = null; changed();
        worker.execute(() -> {
            CloudStatus cloud;
            try { cloud = source.cloud(); } catch (RuntimeException error) { cloud = null; }
            CloudStatus receipt = cloud;
            ui.execute(() -> {
                enabled = receipt != null && receipt.getCode() == 0 ? receipt.isEnabled() : null;
                busy = false; changed();
            });
        });
    }
    /** 用户确认后提交一次操作；开启和同步均要求已经读取到真实状态。 */
    void run(Action requested, String input) {
        if (disposed || busy || (requested == Action.ENABLE && !Boolean.FALSE.equals(enabled))
                || ((requested == Action.GOODS || requested == Action.IMAGES) && !Boolean.TRUE.equals(enabled))) return;
        busy = true; action = requested; pendingNotice = null; changed();
        worker.execute(() -> {
            Result receipt;
            try { receipt = source.run(requested, input); }
            catch (RuntimeException error) { receipt = new Result(false, source.failure(error)); }
            Result completed = receipt;
            ui.execute(() -> {
                busy = false; action = null;
                pendingNotice = completed;
                changed();
                deliverNotice();
                refresh();
            });
        });
    }
    /** 先消费再通知，重绘或重开弹窗不会重复提示；不访问已销毁视图。 */
    private void deliverNotice() {
        if (view == null || pendingNotice == null) return;
        Result receipt = pendingNotice;
        pendingNotice = null;
        view.notifyResult(receipt);
    }
    /** 只通知仍然绑定的弹窗。 */
    private void changed() { if (view != null) view.render(); }
}
