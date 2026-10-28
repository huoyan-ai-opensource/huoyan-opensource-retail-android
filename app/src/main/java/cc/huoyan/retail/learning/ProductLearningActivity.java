package cc.huoyan.retail.learning;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.common.ui.ToastEx;
import java.util.List;
import java.util.concurrent.Executors;

/** 单商品学习工作页，商品检索与切换由独立弹窗处理。 */
public class ProductLearningActivity extends StudyActivity implements ProductLearningPresenter.View {
    /** 从商品管理传入的学习目标 PLU。 */
    public static final String EXTRA_GOODS_PLU = "learningGoodsPlu";
    /** 页面重建时尚未完成选择的商品。 */
    private String initialPlu;
    /** 商品选择与历史图片分页的协调者。 */
    private ProductLearningPresenter presenter;
    /** 主线程回调队列，销毁时清理。 */
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean screenReady;
    private RecyclerView pictures;
    private GridLayoutManager pictureGrid;

    @Override protected int initContentView() { return R.layout.activity_product_learning; }
    @Override protected StudyPicAdapter createPicAdapter() { return new LearningPictureAdapter(); }

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        presenter = new ProductLearningPresenter(this, Executors.newSingleThreadExecutor(),
                Executors.newSingleThreadExecutor(), ui::post, new LearningPageSource());
        initialPlu = state == null ? getIntent().getStringExtra(EXTRA_GOODS_PLU)
                : state.getString(EXTRA_GOODS_PLU);
        pictures = findViewById(R.id.picRecycler);
        pictureGrid = new GridLayoutManager(this, 3);
        pictures.setLayoutManager(pictureGrid);
        pictures.setItemAnimator(null);
        pictures.setItemViewCacheSize(0);
        pictureGrid.setItemPrefetchEnabled(false);
        pictures.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view, int dx, int dy) {
                if (dy > 0) loadMoreImagesIfNeeded();
            }
        });
        findViewById(R.id.learningMoreImages).setOnClickListener(v -> presenter.loadNext());
        pictures.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int available = r - l - pictures.getPaddingLeft() - pictures.getPaddingRight();
            int spans = Math.max(1, available / Math.round(96 * getResources().getDisplayMetrics().density));
            if (spans != pictureGrid.getSpanCount()) pictureGrid.setSpanCount(spans);
            if (r - l != or - ol || b - t != ob - ot) pictures.post(this::loadMoreImagesIfNeeded);
        });
        studyPicAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { updateEmpty(); }
            @Override public void onItemRangeInserted(int start, int count) { updateEmpty(); }
            @Override public void onItemRangeRemoved(int start, int count) { updateEmpty(); }
        });
        getSupportFragmentManager().setFragmentResultListener(GoodsSearchDialog.RESULT, this,
                (key, result) -> presenter.select(result.getString(EXTRA_GOODS_PLU)));
        findViewById(R.id.learningSwitchGoods).setOnClickListener(v -> openGoodsSearch());
        findViewById(R.id.learningEmpty).setOnClickListener(v -> {
            if (mSelectedGoods == null) openGoodsSearch();
            else if (presenter.failed()) presenter.loadNext();
        });
        if (state == null && cc.huoyan.retail.learning.LearningGuideDialog.shouldShowAutomatically(this)) {
            new cc.huoyan.retail.learning.LearningGuideDialog()
                    .showNow(getSupportFragmentManager(), "learningGuide");
        }
        screenReady = true;
        updateEmpty();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mSelectedGoods == null && initialPlu != null) presenter.select(initialPlu);
        else if (presenter.failed()) presenter.loadNext();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(EXTRA_GOODS_PLU, mSelectedGoods == null ? initialPlu : mSelectedGoods.getPlu());
        super.onSaveInstanceState(state);
    }

    private void openGoodsSearch() {
        if (getSupportFragmentManager().findFragmentByTag("goodsSearch") == null) {
            GoodsSearchDialog.create(mSelectedGoods == null ? null : mSelectedGoods.getPlu())
                    .show(getSupportFragmentManager(), "goodsSearch");
        }
    }

    /** 把查询结果交给共享学习会话；清除已消费的恢复参数。 */
    @Override public void selected(GoodsEntity goods) {
        initialPlu = null;
        if (goods == null) ToastEx.createToast(this, getString(R.string.goods_search_empty));
        else selectLearningGoods(goods);
    }
    /** 将学习会话切换同步给分页协调者。 */
    @Override protected void onSelectedGoodsChanged() { presenter.changedGoods(mSelectedGoods); }
    /** 保存后固定图片状态，避免历史查询回写未保存标记。 */
    @Override protected void onLearningSaved() { presenter.saved(); }
    /** 删除完成后更新分页屏蔽记录。 */
    @Override protected void onLearningPictureRemoved(StudyPicEntity picture) { presenter.removed(picture); }

    private void loadMoreImagesIfNeeded() {
        if (!screenReady || !presenter.canAutoLoad()) return;
        int lastVisible = pictureGrid.findLastVisibleItemPosition();
        if (!pictures.canScrollVertically(1)
                || lastVisible >= studyPicAdapter.getItemCount() - pictureGrid.getSpanCount() - 1) {
            presenter.loadNext();
        }
    }

    /** 将相机学习结果交给 Presenter 合并并重建分页。 */
    @Override protected void showCapturedPictures(List<StudyPicEntity> pictures) { presenter.captured(pictures); }
    /** 共享会话保留图片所有权，Presenter 只读取并构造替换列表。 */
    @Override public List<StudyPicEntity> pictures() { return mStudyPicList; }
    /** 展示合并结果；新拍图片回到列表顶部。 */
    @Override public void showPictures(List<StudyPicEntity> pictures, boolean captured) {
        showPictureList(pictures);
        if (captured) this.pictures.scrollToPosition(0);
    }
    /** 将分页状态映射为控件文案。 */
    @Override public void stateChanged() { updateEmpty(); }
    /** 保留测试平台依赖的耗时日志，并在布局后检查是否需要继续加载。 */
    @Override public void pageLoaded(boolean failed, long elapsedMillis, int page, int count) {
        if (!failed) pictures.post(this::loadMoreImagesIfNeeded);
        if (cc.huoyan.retail.BuildConfig.DEBUG) android.util.Log.d("ProductLearning",
                "[Biz] learning_images_ms=" + elapsedMillis + " page=" + page + " count=" + count);
    }

    private void showPictureList(List<StudyPicEntity> updated) {
        mStudyPicList.clear();
        mStudyPicList.addAll(updated);
        ((LearningPictureAdapter) studyPicAdapter).submitPictures(updated);
        updateEmpty();
    }

    @Override protected void onLearningDataChanged(GoodsEntity goods, boolean captured) {
        if (!captured) presenter.reload();
    }

    private void updateEmpty() {
        if (!screenReady) return;
        boolean empty = studyPicAdapter.getData().isEmpty();
        findViewById(R.id.learningEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
        TextView more = findViewById(R.id.learningMoreImages);
        more.setVisibility(!empty && (presenter.loading() || presenter.failed() || presenter.hasMore()) ? View.VISIBLE : View.GONE);
        more.setText(presenter.loading() ? R.string.learning_loading_more
                : presenter.failed() ? R.string.learning_more_retry : R.string.learning_load_more);
        more.setEnabled(!presenter.loading());
        ((TextView)findViewById(R.id.learningEmptyText)).setText(mSelectedGoods == null
                ? R.string.learning_select : presenter.loading() ? R.string.learning_loading
                : presenter.failed() ? R.string.learning_retry : R.string.learning_empty);
    }

    @Override protected void onDestroy() {
        presenter.detach();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
