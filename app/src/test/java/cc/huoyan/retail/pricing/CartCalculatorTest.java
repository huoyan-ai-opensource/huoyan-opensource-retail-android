package cc.huoyan.retail.pricing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * {@link CartCalculator} 单测矩阵：
 * 单计件/单称重、舍入策略 A、混合聚合、同价混称、叠盘增量、同 SKU 双形态、
 * 折扣口径、防御（空/0/负构造拒绝/小数件数/溢出）、金额回填。
 */
public class CartCalculatorTest {

    private static void assertDecimalEquals(String expected, BigDecimal actual) {
        assertTrue("期望 " + expected + " 实际 " + actual,
                new BigDecimal(expected).compareTo(actual) == 0);
    }

    private static CartItem countItem(String plu, int unitPriceCent, long quantity) {
        return new CartItem(plu, plu + "-名", unitPriceCent, PricingMode.COUNT,
                BigDecimal.valueOf(quantity), null);
    }

    private static CartItem weightItem(String plu, int unitPriceCent, String weightKg) {
        return new CartItem(plu, plu + "-名", unitPriceCent, PricingMode.WEIGHT,
                null, new BigDecimal(weightKg));
    }

    @Test
    public void countOnly_threePieces() {
        CartTotal total = CartCalculator.computeTotal(
                Collections.singletonList(countItem("1001", 1200, 3)), null);

        assertEquals(3600L, total.getTotalAmountCent());
        assertEquals(3L, total.getTotalPieceCount());
        assertDecimalEquals("0", total.getTotalWeightKg());
        assertEquals(3600L, total.getPayableAmountCent());
        assertEquals(0L, total.getDiscountAmountCent());
        assertEquals(3600L, total.getItems().get(0).getAmountCent());
    }

    @Test
    public void weightOnly_0p385kg() {
        CartTotal total = CartCalculator.computeTotal(
                Collections.singletonList(weightItem("2001", 1200, "0.385")), null);

        assertEquals(462L, total.getTotalAmountCent());
        assertEquals(1L, total.getTotalPieceCount());
        assertDecimalEquals("0.385", total.getTotalWeightKg());
        assertEquals(462L, total.getPayableAmountCent());
        assertEquals(462L, total.getItems().get(0).getAmountCent());
    }

    @Test
    public void roundingA_0p333_at100_roundsDownTo33() {
        // 决策 1：逐条目 HALF_UP 到分，33.3 → 33
        CartTotal total = CartCalculator.computeTotal(
                Collections.singletonList(weightItem("2001", 100, "0.333")), null);

        assertEquals(33L, total.getTotalAmountCent());
    }

    @Test
    public void roundingA_0p335_at100_roundsUpTo34() {
        // 决策 1：33.5 → 34（HALF_UP 进位方向）
        CartTotal total = CartCalculator.computeTotal(
                Collections.singletonList(weightItem("2001", 100, "0.335")), null);

        assertEquals(34L, total.getTotalAmountCent());
    }

    @Test
    public void roundingA_multipleEntries_totalEqualsSumOfRoundedAmounts() {
        // 决策 1：合计 = Σ逐条目舍入后金额（0.334→33 + 0.334→33 = 66），
        // 而非合并后一次舍入（0.668→67）
        CartTotal total = CartCalculator.computeTotal(Arrays.asList(
                weightItem("2001", 100, "0.334"),
                weightItem("2001", 100, "0.334")), null);

        assertEquals(33L, total.getItems().get(0).getAmountCent());
        assertEquals(33L, total.getItems().get(1).getAmountCent());
        assertEquals(66L, total.getTotalAmountCent());
    }

    @Test
    public void mixedAggregation_amountPiecesWeight() {
        // 决策 3：金额 = Σ称重 + Σ计件；件数 = 计件数量 + 称重每行 1 件；总重 = Σ称重条目
        CartTotal total = CartCalculator.computeTotal(Arrays.asList(
                countItem("1001", 500, 2),
                weightItem("2001", 1000, "0.5"),
                weightItem("2002", 800, "0.25")), null);

        assertEquals(1000L + 500L + 200L, total.getTotalAmountCent());
        assertEquals(2L + 1L + 1L, total.getTotalPieceCount());
        assertDecimalEquals("0.75", total.getTotalWeightKg());
    }

