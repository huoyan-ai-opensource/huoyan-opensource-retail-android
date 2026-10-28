package cc.huoyan.retail.checkout.domain;

import cc.huoyan.retail.pricing.CartCalculator;
import cc.huoyan.retail.pricing.CartItem;
import cc.huoyan.retail.pricing.PricingGate;
import cc.huoyan.retail.pricing.PricingMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 整秤同价校验与混合购物车报价，不依赖设备或页面状态。 */
public final class SalePricing {
    private SalePricing() {}

    static final class Quote {
        final Set<String> conflicts = new LinkedHashSet<>();
        boolean invalidGoods;
        /** 已匹配商品价格未填写或为零，优先于称重同价冲突提示。 */
        boolean missingPrice;
        WeightedSaleResult receipt;
        /** 可正常计价时的整秤商品皮重，kg；冲突、禁用及无皮重均为零。 */
        BigDecimal tareKg = BigDecimal.ZERO;
    }

    static Quote quote(String sessionId, List<SaleLine> lines, BigDecimal weight, boolean weighing) {
        Quote quote = new Quote();
        List<SaleLine> priced = new ArrayList<>();
        List<WeightedSaleResult.Item> items = new ArrayList<>();
        long weightPrice = 0;
        int count = 0;
        for (SaleLine line : lines) {
            if (line.unmatched) continue;
            if (line.priceCent <= 0) quote.missingPrice = true;
            if (!line.valid()) { quote.invalidGoods = true; continue; }
            if (PricingGate.check(line.mode, weighing) == PricingGate.GateResult.BLOCK) continue;
            if (line.mode == PricingMode.WEIGHT) {
                if (weightPrice == 0) weightPrice = line.priceCent;
                else if (weightPrice != line.priceCent) quote.conflicts.add(line.plu);
            }
            count += line.count;
            priced.add(line);
            items.add(new WeightedSaleResult.Item(line.plu, line.name, line.priceCent, line.count,
                    line.corrected, line.originalPlu, line.mode));
        }
        if (items.isEmpty() || quote.invalidGoods || !quote.conflicts.isEmpty()) return quote;
        for (SaleLine line : priced) {
            if (line.mode == PricingMode.WEIGHT && line.tareWeightGrams > 0)
                quote.tareKg = quote.tareKg.add(BigDecimal.valueOf(line.tareWeightGrams, 3)
                        .multiply(BigDecimal.valueOf(line.count)));
        }
        long amount = CartCalculator.computeTotal(cartItems(priced, weight), null).getTotalAmountCent();
        quote.receipt = new WeightedSaleResult(sessionId, weight, weightPrice, amount,
                items.size(), count, items);
        return quote;
    }

    /** 将有效计价行映射为购物车；整秤重量只收取一次，调用者负责同价与模块校验。 */
    public static List<CartItem> cartItems(List<SaleLine> lines, BigDecimal weight) {
        List<CartItem> items = new ArrayList<>();
        CartItem weighted = null;
        for (SaleLine line : lines) {
            if (line.unmatched || !line.valid()) continue;
            if (line.mode == PricingMode.COUNT) {
                items.add(new CartItem(line.plu, line.name, line.priceCent, line.mode,
                        BigDecimal.valueOf(line.count), null));
            } else if (weighted == null) {
                weighted = new CartItem(line.plu, line.name, line.priceCent, line.mode, null, weight);
            }
        }
        if (weighted != null) items.add(weighted);
        return items;
    }

    /** 计件不依赖秤；称重必须连接、稳定且达到起秤重量。 */
    public static boolean isReceiptReady(long weightPrice, boolean stable, boolean connected, BigDecimal weight) {
        return weightPrice <= 0 || (connected && stable && weight.compareTo(CheckoutSession.MIN_WEIGHT) >= 0);
    }
}
