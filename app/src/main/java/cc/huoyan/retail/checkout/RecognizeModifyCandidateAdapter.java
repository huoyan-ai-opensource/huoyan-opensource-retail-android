package cc.huoyan.retail.checkout;

import cc.huoyan.retail.common.ui.RetailAdapter;
import cc.huoyan.retail.common.ui.RowViewHolder;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.common.LocalizedText;

import java.util.Objects;


/** 改品宫格适配器，呈现商品信息和相似候选标签，选择状态仅在主线程更新。 */
public class RecognizeModifyCandidateAdapter extends RetailAdapter<RecognizeModifyCandidate> {
    /** 当前选中的商品编码，以底色显示选择状态。 */
    private String selectedPlu;

    /** 使用与学习选择器共享的三层商品卡片。 */
    public RecognizeModifyCandidateAdapter() {
        super(R.layout.item_goods_choice);
    }

    /** 在查询结果绑定前设置当前选择。 */
    public void setSelectedPlu(String selectedPlu) {
        this.selectedPlu = selectedPlu;
    }

    /** 只刷新原选择与新选择两张卡片，避免整页重绑。 */
    public void refreshSelection(String selectedPlu) {
        String previous = this.selectedPlu;
        if (Objects.equals(previous, selectedPlu)) {
            return;
        }
        this.selectedPlu = selectedPlu;
        for (int i = 0; i < getItemCount(); i++) {
            GoodsEntity goods = getItem(i).getGoods();
            String plu = goods == null ? null : goods.getPlu();
            if (Objects.equals(plu, previous) || Objects.equals(plu, selectedPlu)) {
                notifyItemChanged(i, "selection");
            }
        }
    }

    /** 绑定商品三层信息与选择底色。 */
    @Override
    protected void convert(@NonNull RowViewHolder holder, RecognizeModifyCandidate item) {
        GoodsEntity goods = item.getGoods();
        boolean selected = goods != null && TextUtils.equals(goods.getPlu(), selectedPlu);
        bindSelection(holder, selected);
        holder.itemView.findViewById(R.id.similarBadge).setVisibility(
                item.isSimilarCandidate() ? android.view.View.VISIBLE : android.view.View.GONE);
        if (goods == null) {
            return;
        }
        holder.setText(R.id.plu, getContext().getString(R.string.goods_plu_prefix) + goods.getPlu())
                .setText(R.id.pluName, goods.getName());
        holder.setText(R.id.price, getContext().getString(R.string.goods_choice_price,
                java.math.BigDecimal.valueOf(goods.getPrice(), 2).toPlainString(),
                LocalizedText.unit(getContext(), goods.getUnit())));
    }

    /** 局部刷新仅更新选择状态。 */
    @Override
    protected void convert(@NonNull RowViewHolder holder, RecognizeModifyCandidate item,
            java.util.List<? extends Object> payloads) {
        if (payloads.isEmpty()) {
            convert(holder, item);
            return;
        }
        GoodsEntity goods = item.getGoods();
        boolean selected = goods != null && TextUtils.equals(goods.getPlu(), selectedPlu);
        bindSelection(holder, selected);
    }

    /** 通过背景突出选中卡片，与橙色相似候选标签区分。 */
    private void bindSelection(RowViewHolder holder, boolean selected) {
        holder.itemView.setSelected(selected);
        holder.itemView.findViewById(R.id.main).setSelected(selected);
        // XML clipToOutline 仅 API 31 起生效，使用 API 21 起可用的方法覆盖旧设备。
        holder.itemView.findViewById(R.id.main).setClipToOutline(true);
    }
}
