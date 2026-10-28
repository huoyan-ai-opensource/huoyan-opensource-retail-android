package cc.huoyan.retail.goods;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.pricing.PricingMode;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;

/** 商品管理的计价筛选与稳定排序，分页在结果上执行。 */
public final class ManagedGoodsFilter {
    public enum Order { NEWEST, NAME, PLU, PRICE_ASC, PRICE_DESC, IMAGES_ASC, IMAGES_DESC }
    public enum Pricing { ALL, WEIGHT, COUNT }

    private ManagedGoodsFilter() { }

    /** 在搜索结果中筛选计价类型并排序，保留原列表；同价或同名时按 PLU 与 ID 稳定排列。 */
    public static List<GoodsEntity> apply(List<GoodsEntity> source, Order order, Pricing pricing) {
        return apply(source, order, pricing, Collections.emptyMap());
    }

    /** 按完整学习统计排序；未读取成功的计数在升降序中均放在末尾，不当作零。 */
    public static List<GoodsEntity> apply(List<GoodsEntity> source, Order order, Pricing pricing,
                                          Map<String, Long> imageCounts) {
        List<GoodsEntity> result = new ArrayList<>();
        for (GoodsEntity goods : source) {
            PricingMode mode = PricingMode.fromUnit(goods.getUnit());
            if (pricing == Pricing.ALL || (pricing == Pricing.COUNT && mode == PricingMode.COUNT)
                    || (pricing == Pricing.WEIGHT && mode == PricingMode.WEIGHT)) result.add(goods);
        }
        Collator names = Collator.getInstance(Locale.CHINA);
        Comparator<GoodsEntity> plu = Comparator.comparing(g -> text(g.getPlu()), String.CASE_INSENSITIVE_ORDER);
        Comparator<GoodsEntity> comparator;
        switch (order) {
            case NAME: comparator = (a, b) -> names.compare(text(a.getName()), text(b.getName())); break;
            case PLU: comparator = plu; break;
            case PRICE_ASC: comparator = Comparator.comparingLong(GoodsEntity::getPrice); break;
            case PRICE_DESC: comparator = Comparator.comparingLong(GoodsEntity::getPrice).reversed(); break;
            case IMAGES_ASC:
            case IMAGES_DESC:
                Comparator<Long> countOrder = order == Order.IMAGES_ASC
                        ? Comparator.naturalOrder() : Comparator.reverseOrder();
                comparator = Comparator.comparing(g -> imageCounts.get(g.getPlu()),
                        Comparator.nullsLast(countOrder));
                break;
            default: comparator = Comparator.comparing((GoodsEntity g) -> text(g.getCreate_time())).reversed().thenComparing(ManagedGoodsFilter::id, Comparator.reverseOrder()); break;
        }
        result.sort(comparator.thenComparing(plu).thenComparing(ManagedGoodsFilter::id));
        return result;
    }

    /** 雪花 ID 按数值排序，同时间商品保持稳定顺序。 */
    private static java.math.BigInteger id(GoodsEntity goods) {
        return goods.getGoodsId() == null ? java.math.BigInteger.valueOf(-1) : new java.math.BigInteger(goods.getGoodsId());
    }
    private static String text(String value) { return value == null ? "" : value; }
}
