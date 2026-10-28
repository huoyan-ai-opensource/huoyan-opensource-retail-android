package cc.huoyan.retail.learning;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** 商品学习页 16:9 摄像头容器，按可用空间等比居中显示。 */
public class LearningPreviewFrame extends FrameLayout {
    /** 创建固定宽高比的预览容器。 */
    public LearningPreviewFrame(Context context) { super(context); }
    /** 从 XML 创建预览容器。 */
    public LearningPreviewFrame(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int maxW = MeasureSpec.getSize(widthSpec), maxH = MeasureSpec.getSize(heightSpec);
        int width = Math.min(maxW, maxH * 16 / 9);
        int height = width * 9 / 16;
        setMeasuredDimension(width, height);
        int childW = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int childH = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(childW, childH);
    }
}
