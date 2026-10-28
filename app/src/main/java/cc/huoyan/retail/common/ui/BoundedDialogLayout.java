package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** 弹窗内容按需收缩；高度达到窗口上限后，由带权重的滚动区让出固定操作区。 */
public final class BoundedDialogLayout extends LinearLayout {
    /** 主线程设置的窗口可用高度，初始由系统测量约束决定。 */
    private int maxHeight = Integer.MAX_VALUE;

    /** XML 创建容器，沿用 LinearLayout 的布局属性。 */
    public BoundedDialogLayout(Context context, AttributeSet attrs) { super(context, attrs); }

    /** 在主线程更新高度上限，内容变化后仍按实际所需高度布局。 */
    public void setMaxHeight(int height) {
        maxHeight = Math.max(1, height);
        requestLayout();
    }

    /** 只限制最大高度，不用固定高度撑出空白。 */
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int limit = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? maxHeight : Math.min(maxHeight, MeasureSpec.getSize(heightSpec));
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
    }
}
