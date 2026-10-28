package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cc.huoyan.retail.R;

/** 商品选择弹窗共用的字母数字键盘；仅在主线程操作，引用随宿主视图释放。 */
public final class SearchKeyboard {
    /** 接收退出编辑焦点的弹窗根控件。 */
    private final View root;
    /** 由宿主持有并监听文本变化的输入框。 */
    private final EditText query;
    /** 可展开、收起的自定义键盘容器。 */
    private final LinearLayout keyboard;
    /** 中英文输入模式切换按钮。 */
    private final TextView mode;
    /** 完成输入后触发宿主立即检索，不提交商品选择。 */
    private final Runnable done;
    /** 当前是否启用系统中文输入法，初始为关闭。 */
    private boolean chinese;

    /** 绑定输入框和键盘；默认无输入焦点，首次触摸获得焦点时立即展开。 */
    public SearchKeyboard(View root, EditText query, LinearLayout keyboard, TextView mode, Runnable done) {
        this.root = root;
        this.query = query;
        this.keyboard = keyboard;
        this.mode = mode;
        this.done = done;
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        query.setShowSoftInputOnFocus(false);
        keyboard.setVisibility(View.GONE);
        buildKeyboard(keyboard);
        // EditText 的首次触摸可能仅获取焦点，不产生点击回调；两条入口共用幂等展开逻辑。
        query.setOnFocusChangeListener((v, focused) -> { if (focused) showInput(); });
        query.setOnClickListener(v -> showInput());
        mode.setOnClickListener(v -> {
            chinese = !chinese;
            query.requestFocus();
            showInput();
        });
        query.setOnEditorActionListener((v, action, event) -> { finishInput(); return true; });
    }

    /** 按当前模式展开键盘，不改变查询词或光标位置。 */
    private void showInput() {
        query.setShowSoftInputOnFocus(chinese);
        keyboard.setVisibility(chinese ? View.GONE : View.VISIBLE);
        mode.setText(chinese ? R.string.goods_input_letters : R.string.goods_input_chinese);
        InputMethodManager ime = (InputMethodManager) query.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (chinese) ime.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
        else ime.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    /** 关闭或完成输入时收起键盘；先转移焦点，防止清焦点后输入框自动重新获焦。 */
    public void hide() {
        root.requestFocus();
        keyboard.setVisibility(View.GONE);
        InputMethodManager ime = (InputMethodManager) query.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        ime.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    /** 收起编辑区并通知宿主立即搜索，保留输入内容。 */
    private void finishInput() {
        hide();
        done.run();
    }

    /** 创建字母数字键盘，删除键按 Unicode 码点处理。 */
    private void buildKeyboard(LinearLayout keyboard) {
        for (String letters : new String[]{"1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"}) {
            LinearLayout row = new LinearLayout(query.getContext());
            row.setGravity(android.view.Gravity.CENTER);
            keyboard.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
            for (char letter : letters.toCharArray()) addKey(row, String.valueOf(letter), () -> insert(String.valueOf(letter)), 1);
        }
        LinearLayout row = new LinearLayout(query.getContext());
        keyboard.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        addKey(row, query.getContext().getString(R.string.goods_clear), () -> query.setText(""), 1, R.drawable.keypad_clear, Color.WHITE);
        addKey(row, query.getContext().getString(R.string.goods_space), () -> insert(" "), 2);
        addKey(row, "⌫", () -> {
            int start = Math.max(0, query.getSelectionStart()), end = Math.max(start, query.getSelectionEnd());
            if (start != end) query.getText().delete(start, end);
            else if (start > 0) query.getText().delete(Character.offsetByCodePoints(query.getText(), start, -1), start);
        }, 1, R.drawable.keypad_backspace, Color.BLACK);
        addKey(row, query.getContext().getString(R.string.goods_done), this::finishInput,
                1, R.drawable.land_primary, Color.WHITE);
    }

    /** 创建统一样式的键帽并绑定对应编辑操作。 */
    private void addKey(LinearLayout row, String label, Runnable action, int weight) {
        addKey(row, label, action, weight, R.drawable.land_card, Color.rgb(41, 58, 81));
    }

    /** 操作键使用语义背景色，文字字体与页面按钮一致。 */
    private void addKey(LinearLayout row, String label, Runnable action, int weight, int background, int textColor) {
        TextView key = new TextView(query.getContext());
        key.setText(label); key.setTextSize(18); key.setTextColor(textColor);
        key.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(query.getContext(), R.font.retail_sans),
                background == R.drawable.land_card ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        key.setGravity(android.view.Gravity.CENTER); key.setBackgroundResource(background);
        // 与页面 XML 按钮一致，去掉字体额外留白，文字在键帽内水平、垂直居中。
        key.setIncludeFontPadding(false);
        key.setPadding(0, 0, 0, 0);
        key.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        key.setFocusable(true); key.setClickable(true); key.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, weight);
        params.setMargins(dp(2), dp(2), dp(2), dp(2)); row.addView(key, params);
    }

    /** 替换选中区间并把光标放到插入文本之后。 */
    private void insert(String value) {
        int start = Math.max(0, query.getSelectionStart()), end = Math.max(start, query.getSelectionEnd());
        query.getText().replace(start, end, value); query.setSelection(start + value.length());
    }

    /** 将键盘尺寸转换为当前设备像素。 */
    private int dp(int value) { return Math.round(value * query.getResources().getDisplayMetrics().density); }
}
