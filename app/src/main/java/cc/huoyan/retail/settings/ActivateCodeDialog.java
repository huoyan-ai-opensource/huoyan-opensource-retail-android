package cc.huoyan.retail.settings;

import android.os.Bundle;
import android.annotation.SuppressLint;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.BoundedDialogLayout;
import cc.huoyan.retail.common.ui.DebounceClickListener;
import cc.huoyan.retail.data.SPManager;

/** 激活码分段输入与专用键盘；授权和扫码继续由宿主协调。 */
public class ActivateCodeDialog extends DialogFragment {
    /** 当前宿主的输入出口，销毁视图后释放。 */
    private ActivateCodeListener activateCodeListener;
    /** 当前视图的真实编辑层，不唤起系统输入法。 */
    private ActivationCodeEditText editText;
    /** 四个分段展示框，销毁视图时释放。 */
    private TextView[] segments;
    /** 完整输入时可用的提交按钮。 */
    private View activate;
    /** 长按退格触点，停止触摸或离开页面时取消回调。 */
    private View backspace;
    /** 长按连续删除任务，仅在当前可见视图上运行。 */
    private final Runnable repeatDelete = new Runnable() {
        /** 主线程删除一位并安排下一次，停止或销毁后不再续订。 */
        @Override public void run() {
            if (backspace == null || !backspace.isPressed() || editText == null) return;
            deletePrevious();
            backspace.postDelayed(this, 80);
        }
    };
    /** 绑定当前授权页会话的输入出口。 */
    public void setActivateCodeListener(ActivateCodeListener listener) { activateCodeListener = listener; }

    /** 渲染分段和真实插入点，不保存或提交输入。 */
    public void setText(String input) {
        if (segments == null) return;
        int position = Math.max(0, editText.getSelectionStart());
        int activeGroup = input.length() == 16 && position == 16 ? -1 : Math.min(3, position / 4);
        for (int i = 0; i < segments.length; i++) {
            int start = Math.min(input.length(), i * 4);
            String value = input.substring(start, Math.min(input.length(), start + 4));
            segments[i].setText(value.isEmpty() ? "····" : value);
            segments[i].setTextColor(value.isEmpty() ? 0xFFAAB3C0 : 0xFF293A51);
            segments[i].setBackgroundResource(i == activeGroup ? R.drawable.activation_segment_active : R.drawable.activation_segment);
        }
        activate.setEnabled(ActivationKeyboardInput.complete(input));
        editText.invalidate();
    }
    /** 用户取消输入时通知会话；旋转销毁不发送取消。 */
    @Override public void onCancel(@NonNull android.content.DialogInterface dialog) {
        cancelled();
        super.onCancel(dialog);
    }

    /** 通过 FragmentResult 交付取消，避免恢复后监听器丢失。 */
    private void cancelled() {
        getParentFragmentManager().setFragmentResult("activationInputCancelled", new Bundle());
    }

