package cc.huoyan.retail.settings;

import java.util.concurrent.Executor;

/** 识别设置只读模型快照；后台不持有 Fragment，旧代次不回写。 */
final class RecognitionSettingsPresenter {
    /** 同时显示模型名称和版本。 */
    interface View { /** 空快照显示占位，不展示诊断状态。 */ void showModel(Model model); }
    /** 工作线程查询边界。 */
    interface Source { /** 从同一设备取得名称与版本。 */ Model currentModel(); }
    /** 名称与版本的同次查询快照。 */
    static final class Model {
        /** 动态模型名称。 */
        final String name;
        /** 状态接口返回的模型版本。 */
        final String version;
        /** 固定展示快照。 */
        Model(String name, String version) { this.name = name; this.version = version; }
    }
    /** 当前主线程视图，销毁时释放。 */
    private View view;
    /** 应用级后台队列。 */
    private final Executor worker;
    /** 主线程提交器。 */
    private final Executor ui;
    /** 不含视图引用的数据源。 */
    private final Source source;
    /** 最新查询代次。 */
    private int generation;
    /** 注入执行边界。 */
    RecognitionSettingsPresenter(View view, Executor worker, Executor ui, Source source) {
        this.view = view; this.worker = worker; this.ui = ui; this.source = source;
    }
    /** 刷新真实模型；切换失败也不保留乐观选中值。 */
    void refresh() {
        if (view == null) return;
        int request = ++generation;
        view.showModel(null);
        worker.execute(() -> {
            Model result;
            try { result = source.currentModel(); } catch (RuntimeException error) { result = null; }
            Model snapshot = result;
            ui.execute(() -> { if (view != null && request == generation) view.showModel(snapshot); });
        });
    }
    /** 视图销毁使所有旧查询失效。 */
    void detach() { view = null; generation++; }
}
