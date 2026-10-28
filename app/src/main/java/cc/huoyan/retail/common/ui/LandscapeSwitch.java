package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.Switch;

/** 保留原生开关触摸、键盘和无障碍语义，按横屏设计绘制轨道及滑块。 */
public final class LandscapeSwitch extends Switch {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 从布局创建横屏开关。 */
    public LandscapeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float left = (getWidth() - 52 * density) / 2f;
        float top = (getHeight() - 30 * density) / 2f;
        paint.setColor(Color.parseColor(isChecked() ? "#3F3EED" : "#CBD2DF"));
        paint.setAlpha(isEnabled() ? 255 : 128);
        canvas.drawRoundRect(left, top, left + 52 * density, top + 30 * density,
                15 * density, 15 * density, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(left + (isChecked() ? 37 : 15) * density, top + 15 * density,
                11 * density, paint);
    }
}
