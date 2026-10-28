package cc.huoyan.retail.checkout.domain;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SalePrintJobTest {
    private static WeightedSaleResult receipt(String id, int amount) {
        return new WeightedSaleResult(id, java.math.BigDecimal.ONE, amount, amount, 1, 1, Collections.emptyList());
    }

    @Test public void changedPageDoesNotDiscardPrintedReceiptOrFeedback() throws Exception {
        WeightedSaleResult original = receipt("old", 100);
        SalePrintJob job = new SalePrintJob(1, original);
        WeightedSaleResult[] page = {original};
        long[] generation = {1};
        List<WeightedSaleResult> printed = new ArrayList<>();
        AtomicInteger reported = new AtomicInteger();
        job.execute(value -> {
            printed.add(value);
            page[0] = receipt("new", 500);
            generation[0]++;
        }, reported::incrementAndGet, e -> fail());
        assertSame(original, printed.get(0));
        assertEquals(1, reported.get());
        assertFalse(job.belongsTo(generation[0]));
        assertEquals(500, page[0].getTotalAmountCent());
    }

    @Test public void changedWeightWithinSessionDoesNotLoseCompletion() throws Exception {
        SalePrintJob job = new SalePrintJob(1, receipt("same", 100));
        AtomicInteger reported = new AtomicInteger();
        WeightedSaleResult[] liveReceipt = {receipt("same", 100)};
        job.execute(value -> {
            liveReceipt[0] = receipt("same", 200);
            assertEquals(100, value.getTotalAmountCent());
        }, reported::incrementAndGet, e -> fail());
        assertEquals(200, liveReceipt[0].getTotalAmountCent());
        assertTrue(job.belongsTo(1));
        assertEquals(1, reported.get());
    }

    @Test public void feedbackFailureIsNotPrintFailure() throws Exception {
        SalePrintJob job = new SalePrintJob(1, receipt("sale", 100));
        AtomicInteger feedbackErrors = new AtomicInteger();
        job.execute(value -> {}, () -> { throw new IllegalStateException("feedback"); },
                e -> feedbackErrors.incrementAndGet());
        assertEquals(1, feedbackErrors.get());
    }

    @Test public void failedPrinterDoesNotReportSale() {
        SalePrintJob job = new SalePrintJob(1, receipt("sale", 100));
        AtomicInteger reported = new AtomicInteger();
        assertThrows(Exception.class, () -> job.execute(value -> { throw new Exception("printer"); },
                reported::incrementAndGet, e -> fail()));
        assertEquals(0, reported.get());
    }

    @Test public void jobCannotPrintTwice() throws Exception {
        SalePrintJob job = new SalePrintJob(1, receipt("sale", 100));
        AtomicInteger prints = new AtomicInteger();
        job.execute(value -> prints.incrementAndGet(), () -> {}, e -> fail());
        assertThrows(IllegalStateException.class, () -> job.execute(value -> prints.incrementAndGet(),
                () -> {}, e -> fail()));
        assertEquals(1, prints.get());
    }
}
