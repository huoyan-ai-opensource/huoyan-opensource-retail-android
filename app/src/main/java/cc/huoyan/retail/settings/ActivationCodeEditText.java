package cc.huoyan.retail.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatEditText;

/** 分段输入的透明编辑层；主线程定位真实光标，系统输入法始终关闭。 */
public final class ActivationCodeEditText extends AppCompatEditText {
    /** 当前视图生命周期中的四个展示框。 */
    private TextView[] segments;
    /** 光标变化通知，仅绑定当前弹窗视图。 */
    private Runnable selectionListener;
    /** 光标画笔，由主线程绘制。 */
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** XML 创建编辑层，保留物理键盘和无障碍编辑能力。 */
    public ActivationCodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setShowSoftInputOnFocus(false);
        setLongClickable(false);
        setTextIsSelectable(false);
        cursorPaint.setColor(0xFF3F3EED);
        cursorPaint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
    }
    /** 绑定展示位置与光标监听；视图销毁时传 null 释放引用。 */
    public void bindSegments(TextView[] boxes, Runnable listener) {
        segments = boxes;
        selectionListener = listener;
    }
    /** 点按分段文字定位插入点，不调用系统选择菜单或软键盘。 */
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && segments != null) {
            requestFocus();
            for (int i = 0; i < segments.length; i++) {
                TextView box = segments[i];
                if (event.getX() <= box.getRight() || i == segments.length - 1) {
                    int count = Math.max(0, Math.min(4, length() - i * 4));
                    float unit = box.getPaint().measureText("M");
                    float start = box.getLeft() + (box.getWidth() - count * unit) / 2;
                    int offset = Math.max(0, Math.min(count, Math.round((event.getX() - start) / unit)));
                    setSelection(Math.min(length(), i * 4 + offset));
                    break;
                }
            }
            performClick();
        }
        return true;
    }
    /** 为无障碍服务保留标准点击语义。 */
    @Override public boolean performClick() { return super.performClick(); }
    /** 物理键盘、无障碍和自定义按键共用同一选区。 */
    @Override protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        if (selectionListener != null) selectionListener.run();
        invalidate();
    }
    /** 在真实插入位置绘制分段光标，完整输入默认不额外显示光标。 */
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (segments == null || getSelectionStart() < 0 || length() == 16 && getSelectionStart() == 16) return;
        int position = getSelectionStart();
        TextView box = segments[Math.min(3, position / 4)];
        int count = Math.max(0, Math.min(4, length() - position / 4 * 4));
        float unit = box.getPaint().measureText("M");
        float x = box.getLeft() + (box.getWidth() - count * unit) / 2 + position % 4 * unit;
        float half = 12 * getResources().getDisplayMetrics().density;
        canvas.drawLine(x, getHeight() / 2f - half, x, getHeight() / 2f + half, cursorPaint);
    }
}
