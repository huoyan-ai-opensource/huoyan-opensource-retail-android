package cc.huoyan.retail.learning;

import cc.huoyan.retail.data.GoodsEntity;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** 学习页商品选择与历史样本分页；相机和 SDK 拍照学习仍由 StudyActivity 管理。 */
final class ProductLearningPresenter {
    /** 页面对共享学习会话和图片展示的适配。 */
    interface View {
        /** 返回当前展示列表；Presenter 合并时不会原地修改该列表。 */
        List<StudyPicEntity> pictures();
        /** 接受查到的商品；null 对应查无资料的提示。 */
        void selected(GoodsEntity goods);
        /** 替换展示列表，captured 时回到顶部。 */
        void showPictures(List<StudyPicEntity> pictures, boolean captured);
        /** 更新加载中、重试和空态控件。 */
        void stateChanged();
        /** 记录读取耗时，成功后检查视口是否还需要下一页。 */
        void pageLoaded(boolean failed, long elapsedMillis, int page, int count);
    }
    /** 后台商品查找及 SDK 分页边界。 */
    interface Data {
        /** 按 PLU 查询 SDK 商品，找不到返回 null。 */
        GoodsEntity findGoods(String plu);
        /** page 为 0 时定位最新页；current 用于长查询协作取消。 */
        Page load(String plu, int page, BooleanSupplier current);
    }
    /** 一页历史样本及 SDK 游标快照。 */
    static final class Page {
        /** 本页图片，尚未与本地拍摄、删除记录合并。 */
        final List<StudyPicEntity> pictures;
        /** SDK 当前页码；无有效类别时为 0。 */
        final int current;
        /** SDK 样本总数，0 表示库已清空。 */
        final long total;
        /** 固定 SDK 回执用于主线程合并。 */
        Page(List<StudyPicEntity> pictures, int current, long total) {
            this.pictures = pictures; this.current = current; this.total = total;
        }
    }
    /** 页面销毁时清空，所有访问只在主线程。 */
    private View view;
    /** 商品选择使用独立线程，避免被历史图片下载阻塞。 */
    private final ExecutorService queries;
    /** 图片后台串行队列。 */
    private final ExecutorService imageQueries;
    /** 主线程投递器，不捕获 Activity。 */
    private final Executor ui;
    /** 商品与样本数据读取。 */
    private final Data data;
    /** 当前图片查询句柄，切换商品或页边界变化时取消。 */
    private Future<?> imageQuery;
    /** 已点击保存的 UID，防止迟到历史查询恢复未保存标记。 */
    private final Set<Long> savedPictureUids = new HashSet<>();
    /** 已删除 UID，防止迟到历史查询重新插入图片。 */
    private final Set<Long> removedPictureUids = new HashSet<>();
    /** 当前商品 PLU，由学习会话切换回调同步。 */
    private String selectedPlu;
    /** 下一页游标：0 定位最新，-1 已到最旧页。 */
    private int nextImagePage;
    /** 商品选择版本，快速切换只接受最后一次结果。 */
    private int selectionRequest;
    /** 图片查询版本，后台协作取消需要跨线程可见。 */
    private volatile int imagesRequest;
    /** 是否正在读取一页，防止滚动重复提交。 */
    private boolean loading;
    /** 最近一页失败时停止自动追加，等待显式重试。 */
    private boolean failed;
    /** 退出标记，同时供后台协作取消判断。 */
    private volatile boolean destroyed;

