package cc.huoyan.retail.checkout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.pricing.CartCalculator;
import cc.huoyan.retail.pricing.CartItem;
import cc.huoyan.retail.pricing.CartTotal;
import cc.huoyan.retail.pricing.PricingMode;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/** 页面清单映射、计件分发与混合结算回归。 */
public class CheckoutRowsTest {
    /** SDK 商品的整数克皮重随清单复制，后续商品修改不污染业务快照。 */
    @Test public void saleLineCopiesTareInGrams() {
        GoodsVo row = vo("W", 0);
        row.getGoods().setTareWeight(25);
        cc.huoyan.retail.checkout.domain.SaleLine line = CheckoutRows.toSaleLines(
                Collections.singletonList(row), Collections.emptySet()).get(0);
        row.getGoods().setTareWeight(50);
        assertEquals(25L, line.tareWeightGrams);
    }

    private static List<CartItem> cartItemsFromGoodsVos(List<GoodsVo> rows, double weight) {
        return cc.huoyan.retail.checkout.domain.SalePricing.cartItems(
                CheckoutRows.toSaleLines(rows, Collections.emptySet()), BigDecimal.valueOf(weight));
    }


    @Test
    public void countReceiptDoesNotWaitForScale() {
        assertTrue(cc.huoyan.retail.checkout.domain.SalePricing.isReceiptReady(0, false, false, new BigDecimal("0")));
        assertTrue(cc.huoyan.retail.checkout.domain.SalePricing.isReceiptReady(0, true, true, new BigDecimal("1")));
    }

    @Test
    public void weightedReceiptStillRequiresStableScale() {
        assertFalse(cc.huoyan.retail.checkout.domain.SalePricing.isReceiptReady(520, false, true, new BigDecimal("1")));
        assertTrue(cc.huoyan.retail.checkout.domain.SalePricing.isReceiptReady(520, true, true, new BigDecimal("1")));
    }


    @Test
    public void unstableOrDisconnectedScaleRevokesWeightedReceipt() {
        assertTrue(cc.huoyan.retail.checkout.domain.SalePricing.isReceiptReady(520, true, true, new BigDecimal("1")));
        assertFalse(cc.huoyan.retail.checkout.domain.SalePricing.isReceiptReady(520, false, true, new BigDecimal("1")));
        assertFalse(cc.huoyan.retail.checkout.domain.SalePricing.isReceiptReady(520, true, false, new BigDecimal("1")));
        assertFalse(cc.huoyan.retail.checkout.domain.SalePricing.isReceiptReady(520, true, true, new BigDecimal("0.019")));
        assertTrue(cc.huoyan.retail.checkout.domain.SalePricing.isReceiptReady(520, true, true, new BigDecimal("0.020")));
    }

    private static GoodsEntity goods(String plu, int unit) {
        return goods(plu, unit, 100);
    }

    private static GoodsEntity goods(String plu, int unit, int price) {
        GoodsEntity goods = new GoodsEntity();
        goods.setGoodsId("1"); goods.setPlu(plu); goods.setName("name-" + plu); goods.setPrice(price); goods.setUnit(unit);
        return goods;
    }

    private static GoodsVo vo(String plu, int unit) {
        return new GoodsVo(1, 100, goods(plu, unit), null);
    }

    private static GoodsVo vo(String plu, int unit, int price, Integer count) {
        return new GoodsVo(count, price, goods(plu, unit, price), null);
    }

    private static RecognitionSelection detectItem(String clazzName) {
        RecognitionSelection item = new RecognitionSelection(new cc.huoyan.sdk.client.model.RecognitionItem(clazzName, 0, 1, 2, 3, 4));
        return item;
    }

    @Test
    public void resolvePricingMode_nullGoods_fallsBackToWeight() {
        assertEquals(PricingMode.WEIGHT, CheckoutRows.resolvePricingMode(null));
    }

    @Test
    public void resolvePricingMode_unitFive_returnsCount() {
        assertEquals(PricingMode.COUNT, CheckoutRows.resolvePricingMode(goods("1001", 5)));
    }

    @Test
    public void resolvePricingMode_nonFiveUnit_returnsWeight() {
        assertEquals(PricingMode.WEIGHT, CheckoutRows.resolvePricingMode(goods("1002", 0)));
        assertEquals(PricingMode.WEIGHT, CheckoutRows.resolvePricingMode(goods("1003", 1)));
        assertEquals(PricingMode.WEIGHT, CheckoutRows.resolvePricingMode(goods("1004", 6)));
    }

