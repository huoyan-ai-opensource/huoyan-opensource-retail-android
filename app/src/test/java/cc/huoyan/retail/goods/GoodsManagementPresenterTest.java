package cc.huoyan.retail.goods;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoodsManagementPresenterTest {
    static class Screen implements GoodsManagementPresenter.View {
        List<GoodsEntity> rows = new ArrayList<>(); int saves, errors;
        public void showGoods(List<GoodsEntity> rows, boolean reset) { this.rows = rows; }
        public void appendGoods(List<GoodsEntity> rows) { this.rows.addAll(rows); }
        public void saved() { saves++; }
        public void error(RuntimeException error) { errors++; }
    }
    static class Data implements GoodsManagementPresenter.Data {
        List<GoodsEntity> items = new ArrayList<>(); int writes;
        Data() { for (int i = 0; i < 60; i++) { GoodsEntity g = new GoodsEntity(); g.setPlu("" + (1000 + i)); g.setName("商品" + i); g.setUnit(5); items.add(g); } }
        public List<GoodsEntity> goods() { return items; }
        public Map<String, Long> learnedCounts(List<GoodsEntity> goods) { return Collections.singletonMap("1000", 3L); }
        public void save(GoodsEntity goods, boolean editing) { writes++; }
        public void delete(GoodsEntity goods) { writes++; }
    }
    @Test public void staleAppendCannotMixOldSearchResults() {
        Screen v = new Screen(); Data d = new Data(); QueuedExecutor worker = new QueuedExecutor();
        GoodsManagementPresenter p = new GoodsManagementPresenter(v, worker, Runnable::run, d);
        p.refresh(); worker.drain(); assertEquals(24, v.rows.size());
        int version = p.filterVersion(); p.append(version); assertEquals(48, v.rows.size());
        p.search("1000"); p.append(version); assertEquals(1, v.rows.size()); assertEquals("1000", v.rows.get(0).getPlu());
        assertEquals(Long.valueOf(3), p.learnedCount("1000"));
    }
    @Test public void pauseDropsQueriesButSubmittedWriteSurvivesDetach() {
        Screen v = new Screen(); Data d = new Data(); QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
        GoodsManagementPresenter p = new GoodsManagementPresenter(v, worker, ui, d);
        p.refresh(); worker.next(); p.pause(); ui.drain(); assertTrue(v.rows.isEmpty());
        p.save(d.items.get(0), true); p.delete(d.items.get(0)); assertEquals(1, worker.pending());
        p.detach(); worker.next(); ui.drain(); assertEquals(1, d.writes); assertEquals(0, v.saves);
    }
    @Test public void failedMutationReleasesGateAndRefreshes() {
        Screen v = new Screen(); QueuedExecutor worker = new QueuedExecutor();
        GoodsManagementPresenter p = new GoodsManagementPresenter(v, worker, Runnable::run, new Data() {
            public void save(GoodsEntity goods, boolean editing) { throw new IllegalStateException("failed"); }
        });
        p.save(null, false); assertTrue(p.busy()); worker.drain(); assertFalse(p.busy()); assertEquals(1, v.errors); assertEquals(24, v.rows.size());
    }
    /** 配置重建不解锁重复写入，已完成回执只交付给新页面一次。 */
    @Test public void rotationKeepsWriteGateAndDeliversResultOnce() {
        Screen old = new Screen(), current = new Screen(); Data data = new Data();
        QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
        GoodsManagementPresenter presenter = new GoodsManagementPresenter(old, worker, ui, data);
        presenter.save(data.items.get(0), true); presenter.detach(); presenter.attach(current);
        assertTrue(presenter.busy()); presenter.save(data.items.get(0), true);
        assertEquals(1, worker.pending());
        presenter.detach(); worker.next(); ui.drain();
        assertFalse(presenter.busy()); assertEquals(1, data.writes);
        presenter.attach(current); presenter.deliverResult(); presenter.deliverResult();
        assertEquals(0, old.saves); assertEquals(1, current.saves);
    }
}
