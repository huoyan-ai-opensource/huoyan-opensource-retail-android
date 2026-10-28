package cc.huoyan.retail.common.ui;

import android.view.View;

public abstract class DebounceClickListener implements View.OnClickListener {

    private static final long DEFAULT_DEBOUNCE_TIME = 800; // 默认防抖时间间隔，单位：毫秒
    private long lastClickTime = 0;
    private final long debounceTime;

    public DebounceClickListener() {
        this.debounceTime = DEFAULT_DEBOUNCE_TIME;
    }

    public DebounceClickListener(long debounceTime) {
        this.debounceTime = debounceTime;
    }

    @Override
    public void onClick(View v) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime;
            onDebouncedClick(v);
        }
    }

    public abstract void onDebouncedClick(View v);
}
