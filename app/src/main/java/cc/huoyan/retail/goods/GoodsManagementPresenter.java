package cc.huoyan.retail.goods;

import cc.huoyan.retail.data.GoodsEntity;
import java.util.*;
import java.util.concurrent.Executor;

/** 商品管理交互状态；搜索、筛选、分页与写入互斥均在主线程协调。 */
final class GoodsManagementPresenter {
    /** 一次追加的商品数量。 */
    static final int PAGE_SIZE = 24;
    /** 页面只负责列表、滚动位置和反馈。 */
    interface View {
        /** 替换可见列表；resetScroll 表示用户改变了筛选条件。 */
        void showGoods(List<GoodsEntity> rows, boolean resetScroll);
        /** 向列表尾部追加一页。 */
        void appendGoods(List<GoodsEntity> rows);
        /** 告知保存或删除成功。 */
        void saved();
        /** 展示读取或写入失败原因。 */
        void error(RuntimeException error);
    }
    /** 数据访问边界，调用均在后台执行。 */
    interface Data {
        /** 读取全部商品以构建搜索索引。 */
        List<GoodsEntity> goods();
        /** 按 PLU 返回 SDK 已学习样本数量；未知商品不填入结果。 */
        Map<String, Long> learnedCounts(List<GoodsEntity> goods);
        /** 新增或更新资料，editing 决定重复 PLU 校验语义。 */
        void save(GoodsEntity goods, boolean editing);
        /** 删除商品及其关联学习资料。 */
        void delete(GoodsEntity goods);
    }
    /** 销毁后释放的页面引用。 */
    private View view;
    /** 串行工作队列；宿主关闭时允许已提交写入完成。 */
    private final Executor worker;
    /** 不捕获 Activity 的主线程执行器。 */
    private final Executor ui;
    /** 商品和 SDK 数据访问。 */
    private final Data data;
    /** 当前索引，由最新有效查询整体替换。 */
    private GoodsSearchIndex index = new GoodsSearchIndex(Collections.emptyList());
    /** 全量筛选结果，分页只改变展示数量。 */
    private List<GoodsEntity> filtered = Collections.emptyList();
    /** 当前学习数量快照。 */
    private final Map<String, Long> learnedCounts = new HashMap<>();
    /** 当前搜索词，页面恢复时可注入。 */
    private String query = "";
    /** 用户已经展开的数量，刷新后保留。 */
    private int loadedCount = PAGE_SIZE;
    /** 排序选项下标，始终限制在枚举范围内。 */
    private int sortPosition;
    /** 计价筛选下标，始终限制在枚举范围内。 */
    private int pricingPosition;
    /** 筛选版本，防止滚动任务追加旧结果。 */
    private int filterVersion;
    /** 读取代次，暂停及重新查询后旧结果失效。 */
    private int request;
    /** 写入互斥状态，不随暂停清除。 */
    private boolean busy;

    /** 已完成但尚未由当前视图消费的写入回执。 */
    private boolean pendingResult;
    /** 待消费回执中的错误；null 表示 SDK 明确成功。 */
    private RuntimeException pendingFailure;

