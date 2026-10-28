package cc.huoyan.retail.pricing;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeightChangePolicyTest {
    @Test public void toleratesFiveGramsInEitherDirection() {
        for (double weight : new double[]{0.995, 0.999, 1.0, 1.001, 1.005}) {
            assertFalse(WeightChangePolicy.requiresRecognition(java.math.BigDecimal.valueOf(1.0), java.math.BigDecimal.valueOf(weight)));
        }
        assertTrue(WeightChangePolicy.requiresRecognition(java.math.BigDecimal.valueOf(1.0), java.math.BigDecimal.valueOf(1.006)));
        assertTrue(WeightChangePolicy.requiresRecognition(java.math.BigDecimal.valueOf(1.0), java.math.BigDecimal.valueOf(0.994)));
    }

    @Test public void gradualDriftIsComparedWithOriginalBaseline() {
        assertFalse(WeightChangePolicy.requiresRecognition(java.math.BigDecimal.valueOf(0.100), java.math.BigDecimal.valueOf(0.104)));
        assertTrue(WeightChangePolicy.requiresRecognition(java.math.BigDecimal.valueOf(0.100), java.math.BigDecimal.valueOf(0.108)));
        assertFalse(WeightChangePolicy.requiresRecognition(null, java.math.BigDecimal.valueOf(0.108)));
    }
}
