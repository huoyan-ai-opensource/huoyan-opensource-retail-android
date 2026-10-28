package cc.huoyan.retail.goods;
import cc.huoyan.retail.sdk.SdkChannelManager;

import cc.huoyan.retail.learning.ProductLearningActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.BaseActivity;
import cc.huoyan.retail.common.ui.SelectOptionDialog;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.sdk.client.session.SdkStartupController;
import cc.huoyan.retail.common.ui.DialogUtil;
import cc.huoyan.retail.common.ui.AlertConfirmDialogListener;
import cc.huoyan.retail.common.LocalizedText;
import cc.huoyan.retail.common.ui.ToastEx;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 独立商品资料管理；只有进入学习后才打开相机。 */
public final class GoodsManagementActivity extends BaseActivity implements GoodsManagementPresenter.View {
    private final SdkStartupController startup = SdkChannelManager.getInstance().newStartupController();
    private final GoodsAdapter adapter = new GoodsAdapter();
    /** 搜索、筛选、分页及资料写入的唯一协调者。 */
    private GoodsManagementPresenter presenter;
    private EditText query;
    private RecyclerView list;
    private Parcelable restoredScroll;
    private boolean appendPosted;
    /** 当前前台会话的连接状态；连接建立后读取目录，不等待识别模型。 */
    private boolean catalogConnected;

