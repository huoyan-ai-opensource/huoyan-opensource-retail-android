package cc.huoyan.retail.checkout.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不可变收银小票；金额以分保存，供打印及反馈使用。
 */
public final class WeightedSaleResult {
    private final String saleSessionId;
    private final BigDecimal weightKg;
    private final long unitPriceCent;
    private final long totalAmountCent;
    private final int itemKindCount;
    private final int detectedPieceCount;
    private final List<Item> items;

    /** 固定本笔交易的重量、金额和商品清单。 */
    public WeightedSaleResult(String saleSessionId, BigDecimal weightKg, long unitPriceCent,
                              long totalAmountCent, int itemKindCount, int detectedPieceCount,
                              List<Item> items) {
        this.saleSessionId = saleSessionId;
        this.weightKg = weightKg;
        this.unitPriceCent = unitPriceCent;
        this.totalAmountCent = totalAmountCent;
        this.itemKindCount = itemKindCount;
        this.detectedPieceCount = detectedPieceCount;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    /** 返回收银会话标识。 */
    public String getSaleSessionId() { return saleSessionId; }
    /** 返回计价重量，单位千克。 */
    public BigDecimal getWeightKg() { return weightKg; }
    /** 返回称重统一单价，单位分。 */
    public long getUnitPriceCent() { return unitPriceCent; }
    /** 返回总金额，单位分。 */
    public long getTotalAmountCent() { return totalAmountCent; }
    /** 返回商品种类数。 */
    public int getItemKindCount() { return itemKindCount; }
    /** 返回商品件数。 */
    public int getDetectedPieceCount() { return detectedPieceCount; }
    /** 返回不可变商品清单。 */
    public List<Item> getItems() { return items; }

    /** 判断是否为同一收银会话的相同小票内容，忽略重复秤回调创建的对象身份。 */
    public boolean isSameReceipt(WeightedSaleResult other) {
        if (other == null || !java.util.Objects.equals(saleSessionId, other.saleSessionId)
                || weightKg.compareTo(other.weightKg) != 0 || unitPriceCent != other.unitPriceCent
                || totalAmountCent != other.totalAmountCent || itemKindCount != other.itemKindCount
                || detectedPieceCount != other.detectedPieceCount || items.size() != other.items.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            Item left = items.get(i), right = other.items.get(i);
            if (!java.util.Objects.equals(left.plu, right.plu) || !java.util.Objects.equals(left.name, right.name)
                    || left.mode != right.mode || left.priceCent != right.priceCent || left.detectedCount != right.detectedCount
                    || left.corrected != right.corrected
                    || !java.util.Objects.equals(left.originalClazzName, right.originalClazzName)) return false;
        }
        return true;
    }

    public static final class Item {
        /** 原始计价方式，区分计件数量与整秤重量。 */
        private final cc.huoyan.retail.pricing.PricingMode mode;
        private final String plu;
        private final String name;
        private final long priceCent;
        private final int detectedCount;
        private final boolean corrected;
        private final String originalClazzName;

        /** 创建小票商品快照。 */
        public Item(String plu, String name, long priceCent, int detectedCount,
                    boolean corrected, String originalClazzName, cc.huoyan.retail.pricing.PricingMode mode) {
            this.mode = mode;
            this.plu = plu;
            this.name = name;
            this.priceCent = priceCent;
            this.detectedCount = detectedCount;
            this.corrected = corrected;
            this.originalClazzName = originalClazzName;
        }

        /** 返回商品计价方式。 */
        public cc.huoyan.retail.pricing.PricingMode getMode() { return mode; }

        /** 返回商品编码。 */
        public String getPlu() { return plu; }
        /** 返回商品名称。 */
        public String getName() { return name; }
        /** 返回商品单价，单位分。 */
        public long getPriceCent() { return priceCent; }
        /** 返回该商品数量。 */
        public int getDetectedCount() { return detectedCount; }
        /** 返回是否经过人工改品。 */
        public boolean isCorrected() { return corrected; }
        /** 返回原始识别编码。 */
        public String getOriginalClazzName() { return originalClazzName; }
    }
}
