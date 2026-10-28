package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import cc.huoyan.retail.R;

/** 首页底部低透明度品牌水印，不参与交互。 */
public final class BrandWatermarkView extends View {
    private final Drawable logo;

    /** 从 XML 创建水印，复用正式品牌素材。 */
    public BrandWatermarkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        logo = ContextCompat.getDrawable(context, R.mipmap.logo).mutate();
        logo.setAlpha(14);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        int width = Math.round(124 * density);
        int height = Math.round(width * (float) logo.getIntrinsicHeight() / logo.getIntrinsicWidth());
        for (int x = Math.round(24 * density); x < getWidth(); x += Math.round(210 * density)) {
            canvas.save();
            canvas.translate(x, 44 * density);
            canvas.rotate(-24);
            logo.setBounds(0, 0, width, height);
            logo.draw(canvas);
            canvas.restore();
        }
    }
}
