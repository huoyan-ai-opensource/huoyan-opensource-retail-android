package cc.huoyan.retail.device.scale;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;

/** 回读皮重与真实净重配对，不制造去皮过程中的毛重跳变。 */
public class AclasTareReadingsTest {
    /** 回读新皮重不篡改上一帧，等待下一帧真实净重才产生正确毛重。 */
    @Test public void tareReadPairsWithNextWeightAndKeepsPreviousSnapshotImmutable() {
        AclasTareReadings readings = new AclasTareReadings();
        AclasWeightSnapshot before = readings.readWeight(0.5, "kg", true, false);
        readings.invalidate();
        assertNull(readings.readWeight(0.45, "kg", true, true).grossKg());
        readings.readTare(new BigDecimal("0.050"));
        AclasWeightSnapshot after = readings.readWeight(0.45, "kg", true, true);
        assertEquals(new BigDecimal("0.500"), before.grossKg());
        assertEquals(new BigDecimal("0.000"), before.tareKg());
        assertEquals(new BigDecimal("0.500"), after.grossKg());
        assertEquals(new BigDecimal("0.050"), after.tareKg());
    }

    /** 先收到皮重再收到去皮状态重量时也不丢掉回读；归零后外部去皮必须重新查询。 */
    @Test public void readBeforeNetFlagAndExternalTareTransitionAreHandled() {
        AclasTareReadings readings = new AclasTareReadings();
        readings.readWeight(0.5, "kg", true, false);
        readings.readTare(new BigDecimal("0.050"));
        assertEquals(new BigDecimal("0.500"), readings.readWeight(0.45, "kg", true, true).grossKg());
        assertEquals(new BigDecimal("0.000"), readings.readWeight(0.5, "kg", true, false).tareKg());
        assertNull(readings.readWeight(0.4, "kg", true, true).tareKg());
        readings.readTare(new BigDecimal("0.100"));
        assertEquals(new BigDecimal("0.500"), readings.readWeight(0.4, "kg", true, true).grossKg());
    }
}
