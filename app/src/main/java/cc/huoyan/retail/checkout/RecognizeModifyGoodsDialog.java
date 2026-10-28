package cc.huoyan.retail.checkout;

import cc.huoyan.retail.common.ui.SearchDialogWindow;
import cc.huoyan.retail.common.ui.SearchKeyboard;
import android.content.DialogInterface;
import android.widget.TextView;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.SdkGoodsSource;
import cc.huoyan.retail.common.ui.WindowUtil;

import java.util.List;

/** 收银改品视图，候选规则和选择状态由 Presenter 管理。 */
public class RecognizeModifyGoodsDialog extends DialogFragment implements ModifyGoodsPresenter.View {
    /** 打开弹窗时的识别结果，用于创建候选查询快照。 */
    private GoodsVo goodsVo;
    /** 由收银宿主校验改品可行性的回调。 */
    private RecognizeModifyGoodsDialogListener listener;
    /** 用户取消改品后恢复收银状态的通知。 */
    private Runnable cancelListener;
    /** 候选列表及高亮的展示适配器。 */
    private RecognizeModifyCandidateAdapter candidateAdapter;
    /** 搜索输入控件，文本变化交给 Presenter。 */
    private EditText editText;
    /** 共用搜索键盘，随当前视图释放。 */
    private SearchKeyboard keyboard;
    /** 搜索无结果提示，随当前视图释放。 */
    private TextView empty;
    /** 当前候选查询及选择状态。 */
    private ModifyGoodsPresenter presenter;
    /** 视图范围内的SDK 商品查询队列。 */
    private java.util.concurrent.ExecutorService worker;
    /** 主线程结果投递器。 */
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    /** 收银宿主的改品确认出口。 */
    public interface RecognizeModifyGoodsDialogListener {
        /** 提交所选商品，返回 true 时关闭弹窗，返回 false 时保留选择供用户调整。 */
        boolean onConfirm(GoodsEntity goods);
    }

    /** 设置打开弹窗时的识别商品及相似项。 */
    public void setGoodsVo(GoodsVo goodsVo) {
        this.goodsVo = goodsVo;
    }

    /** 绑定宿主的改品确认逻辑。 */
    public void setListener(RecognizeModifyGoodsDialogListener listener) {
        this.listener = listener;
    }

    /** 绑定用户取消改品时的恢复逻辑。 */
    public void setCancelListener(Runnable cancelListener) {
        this.cancelListener = cancelListener;
    }

    /** 创建视图范围内的查询协调器并绑定候选交互。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        presenter = new ModifyGoodsPresenter(this, goodsVo, new CandidateSource(), worker, ui::post);
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_recognize_modify_goods, container, false);
        view.setFocusableInTouchMode(true);
        view.requestFocus();

        RecyclerView resultRecycler = view.findViewById(R.id.resultRecycler);
        resultRecycler.setLayoutManager(new GridLayoutManager(requireContext(),
                getResources().getInteger(R.integer.goods_choice_columns)));
        resultRecycler.setNestedScrollingEnabled(false);
        candidateAdapter = new RecognizeModifyCandidateAdapter();
        resultRecycler.setAdapter(candidateAdapter);

        candidateAdapter.setOnItemClickListener((adapter1, view1, position) -> presenter.select(candidateAdapter.getItem(position)));

        ImageView clearText = view.findViewById(R.id.clearText);
        editText = view.findViewById(R.id.editText);
        editText.clearFocus();
        empty = view.findViewById(R.id.searchEmpty);
        keyboard = new SearchKeyboard(view, editText, view.findViewById(R.id.searchKeyboard),
                view.findViewById(R.id.searchChinese), () -> presenter.search(editText.getText().toString()));
        clearText.setOnClickListener(v -> editText.setText(""));
        editText.addTextChangedListener(new TextWatcher() {
            /** 文本变化前无需修改视图，检索统一由变化回调触发。 */
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            /** 同步清空按钮显隐并提交最新搜索词。 */
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearText.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
                presenter.search(editText.getText().toString());
            }

            /** 不在编辑完成回调重复检索，避免同一次输入查询两次。 */
            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        setCancelable(true);
        presenter.search(editText.getText().toString());
        return view;
    }

    /** 使用共用搜索窗口，中文输入法出现时压缩列表，商品点击即提交。 */
    @Override
    public void onStart() {
        super.onStart();
        SearchDialogWindow.apply(this);
    }

    /** 渲染候选与选中态。 */
    @Override public void candidates(List<RecognizeModifyCandidate> candidates, String selectedPlu) {
        candidateAdapter.setSelectedPlu(selectedPlu); candidateAdapter.setList(candidates);
        empty.setText(R.string.goods_search_empty);
        empty.setVisibility(candidates.isEmpty() ? View.VISIBLE : View.GONE);
    }
    /** 响应行点击的选中高亮。 */
    @Override public void selection(String plu) { candidateAdapter.refreshSelection(plu); }
    /** 空选择不能提交。 */
    @Override public void selectionRequired() {
        Toast.makeText(requireContext(), R.string.toast_select_goods, Toast.LENGTH_LONG).show();
    }
    /** 查询异常保留弹窗。 */
    @Override public void failed(String reason) {
        Toast.makeText(requireContext(), getString(R.string.goods_operation_failed, reason), Toast.LENGTH_LONG).show();
    }
    /** 由收银宿主校验改品是否可执行。 */
    @Override public boolean confirm(GoodsEntity goods) { return listener != null && listener.onConfirm(goods); }
    /** 改品被宿主接受后关闭。 */
    @Override public void close() { dismissAllowingStateLoss(); }
    /** 点击遮罩或系统返回时恢复宿主识别状态，不提交任何候选。 */
    @Override public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        if (cancelListener != null) cancelListener.run();
    }
    /** 关闭弹窗时收起系统与自定义键盘。 */
    @Override public void onDismiss(@NonNull DialogInterface dialog) {
        if (keyboard != null) keyboard.hide();
        super.onDismiss(dialog);
    }
    /** 释放候选查询和视图引用。 */
    @Override public void onDestroyView() {
        presenter.detach(); worker.shutdownNow(); ui.removeCallbacksAndMessages(null);
        keyboard = null; empty = null; editText = null; candidateAdapter = null;
        super.onDestroyView();
    }
    /** 静态数据适配器不持有弹窗，后台查询结束前也不会延长 View 生命周期。 */
    private static final class CandidateSource implements ModifyGoodsPresenter.Source {
            /** 批量读取相似候选，保持合并前数据完整。 */
            public List<GoodsEntity> similar(java.util.Set<String> plus) { return SdkGoodsSource.getInstance().getGoodsByPlu(plus); }
            /** 按搜索词读取补充候选。 */
            public List<GoodsEntity> search(String query) { return SdkGoodsSource.getInstance().getAllGoods(); }
            }
}
