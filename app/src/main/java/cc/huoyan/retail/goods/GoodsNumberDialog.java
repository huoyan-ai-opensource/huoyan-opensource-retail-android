package cc.huoyan.retail.goods;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import cc.huoyan.retail.R;
import cc.huoyan.retail.checkout.CheckoutDialog;
import cc.huoyan.retail.checkout.NumericKeypad;
import cc.huoyan.retail.common.ui.ToastEx;
import java.math.BigDecimal;

/** 商品售价和皮重输入；仅确认时回填字符串，取消及重建不触发商品保存。 */
public final class GoodsNumberDialog extends CheckoutDialog {
    /** 当前编辑值，由实例状态保留。 */
    private String input;
    /** 首次数字输入替换预填值，退格始终删除当前末位。 */
    private boolean editing;
    /** 当前字段是否为整数克皮重。 */
    private boolean tare;
    /** 当前输入视图，销毁时释放。 */
    private TextView value;

    /** 打开独立数字输入，结果交给所属商品表单。 */
    public static void show(FragmentManager manager, boolean tare, boolean count, String current) {
        if (manager.isStateSaved() || manager.findFragmentByTag("goodsNumber") != null) return;
        GoodsNumberDialog dialog = new GoodsNumberDialog();
        Bundle args = new Bundle(); args.putBoolean("tare", tare); args.putString("value", current);
        args.putBoolean("count", count);
        dialog.setArguments(args); dialog.show(manager, "goodsNumber");
    }

    /** 创建键盘，整数皮重不提供小数点。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View root = inflater.inflate(R.layout.dialog_goods_number, container, false);
        tare = requireArguments().getBoolean("tare");
        input = state == null ? requireArguments().getString("value", "") : state.getString("input", "");
        editing = state != null && state.getBoolean("editing");
        value = root.findViewById(R.id.goodsNumberValue); value.setText(input);
        ((TextView)root.findViewById(R.id.keyboardTitle)).setText(tare ? R.string.goods_tare_label
                : requireArguments().getBoolean("count") ? R.string.goods_price_count : R.string.goods_price_weight);
        NumericKeypad.bind(root, this::key);
        if (tare) root.findViewById(R.id.dot).setVisibility(View.INVISIBLE);
        root.findViewById(R.id.clear).setOnClickListener(v -> { input = "0"; editing = false; value.setText(input); });
        root.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.confirm).setOnClickListener(v -> confirm());
        return root;
    }

    /** 控制数字范围和小数精度，避免经过浮点金额转换。 */
    private void key(String key) {
        if ("backspace".equals(key)) {
            editing = true; input = input.isEmpty() ? "" : input.substring(0, input.length() - 1);
        } else {
            if (!editing) { input = ""; editing = true; }
            if (".".equals(key)) {
                if (!tare && !input.contains(".")) input = input.isEmpty() ? "0." : input + ".";
            } else if (input.length() < (tare ? 10 : 11)) {
                int dot = input.indexOf('.');
                if (dot < 0 || input.length() - dot <= 2) input = input.equals("0") ? key : input + key;
            }
        }
        value.setText(input);
    }

    /** 确认时严格检查范围；回填当前字段，不触发远端写入。 */
    private void confirm() {
        try {
            BigDecimal number = new BigDecimal(input);
            if (number.signum() < 0) throw new ArithmeticException();
            if (tare) number.intValueExact(); else number.movePointRight(2).longValueExact();
            Bundle result = new Bundle(); result.putBoolean("tare", tare);
            result.putString("value", tare ? number.toPlainString() : number.setScale(2).toPlainString());
            getParentFragmentManager().setFragmentResult("goodsNumberResult", result); dismiss();
        } catch (NumberFormatException | ArithmeticException invalid) {
            ToastEx.showFailureToast(requireContext(), getString(tare ? R.string.goods_tare_invalid : R.string.toast_price_format));
        }
    }

    /** 重建时保留未确认输入，原商品表单仍保持原值。 */
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state); state.putString("input", input); state.putBoolean("editing", editing);
    }
    /** 释放失效控件引用。 */
    @Override public void onDestroyView() { value = null; super.onDestroyView(); }
}
