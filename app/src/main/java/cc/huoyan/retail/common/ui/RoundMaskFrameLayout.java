package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import cc.huoyan.retail.R;

public class RoundMaskFrameLayout extends RelativeLayout {
    private Path path;
    private RectF rectF;
    private float radius;
    private Paint maskPaint;

    public RoundMaskFrameLayout(Context context) {
        this(context, null);
    }

    public RoundMaskFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoundMaskFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // 获取自定义属性
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.RoundMaskFrameLayout, defStyleAttr, 0);
        try {
            // 获取 customSize 属性的值，若未设置则使用默认值 0
            radius = typedArray.getDimensionPixelSize(R.styleable.RoundMaskFrameLayout_radiusSize, 0);
        } finally {
            // 回收 TypedArray，避免内存泄漏
            typedArray.recycle();
        }
        init();
    }

    private void init() {
        path = new Path();
        rectF = new RectF();
        maskPaint = new Paint();
        maskPaint.setColor(0x000000);
        if (radius == 0)
            radius = getResources().getDimensionPixelSize(R.dimen.dp_3);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rectF.set(0, 0, w, h);
        path.addRoundRect(rectF, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // 绘制遮罩层
        canvas.drawRoundRect(rectF, radius, radius, maskPaint);

        // 裁剪并绘制子视图
        canvas.save();
        canvas.clipPath(path);
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}