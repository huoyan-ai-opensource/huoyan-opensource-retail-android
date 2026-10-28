package cc.huoyan.retail.device.scale;

import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.*;

/** 三项重量只能来自真实净重及已确认皮重。 */
public class AclasWeightSnapshotTest {
    /** 已去皮但尚未回读皮重时，不得伪造总重量。 */
    @Test public void unknownTareKeepsGrossUnknown() {
        AclasWeightSnapshot read = new AclasWeightSnapshot(0.516, "kg", true, true);
        assertEquals(new BigDecimal("0.516"), read.netKg());
        assertNull(read.tareKg()); assertNull(read.grossKg());
    }
    /** 未去皮回执明确皮重为零，净重即总重量。 */
    @Test public void noTareUsesZero() {
        AclasWeightSnapshot read = new AclasWeightSnapshot(0.516, "kg", true, false);
        assertEquals(new BigDecimal("0.000"), read.tareKg());
        assertEquals(new BigDecimal("0.516"), read.grossKg());
    }
    /** 使用十进制相加，三位小数不受浮点误差影响。 */
    @Test public void confirmedTareProducesGross() {
        AclasWeightSnapshot read = new AclasWeightSnapshot(0.516, "kg", true, true, new BigDecimal("0.020"));
        assertEquals(new BigDecimal("0.536"), read.grossKg());
    }
}
