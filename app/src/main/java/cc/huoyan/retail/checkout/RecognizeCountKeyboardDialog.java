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
import cc.huoyan.retail.common.ui.WindowUtil;

public class RecognizeCountKeyboardDialog extends DialogFragment {
    private int count = 1;
    private String inputText = "1";
    private boolean editingStarted;
    private TextView countText;
    private OnCountConfirmListener listener;
    private String title;

    public void setTitle(String title) {
        this.title = title;
    }

    public interface OnCountConfirmListener {
        void onConfirm(int count);
    }

    public void setCount(int count) {
        this.count = Math.max(1, count);
        this.inputText = String.valueOf(this.count);
        this.editingStarted = false;
    }

    public void setListener(OnCountConfirmListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_recognize_count_keyboard, container, false);
        ((TextView) view.findViewById(R.id.keyboardTitle)).setText(title != null ? title : getString(R.string.dialog_title_modify_count));
        countText = view.findViewById(R.id.countText);
        refreshCountText();
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
        view.findViewById(R.id.clear).setOnClickListener(v -> {
            count = 0;
            inputText = "";
            editingStarted = true;
            refreshCountText();
        });
        view.findViewById(R.id.backspace).setOnClickListener(v -> {
            if (!editingStarted) {
                editingStarted = true;
                inputText = String.valueOf(count);
            }
            inputText = TextUtils.isEmpty(inputText) ? "" : inputText.substring(0, inputText.length() - 1);
            updateCountFromInput();
            refreshCountText();
        });
        view.findViewById(R.id.cancel).setOnClickListener(v -> dismissAllowingStateLoss());
        view.findViewById(R.id.confirm).setOnClickListener(v -> {
            Integer confirmCount = parseInputCount();
            if (confirmCount == null) {
                Toast.makeText(requireContext(), getString(R.string.toast_count_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            if (confirmCount < 1) {
                Toast.makeText(requireContext(), getString(R.string.toast_count_min), Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null) {
                listener.onConfirm(confirmCount);
            }
            dismissAllowingStateLoss();
        });
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
        root.findViewById(viewId).setOnClickListener(v -> appendNumber(number));
    }

    private void appendNumber(String number) {
        if (!editingStarted) {
            inputText = "";
            editingStarted = true;
        }
        inputText = inputText + number;
        updateCountFromInput();
        refreshCountText();
    }

    private void updateCountFromInput() {
        try {
            if (TextUtils.isEmpty(inputText)) {
                count = 0;
                return;
            }
            int nextCount = Integer.parseInt(inputText);
            if (nextCount > 999) {
                nextCount = 999;
                inputText = String.valueOf(nextCount);
            }
            count = nextCount;
        } catch (NumberFormatException e) {
            count = 0;
            inputText = "";
        }
    }

    private Integer parseInputCount() {
        if (TextUtils.isEmpty(inputText)) {
            return null;
        }
        try {
            return Integer.parseInt(inputText);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void refreshCountText() {
        if (countText != null) {
            countText.setText(TextUtils.isEmpty(inputText) ? "" : String.valueOf(count));
        }
    }
}
