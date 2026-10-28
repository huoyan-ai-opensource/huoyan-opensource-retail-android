package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ProgressBar;

/** 下载进度条；沿用主题圆角轨道，并将白色百分比绘制在当前进度位置。 */
public class TextProgressBar extends ProgressBar {
    /** 百分比画笔，仅在 UI 线程绘制。 */
    private Paint textPaint;

    /** 创建主题进度条并初始化文字绘制。 */
    public TextProgressBar(Context context) {
        super(context);
        init();
    }

    /** 从 XML 加载轨道及尺寸属性。 */
    public TextProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /** 应用宿主指定的进度条主题属性。 */
    public TextProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /** 应用完整主题参数并初始化百分比画笔。 */
    public TextProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    /** 沿用原有白色文字、字号及居中对齐方式。 */
    private void init() {
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** 在当前进度位置绘制百分比，限制首尾边界；未知总量不显示百分比。 */
    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isIndeterminate()) return;
        int progress = getProgress();
        int max = getMax();
        float progressRatio = (float) progress / max;
        int width = getWidth();
        String text = (int) (progressRatio * 100) + "%";
        float textInset = textPaint.measureText(text) / 2 + 4;
        float textX = Math.max(textInset, Math.min(progressRatio * width, width - textInset));
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float textY = (getHeight() - metrics.ascent - metrics.descent) / 2;
        canvas.drawText(text, textX, textY, textPaint);
    }
}
