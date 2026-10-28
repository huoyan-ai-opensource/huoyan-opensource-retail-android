package cc.huoyan.retail.goods;

import cc.huoyan.retail.common.ui.RetailAdapter;
import cc.huoyan.retail.common.ui.RowViewHolder;

import android.graphics.Color;

import androidx.annotation.NonNull;

import cc.huoyan.retail.R;
import cc.huoyan.retail.checkout.GoodsVo;

public class GoodsAdapter extends RetailAdapter<GoodsVo> {
    public GoodsAdapter() {
        super(R.layout.adapter_goods);
    }

    @Override
    protected void convert(@NonNull RowViewHolder baseViewHolder, GoodsVo vo) {
        int position = getItemPosition(vo);
        baseViewHolder.setBackgroundColor(R.id.background, Color.parseColor(position % 2 == 1 ? "#FFFFFF" : "#163F3EED"))
                .setText(R.id.goodsName, vo.getGoods().getName())
                .setText(R.id.count, vo.getCount().toString())
        ;
        java.math.BigDecimal amount = java.math.BigDecimal.valueOf(vo.getAmount(), 2);
        baseViewHolder.setText(R.id.amount, getContext().getString(R.string.amount_currency_format, amount.toPlainString()))
                .setText(R.id.subTotal, getContext().getString(R.string.amount_currency_format, amount.multiply(java.math.BigDecimal.valueOf(vo.getCount())).toPlainString()));
    }
}
