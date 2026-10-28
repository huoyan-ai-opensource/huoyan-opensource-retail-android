package cc.huoyan.retail.checkout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import cc.huoyan.retail.R;

/** 固定在列表背后的四角水印；不占条目高度，不接收点击。 */
public final class RecognitionWatermarkView extends View {
    /** 主线程复用画笔，避免绘制时分配。 */
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 有识别结果时展示中心文案。 */
    private boolean locked;
    /** XML 创建列表水印。 */
    public RecognitionWatermarkView(Context context, AttributeSet attrs) { super(context, attrs); }
    /** 主线程切换锁定文案，不改变布局。 */
    public void setLocked(boolean value) { if (locked != value) { locked = value; invalidate(); } }
    /** 在整个视口绘制四角，商品条目在此图层之上。 */
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float inset = density, length = 24 * density;
        paint.setColor(0xFFBFCBE0); paint.setStrokeWidth(2 * density);
        for (int xSide = 0; xSide < 2; xSide++) for (int ySide = 0; ySide < 2; ySide++) {
            float x = xSide == 0 ? inset : getWidth() - inset;
            float y = ySide == 0 ? inset : getHeight() - inset;
            canvas.drawLine(x, y, x + (xSide == 0 ? length : -length), y, paint);
            canvas.drawLine(x, y, x, y + (ySide == 0 ? length : -length), paint);
        }
        if (locked) {
            paint.setColor(0xFFCAD3E2); paint.setTextSize(22 * density); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getContext().getString(R.string.title_recognize_result), getWidth() / 2f,
                    (getHeight() - paint.ascent() - paint.descent()) / 2f, paint);
        }
    }
}
