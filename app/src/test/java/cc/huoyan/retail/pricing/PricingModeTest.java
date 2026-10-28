package cc.huoyan.retail.pricing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PricingModeTest {

    @Test
    public void fromUnit_unit5_returnsCount() {
        assertEquals(PricingMode.COUNT, PricingMode.fromUnit(5));
    }

    @Test
    public void fromUnit_unit0_returnsWeight() {
        assertEquals(PricingMode.WEIGHT, PricingMode.fromUnit(0));
    }

    @Test
    public void fromUnit_unit1_returnsWeight() {
        assertEquals(PricingMode.WEIGHT, PricingMode.fromUnit(1));
    }

    @Test
    public void fromUnit_unit2_returnsWeight() {
        assertEquals(PricingMode.WEIGHT, PricingMode.fromUnit(2));
    }

    @Test
    public void fromUnit_negative_returnsWeight() {
        assertEquals(PricingMode.WEIGHT, PricingMode.fromUnit(-1));
    }

    @Test
    public void fromUnit_maxValue_returnsWeight() {
        assertEquals(PricingMode.WEIGHT, PricingMode.fromUnit(Integer.MAX_VALUE));
    }
}
