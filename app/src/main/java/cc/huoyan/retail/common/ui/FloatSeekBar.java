package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;

import androidx.appcompat.widget.AppCompatSeekBar;

public class FloatSeekBar extends AppCompatSeekBar {
    private OnFloatChangeListener floatChangeListener;

    public interface OnFloatChangeListener {
        void onFloatValueChanged(float value);
    }

    public FloatSeekBar(Context context) {
        super(context);
        init();
    }

    public FloatSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FloatSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.setMax(100);
        this.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (floatChangeListener != null) {
                    float value = progress / 100f; // 转换为0-1的浮点数
                    floatChangeListener.onFloatValueChanged(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    public void setOnFloatChangeListener(OnFloatChangeListener listener) {
        this.floatChangeListener = listener;
    }

    public void setFloatValue(float value) {
        setProgress(Math.round(value * 100));
    }

    public float getFloatValue() {
        return getProgress() / 100f;
    }
}