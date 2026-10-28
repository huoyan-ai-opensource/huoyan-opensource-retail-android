package cc.huoyan.retail.goods;

import cc.huoyan.retail.common.ui.LandscapeDialogWindow;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.SdkGoodsSource;
import cc.huoyan.retail.common.ui.DebounceClickListener;
import cc.huoyan.retail.common.ui.WindowUtil;

/** 商品新增与编辑表单视图，校验和实体构建交由 Presenter。 */
public class AddGoodsDialog extends DialogFragment implements GoodsFormPresenter.View {
    /** 过滤新增 PLU 的粘贴和程序输入；合法内容返回 null 保留输入法状态。 */
    private static CharSequence filterPlu(CharSequence source, int start, int end) {
        StringBuilder accepted = new StringBuilder();
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) accepted.append(c);
        }
        return accepted.length() == end - start ? null : accepted.toString();
    }

    /** 横屏使用与设计稿一致的居中弹窗和遮罩。 */
    @Override public void onStart() {
        super.onStart();
        LandscapeDialogWindow.apply(this);
        // 小横屏表单在软键盘出现时缩小滚动区，保留底部确认操作。
        android.content.res.Configuration configuration = getResources().getConfiguration();
        if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && configuration.screenWidthDp < 1100) {
            android.view.Window window = requireDialog().getWindow();
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            View root = requireView();
            // 输入法缩小窗口后，将整个输入框滚入可见区域；监听随本次视图销毁。
            root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (bottom - top == oldBottom - oldTop) return;
                root.post(() -> {
                    View focused = root.findFocus();
                    if (focused instanceof android.widget.EditText)
                        focused.requestRectangleOnScreen(new android.graphics.Rect(0, 0, focused.getWidth(), focused.getHeight()), true);
                });
            });
        }
    }

    /** 将合法表单交给宿主提交 SDK。 */
    private AddGoodsDialogListener listener;
    /** 当前表单规则与异步校验。 */
    private GoodsFormPresenter presenter;
    /** 仅用于SDK 商品查询，随视图销毁关闭。 */
    private java.util.concurrent.ExecutorService worker;
    /** 不引用页面的主线程投递器。 */
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    /** 新增商品计价偏好；编辑商品不覆盖该值。 */
    private static final String LAST_PRICING_COUNT = "last_new_goods_pricing_count";
    /** 恢复后的编辑商品，仅用于初始表单回显。 */
    private GoodsEntity editingGoods;

    /** 设置待编辑商品，PLU 保持不变以保留学习和 SDK 商品映射。 */
    public void setEditingGoods(GoodsEntity goods) {
        Bundle args = new Bundle();
        args.putString("editingGoods", cc.huoyan.retail.common.GsonUtil.toJson(goods));
        setArguments(args);
    }

    /** 向宿主交付校验通过的商品。 */
    public interface AddGoodsDialogListener {
        /** 商品校验完成，由宿主执行实际保存。 */
        void onConfirm(GoodsEntity goods);
    }

    /** 绑定宿主的商品保存入口。 */
    public void setListener(AddGoodsDialogListener listener) {
        this.listener = listener;
    }

    /** 恢复编辑参数并绑定表单，不在控件回调中查询 SDK。 */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            editingGoods = cc.huoyan.retail.common.GsonUtil.fromJson(
                    getArguments().getString("editingGoods"), GoodsEntity.class);
        }
        worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        presenter = new GoodsFormPresenter(this, editingGoods,
                SdkGoodsSource.getInstance()::getGoodsByPlu, worker, ui::post);
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_add_gooods, container, false);
        TextView pluTv = view.findViewById(R.id.plu);
        pluTv.setFilters(new android.text.InputFilter[]{
                (source, start, end, dest, dstart, dend) -> filterPlu(source, start, end),
                new android.text.InputFilter.LengthFilter(32)});
        TextView nameTv = view.findViewById(R.id.name);
        TextView priceTv = view.findViewById(R.id.price);
        TextView tareTv = view.findViewById(R.id.goodsTare);
        priceTv.setOnClickListener(v -> openNumber(false, priceTv.getText().toString()));
        tareTv.setOnClickListener(v -> openNumber(true, tareTv.getText().toString()));
        getChildFragmentManager().setFragmentResultListener("goodsNumberResult", this, (key, result) ->
                (result.getBoolean("tare") ? tareTv : priceTv).setText(result.getString("value")));
        RadioGroup pricingTypeRg = view.findViewById(R.id.pricingType);
        if (editingGoods != null) {
            ((TextView) view.findViewById(R.id.goodsName)).setText(R.string.study_edit_goods);
            pluTv.setFilters(new android.text.InputFilter[0]);
            pluTv.setText(editingGoods.getPlu());
            pluTv.setEnabled(false);
            if (view.findViewById(R.id.goodsPluLabel) != null) {
                ((TextView) view.findViewById(R.id.goodsPluLabel)).setText(R.string.land_plu_read_only);
                pluTv.setBackgroundResource(R.drawable.land_secondary);
            }
            nameTv.setText(editingGoods.getName());
            priceTv.setText(java.math.BigDecimal.valueOf(editingGoods.getPrice(), 2).toPlainString());
            tareTv.setText(Long.toString(editingGoods.getTareWeight()));
            pricingTypeRg.check(editingGoods.getUnit() == 5 ? R.id.pricingTypeCount : R.id.pricingTypeWeight);
        }
        if (editingGoods == null) pricingTypeRg.check(
                cc.huoyan.retail.data.SPManager.getInstance().getBoolean(LAST_PRICING_COUNT)
                        ? R.id.pricingTypeCount : R.id.pricingTypeWeight);
        updatePricingFields(view, pricingTypeRg.getCheckedRadioButtonId());
        pricingTypeRg.setOnCheckedChangeListener((group, checked) -> {
            updatePricingFields(view, checked);
            if (editingGoods == null) cc.huoyan.retail.data.SPManager.getInstance()
                    .putBoolean(LAST_PRICING_COUNT, checked == R.id.pricingTypeCount);
        });
        view.findViewById(R.id.close).setOnClickListener(new DebounceClickListener() {
            /** 取消商品编辑，不交付表单。 */
            @Override
            public void onDebouncedClick(View v) {
                dismiss();
            }
        });
        view.findViewById(R.id.confirm).setOnClickListener(new DebounceClickListener() {
            /** 读取当前表单并请求 Presenter 校验。 */
            @Override
            public void onDebouncedClick(View v) {
                presenter.confirm(priceTv.getText().toString(), pluTv.getText().toString(),
                        nameTv.getText().toString(), pricingTypeRg.getCheckedRadioButtonId() == R.id.pricingTypeCount, tareTv.getText().toString());
            }
        });
        return view;
    }

    /** 按当前计价方式同步单价单位与皮重可见性，重建时沿用控件恢复的选中项。 */
    private void updatePricingFields(View root, int checked) {
        boolean count = checked == R.id.pricingTypeCount;
        root.findViewById(R.id.goodsTareField).setVisibility(count ? View.GONE : View.VISIBLE);
        ((TextView) root.findViewById(R.id.goodsPriceLabel)).setText(
                count ? R.string.goods_price_count : R.string.goods_price_weight);
    }

    /** 关闭商品名称输入法，再打开当前字段的自定义键盘。 */
    private void openNumber(boolean tare, String current) {
        android.view.inputmethod.InputMethodManager input = (android.view.inputmethod.InputMethodManager)
                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(requireView().getWindowToken(), 0);
        boolean count = ((RadioGroup) requireView().findViewById(R.id.pricingType))
                .getCheckedRadioButtonId() == R.id.pricingTypeCount;
        GoodsNumberDialog.show(getChildFragmentManager(), tare, count, current);
    }

    /** 显示本地化表单校验错误，保留当前输入。 */
    @Override public void invalid(int resource) { cc.huoyan.retail.common.ui.ToastEx.showFailureToast(requireContext(), getString(resource)); }
    /** 查询失败允许重试，不关闭表单。 */
    @Override public void failed(String reason) {
        cc.huoyan.retail.common.ui.ToastEx.showFailureToast(requireContext(), getString(R.string.goods_operation_failed, reason));
    }
    /** 交付校验后的独立商品并关闭弹窗。 */
    @Override public void accepted(GoodsEntity goods) {
        if (listener != null) listener.onConfirm(goods);
        dismiss();
    }
    /** 视图销毁后停止查询并释放 Presenter 引用。 */
    @Override public void onDestroyView() {
        presenter.detach(); worker.shutdownNow(); ui.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
