package cc.huoyan.retail.device.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class AutoFitTextureView extends TextureView {
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    /** 设置页完整展示取景范围；识别页保持其自身坐标约定。 */
    private boolean fitInside;

    /** 显式指定预览是否同时受容器宽高约束，须在主线程调用。 */
    public void setFitInside(boolean fitInside) {
        this.fitInside = fitInside;
        requestLayout();
    }

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * 设置视图的宽高比。调用此方法会触发视图的重新测量。
     *
     * @param width  相对宽度
     * @param height 相对高度
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("尺寸不能为负");
        }
        if (mRatioWidth == width && mRatioHeight == height) return;
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    public int getRatioWidth() {
        return mRatioWidth;
    }

    public int getRatioHeight() {
        return mRatioHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (mRatioWidth > 0 && mRatioHeight > 0) {
            if (fitInside || getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                float scale = PreviewScale.compute(mRatioWidth, mRatioHeight, width, height, true);
                width = Math.round(mRatioWidth * scale);
                height = Math.round(mRatioHeight * scale);
            } else {
                width = height * mRatioWidth / mRatioHeight;
            }
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
