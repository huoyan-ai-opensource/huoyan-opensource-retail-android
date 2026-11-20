package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.Switch;

import cc.huoyan.retail.R;

/**
 * 保留原生开关触摸、键盘和无障碍语义，按横屏设计绘制轨道及滑块。
 */
public final class LandscapeSwitch extends Switch {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * 从布局创建横屏开关。
     */
    public LandscapeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getResources().getDimension(R.dimen.dp_26);
        float height = getResources().getDimension(R.dimen.dp_15);
        float scale = Math.min(1f, Math.min(getWidth() / width, getHeight() / height));
        width *= scale;
        height *= scale;
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        float radius = height / 2f;
        paint.setColor(Color.parseColor(isChecked() ? "#3F3EED" : "#CBD2DF"));
        paint.setAlpha(isEnabled() ? 255 : 128);
        canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint);
        paint.setColor(Color.WHITE);
        float centerX = isChecked() ? left + width - radius : left + radius;
        float thumbRadius = getResources().getDimension(R.dimen.dp_11) * scale / 2f;
        canvas.drawCircle(centerX, top + radius, thumbRadius, paint);
    }
}
