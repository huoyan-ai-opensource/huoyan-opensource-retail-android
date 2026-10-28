package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.recyclerview.widget.RecyclerView;

import cc.huoyan.retail.R;

public class MaxHeightRecyclerView extends RecyclerView {
    private int maxHeight;

    public MaxHeightRecyclerView(Context context) {
        super(context);
        init(context, null);
    }

    public MaxHeightRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MaxHeightRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightRecyclerView);
            maxHeight = a.getDimensionPixelSize(R.styleable.MaxHeightRecyclerView_maxHeight, Integer.MAX_VALUE);
            a.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int newHeightSpec = heightSpec;
        if (maxHeight > 0) {
            int hSize = MeasureSpec.getSize(heightSpec);
            int hMode = MeasureSpec.getMode(heightSpec);

            switch (hMode) {
                case MeasureSpec.AT_MOST:
                    newHeightSpec = MeasureSpec.makeMeasureSpec(Math.min(hSize, maxHeight), MeasureSpec.AT_MOST);
                    break;
                case MeasureSpec.UNSPECIFIED:
                    newHeightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                    break;
                case MeasureSpec.EXACTLY:
                    newHeightSpec = MeasureSpec.makeMeasureSpec(Math.min(hSize, maxHeight), MeasureSpec.EXACTLY);
                    break;
            }
        }
        super.onMeasure(widthSpec, newHeightSpec);
    }
}