package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 项目共用的 AndroidX 适配器，只提供列表、空态和点击绑定。所有更新在主线程执行。 */
public abstract class RetailAdapter<T> extends RecyclerView.Adapter<RowViewHolder> {
    private static final int ITEM = 0;
    private static final int EMPTY = 1;
    private final int itemLayout;
    private final List<T> data = new ArrayList<>();
    private final List<Integer> childClickIds = new ArrayList<>();
    private int emptyLayout;
    private Context context;
    private OnItemClickListener itemClickListener;
    private OnItemClickListener childClickListener;
    private GridLayoutManager attachedGrid;
    private GridLayoutManager.SpanSizeLookup originalSpanLookup;
    private GridLayoutManager.SpanSizeLookup emptySpanLookup;

    protected RetailAdapter(@LayoutRes int itemLayout) {
        this.itemLayout = itemLayout;
    }

    /** 返回适配器持有的列表；直接修改后须发送对应的更新通知。 */
    public final List<T> getData() { return data; }

    /** 按数据位置读取条目，空态不属于数据条目。 */
    public final T getItem(int position) { return data.get(position); }

    /** 返回条目在数据列表中的位置。 */
    public final int getItemPosition(T item) { return data.indexOf(item); }

    protected final Context getContext() { return context; }

    /** 复制列表后替换数据，避免调用方清空列表时连带清空适配器。 */
    public void setList(Collection<? extends T> items) {
        List<T> next = items == null ? new ArrayList<>() : new ArrayList<>(items);
        data.clear();
        data.addAll(next);
        notifyDataSetChanged();
    }

    /** 追加一批数据；空态和第一批数据之间正确切换视图类型。 */
    public final void addData(Collection<? extends T> items) {
        if (items == null || items.isEmpty()) return;
        List<T> added = new ArrayList<>(items);
        boolean wasEmpty = showsEmpty();
        int start = data.size();
        data.addAll(added);
        if (wasEmpty) notifyItemRemoved(0);
        notifyItemRangeInserted(start, added.size());
    }

    /** 只通知被删除的条目；点击位置由 ViewHolder 实时读取。 */
    public void removeAt(int position) {
        if (position < 0 || position >= data.size()) return;
        data.remove(position);
        notifyItemRemoved(position);
        if (showsEmpty()) notifyItemInserted(0);
    }

    /** 设置数据为空时展示的整行布局。 */
    public final void setEmptyView(@LayoutRes int layout) {
        emptyLayout = layout;
        notifyDataSetChanged();
    }

    /** 设置条目点击回调。 */
    public final void setOnItemClickListener(OnItemClickListener listener) {
        itemClickListener = listener;
    }

    /** 设置已注册子视图的点击回调。 */
    public final void setOnItemChildClickListener(OnItemClickListener listener) {
        childClickListener = listener;
    }

    protected final void addChildClickViewIds(int... ids) {
        for (int id : ids) childClickIds.add(id);
    }

    private boolean showsEmpty() { return data.isEmpty() && emptyLayout != 0; }

    /** 返回数据条目数，启用空态时空列表占一行。 */
    @Override public final int getItemCount() { return showsEmpty() ? 1 : data.size(); }

    /** 区分普通条目与空态，避免复用错误的布局。 */
    @Override public final int getItemViewType(int position) { return showsEmpty() ? EMPTY : ITEM; }

    /** 安装网格空态的整行跨度规则，普通条目沿用原布局管理器规则。 */
    @Override public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        context = recyclerView.getContext();
        if (recyclerView.getLayoutManager() instanceof GridLayoutManager) {
            GridLayoutManager grid = (GridLayoutManager) recyclerView.getLayoutManager();
            GridLayoutManager.SpanSizeLookup original = grid.getSpanSizeLookup();
            attachedGrid = grid;
            originalSpanLookup = original;
            emptySpanLookup = new GridLayoutManager.SpanSizeLookup() {
                @Override public int getSpanSize(int position) {
                    return showsEmpty() ? grid.getSpanCount() : original.getSpanSize(position);
                }
            };
            grid.setSpanSizeLookup(emptySpanLookup);
        }
    }

    /** 解除适配器时恢复跨度规则，避免布局管理器继续持有旧适配器。 */
    @Override public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        if (attachedGrid != null && attachedGrid.getSpanSizeLookup() == emptySpanLookup) {
            attachedGrid.setSpanSizeLookup(originalSpanLookup);
        }
        attachedGrid = null;
        originalSpanLookup = null;
        emptySpanLookup = null;
        context = null;
        super.onDetachedFromRecyclerView(recyclerView);
    }

    /** 创建条目并安装按实时位置分发的点击监听。 */
    @NonNull @Override public final RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(viewType == EMPTY ? emptyLayout : itemLayout, parent, false);
        RowViewHolder holder = new RowViewHolder(view);
        if (viewType == ITEM) {
            view.setOnClickListener(v -> dispatchClick(holder, v, itemClickListener));
            for (int id : childClickIds) {
                holder.getView(id).setOnClickListener(v -> dispatchClick(holder, v, childClickListener));
            }
        }
        return holder;
    }

    private void dispatchClick(RowViewHolder holder, View view, OnItemClickListener listener) {
        int position = holder.getBindingAdapterPosition();
        if (listener != null && position != RecyclerView.NO_POSITION && position < data.size()) {
            listener.onItemClick(this, view, position);
        }
    }

    /** 绑定普通数据条目，空态不进入业务绑定。 */
    @Override public final void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        if (!showsEmpty()) convert(holder, data.get(position));
    }

    /** 将局部更新载荷交给业务适配器处理。 */
    @Override public final void onBindViewHolder(@NonNull RowViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (!showsEmpty()) convert(holder, data.get(position), payloads);
    }

    protected abstract void convert(@NonNull RowViewHolder holder, T item);

    protected void convert(@NonNull RowViewHolder holder, T item, List<? extends Object> payloads) {
        convert(holder, item);
    }

    /** 条目或子视图的点击事件，位置始终对应当前数据列表。 */
    public interface OnItemClickListener {
        /** 处理有效数据位置上的点击。 */
        void onItemClick(@NonNull RetailAdapter<?> adapter, @NonNull View view, int position);
    }
}
