package cc.huoyan.retail.recognition;

import org.junit.Test;
import static org.junit.Assert.*;

/** 无稳定称重时的连续主结果及超时边界回归。 */
public class RecognitionResultGateTest {
    @Test public void threeConsecutiveResultsLockWithoutStableWeight() {
        RecognitionResultGate gate = new RecognitionResultGate();
        assertFalse(gate.accept("1,2", 0, false));
        assertFalse(gate.accept("1,2", 100, false));
        assertTrue(gate.accept("1,2", 200, false));
    }
    @Test public void changedResultRestartsCountButNotDeadline() {
        RecognitionResultGate gate = new RecognitionResultGate();
        assertFalse(gate.accept("1", 100, false));
        assertFalse(gate.accept("1", 200, false));
        assertFalse(gate.accept("2", 300, false));
        assertFalse(gate.accept("3", 3099, false));
        assertEquals(1, gate.remaining(3099));
        assertTrue(gate.accept("4", 3100, false));
    }
    @Test public void timeoutDoesNotNeedAnotherReceipt() {
        RecognitionResultGate gate = new RecognitionResultGate();
        gate.accept("1", 50, false);
        assertFalse(gate.timedOut(3049));
        assertTrue(gate.timedOut(3050));
    }
    @Test public void emptyResultAndNewSessionDiscardPreviousWindow() {
        RecognitionResultGate gate = new RecognitionResultGate();
        gate.accept("1", 0, false);
        gate.accept("1", 100, false);
        assertFalse(gate.accept(null, 200, false));
        assertFalse(gate.timedOut(4000));
        assertFalse(gate.accept("1", 4000, false));
        gate.reset();
        assertFalse(gate.timedOut(9000));
        assertFalse(gate.accept("1", 9000, false));
        assertFalse(gate.accept("1", 9100, false));
    }
    @Test public void stableWeightKeepsTwoResultConfirmation() {
        RecognitionResultGate gate = new RecognitionResultGate();
        assertFalse(gate.accept("1", 0, true));
        assertTrue(gate.accept("1", 100, true));
    }
    @Test public void weightBecomingUnstableUsesOriginalDeadline() {
        RecognitionResultGate gate = new RecognitionResultGate();
        assertFalse(gate.accept("1", 0, true));
        assertFalse(gate.accept("2", 2500, true));
        assertTrue(gate.accept("3", 3000, false));
    }
}