    @Override protected int initContentView() { return R.layout.activity_goods_management; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        presenter = new androidx.lifecycle.ViewModelProvider(this).get(GoodsManagementModel.class).presenter;
        query = findViewById(R.id.managementSearch);
        if (state != null) {
            query.setText(state.getString("query", ""));
            restoredScroll = state.getParcelable("scroll");
            presenter.restore(query.getText().toString(), state.getInt("loadedCount", GoodsManagementPresenter.PAGE_SIZE), state.getInt("sort"), state.getInt("pricing"));
        }
        list = findViewById(R.id.managementList);
        list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter); list.setItemAnimator(null);
        findViewById(R.id.navBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.navTitle)).setText(R.string.goods_management_title);
        findViewById(R.id.managementAdd).setOnClickListener(v -> edit(null));
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recycler, int dx, int dy) {
                LinearLayoutManager layout = (LinearLayoutManager) recycler.getLayoutManager();
                if (dy < 0 || appendPosted || !presenter.hasMore()
                        || layout.findLastVisibleItemPosition() < adapter.rows.size() - 5) return;
                appendPosted = true;
                int version = presenter.filterVersion();
                recycler.post(() -> {
                    appendPosted = false;
                    presenter.append(version);
                });
            }
        });
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { restoredScroll = null; presenter.search(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        setupFilter(true);
        setupFilter(false);
        restoreFormListener();
    }

    private void setupFilter(boolean sorting) {
        int optionsId = sorting ? R.array.goods_management_sort_options : R.array.goods_management_pricing_options;
        String[] options = getResources().getStringArray(optionsId);
        String key = sorting ? "managementSort" : "managementPricing";
        updateFilterLabel(sorting, options);
        SelectOptionDialog.listen(getSupportFragmentManager(), this, key,
                (position, value) -> selectFilter(sorting, position));
        findViewById(sorting ? R.id.managementSort : R.id.managementPricing).setOnClickListener(v ->
                SelectOptionDialog.show(getSupportFragmentManager(), key,
                        getString(sorting ? R.string.goods_management_sort : R.string.goods_management_pricing),
                        java.util.Arrays.asList(options), options[presenter.selection(sorting)]));
    }

    /** 更换排序或计价类型时回到第一批，继续保留搜索词。 */
    void selectFilter(boolean sorting, int position) {
        String[] options = getResources().getStringArray(sorting
                ? R.array.goods_management_sort_options : R.array.goods_management_pricing_options);
        if (position < 0 || position >= options.length) return;
        restoredScroll = null;
        presenter.selectFilter(sorting, position);
        updateFilterLabel(sorting, options);
    }

    private void updateFilterLabel(boolean sorting, String[] options) {
        TextView label = findViewById(sorting ? R.id.managementSort : R.id.managementPricing);
        boolean compact = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        String selected = options[presenter.selection(sorting)];
        label.setText(compact ? getString(sorting ? R.string.goods_management_sort
                : R.string.goods_management_filter) : getString(sorting
                ? R.string.goods_management_sort_value : R.string.goods_management_pricing_value, selected));
        label.setSelected(presenter.selection(sorting) != 0);
        label.setContentDescription(getString(sorting ? R.string.goods_management_sort_value
                : R.string.goods_management_pricing_value, selected));
    }

    private void restoreFormListener() {
        AddGoodsDialog dialog = (AddGoodsDialog) getSupportFragmentManager().findFragmentByTag("managedGoodsForm");
        if (dialog != null) dialog.setListener(goods -> save(goods, goods.getGoodsId() != null));
    }

    @Override protected void onResume() {
        super.onResume();
        presenter.attach(this);
        presenter.deliverResult();
        renderLoading();
        catalogConnected = SdkChannelManager.getInstance().getClient().isConnected();
        if (catalogConnected) presenter.refresh();
        startup.start((code, status, ready) -> {
            boolean connected = SdkChannelManager.getInstance().getClient().isConnected();
            if (connected && (!catalogConnected || ready)) presenter.refresh();
            catalogConnected = connected;
        });
    }
    @Override protected void onPause() { presenter.detach(); dismissLoading(); startup.stop(); super.onPause(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putInt("sort", presenter.selection(true)); state.putInt("pricing", presenter.selection(false));
        state.putString("query", query.getText().toString()); state.putInt("loadedCount", presenter.loadedCount());
        state.putParcelable("scroll", list.getLayoutManager().onSaveInstanceState()); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { presenter.detach(); dismissLoading(); super.onDestroy(); }

    /** 渲染 Presenter 给出的可见商品，滚动状态仅归页面所有。 */
    @Override public void showGoods(List<GoodsEntity> rows, boolean resetScroll) {
        Parcelable scroll = restoredScroll != null ? restoredScroll : list.getLayoutManager().onSaveInstanceState();
        adapter.rows.clear();
        adapter.rows.addAll(rows);
        adapter.notifyDataSetChanged();
        if (!adapter.rows.isEmpty()) {
            list.getLayoutManager().onRestoreInstanceState(scroll);
            restoredScroll = null;
        }
        TextView empty = findViewById(R.id.managementEmpty);
        empty.setText(query.length() == 0 ? R.string.goods_no_items : R.string.goods_search_empty);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.managementAdd).setEnabled(!presenter.busy());
        renderLoading();
        if (resetScroll) list.scrollToPosition(0);
    }
    private void edit(GoodsEntity goods) {
        if (presenter.busy() || getSupportFragmentManager().findFragmentByTag("managedGoodsForm") != null) return;
        AddGoodsDialog form = new AddGoodsDialog();
        if (goods != null) form.setEditingGoods(goods);
        form.setListener(value -> save(value, goods != null));
        form.show(getSupportFragmentManager(), "managedGoodsForm");
    }
    private void save(GoodsEntity goods, boolean editing) { presenter.save(goods, editing); }
    private void delete(GoodsEntity goods) {
        if (presenter.busy()) return;
        DialogUtil.showAlertDialog(getSupportFragmentManager(), getString(R.string.learning_delete),
                getString(R.string.goods_delete_named, goods.getName(), goods.getPlu()),
                getString(R.string.button_confirm), getString(R.string.button_cancel),
                new AlertConfirmDialogListener() {
                    @Override public void onConfirm() { presenter.delete(goods); }
                    @Override public void onCancel() { }
                });
    }
    /** 追加本地分页而不打断当前滚动。 */
    @Override public void appendGoods(List<GoodsEntity> rows) {
        int start = adapter.rows.size();
        adapter.rows.addAll(rows); adapter.notifyItemRangeInserted(start, rows.size());
    }
    /** 按在途 SDK 写入状态恢复项目等待弹窗，进度不回显在商品列表。 */
    private void renderLoading() {
        if (getSupportFragmentManager().isStateSaved()) return;
        cc.huoyan.retail.common.ui.LoadingDialog dialog = (cc.huoyan.retail.common.ui.LoadingDialog)
                getSupportFragmentManager().findFragmentByTag("goodsWriting");
        if (!presenter.busy()) { dismissLoading(); return; }
        if (dialog == null) {
            dialog = new cc.huoyan.retail.common.ui.LoadingDialog(getString(R.string.goods_operation_running));
            dialog.setCancelable(false);
            dialog.showNow(getSupportFragmentManager(), "goodsWriting");
        }
    }
    /** 暂停或结束操作时关闭遮罩，恢复页面时按 Presenter 状态重建。 */
    private void dismissLoading() {
        cc.huoyan.retail.common.ui.LoadingDialog dialog = (cc.huoyan.retail.common.ui.LoadingDialog)
                getSupportFragmentManager().findFragmentByTag("goodsWriting");
        if (dialog != null) dialog.dismissAllowingStateLoss();
    }
    /** 写入完成后展示成功提示。 */
    @Override public void saved() { dismissLoading(); ToastEx.showSuccessToast(this, getString(R.string.toast_save_success)); }
    /** 展示后台资料操作的失败原因。 */
    @Override public void error(RuntimeException error) {
        if (!presenter.busy()) dismissLoading();
        if (!isDestroyed()) ToastEx.createToast(this, getString(R.string.goods_operation_failed, error.getMessage()));
    }
    private final class GoodsAdapter extends RecyclerView.Adapter<GoodsHolder> {
        final List<GoodsEntity> rows = new ArrayList<>();
        @NonNull @Override public GoodsHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new GoodsHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_managed_goods, parent, false));
        }
        @Override public int getItemCount() { return rows.size(); }
        @Override public void onBindViewHolder(@NonNull GoodsHolder holder, int position) {
            GoodsEntity goods = rows.get(position);
            ((TextView) holder.itemView.findViewById(R.id.manageName)).setText(goods.getName());
            ((TextView) holder.itemView.findViewById(R.id.managePlu)).setText(goods.getPlu());
            ((TextView) holder.itemView.findViewById(R.id.managePrice)).setText(getString(R.string.amount_currency_format, BigDecimal.valueOf(goods.getPrice(), 2).toPlainString())
                    + " / " + LocalizedText.unit(GoodsManagementActivity.this, goods.getUnit()));
            Long count = presenter.learnedCount(goods.getPlu());
            ((TextView) holder.itemView.findViewById(R.id.manageLearned)).setText(
                    count == null ? "—" : String.valueOf(count));
            holder.itemView.findViewById(R.id.manageLearn).setOnClickListener(v -> {
                if (!presenter.busy()) startActivity(new Intent(GoodsManagementActivity.this, ProductLearningActivity.class)
                        .putExtra(ProductLearningActivity.EXTRA_GOODS_PLU, goods.getPlu()));
            });
            holder.itemView.findViewById(R.id.manageEdit).setOnClickListener(v -> edit(goods));
            holder.itemView.findViewById(R.id.manageDelete).setOnClickListener(v -> delete(goods));
        }
    }
    private static final class GoodsHolder extends RecyclerView.ViewHolder { GoodsHolder(View view) { super(view); } }
}
