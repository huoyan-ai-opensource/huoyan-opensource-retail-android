package cc.huoyan.retail.pricing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PricingGateTest {

    @Test
    public void check_weightAndWeighingDisabled_returnsBlockWithWeighingDisabled() {
        PricingGate.GateResult result = PricingGate.check(PricingMode.WEIGHT, false);

        assertEquals(PricingGate.GateResult.BLOCK, result);
        assertEquals(PricingGate.BlockReason.WEIGHING_DISABLED, result.getReason());
    }

    @Test
    public void check_weightAndWeighingEnabled_returnsPass() {
        assertEquals(PricingGate.GateResult.PASS, PricingGate.check(PricingMode.WEIGHT, true));
    }

    @Test
    public void check_countAndWeighingDisabled_returnsPass() {
        assertEquals(PricingGate.GateResult.PASS, PricingGate.check(PricingMode.COUNT, false));
    }

    @Test
    public void check_countAndWeighingEnabled_returnsPass() {
        assertEquals(PricingGate.GateResult.PASS, PricingGate.check(PricingMode.COUNT, true));
    }
}