    @Test
    public void buildVirtualGoodsVo_nameAndPluAreClazzName_priceZero_unmatchedTrue() {
        RecognitionSelection item = detectItem("9999");
        GoodsVo virtual = cc.huoyan.retail.checkout.integration.CheckoutGoodsLoader.unmatched("9999", Collections.singletonList(item));

        assertTrue(virtual.isUnmatched());
        assertEquals("9999", virtual.getGoods().getPlu());
        assertEquals("9999", virtual.getGoods().getName());
        assertEquals(0, virtual.getGoods().getPrice());
        assertEquals(0, virtual.getGoods().getUnit());
        assertEquals(Integer.valueOf(1), virtual.getCount());
        assertSame(item, virtual.getRecognitionSelection().get(0));
    }

    @Test
    public void buildVirtualGoodsVo_nullItems_countZero() {
        GoodsVo virtual = cc.huoyan.retail.checkout.integration.CheckoutGoodsLoader.unmatched("9999", null);
        assertTrue(virtual.isUnmatched());
        assertEquals(Integer.valueOf(0), virtual.getCount());
    }

    @Test
    public void findCountConfirmGoodsVo_skipsUnmatchedVirtualGoods() {
        // 虚拟商品（无匹配识别项）不参与计件确认分发
        GoodsVo virtualCount = cc.huoyan.retail.checkout.integration.CheckoutGoodsLoader.unmatched("9999",
                Collections.singletonList(detectItem("9999")));
        GoodsVo realCount = vo("1001", 5);

        assertNull(CheckoutRows.findCountConfirmGoodsVo(Collections.singletonList(virtualCount), null));
        assertSame(realCount, CheckoutRows.findCountConfirmGoodsVo(Arrays.asList(virtualCount, realCount), null));
    }

    @Test
    public void containsVirtualGoods_detectsUnmatchedOnly() {
        GoodsVo virtual = cc.huoyan.retail.checkout.integration.CheckoutGoodsLoader.unmatched("9999",
                Collections.singletonList(detectItem("9999")));
        GoodsVo real = vo("1001", 5);

        assertTrue(CheckoutRows.containsVirtualGoods(Collections.singletonList(virtual)));
        assertTrue(CheckoutRows.containsVirtualGoods(Arrays.asList(real, virtual)));
        assertFalse(CheckoutRows.containsVirtualGoods(Collections.singletonList(real)));
        assertFalse(CheckoutRows.containsVirtualGoods(new ArrayList<>()));
        assertFalse(CheckoutRows.containsVirtualGoods(null));
    }

    @Test
    public void findCountConfirmGoodsVo_nullOrEmptyList_returnsNull() {
        assertNull(CheckoutRows.findCountConfirmGoodsVo(null, null));
        assertNull(CheckoutRows.findCountConfirmGoodsVo(new ArrayList<>(), null));
    }

    @Test
    public void findCountConfirmGoodsVo_weightOnlyList_returnsNull() {
        List<GoodsVo> list = Arrays.asList(vo("1002", 0), vo("1003", 1));

        assertNull(CheckoutRows.findCountConfirmGoodsVo(list, null));
    }

    @Test
    public void findCountConfirmGoodsVo_noFocus_returnsFirstCountVo() {
        GoodsVo weightVo = vo("1002", 0);
        GoodsVo firstCountVo = vo("1001", 5);
        GoodsVo secondCountVo = vo("1005", 5);
        List<GoodsVo> list = Arrays.asList(weightVo, firstCountVo, secondCountVo);

        assertSame(firstCountVo, CheckoutRows.findCountConfirmGoodsVo(list, null));
    }

    @Test
    public void findCountConfirmGoodsVo_focusPlu_returnsMatchingCountVo() {
        GoodsVo firstCountVo = vo("1001", 5);
        GoodsVo secondCountVo = vo("1005", 5);
        List<GoodsVo> list = Arrays.asList(firstCountVo, secondCountVo);

        assertSame(secondCountVo, CheckoutRows.findCountConfirmGoodsVo(list, "1005"));
    }

