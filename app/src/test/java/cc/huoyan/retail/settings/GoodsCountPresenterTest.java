package cc.huoyan.retail.settings;

import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

/** 商品统计的异步生命周期与失败回显回归。 */
public class GoodsCountPresenterTest {
    @Test public void oldViewReceiptCannotOverwriteNewCount() {
        QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
        AtomicLong count = new AtomicLong(10);
        GoodsCountPresenter presenter = new GoodsCountPresenter(worker, ui, count::getAndIncrement);
        List<Long> old = new ArrayList<>(), current = new ArrayList<>();
        presenter.attach(old::add); presenter.refresh(); worker.drain(); presenter.detach();
        presenter.attach(current::add); presenter.refresh(); worker.drain(); ui.drain();
        assertEquals(Arrays.asList(-1L), old);
        assertEquals(Arrays.asList(-1L, 11L), current);
    }
    @Test public void failureDoesNotDisplayOldValueOrZero() {
        List<Long> values = new ArrayList<>();
        GoodsCountPresenter presenter = new GoodsCountPresenter(Runnable::run, Runnable::run,
                () -> { throw new IllegalStateException("disconnected"); });
        presenter.attach(values::add); presenter.refresh();
        assertEquals(Arrays.asList(-1L, -1L), values);
    }
}