    /** 注入两条独立队列，销毁时由本对象取消并关闭。 */
    ProductLearningPresenter(View view, ExecutorService queries, ExecutorService imageQueries, Executor ui, Data data) {
        this.view = view; this.queries = queries; this.imageQueries = imageQueries; this.ui = ui; this.data = data;
    }
    /** 返回分页是否在途，用于按钮和空态渲染。 */
    boolean loading() { return loading; }
    /** 返回最近一次失败状态，用于重试入口。 */
    boolean failed() { return failed; }
    /** 返回是否还有历史页，加载中和失败时仍保留游标。 */
    boolean hasMore() { return nextImagePage >= 0; }
    /** 视口接近尾部时能否自动请求下一页。 */
    boolean canAutoLoad() { return !destroyed && selectedPlu != null && !failed && !loading && hasMore(); }
    /** 异步查找商品，只交付最后一次选择结果。 */
    void select(String plu) {
        if (destroyed || plu == null || plu.isEmpty()) return;
        int request = ++selectionRequest;
        queries.execute(() -> {
            GoodsEntity target = data.findGoods(plu);
            ui.execute(() -> { if (!destroyed && request == selectionRequest) view.selected(target); });
        });
    }
    /** 学习会话已切换商品；清除上一商品的保存及删除记录。 */
    void changedGoods(GoodsEntity goods) {
        selectedPlu = goods == null ? null : goods.getPlu();
        savedPictureUids.clear(); removedPictureUids.clear(); reload();
    }
    /** 保存当前展示图片的状态，供在途历史结果合并。 */
    void saved() { if (view != null) for (StudyPicEntity picture : view.pictures()) savedPictureUids.add(picture.getUid()); }
    /** 记录当前商品的删除并重建因删除而变化的页边界。 */
    void removed(StudyPicEntity picture) {
        if (selectedPlu != null && selectedPlu.equals(picture.getClazzName())) {
            removedPictureUids.add(picture.getUid()); reload();
        }
    }
    /** 新拍图片置顶，允许 UID 复用，同时使拍照前查询失效。 */
    void captured(List<StudyPicEntity> pictures) {
        if (view == null) return;
        view.showPictures(LearningPictureUpdates.prepend(view.pictures(), pictures, savedPictureUids, removedPictureUids), true);
        reload();
    }
    /** 从最新页重新读取，保留已展示图片直到合并新结果。 */
    void reload() {
        ++imagesRequest;
        if (imageQuery != null) imageQuery.cancel(true);
        nextImagePage = 0; loading = false; failed = false; loadNext();
    }
    /** 读取一页；手动重试允许覆盖 failed，自动滚动须先检查 canAutoLoad。 */
    void loadNext() {
        if (destroyed || selectedPlu == null || loading || !hasMore()) return;
        int request = imagesRequest;
        int page = nextImagePage;
        String plu = selectedPlu;
        loading = true; failed = false; view.stateChanged();
        imageQuery = imageQueries.submit(() -> {
            long startedAt = System.nanoTime();
            Page loaded = null;
            boolean failure = false;
            try { loaded = data.load(plu, page, () -> !destroyed && request == imagesRequest); }
            catch (CancellationException ignored) { return; }
            catch (RuntimeException error) { failure = true; }
            Page receipt = loaded;
            boolean error = failure;
            ui.execute(() -> {
                if (destroyed || request != imagesRequest || !plu.equals(selectedPlu)) return;
                loading = false; failed = error;
                if (!error) {
                    nextImagePage = receipt.current <= 1 || receipt.total == 0 ? -1 : receipt.current - 1;
                    view.showPictures(receipt.total == 0
                            ? LearningPictureUpdates.mergeHistory(view.pictures(), receipt.pictures, savedPictureUids, removedPictureUids)
                            : LearningPictureUpdates.mergePage(view.pictures(), receipt.pictures, savedPictureUids, removedPictureUids), false);
                }
                view.stateChanged();
                view.pageLoaded(error, (System.nanoTime() - startedAt) / 1000000,
                        receipt == null ? page : receipt.current, receipt == null ? 0 : receipt.pictures.size());
            });
        });
    }
    /** 销毁学习页，取消查询并永久断开 View 引用。 */
    void detach() {
        destroyed = true; ++selectionRequest; ++imagesRequest; view = null;
        if (imageQuery != null) imageQuery.cancel(true);
        queries.shutdownNow(); imageQueries.shutdownNow();
    }
}
