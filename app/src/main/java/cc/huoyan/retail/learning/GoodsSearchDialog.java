package cc.huoyan.retail.learning;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import cc.huoyan.retail.common.LocalizedText;
import androidx.recyclerview.widget.RecyclerView;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.SearchKeyboard;
import cc.huoyan.retail.common.ui.SearchDialogWindow;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.SdkGoodsSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** 学习目标选择器；默认隐藏键盘，点击输入框启用字母数字键盘，中文输入由用户显式开启。 */
public final class GoodsSearchDialog extends DialogFragment implements GoodsSearchPresenter.View {
    /** 向学习页发布所选 PLU 的 FragmentResult 键。 */
    public static final String RESULT = "learningGoodsSearchResult";
    /** 后台查询队列，随当前视图创建和释放。 */
    private java.util.concurrent.ScheduledExecutorService worker;
    /** 当前视图的索引和搜索协调器。 */
    private GoodsSearchPresenter presenter;
    /** 主线程投递器，不通过闭包保留页面。 */
    private final Handler ui = new Handler(Looper.getMainLooper());
    /** 当前呈现的查询结果，仅在主线程替换。 */
    private final List<GoodsEntity> matches = new ArrayList<>();
    /** 搜索输入框，重建时保留尚未提交的文本。 */
    private EditText query;
    /** 弹窗内容根视图，用于查找键盘和状态控件。 */
    private View content;
    /** 商品候选列表控件。 */
    private RecyclerView results;
    /** 当前视图共用的搜索键盘控制器。 */
    private SearchKeyboard keyboard;

    /** 创建选择器并标注当前学习商品。 */
    public static GoodsSearchDialog create(String currentPlu) {
        GoodsSearchDialog dialog = new GoodsSearchDialog();
        Bundle args = new Bundle(); args.putString("current", currentPlu); dialog.setArguments(args);
        return dialog;
    }

    /** 重建时保留查询词，初始不唤起系统输入法。 */
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_goods_search, null);
        query = content.findViewById(R.id.searchQuery);
        query.setShowSoftInputOnFocus(false);
        query.setText(state == null ? "" : state.getString("query", ""));
        query.setSelection(query.length());
        content.requestFocus();
        content.findViewById(R.id.searchCancel).setOnClickListener(v -> dismiss());
        results = content.findViewById(R.id.searchResults);
        results.setLayoutManager(new GridLayoutManager(requireContext(),
                getResources().getInteger(R.integer.goods_choice_columns)));
        results.setAdapter(new ResultAdapter()); results.setItemAnimator(null);
        worker = Executors.newSingleThreadScheduledExecutor();
        presenter = new GoodsSearchPresenter(this, () -> SdkGoodsSource.getInstance().getAllGoods(), worker, ui::post);
        query.addTextChangedListener(new TextWatcher() {
            /** 文本变化前无需修改视图，检索统一由变化回调触发。 */
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            /** 将最新输入交给 Presenter 防抖检索。 */
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                presenter.search(s.toString(), false);
            }
            /** 不在编辑完成回调重复检索，避免同一次输入查询两次。 */
            @Override public void afterTextChanged(Editable s) { }
        });
        keyboard = new SearchKeyboard(content, query, content.findViewById(R.id.searchKeyboard),
                content.findViewById(R.id.searchChinese), () -> presenter.search(query.getText().toString(), true));
        presenter.load(query.getText().toString());
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content, new ViewGroup.LayoutParams(-1, -1));
        return dialog;
    }

    /** 限定窗口尺寸并允许中文输入法压缩结果列表。 */
    @Override public void onStart() {
        super.onStart();
        SearchDialogWindow.apply(this);
    }

    /** 索引构建期间显示加载状态。 */
    @Override public void loading() {
        int previousCount = matches.size();
        matches.clear();
        if (previousCount > 0) results.getAdapter().notifyItemRangeRemoved(0, previousCount);
        content.findViewById(R.id.searchEmpty).setVisibility(View.VISIBLE);
        ((TextView) content.findViewById(R.id.searchEmpty)).setText(R.string.goods_loading);
    }
    /** 原子替换结果并滚动到顶部。 */
    @Override public void results(List<GoodsEntity> goods) {
        matches.clear(); matches.addAll(goods);
        results.getAdapter().notifyDataSetChanged(); results.scrollToPosition(0);
        TextView empty = content.findViewById(R.id.searchEmpty);
        empty.setText(R.string.goods_search_empty); empty.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
    }
    /** 加载失败显示原因，不丢失搜索词。 */
    @Override public void failed(String reason) {
        TextView empty = content.findViewById(R.id.searchEmpty);
        empty.setText(getString(R.string.goods_operation_failed, reason)); empty.setVisibility(View.VISIBLE);
    }

    /** 保存尚未选定的搜索词。 */
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        state.putString("query", query.getText().toString()); super.onSaveInstanceState(state);
    }

    /** 关闭选择器时收起系统键盘。 */
    @Override public void onDismiss(@NonNull DialogInterface dialog) {
        if (keyboard != null) keyboard.hide();
        super.onDismiss(dialog);
    }

    /** 释放查询任务与主线程回调。 */
    @Override public void onDestroyView() {
        presenter.detach(); worker.shutdownNow(); ui.removeCallbacksAndMessages(null);
        keyboard = null; super.onDestroyView();
    }

    /** 展示与改品一致的商品宫格，当前学习商品通过底色区分。 */
    private final class ResultAdapter extends RecyclerView.Adapter<ResultHolder> {
        /** 创建共用的三层卡片，名称允许两行，超长内容省略。 */
        @NonNull @Override public ResultHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View card = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_goods_choice, parent, false);
            card.findViewById(R.id.main).setClipToOutline(true);
            card.setFocusable(true);
            card.setClickable(true);
            return new ResultHolder(card);
        }
        /** 返回当前可显示的结果数量。 */
        @Override public int getItemCount() { return matches.size(); }
        /** 绑定名称、PLU、价格/单位，点击后发布所选商品并关闭。 */
        @Override public void onBindViewHolder(@NonNull ResultHolder holder, int position) {
            GoodsEntity goods = matches.get(position);
            boolean current = getArguments() != null && java.util.Objects.equals(goods.getPlu(), getArguments().getString("current"));
            holder.name.setText(goods.getName());
            holder.plu.setText(getString(R.string.goods_plu_prefix) + goods.getPlu());
            holder.price.setText(getString(R.string.goods_choice_price,
                    java.math.BigDecimal.valueOf(goods.getPrice(), 2).toPlainString(),
                    LocalizedText.unit(requireContext(), goods.getUnit())));
            holder.itemView.setSelected(current);
            holder.itemView.findViewById(R.id.main).setSelected(current);
            holder.itemView.setOnClickListener(v -> {
                Bundle result = new Bundle(); result.putString(ProductLearningActivity.EXTRA_GOODS_PLU, goods.getPlu());
                getParentFragmentManager().setFragmentResult(RESULT, result); dismiss();
            });
        }
    }
    /** 缓存商品卡片的三层文本控件，不持有弹窗或查询状态。 */
    private static final class ResultHolder extends RecyclerView.ViewHolder {
        /** 卡片顶部的商品名称。 */
        private final TextView name;
        /** 卡片中部的商品编码。 */
        private final TextView plu;
        /** 卡片底部的价格与计价单位。 */
        private final TextView price;

        /** 在创建卡片时缓存控件，避免绑定时反复查找。 */
        ResultHolder(View view) {
            super(view);
            name = view.findViewById(R.id.pluName);
            plu = view.findViewById(R.id.plu);
            price = view.findViewById(R.id.price);
        }
    }
}
