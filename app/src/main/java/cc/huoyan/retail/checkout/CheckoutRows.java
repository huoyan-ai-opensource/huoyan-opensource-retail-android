package cc.huoyan.retail.checkout;

import cc.huoyan.retail.checkout.domain.SaleLine;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.pricing.PricingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 页面商品清单与业务输入的转换，识别框及修正标记停留在界面边界。 */
final class CheckoutRows {
    private CheckoutRows() {}
    static PricingMode resolvePricingMode(GoodsEntity goods) {
        return goods == null ? PricingMode.WEIGHT : PricingMode.fromUnit(goods.getUnit());
    }

    static boolean containsVirtualGoods(List<GoodsVo> goodsVoList) {
        if (goodsVoList == null || goodsVoList.isEmpty()) {
            return false;
        }
        for (GoodsVo goodsVo : goodsVoList) {
            if (goodsVo != null && goodsVo.isUnmatched()) {
                return true;
            }
        }
        return false;
    }


    static GoodsVo findCountConfirmGoodsVo(List<GoodsVo> goodsVoList, String focusPlu) {
        if (goodsVoList == null || goodsVoList.isEmpty()) {
            return null;
        }
        GoodsVo firstCount = null;
        for (GoodsVo goodsVo : goodsVoList) {
            if (goodsVo == null || goodsVo.isUnmatched()
                    || resolvePricingMode(goodsVo.getGoods()) != PricingMode.COUNT) {
                // 虚拟商品（无匹配识别项）不参与计件确认分发
                continue;
            }
            if (firstCount == null) {
                firstCount = goodsVo;
            }
            if (focusPlu != null && goodsVo.getGoods() != null && focusPlu.equals(goodsVo.getGoods().getPlu())) {
                return goodsVo;
            }
        }
        return firstCount;
    }

    static List<SaleLine> toSaleLines(List<GoodsVo> rows, Set<RecognitionSelection> corrected) {
        List<SaleLine> result = new ArrayList<>();
        if (rows == null) return result;
        for (GoodsVo row : rows) {
            if (row == null) continue;
            GoodsEntity goods = row.getGoods();
            boolean changed = false;
            String original = "";
            if (row.getRecognitionSelection() != null) {
                for (RecognitionSelection item : row.getRecognitionSelection()) {
                    if (item == null) continue;
                    changed |= corrected.contains(item);
                    if (original == null || original.isEmpty()) original = item.getOriginalClazz();
                }
            }
            result.add(new SaleLine(goods == null ? null : goods.getPlu(), goods == null ? null : goods.getName(),
                    goods == null ? 0 : goods.getPrice(), goods == null ? 0 : goods.getTareWeight(),
                    row.getCount() == null ? 1 : row.getCount(),
                    resolvePricingMode(goods), row.isUnmatched(), changed, original));
        }
        return result;
    }
}