    @Test
    public void samePriceMixedWeigh_eachEntryComputedIndependently() {
        // 同价混称：两条目各自计价（0.333→33、0.335→34），合计取和
        CartTotal total = CartCalculator.computeTotal(Arrays.asList(
                weightItem("2001", 100, "0.333"),
                weightItem("2001", 100, "0.335")), null);

        assertEquals(33L, total.getItems().get(0).getAmountCent());
        assertEquals(34L, total.getItems().get(1).getAmountCent());
        assertEquals(67L, total.getTotalAmountCent());
        assertDecimalEquals("0.668", total.getTotalWeightKg());
        assertEquals(2L, total.getTotalPieceCount());
    }

    @Test
    public void stackedIncrements_samePlu_aggregatedBySum() {
        // 叠盘增量：同 PLU 两条独立称重条目，逐条计价后聚合
        CartTotal total = CartCalculator.computeTotal(Arrays.asList(
                weightItem("2001", 1000, "0.2"),
                weightItem("2001", 1000, "0.15")), null);

        assertEquals(2, total.getItems().size());
        assertEquals(200L, total.getItems().get(0).getAmountCent());
        assertEquals(150L, total.getItems().get(1).getAmountCent());
        assertEquals(350L, total.getTotalAmountCent());
        assertDecimalEquals("0.35", total.getTotalWeightKg());
    }

    @Test
    public void sameSkuDualForm_computedSeparately_noMerge() {
        // 决策 4：同 SKU 双形态分条独立计价，计算器不合并
        CartTotal total = CartCalculator.computeTotal(Arrays.asList(
                new CartItem("3001", "曲奇", 300, PricingMode.COUNT, BigDecimal.valueOf(2), null),
                new CartItem("3001", "曲奇", 500, PricingMode.WEIGHT, null, new BigDecimal("0.4"))), null);

        assertEquals(2, total.getItems().size());
        assertEquals(600L, total.getItems().get(0).getAmountCent());
        assertEquals(200L, total.getItems().get(1).getAmountCent());
        assertEquals(800L, total.getTotalAmountCent());
        assertEquals(2L + 1L, total.getTotalPieceCount());
        assertDecimalEquals("0.4", total.getTotalWeightKg());
    }

    @Test
    public void discount_1000at0p9_payable900Discount100() {
        // 决策 5：折后应付 = 合计 × 折扣率 HALF_UP；折扣金额 = 原合计 − 折后合计
        CartTotal total = CartCalculator.computeTotal(
                Collections.singletonList(countItem("1001", 1000, 1)), new BigDecimal("0.9"));

        assertEquals(1000L, total.getTotalAmountCent());
        assertEquals(900L, total.getPayableAmountCent());
        assertEquals(100L, total.getDiscountAmountCent());
    }

    @Test
    public void discount_halfUpRoundingOnPayable() {
        // 合计 33 × 0.9 = 29.7 → 30（HALF_UP），折扣金额 = 33 − 30 = 3
        CartTotal total = CartCalculator.computeTotal(
                Collections.singletonList(weightItem("2001", 100, "0.333")), new BigDecimal("0.9"));

        assertEquals(33L, total.getTotalAmountCent());
        assertEquals(30L, total.getPayableAmountCent());
        assertEquals(3L, total.getDiscountAmountCent());
    }

    @Test
    public void discount_nullRate_noDiscount() {
        CartTotal total = CartCalculator.computeTotal(
                Collections.singletonList(countItem("1001", 1000, 1)), null);

        assertEquals(1000L, total.getTotalAmountCent());
        assertEquals(1000L, total.getPayableAmountCent());
        assertEquals(0L, total.getDiscountAmountCent());
    }

    @Test
    public void emptyList_allZero() {
        // 决策 7：空列表 → 全 0 结果
        CartTotal total = CartCalculator.computeTotal(Collections.<CartItem>emptyList(), null);

        assertTrue(total.getItems().isEmpty());
        assertEquals(0L, total.getTotalAmountCent());
        assertEquals(0L, total.getTotalPieceCount());
        assertDecimalEquals("0", total.getTotalWeightKg());
        assertEquals(0L, total.getDiscountAmountCent());
        assertEquals(0L, total.getPayableAmountCent());
    }

    @Test
    public void nullList_allZero() {
        // 决策 7：null 视同空列表
        CartTotal total = CartCalculator.computeTotal(null, null);

        assertTrue(total.getItems().isEmpty());
        assertEquals(0L, total.getTotalAmountCent());
        assertEquals(0L, total.getTotalPieceCount());
    }

