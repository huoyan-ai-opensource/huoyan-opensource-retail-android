package cc.huoyan.retail.settings;
import cc.huoyan.retail.testing.QueuedExecutor;
import cc.huoyan.sdk.client.model.BaseResult;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class LibraryPresenterTest {
    @Test public void detachedOperationRetainsResultUntilViewCanConsumeIt() {
        QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor(); AtomicInteger calls = new AtomicInteger();
        LibraryPresenter p = new LibraryPresenter(worker, ui, restore -> new BaseResult(0, null), () -> null, () -> -1);
        p.attach(calls::incrementAndGet); p.run(true, false); p.run(false, false);
        assertEquals(1, worker.pending()); assertTrue(p.running()); p.detach(); worker.next(); ui.drain();
        assertEquals(1, calls.get()); assertFalse(p.running()); assertTrue(p.restoring()); assertNotNull(p.result());
        p.attach(calls::incrementAndGet); assertEquals(0, p.result().getCode()); p.consumeResult(); assertNull(p.result());
    }
    @Test public void activeImageLearningBlocksLibraryMutation() {
        QueuedExecutor worker = new QueuedExecutor(); LibraryPresenter p = new LibraryPresenter(worker, Runnable::run, restore -> new BaseResult(0, null), () -> null, () -> -1);
        p.run(true, true); assertFalse(p.running()); assertEquals(0, worker.pending());
    }
    @Test public void queryFromOldViewCannotOverwriteNewViewOrCancelWriteReceipt() {
        QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
        AtomicInteger nextCount = new AtomicInteger(3), calls = new AtomicInteger();
        LibraryPresenter p = new LibraryPresenter(worker, ui, restore -> new BaseResult(0, null), () -> null, nextCount::getAndIncrement);
        p.attach(calls::incrementAndGet); p.refreshCount(); worker.drain();
        p.run(false, false); p.detach(); p.attach(calls::incrementAndGet);
        p.refreshCount(); worker.drain(); ui.drain();
        assertEquals(4, p.count()); assertNotNull(p.result()); assertFalse(p.running());
        assertEquals(3, calls.get());
    }
    @Test public void failedMetadataQueryReportsUnavailable() {
        LibraryPresenter p = new LibraryPresenter(Runnable::run, Runnable::run, restore -> null,
                () -> { throw new IllegalStateException(); }, () -> { throw new IllegalStateException(); });
        p.attach(() -> { }); p.refreshCount(); p.refreshBackup();
        assertEquals(-1, p.count()); assertNull(p.info());
    }
}
