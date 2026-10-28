package cc.huoyan.retail.settings;

import java.util.concurrent.Executor;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** 设置页商品数量查询；只保存当前视图的在途代次，不持久化商品统计。 */
final class GoodsCountPresenter {
    /** 后台 SDK 查询队列。 */
    private final Executor worker;
    /** 主线程回执队列。 */
    private final Executor ui;
    /** SDK 数量入口，不捕获 Fragment。 */
    private final LongSupplier source;
    /** 销毁视图后释放的渲染端口。 */
    private LongConsumer view;
    /** 最新有效请求代次。 */
    private int generation;
    /** 注入后台查询和主线程调度。 */
    GoodsCountPresenter(Executor worker, Executor ui, LongSupplier source) {
        this.worker = worker; this.ui = ui; this.source = source;
    }
    /** 绑定当前视图。 */
    void attach(LongConsumer view) { this.view = view; }
    /** 释放视图，后台查询不得保留 Fragment。 */
    void detach() { ++generation; view = null; }
    /** 读取 SDK 商品数，失败或未返回时显示不可用。 */
    void refresh() {
        int request = ++generation;
        if (view == null) return;
        view.accept(-1);
        worker.execute(() -> {
            long loaded;
            try { loaded = source.getAsLong(); } catch (RuntimeException error) { loaded = -1; }
            long result = loaded;
            ui.execute(() -> { if (view != null && request == generation) view.accept(result); });
        });
    }
}
