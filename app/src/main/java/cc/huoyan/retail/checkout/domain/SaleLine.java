package cc.huoyan.retail.checkout.domain;

import cc.huoyan.retail.pricing.PricingMode;

/** 从页面清单复制的计价输入，不持有数据库实体、识别框或图片。 */
public final class SaleLine {
    public final String plu;
    public final String name;
    public final long priceCent;
    /** 单件商品皮重，单位克；计价快照不持有 SDK 商品对象。 */
    public final long tareWeightGrams;
    public final int count;
    public final PricingMode mode;
    public final boolean unmatched;
    public final boolean corrected;
    public final String originalPlu;

    /** 创建独立的商品条目，缺省数量按一件处理。 */
    public SaleLine(String plu, String name, long priceCent, long tareWeightGrams, int count, PricingMode mode,
            boolean unmatched, boolean corrected, String originalPlu) {
        this.plu = plu;
        this.name = name;
        this.priceCent = priceCent;
        this.tareWeightGrams = tareWeightGrams;
        this.count = Math.max(1, count);
        this.mode = mode;
        this.unmatched = unmatched;
        this.corrected = corrected;
        this.originalPlu = originalPlu;
    }

    boolean valid() { return plu != null && !plu.isEmpty() && priceCent > 0; }
}