    /** 创建共享布局，按方向资源设置键盘尺寸并恢复未提交输入。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        View root = inflater.inflate(R.layout.dialog_activate_code, container, false);
        editText = root.findViewById(R.id.editText);
        segments = new TextView[]{root.findViewById(R.id.text1), root.findViewById(R.id.text2),
                root.findViewById(R.id.text3), root.findViewById(R.id.text4)};
        activate = root.findViewById(R.id.activate);
        editText.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            String next = dest.subSequence(0, dstart).toString() + source.subSequence(start, end)
                    + dest.subSequence(dend, dest.length());
            return ActivationKeyboardInput.accepts(next) ? null : dest.subSequence(dstart, dend);
        }});
        editText.bindSegments(segments, () -> setText(editText.getText().toString()));
        editText.addTextChangedListener(new TextWatcher() {
            /** 修改前无需更新展示。 */
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            /** 统一更新物理键盘、无障碍与触屏输入的分段。 */
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { setText(s.toString()); }
            /** 编辑完成后不再次修改文本。 */
            @Override public void afterTextChanged(Editable text) { }
        });
        buildKeyboard(root.findViewById(R.id.activationKeyboard), inflater);
        root.findViewById(R.id.activationClear).setOnClickListener(v -> editText.setText(""));
        root.findViewById(R.id.cancel).setOnClickListener(v -> { cancelled(); dismiss(); });
        activate.setOnClickListener(v -> {
            String code = editText.getText().toString();
            if (activateCodeListener == null || !ActivationKeyboardInput.complete(code)) return;
            activate.setEnabled(false);
            activateCodeListener.onActivate(code);
            dismiss();
        });
        root.findViewById(R.id.scan).setOnClickListener(new DebounceClickListener() {
            /** 切换扫码沿用宿主通道，不转换扫码内容。 */
            @Override public void onDebouncedClick(View view) {
                if (activateCodeListener != null) activateCodeListener.onScan();
                dismiss();
            }
        });
        String input = state == null ? SPManager.getInstance().getString(SPManager.SP_ACTIVATE_CODE)
                : state.getString("manualCode", "");
        editText.setText(ActivationKeyboardInput.restore(input));
        editText.setSelection(Math.max(0, Math.min(editText.length(), state == null
                ? editText.length() : state.getInt("manualCursor", editText.length()))));
        setText(editText.getText().toString());
        return root;
    }
    /** 以四行固定字符排列构建键盘；尺寸取当前方向资源，长按退格可连续删除。 */
    @SuppressLint("ClickableViewAccessibility") // 返回 false，由 ImageButton 执行标准点击；这里只取消重复任务。
    private void buildKeyboard(LinearLayout keyboard, LayoutInflater inflater) {
        String[] rows = {"123456789", "QWERTYUP", "ASDFGHJKL", "ZXCVBNM"};
        // 小数间距向下取整，避免九列累积舍入超过竖屏内容宽度。
        int gap = (int) getResources().getDimension(R.dimen.activation_key_gap);
        int width = getResources().getDimensionPixelSize(R.dimen.activation_key_width);
        int height = getResources().getDimensionPixelSize(R.dimen.activation_key_height);
        for (int i = 0; i < rows.length; i++) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
            if (i > 0) rowParams.topMargin = dp(6);
            keyboard.addView(row, rowParams);
            for (int j = 0; j < rows[i].length(); j++) {
                String character = rows[i].substring(j, j + 1);
                TextView key = (TextView) inflater.inflate(R.layout.item_activation_key, row, false);
                key.setText(character);
                key.setTag("activation-key-" + character);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
                if (j > 0) params.leftMargin = gap;
                row.addView(key, params);
                key.setOnClickListener(v -> insert(character));
            }
            if (i == 3) {
                ImageButton delete = new ImageButton(requireContext());
                delete.setImageResource(R.drawable.activation_backspace);
                delete.setBackgroundResource(R.drawable.keypad_backspace);
                delete.setContentDescription(getString(R.string.activation_backspace));
                delete.setTag("activation-backspace");
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width * 2 + gap, height);
                params.leftMargin = gap;
                row.addView(delete, params);
                backspace = delete;
                delete.setOnClickListener(v -> deletePrevious());
                delete.setOnLongClickListener(v -> {
                    deletePrevious();
                    delete.postDelayed(repeatDelete, 80);
                    return true;
                });
                delete.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                        v.removeCallbacks(repeatDelete);
                    return false;
                });
            }
        }
    }
    /** 在当前光标或选区插入合法字符，长度及字符约束由编辑框统一执行。 */
    private void insert(String character) {
        int start = Math.max(0, Math.min(editText.getSelectionStart(), editText.getSelectionEnd()));
        int end = Math.max(start, Math.max(editText.getSelectionStart(), editText.getSelectionEnd()));
        editText.getText().replace(start, end, character);
    }
    /** 删除选区或光标前一位，空输入不产生副作用。 */
    private void deletePrevious() {
        int start = Math.max(0, Math.min(editText.getSelectionStart(), editText.getSelectionEnd()));
        int end = Math.max(start, Math.max(editText.getSelectionStart(), editText.getSelectionEnd()));
        if (start == end && start > 0) start--;
        if (start < end) editText.getText().delete(start, end);
    }
    /** 将设计 dp 转为当前窗口像素，避免历史倍增 dimens。 */
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    /** 限制弹窗边界并隐藏系统输入法，矮窗口保留固定取消和激活按钮。 */
    @Override public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        View decor = requireActivity().getWindow().getDecorView();
        ((BoundedDialogLayout) requireView()).setMaxHeight(decor.getHeight() - dp(32));
        window.setLayout(Math.min(getResources().getDimensionPixelSize(R.dimen.activation_dialog_width),
                decor.getWidth() - dp(32)), WindowManager.LayoutParams.WRAP_CONTENT);
        editText.requestFocus();
    }
    /** 保存未提交输入与插入点，重建不回退到持久化的旧码。 */
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (editText != null) {
            out.putString("manualCode", editText.getText().toString());
            out.putInt("manualCursor", editText.getSelectionStart());
        }
    }
    /** 页面不可见时立即停止连续删除。 */
    @Override public void onStop() {
        if (backspace != null) backspace.removeCallbacks(repeatDelete);
        super.onStop();
    }
    /** 释放视图、宿主监听及延迟任务，避免重建后操作旧控件。 */
    @Override public void onDestroyView() {
        if (backspace != null) backspace.removeCallbacks(repeatDelete);
        if (editText != null) editText.bindSegments(null, null);
        backspace = null;
        editText = null;
        segments = null;
        activate = null;
        activateCodeListener = null;
        super.onDestroyView();
    }
    /** 激活输入方式切换和内容交付的宿主出口。 */
    public interface ActivateCodeListener {
        /** 交付合法手输文本，规范化与保存由 Presenter 完成。 */
        void onActivate(String code);
        /** 请求切换到相机扫码。 */
        void onScan();
    }
}
