package cc.huoyan.retail.pricing;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

public class PricingStrategyTest {

    private final CountPricingStrategy countStrategy = new CountPricingStrategy();
    private final WeightPricingStrategy weightStrategy = new WeightPricingStrategy();

    @Test
    public void count_threePieces_times1200_returns3600() {
        assertEquals(3600L, countStrategy.calculateAmountCent(BigDecimal.valueOf(3), 1200));
    }

    @Test
    public void count_onePiece_returnsUnitPrice() {
        assertEquals(1200L, countStrategy.calculateAmountCent(BigDecimal.ONE, 1200));
    }

    @Test
    public void weight_0p385kg_times1200_returns462() {
        assertEquals(462L, weightStrategy.calculateAmountCent(new BigDecimal("0.385"), 1200));
    }

    @Test
    public void weight_0p333_times100_roundsTo33() {
        // 33.3 -> 33（HALF_UP 舍去不足半分的部分）
        assertEquals(33L, weightStrategy.calculateAmountCent(new BigDecimal("0.333"), 100));
    }

    @Test
    public void weight_0p335_times100_roundsHalfUpTo34() {
        // 33.5 -> 34（HALF_UP 进位方向）
        assertEquals(34L, weightStrategy.calculateAmountCent(new BigDecimal("0.335"), 100));
    }

    @Test
    public void weight_zeroQuantity_returnsZero() {
        assertEquals(0L, weightStrategy.calculateAmountCent(BigDecimal.ZERO, 1200));
    }

    @Test
    public void weight_negativeQuantity_returnsNegativeAmount() {
        assertEquals(-500L, weightStrategy.calculateAmountCent(new BigDecimal("-0.5"), 1000));
    }
}
