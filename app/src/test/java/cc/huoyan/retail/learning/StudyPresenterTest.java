package cc.huoyan.retail.learning;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudyPresenterTest {
    private final QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
    private final List<String> forgotten = new ArrayList<>(), deleted = new ArrayList<>();
    private int uploads, captured, cleared, busy;
    private final GoodsEntity a = goods("123"), b = goods("456");
    private StudyPresenter.Capture receipt = receipt(a);
    private final StudyPresenter.Source source = new StudyPresenter.Source() {
        public StudyPresenter.Capture capture(byte[] bytes, int width, int height, GoodsEntity goods, BooleanSupplier active) { return receipt; }
        public void forgetClass(GoodsEntity goods) { forgotten.add(goods.getPlu()); }
        public void forgetSample(StudyPicEntity picture) { }
        public void invalidate(GoodsEntity goods) { }
        public void close() { }
    };
    private final StudyPresenter presenter = new StudyPresenter(new StudyPresenter.View() {
        public void showLearningTarget(GoodsEntity goods) { }
        public void busy() { busy++; }
        public void captureStarted() { }
        public void captured(GoodsEntity goods, List<StudyPicEntity> pictures) { captured++; }
        public void saved(GoodsEntity goods) { }
        public void forgotten(GoodsEntity goods) { cleared++; }
        public void removed(StudyPicEntity picture) { }
        public void failed(int message, String reason) { fail(reason); }
    }, source, worker, ui);
    private static GoodsEntity goods(String plu) { GoodsEntity goods = new GoodsEntity(); goods.setPlu(plu); return goods; }
    private static StudyPresenter.Capture receipt(GoodsEntity goods) {
        return new StudyPresenter.Capture(Collections.emptyList());
    }
    @Test public void captureBlocksSwitchAndFinishAndDetachedCompletionStillUploads() {
        presenter.select(a); presenter.capture(new byte[]{1}, 1, 1);
        presenter.select(b); assertFalse(presenter.finish()); assertEquals(2, busy);
        presenter.detach(); worker.drain(); ui.drain();
        assertEquals(0, captured);
    }
    @Test public void forgettingSnapshotNeverClearsNewSelection() {
        presenter.select(a); presenter.clear(); presenter.select(b);
        worker.drain(); ui.drain();
        assertEquals(Collections.singletonList("123"), forgotten); assertEquals(0, cleared);
    }
}
