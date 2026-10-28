package cc.huoyan.retail.common.ui;

import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

/** 缓存行内视图引用，统一项目已有布局的常用赋值操作。 */
public final class RowViewHolder extends RecyclerView.ViewHolder {
    private final SparseArray<View> views = new SparseArray<>();

    /** 持有一个已创建的列表行。 */
    public RowViewHolder(View itemView) { super(itemView); }

    /** 获取并缓存指定子视图。 */
    @SuppressWarnings("unchecked")
    public <V extends View> V getView(int id) {
        View view = views.get(id);
        if (view == null) {
            view = itemView.findViewById(id);
            if (view == null) throw new IllegalArgumentException("列表布局缺少视图：" + id);
            views.put(id, view);
        }
        return (V) view;
    }

    /** 设置文本内容。 */
    public RowViewHolder setText(int id, CharSequence text) {
        this.<TextView>getView(id).setText(text); return this;
    }

    /** 设置文字颜色。 */
    public RowViewHolder setTextColor(int id, int color) {
        this.<TextView>getView(id).setTextColor(color); return this;
    }

    /** 设置背景颜色。 */
    public RowViewHolder setBackgroundColor(int id, int color) {
        getView(id).setBackgroundColor(color); return this;
    }

    /** 设置背景资源。 */
    public RowViewHolder setBackgroundResource(int id, int resource) {
        getView(id).setBackgroundResource(resource); return this;
    }

    /** 设置图片资源。 */
    public RowViewHolder setImageResource(int id, int resource) {
        this.<ImageView>getView(id).setImageResource(resource); return this;
    }

    /** 切换可见性；隐藏时仍占据布局空间。 */
    public RowViewHolder setVisible(int id, boolean visible) {
        getView(id).setVisibility(visible ? View.VISIBLE : View.INVISIBLE); return this;
    }

    /** 切换隐藏状态；隐藏时不占据布局空间。 */
    public RowViewHolder setGone(int id, boolean gone) {
        getView(id).setVisibility(gone ? View.GONE : View.VISIBLE); return this;
    }
}
