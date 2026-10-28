package cc.huoyan.retail.learning;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.GoodsSearchIndex;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** 学习商品选择器的索引加载、百毫秒防抖和过期查询过滤。 */
final class GoodsSearchPresenter {
    /** 页面负责结果列表及本地化状态。 */
    interface View {
        /** 首次索引加载中。 */
        void loading();
        /** 展示查询结果，包括空列表。 */
        void results(List<GoodsEntity> goods);
        /** 加载失败仍保留输入内容。 */
        void failed(String reason);
    }
    /** 当前选择器视图。 */
    private View view;
    /** 索引和搜索共用串行工作队列。 */
    private final ScheduledExecutorService worker;
    /** 主线程结果交付器。 */
    private final Executor ui;
    /** 商品列表读取端口。 */
    private final Supplier<List<GoodsEntity>> source;
    /** 加载后在主线程发布的只读索引。 */
    private GoodsSearchIndex index;
    /** 最新输入，索引尚未加载时也保留。 */
    private String query = "";
    /** 搜索代次，防止较慢结果覆盖新输入。 */
    private int generation;
    /** 尚未开始的防抖任务。 */
    private Future<?> pending;
    /** 注入数据源及独立于 Dialog 的调度器。 */
    GoodsSearchPresenter(View view, Supplier<List<GoodsEntity>> source, ScheduledExecutorService worker, Executor ui) {
        this.view = view; this.source = source; this.worker = worker; this.ui = ui;
    }
    /** 构建索引后按当时最新输入查询，而非启动时输入。 */
    void load(String initialQuery) {
        query = initialQuery; view.loading();
        worker.execute(() -> {
            try {
                GoodsSearchIndex loaded = new GoodsSearchIndex(source.get());
                ui.execute(() -> { if (view != null) { index = loaded; search(query, true); } });
            } catch (RuntimeException error) {
                ui.execute(() -> { if (view != null) view.failed(error.getMessage()); });
            }
        });
    }
    /** 普通输入防抖，完成按钮立即执行；加载期间只记住输入。 */
    void search(String query, boolean immediate) {
        this.query = query;
        int request = ++generation;
        if (pending != null) pending.cancel(false);
        if (index == null || view == null) return;
        GoodsSearchIndex snapshot = index;
        pending = worker.schedule(() -> {
            List<GoodsEntity> matches = snapshot.search(query);
            ui.execute(() -> { if (view != null && request == generation) view.results(matches); });
        }, immediate ? 0 : 100, TimeUnit.MILLISECONDS);
    }
    /** 视图销毁时撤销防抖任务及所有晚到回执。 */
    void detach() { ++generation; view = null; if (pending != null) pending.cancel(false); }
}