    @Test
    public void zeroCountQuantity_amountZero_pieceCountZero() {
        // 决策 7：数量 0 允许入车，金额按 0 计
        CartTotal total = CartCalculator.computeTotal(
                Collections.singletonList(countItem("1001", 1200, 0)), null);

        assertEquals(0L, total.getTotalAmountCent());
        assertEquals(0L, total.getTotalPieceCount());
        assertEquals(0L, total.getItems().get(0).getAmountCent());
    }

    @Test
    public void zeroWeight_amountZero_stillOnePiece() {
        // 决策 3：称重行每行计 1 件（重量 0 金额为 0，件数口径不变）
        CartTotal total = CartCalculator.computeTotal(
                Collections.singletonList(weightItem("2001", 1200, "0")), null);

        assertEquals(0L, total.getTotalAmountCent());
        assertEquals(1L, total.getTotalPieceCount());
    }

    @Test
    public void countQuantity_3p0_integerScaleAllowed() {
        // 3.0 尾零剥离后为整数，允许
        CartItem item = new CartItem("1001", "蛋挞", 100, PricingMode.COUNT,
                new BigDecimal("3.0"), null);

        CartTotal total = CartCalculator.computeTotal(Collections.singletonList(item), null);

        assertEquals(300L, total.getTotalAmountCent());
        assertEquals(3L, total.getTotalPieceCount());
    }

    @Test
    public void fractionalCountQuantity_rejected() {
        CartItem item = new CartItem("1001", "蛋挞", 100, PricingMode.COUNT,
                new BigDecimal("1.5"), null);

        try {
            CartCalculator.computeTotal(Collections.singletonList(item), null);
            throw new AssertionError("期望抛出 IllegalArgumentException，实际未抛出");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("计件条目数量必须为整数值"));
        }
    }

    @Test
    public void totalAmountOverflow_throwsIllegalState() {
        // 决策 7：合计累加用 addExact，溢出抛 IllegalStateException
        // 单条 2147483647 × 2^32 = 9223372032559808512（未溢出），两条相加溢出
        CartItem huge = new CartItem("1001", "天价", Integer.MAX_VALUE, PricingMode.COUNT,
                BigDecimal.valueOf(4294967296L), null);

        try {
            CartCalculator.computeTotal(Arrays.asList(huge, huge), null);
            throw new AssertionError("期望抛出 IllegalStateException，实际未抛出");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("溢出"));
        }
    }

    @Test
    public void singleEntryAmountOverflow_throwsIllegalState() {
        // 单条金额即超出 long：2147483647 × 2^40 > Long.MAX，策略 longValueExact 溢出由计算器统一包装
        CartItem huge = new CartItem("1001", "天价", Integer.MAX_VALUE, PricingMode.COUNT,
                BigDecimal.valueOf(1099511627776L), null);

        try {
            CartCalculator.computeTotal(Collections.singletonList(huge), null);
            throw new AssertionError("期望抛出 IllegalStateException，实际未抛出");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("溢出"));
        }
    }

    @Test
    public void pieceCountOverflow_throwsIllegalState() {
        // 件数累加溢出：单价 0 时金额不溢出，2 × 2^62 件超出 long
        CartItem huge = new CartItem("1001", "零价", 0, PricingMode.COUNT,
                BigDecimal.valueOf(4611686018427387904L), null);

        try {
            CartCalculator.computeTotal(Arrays.asList(huge, huge), null);
            throw new AssertionError("期望抛出 IllegalStateException，实际未抛出");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("溢出"));
        }
    }

    @Test
    public void resultItems_backfilledInOrder_originalItemsUntouched() {
        CartItem count = countItem("1001", 500, 2);
        CartItem weight = weightItem("2002", 1000, "0.5");
        List<CartItem> input = new ArrayList<>();
        input.add(count);
        input.add(weight);

        CartTotal total = CartCalculator.computeTotal(input, null);

        assertEquals(2, total.getItems().size());
        assertEquals("1001", total.getItems().get(0).getPlu());
        assertEquals(1000L, total.getItems().get(0).getAmountCent());
        assertEquals("2002", total.getItems().get(1).getPlu());
        assertEquals(500L, total.getItems().get(1).getAmountCent());
        // 入参条目不被修改（纯函数）
        assertEquals(0L, input.get(0).getAmountCent());
        assertEquals(0L, input.get(1).getAmountCent());
    }
}