    /** 绑定页面与后台数据访问；构造时不启动查询。 */
    GoodsManagementPresenter(View view, Executor worker, Executor ui, Data data) {
        this.view = view; this.worker = worker; this.ui = ui; this.data = data;
    }
    /** 页面恢复时绑定新视图；在途写入和结果跨配置变化保留。 */
    void attach(View view) { this.view = view; }
    /** 页面具备提示条件后只消费一次 SDK 写入结果。 */
    void deliverResult() {
        if (view == null || !pendingResult) return;
        RuntimeException failure = pendingFailure;
        pendingResult = false; pendingFailure = null;
        if (failure == null) view.saved(); else view.error(failure);
    }
    /** 恢复筛选及已展开页数，等待首次刷新后展示。 */
    void restore(String query, int count, int sort, int pricing) {
        this.query = query;
        loadedCount = Math.max(PAGE_SIZE, count);
        sortPosition = Math.max(0, Math.min(sort, ManagedGoodsFilter.Order.values().length - 1));
        pricingPosition = Math.max(0, Math.min(pricing, ManagedGoodsFilter.Pricing.values().length - 1));
    }
    /** 返回已展开数量供实例状态保存。 */
    int loadedCount() { return loadedCount; }
    /** 返回排序或计价选项供标签渲染及保存。 */
    int selection(boolean sorting) { return sorting ? sortPosition : pricingPosition; }
    /** 返回写入互斥状态，控制编辑和导航入口。 */
    boolean busy() { return busy; }
    /** 返回学习数；null 表示 SDK 尚未提供结果。 */
    Long learnedCount(String plu) { return learnedCounts.get(plu); }
    /** 返回当前筛选版本供延迟滚动任务携带。 */
    int filterVersion() { return filterVersion; }
    /** 是否还有本地筛选结果未展示。 */
    boolean hasMore() { return loadedCount < filtered.size(); }
    /** 改变搜索词并回到第一批商品。 */
    void search(String query) { this.query = query; loadedCount = PAGE_SIZE; filter(true); }
    /** 更换合法筛选选项；无效下标不会改变状态。 */
    void selectFilter(boolean sorting, int position) {
        int size = sorting ? ManagedGoodsFilter.Order.values().length : ManagedGoodsFilter.Pricing.values().length;
        if (position < 0 || position >= size) return;
        if (sorting) sortPosition = position; else pricingPosition = position;
        loadedCount = PAGE_SIZE; filter(true);
    }
    /** 仅追加当前版本的一页，过期滚动任务无副作用。 */
    void append(int version) {
        if (view == null || version != filterVersion || !hasMore()) return;
        int start = Math.min(loadedCount, filtered.size());
        int end = Math.min(filtered.size(), start + PAGE_SIZE);
        loadedCount = end;
        view.appendGoods(new ArrayList<>(filtered.subList(start, end)));
    }
    /** 分两阶段展示SDK 商品资料及 SDK 学习数，保留当前页数。 */
    void refresh() {
        if (view == null) return;
        int version = ++request;
        worker.execute(() -> {
            try {
                List<GoodsEntity> goods = data.goods();
                GoodsSearchIndex loaded = new GoodsSearchIndex(goods);
                ui.execute(() -> {
                    if (view == null || version != request) return;
                    index = loaded; learnedCounts.clear(); filter(false);
                });
                Map<String, Long> counts = data.learnedCounts(goods);
                ui.execute(() -> {
                    if (view == null || version != request) return;
                    learnedCounts.clear(); learnedCounts.putAll(counts); filter(false);
                });
            } catch (RuntimeException error) {
                ui.execute(() -> { if (view != null && version == request) view.error(error); });
            }
        });
    }
    /** 暂停只作废读取，不取消已经提交的保存或删除。 */
    void pause() { ++request; }
    /** 销毁时释放页面，已提交写入仍可在后台完成。 */
    void detach() { pause(); view = null; }
    /** 提交新增或编辑，快速重复点击只接受一次。 */
    void save(GoodsEntity goods, boolean editing) { mutate(() -> data.save(goods, editing)); }
    /** 删除已确认商品，和保存共享互斥状态。 */
    void delete(GoodsEntity goods) { mutate(() -> data.delete(goods)); }
    /** 重新计算排序筛选，并使已排队追加任务失效。 */
    private void filter(boolean resetScroll) {
        ++filterVersion;
        filtered = ManagedGoodsFilter.apply(index.search(query), ManagedGoodsFilter.Order.values()[sortPosition],
                ManagedGoodsFilter.Pricing.values()[pricingPosition], learnedCounts);
        render(resetScroll);
    }
    /** 复制可见结果，避免 Adapter 改写 Presenter 内部列表。 */
    private void render(boolean resetScroll) {
        if (view != null) view.showGoods(new ArrayList<>(filtered.subList(0, Math.min(filtered.size(), loadedCount))), resetScroll);
    }
    /** 串行执行写入，主线程恢复按钮并刷新最新资料。 */
    private void mutate(Runnable action) {
        if (view == null || busy) return;
        busy = true; render(false);
        worker.execute(() -> {
            RuntimeException failure = null;
            try { action.run(); } catch (RuntimeException error) { failure = error; }
            RuntimeException result = failure;
            ui.execute(() -> {
                busy = false; pendingResult = true; pendingFailure = result;
                deliverResult();
                refresh();
            });
        });
    }
}
