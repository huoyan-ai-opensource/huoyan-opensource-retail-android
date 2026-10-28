package cc.huoyan.retail.checkout;

import cc.huoyan.retail.common.ui.LandscapeDialogWindow;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import cc.huoyan.retail.R;
import cc.huoyan.retail.common.NumberUtil;
import cc.huoyan.retail.common.ui.WindowUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 称重商品修改重量弹窗（用户 2026-08-25 指示）：小键盘输入重量（kg），
 * 布局与交互参照计件键盘（RecognizeCountKeyboardDialog），「清空」位替换为「点」，
 * 输入时即限制小数位不超过 3 位（与秤读数 3 位口径一致），确认后回调重量值。
 */
public class WeightInputDialog extends DialogFragment {

    /** 重量小数位数上限（与秤读数 3 位小数口径一致） */
    public static final int MAX_DECIMAL_PLACES = 3;
    /** 输入文本长度上限（含小数点，兜底防超长） */
    public static final int MAX_INPUT_LENGTH = 10;

    private double weightKg;
    private String inputText = "";
    private boolean editingStarted;
    private TextView weightText;
    private OnWeightConfirmListener listener;

    public interface OnWeightConfirmListener {
        void onConfirm(double weightKg);
    }

    public void setListener(OnWeightConfirmListener listener) {
        this.listener = listener;
    }

    /**
     * 预填当前计价重量（kg），弹窗内按 3 位小数展示供直接修改。
     *
     * @param weightKg 当前计价重量（kg）
     */
    public void setWeight(double weightKg) {
        this.weightKg = weightKg;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_weight_keyboard, container, false);
        weightText = view.findViewById(R.id.weightText);
        inputText = BigDecimal.valueOf(weightKg)
                .setScale(MAX_DECIMAL_PLACES, RoundingMode.HALF_UP).toPlainString();
        editingStarted = false;
        refreshWeightText();
        bindNumber(view, R.id.num0, "0");
        bindNumber(view, R.id.num1, "1");
        bindNumber(view, R.id.num2, "2");
        bindNumber(view, R.id.num3, "3");
        bindNumber(view, R.id.num4, "4");
        bindNumber(view, R.id.num5, "5");
        bindNumber(view, R.id.num6, "6");
        bindNumber(view, R.id.num7, "7");
        bindNumber(view, R.id.num8, "8");
        bindNumber(view, R.id.num9, "9");
        view.findViewById(R.id.dot).setOnClickListener(v -> {
            startEditing();
            inputText = appendDot(inputText);
            refreshWeightText();
        });
        view.findViewById(R.id.backspace).setOnClickListener(v -> {
            startEditing();
            inputText = backspace(inputText);
            refreshWeightText();
        });
        view.findViewById(R.id.cancel).setOnClickListener(v -> dismissAllowingStateLoss());
        view.findViewById(R.id.confirm).setOnClickListener(v -> confirmWeight());
        setCancelable(false);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (LandscapeDialogWindow.apply(this)) return;
        Dialog dialog = getDialog();
        if (dialog == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window != null) {
            int maxDialogWidth = getResources().getDisplayMetrics().widthPixels - dpToPx(32);
            window.setLayout(Math.min(dpToPx(420), maxDialogWidth), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void bindNumber(View root, int viewId, String number) {
        root.findViewById(viewId).setOnClickListener(v -> {
            startEditing();
            inputText = appendDigit(inputText, number);
            refreshWeightText();
        });
    }

    /** 首次按键进入编辑态：丢弃预填值从头输入（与计件键盘语义一致）。 */
    private void startEditing() {
        if (!editingStarted) {
            editingStarted = true;
            inputText = "";
        }
    }

    private void refreshWeightText() {
        if (weightText != null) {
            weightText.setText(inputText);
        }
    }

    private void confirmWeight() {
        String input = inputText.trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(requireContext(), getString(R.string.toast_weight_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!NumberUtil.isFloat(input)) {
            Toast.makeText(requireContext(), getString(R.string.toast_weight_format), Toast.LENGTH_SHORT).show();
            return;
        }
        if (decimalPlaces(input) > MAX_DECIMAL_PLACES) {
            Toast.makeText(requireContext(), getString(R.string.toast_weight_three_decimals), Toast.LENGTH_SHORT).show();
            return;
        }
        double parsedWeightKg = new BigDecimal(input).doubleValue();
        if (parsedWeightKg <= 0d) {
            Toast.makeText(requireContext(), getString(R.string.toast_weight_min), Toast.LENGTH_SHORT).show();
            return;
        }
        if (listener != null) {
            listener.onConfirm(parsedWeightKg);
        }
        dismissAllowingStateLoss();
    }

    /**
     * 追加数字键：小数位已达 3 位（处于小数部分）或超长时忽略输入，其余原样追加。
     *
     * @param input  当前输入文本
     * @param digit  数字字符
     * @return 追加后的输入文本（不满足条件时返回原文）
     */
    public static String appendDigit(String input, String digit) {
        String current = input == null ? "" : input;
        if (current.length() >= MAX_INPUT_LENGTH) {
            return current;
        }
        if (current.indexOf('.') >= 0 && decimalPlaces(current) >= MAX_DECIMAL_PLACES) {
            return current;
        }
        return current + digit;
    }

    /**
     * 追加小数点：已含小数点或空输入时忽略，其余追加（如 "2" → "2."）。
     *
     * @param input 当前输入文本
     * @return 追加后的输入文本（不满足条件时返回原文）
     */
    public static String appendDot(String input) {
        String current = input == null ? "" : input;
        // 不用 TextUtils.isEmpty：静态方法被纯 JUnit 单测调用，mockable jar 下会抛 not mocked
        if (current.isEmpty()) {
            // 空内容输入点：默认显示 0.（用户 2026-08-25 指示）
            return "0.";
        }
        if (current.indexOf('.') >= 0) {
            return current;
        }
        return current + ".";
    }

    /**
     * 退格删除末位字符（空输入保持为空）。
     *
     * @param input 当前输入文本
     * @return 退格后的输入文本
     */
    public static String backspace(String input) {
        String current = input == null ? "" : input;
        return current.length() <= 1 ? "" : current.substring(0, current.length() - 1);
    }

    /**
     * 统计输入文本的小数位数（无小数点或空输入返回 0），用于重量 3 位小数上限校验。
     *
     * @param input 重量输入文本
     * @return 小数位数
     */
    public static int decimalPlaces(String input) {
        if (input == null) {
            return 0;
        }
        int dotIndex = input.indexOf('.');
        return dotIndex < 0 ? 0 : input.length() - dotIndex - 1;
    }
}