    @Test
    public void findCountConfirmGoodsVo_focusOnWeightPlu_fallsBackToFirstCount() {
        GoodsVo weightVo = vo("1002", 0);
        GoodsVo countVo = vo("1001", 5);
        List<GoodsVo> list = Arrays.asList(weightVo, countVo);

        assertSame(countVo, CheckoutRows.findCountConfirmGoodsVo(list, "1002"));
    }

    @Test
    public void findCountConfirmGoodsVo_skipsNullEntries() {
        List<GoodsVo> list = Arrays.asList(null, vo("1001", 5), null);

        assertSame(list.get(1), CheckoutRows.findCountConfirmGoodsVo(list, null));
    }

    @Test
    public void findCountConfirmGoodsVo_countGoodsWithNullEntity_skipped() {
        GoodsVo nullGoodsVo = new GoodsVo(1, 100, null, null);
        List<GoodsVo> list = Collections.singletonList(nullGoodsVo);

        assertNull(CheckoutRows.findCountConfirmGoodsVo(list, null));
    }

    private static int legacyWeightedAmountCent(double weightKg, int unitPriceCent) {
        return BigDecimal.valueOf(weightKg)
                .multiply(BigDecimal.valueOf(unitPriceCent))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    @Test
    public void cartItemsFromGoodsVos_nullOrEmpty_returnsEmpty() {
        assertTrue(cartItemsFromGoodsVos(null, 0.5d).isEmpty());
        assertTrue(cartItemsFromGoodsVos(new ArrayList<>(), 0.5d).isEmpty());
    }

    @Test
    public void cartItemsFromGoodsVos_pureWeight_aggregatesToSingleWeightLine() {
        List<GoodsVo> list = Arrays.asList(
                vo("2001", 0, 990, 2),
                vo("2002", 1, 990, 3));

        List<CartItem> items = cartItemsFromGoodsVos(list, 0.356d);

        assertEquals(1, items.size());
        CartItem weightLine = items.get(0);
        assertEquals(PricingMode.WEIGHT, weightLine.getPricingMode());
        assertEquals("2001", weightLine.getPlu());
        assertEquals(990, weightLine.getUnitPriceCent());
        assertEquals(0, weightLine.getWeightKg().compareTo(BigDecimal.valueOf(0.356d)));
        assertNull(weightLine.getQuantity());
    }

    @Test
    public void cartItemsFromGoodsVos_weightRowsDifferentPrice_keepsFirstWeightPrice() {
        // 映射层只取首个称重行单价；称重行同价拦截由 evaluateWeightedSaleResult 负责
        List<GoodsVo> list = Arrays.asList(
                vo("2001", 0, 990, 1),
                vo("2002", 0, 1580, 1));

        List<CartItem> items = cartItemsFromGoodsVos(list, 0.5d);

        assertEquals(1, items.size());
        assertEquals(990, items.get(0).getUnitPriceCent());
    }

    @Test
    public void cartItemsFromGoodsVos_countRow_quantityFromCount() {
        List<GoodsVo> list = Collections.singletonList(vo("1001", 5, 500, 3));

        List<CartItem> items = cartItemsFromGoodsVos(list, 0.356d);

        assertEquals(1, items.size());
        CartItem countLine = items.get(0);
        assertEquals(PricingMode.COUNT, countLine.getPricingMode());
        assertEquals("1001", countLine.getPlu());
        assertEquals(500, countLine.getUnitPriceCent());
        assertEquals(0, countLine.getQuantity().compareTo(BigDecimal.valueOf(3)));
        assertNull(countLine.getWeightKg());
    }

    @Test
    public void cartItemsFromGoodsVos_countNullOrZero_fallbackToOne() {
        List<GoodsVo> list = Arrays.asList(
                vo("1001", 5, 500, null),
                vo("1002", 5, 250, 0));

        List<CartItem> items = cartItemsFromGoodsVos(list, 0.1d);

        assertEquals(2, items.size());
        assertEquals(0, items.get(0).getQuantity().compareTo(BigDecimal.ONE));
        assertEquals(0, items.get(1).getQuantity().compareTo(BigDecimal.ONE));
    }

    @Test
    public void cartItemsFromGoodsVos_mixed_countLinesPlusSingleWeightLine() {
        List<GoodsVo> list = Arrays.asList(
                vo("1001", 5, 300, 2),
                vo("2001", 0, 990, 1),
                vo("1002", 5, 250, 4),
                vo("2002", 1, 990, 5));

        List<CartItem> items = cartItemsFromGoodsVos(list, 0.4d);

        assertEquals(3, items.size());
        assertEquals(PricingMode.COUNT, items.get(0).getPricingMode());
        assertEquals("1001", items.get(0).getPlu());
        assertEquals(PricingMode.COUNT, items.get(1).getPricingMode());
        assertEquals("1002", items.get(1).getPlu());
        assertEquals(PricingMode.WEIGHT, items.get(2).getPricingMode());
        assertEquals(0, items.get(2).getWeightKg().compareTo(BigDecimal.valueOf(0.4d)));
    }

    @Test
    public void cartItemsFromGoodsVos_skipsInvalidRows() {
        GoodsVo emptyPluVo = new GoodsVo(1, 100, goods("", 0, 100), null);
        GoodsVo nullGoodsVo = new GoodsVo(1, 100, null, null);
        List<GoodsVo> list = Arrays.asList(
                null,
                nullGoodsVo,
                vo("3001", 0, 0, 1),
                vo("3002", 0, -5, 1),
                emptyPluVo);

        assertTrue(cartItemsFromGoodsVos(list, 0.5d).isEmpty());
    }

    @Test
    public void computeTotal_pureWeight_matchesLegacyFormula() {
        // 纯称重回归：CartCalculator 金额必须与改造前 evaluateWeightedSaleResult 的
        // 旧公式（秤盘重量×单价 HALF_UP 到分）逐位一致；0.356×990=352 为规划文档示例
        double[][] cases = {
                {0.356d, 990d}, {0.5d, 990d}, {0.123d, 1234d},
                {0.001d, 1d}, {1.0d, 1580d}, {2.049d, 1999d}, {0.02d, 9999d}
        };
        for (double[] testCase : cases) {
            double weightKg = testCase[0];
            int unitPriceCent = (int) testCase[1];
            List<GoodsVo> list = Arrays.asList(
                    vo("2001", 0, unitPriceCent, 2),
                    vo("2002", 1, unitPriceCent, 3));

            CartTotal total = CartCalculator.computeTotal(
                    cartItemsFromGoodsVos(list, weightKg), null);

            assertEquals(legacyWeightedAmountCent(weightKg, unitPriceCent), total.getTotalAmountCent());
            assertEquals(total.getTotalAmountCent(), total.getPayableAmountCent());
        }
        List<GoodsVo> docExample = Collections.singletonList(vo("2001", 0, 990, 1));
        assertEquals(352L, CartCalculator.computeTotal(
                cartItemsFromGoodsVos(docExample, 0.356d), null).getTotalAmountCent());
    }

    @Test
    public void computeTotal_pureCount_amountIsQuantityTimesPrice() {
        List<GoodsVo> list = Arrays.asList(
                vo("1001", 5, 500, 3),
                vo("1002", 5, 250, 2));

        CartTotal total = CartCalculator.computeTotal(
                cartItemsFromGoodsVos(list, 0.356d), null);

        assertEquals(2000L, total.getTotalAmountCent());
        assertEquals(2000L, total.getPayableAmountCent());
        assertEquals(5L, total.getTotalPieceCount());
        assertEquals(0, total.getTotalWeightKg().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void computeTotal_mixed_sumsCountAndWeightPortions() {
        List<GoodsVo> list = Arrays.asList(
                vo("1001", 5, 300, 2),
                vo("2001", 0, 990, 1));

        CartTotal total = CartCalculator.computeTotal(
                cartItemsFromGoodsVos(list, 0.4d), null);

        assertEquals(600L + legacyWeightedAmountCent(0.4d, 990), total.getTotalAmountCent());
        assertEquals(3L, total.getTotalPieceCount());
        assertEquals(0, total.getTotalWeightKg().compareTo(BigDecimal.valueOf(0.4d)));
    }

    @Test
    public void computeTotal_emptyCart_zero() {
        CartTotal total = CartCalculator.computeTotal(
                cartItemsFromGoodsVos(new ArrayList<>(), 0.356d), null);

        assertEquals(0L, total.getTotalAmountCent());
        assertEquals(0L, total.getTotalPieceCount());
        assertEquals(0L, total.getPayableAmountCent());
    }

}
