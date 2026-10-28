package cc.huoyan.retail.checkout.domain;

import cc.huoyan.retail.pricing.PricingMode;
import org.junit.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class CheckoutSessionTest {
    private static SaleLine line(String plu, long price, int count, PricingMode mode) {
        return new SaleLine(plu, plu, price, 0, count, mode, false, false, plu);
    }

    private CheckoutSession weighted() {
        CheckoutSession session = new CheckoutSession();
        assertEquals(CheckoutSession.WeightEvent.STARTED, session.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, true));
        session.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, true);
        session.lockRecognition();
        session.replaceLines(Collections.singletonList(line("W", 1000, 3, PricingMode.WEIGHT)));
        return session;
    }

    @Test public void scaleWeightChargedOnceAndCountLinesAddedIndependently() {
        CheckoutSession session = weighted();
        session.replaceLines(Arrays.asList(line("W", 1000, 3, PricingMode.WEIGHT),
                line("W2", 1000, 2, PricingMode.WEIGHT), line("C", 120, 5, PricingMode.COUNT)));
        assertEquals(956L, session.state().receipt.getTotalAmountCent());
        assertEquals(10, session.state().receipt.getDetectedPieceCount());
        assertTrue(session.state().canPrint);
    }

    @Test public void conflictingWeightPricesBlockButCountPricesDoNot() {
        CheckoutSession session = weighted();
        session.replaceLines(Arrays.asList(line("W", 1000, 1, PricingMode.WEIGHT),
                line("W2", 1100, 1, PricingMode.WEIGHT), line("C", 999, 2, PricingMode.COUNT)));
        assertEquals(CheckoutState.Phase.LOCKED_PRICE_CONFLICT, session.state().phase);
        assertEquals(Collections.singleton("W2"), session.state().priceConflictPlus);
        assertFalse(session.state().canPrint);
        assertNull(session.state().receipt);
    }

    @Test public void countOnlyWorksWithoutScaleAndBlockedWeightDoesNotEnterTotal() {
        CheckoutSession session = new CheckoutSession();
        session.setWeighingEnabled(false);
        session.lockRecognition();
        session.replaceLines(Arrays.asList(line("C", 120, 5, PricingMode.COUNT), line("W", 1000, 2, PricingMode.WEIGHT)));
        assertTrue(session.state().canPrint);
        assertEquals(600L, session.state().receipt.getTotalAmountCent());
    }

    @Test public void unstableOrDisconnectedScaleRevokesPrintAndTwoReadsRestoreIt() {
        CheckoutSession session = weighted();
        assertTrue(session.state().canPrint);
        session.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, false);
        assertFalse(session.state().canPrint);
        session.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, true);
        assertFalse(session.state().canPrint);
        session.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, true);
        assertTrue(session.state().canPrint);
        session.scaleDisconnected();
        assertFalse(session.state().canPrint);
    }

    @Test public void threeEmptyReadingsEndSaleButFirstAlreadyBlocksPrinting() {
        CheckoutSession session = weighted();
        session.onWeight(BigDecimal.ZERO, java.math.BigDecimal.ZERO, true);
        assertFalse(session.state().canPrint);
        assertTrue(session.state().recognitionLocked);
        session.onWeight(BigDecimal.ZERO, java.math.BigDecimal.ZERO, true);
        assertEquals(CheckoutSession.WeightEvent.CLEARED, session.onWeight(BigDecimal.ZERO, java.math.BigDecimal.ZERO, true));
        assertFalse(session.state().recognitionLocked);
        assertNull(session.state().receipt);
    }

    @Test public void recognitionUsesFixedWeightBaselineAndManualWeightUsesScaleBaseline() {
        CheckoutSession session = weighted();
        session.setManualWeight(new BigDecimal("1.200"));
        assertEquals(1200L, session.state().receipt.getTotalAmountCent());
        session.onWeight(new BigDecimal("0.359"), java.math.BigDecimal.ZERO, true);
        assertTrue(session.state().recognitionLocked);
        assertEquals(CheckoutSession.WeightEvent.RESTARTED, session.onWeight(new BigDecimal("0.362"), java.math.BigDecimal.ZERO, true));
        assertFalse(session.state().recognitionLocked);
        assertNull(session.state().receipt);
    }

    @Test public void printingFreezesReceiptAndFinishesDespiteChangingWeight() {
        CheckoutSession session = weighted();
        SalePrintJob job = session.beginPrint();
        WeightedSaleResult receipt = session.state().receipt;
        assertFalse(session.state().canEdit);
        assertFalse(session.state().canPrint);
        assertFalse(session.restart());
        session.onWeight(new BigDecimal("0.500"), java.math.BigDecimal.ZERO, true);
        assertSame(receipt, session.state().receipt);
        session.finishPrint(job, true);
        assertEquals(CheckoutState.Phase.SOLD_OR_REPORTED, session.state().phase);
        assertFalse(session.state().canPrint);
    }

    @Test public void completionOfOldPrintCannotMarkNewSaleSold() {
        CheckoutSession session = weighted();
        SalePrintJob old = session.beginPrint();
        for (int i = 0; i < 3; i++) session.onWeight(BigDecimal.ZERO, java.math.BigDecimal.ZERO, true);
        session.onWeight(new BigDecimal("0.700"), java.math.BigDecimal.ZERO, true);
        session.onWeight(new BigDecimal("0.700"), java.math.BigDecimal.ZERO, true);
        session.lockRecognition();
        session.replaceLines(Collections.singletonList(line("NEW", 200, 1, PricingMode.COUNT)));
        session.finishPrint(old, true);
        assertEquals(CheckoutState.Phase.LOCKED_VALID, session.state().phase);
        assertEquals("NEW", session.state().receipt.getItems().get(0).getPlu());
        assertTrue(session.state().canPrint);
        SalePrintJob next = session.beginPrint();
        session.finishPrint(old, true);
        assertTrue(session.state().printing);
        session.finishPrint(next, false);
        assertFalse(session.state().printing);
    }

    @Test public void failedPrintReevaluatesLatestScaleState() {
        CheckoutSession session = weighted();
        SalePrintJob job = session.beginPrint();
        session.scaleDisconnected();
        session.finishPrint(job, false);
        assertFalse(session.state().canPrint);
        assertTrue(session.state().canEdit);
    }

    @Test public void unresolvedRowsDoNotCausePriceConflictAndAmountsKeepLongPrecision() {
        CheckoutSession session = new CheckoutSession();
        session.setWeighingEnabled(false);
        session.lockRecognition();
        session.replaceLines(Arrays.asList(new SaleLine("unknown", "unknown", 0, 0, 1, PricingMode.WEIGHT, true, false, ""),
                line("C", 2_000_000_000L, 2, PricingMode.COUNT)));
        assertEquals(4_000_000_000L, session.state().receipt.getTotalAmountCent());
        assertTrue(session.state().canPrint);
    }

    @Test public void stateSnapshotsRemainUnchangedAfterLaterEvents() {
        CheckoutSession session = weighted();
        CheckoutState before = session.state();
        session.scaleDisconnected();
        assertTrue(before.canPrint);
        assertFalse(session.state().canPrint);
        assertThrows(UnsupportedOperationException.class, () -> before.priceConflictPlus.add("X"));
    }

    @Test public void invalidGoodsBlockSaleAndRemovingLastLineRevokesValidPhase() {
        CheckoutSession session = weighted();
        session.replaceLines(Arrays.asList(line("W", 1000, 1, PricingMode.WEIGHT),
                line("INVALID", 0, 1, PricingMode.COUNT)));
        assertFalse(session.state().canPrint);
        assertEquals(CheckoutState.Phase.LOCKED_PRICE_MISSING, session.state().phase);
        session.replaceLines(Collections.emptyList());
        assertNull(session.state().receipt);
        assertEquals(CheckoutState.Phase.WEIGHT_STABILIZING, session.state().phase);
    }

    @Test public void negativeWeightClearsSaleAndReconnectingAloneDoesNotRestoreStability() {
        CheckoutSession session = weighted();
        assertEquals(CheckoutSession.WeightEvent.NEGATIVE, session.onWeight(new BigDecimal("-0.005"), java.math.BigDecimal.ZERO, true));
        assertNull(session.state().receipt);
        assertFalse(session.state().recognitionLocked);
        session.scaleDisconnected();
        session.scaleConnected();
        assertFalse(session.state().hasStableWeight);
    }

    @Test public void changesToInputListCannotChangePublishedSale() {
        CheckoutSession session = weighted();
        java.util.List<SaleLine> lines = new java.util.ArrayList<>();
        lines.add(line("C", 200, 3, PricingMode.COUNT));
        session.replaceLines(lines);
        lines.clear();
        session.scaleDisconnected();
        assertEquals(600L, session.state().receipt.getTotalAmountCent());
        assertTrue(session.state().canPrint);
    }

    @Test public void editingDuringPrintCannotChangePrintedReceipt() {
        CheckoutSession session = weighted();
        SalePrintJob job = session.beginPrint();
        WeightedSaleResult original = session.state().receipt;
        session.replaceLines(Collections.singletonList(line("OTHER", 100, 1, PricingMode.COUNT)));
        session.setManualWeight(BigDecimal.TEN);
        assertSame(original, session.state().receipt);
        session.finishPrint(job, true);
        session.replaceLines(Collections.emptyList());
        assertSame(original, session.state().receipt);
    }
    /** 无称重打印完成后暂停接收结果，显式恢复才开始新单，失败则允许重试。 */
    @Test public void countReceiptWaitsForResumeAfterSuccessfulPrint() {
        CheckoutSession session = new CheckoutSession();
        session.setWeighingEnabled(false);
        session.lockRecognition();
        session.replaceLines(Collections.singletonList(line("C", 1000, 1, PricingMode.COUNT)));
        SalePrintJob failed = session.beginPrint();
        assertFalse(session.state().recognitionActive);
        session.finishPrint(failed, false);
        assertTrue(session.state().canPrint);
        SalePrintJob printed = session.beginPrint();
        session.finishPrint(printed, true);
        assertEquals(CheckoutState.Phase.SOLD_OR_REPORTED, session.state().phase);
        assertFalse(session.state().recognitionActive);
        assertTrue(session.restart());
        assertTrue(session.state().recognitionActive);
        assertNull(session.state().receipt);
    }

    /** 零价格优先于整秤价格冲突，纠正价格后恢复正常结算。 */
    @Test public void missingPriceIsNotAWeightPriceConflict() {
        CheckoutSession session = weighted();
        session.replaceLines(Arrays.asList(line("W", 1000, 1, PricingMode.WEIGHT),
                line("Z", 0, 1, PricingMode.COUNT), line("W2", 2000, 1, PricingMode.WEIGHT)));
        assertEquals(CheckoutState.Phase.LOCKED_PRICE_MISSING, session.state().phase);
        assertFalse(session.state().canPrint);
        session.replaceLines(Arrays.asList(line("W", 1000, 1, PricingMode.WEIGHT),
                line("W2", 2000, 1, PricingMode.WEIGHT)));
        assertEquals(CheckoutState.Phase.LOCKED_PRICE_CONFLICT, session.state().phase);
    }
}
