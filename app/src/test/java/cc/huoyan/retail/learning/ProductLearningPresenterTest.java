package cc.huoyan.retail.learning;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductLearningPresenterTest {
    static GoodsEntity goods(String plu) { GoodsEntity g = new GoodsEntity(); g.setPlu(plu); return g; }
    static StudyPicEntity picture(String plu, long uid) { StudyPicEntity p = new StudyPicEntity(); p.setClazzName(plu); p.setUid(uid); return p; }
    static class Screen implements ProductLearningPresenter.View {
        List<StudyPicEntity> rows = new ArrayList<>(); GoodsEntity selected; int events;
        public List<StudyPicEntity> pictures() { return rows; }
        public void selected(GoodsEntity g) { selected = g; }
        public void showPictures(List<StudyPicEntity> pictures, boolean captured) { rows = pictures; events++; }
        public void stateChanged() { }
        public void pageLoaded(boolean failed, long elapsed, int page, int count) { }
    }
    static class Source implements ProductLearningPresenter.Data {
        boolean fail; int reads;
        public GoodsEntity findGoods(String plu) { return goods(plu); }
        public ProductLearningPresenter.Page load(String plu, int page, BooleanSupplier current) {
            reads++; if (fail) throw new IllegalStateException();
            return new ProductLearningPresenter.Page(Collections.singletonList(picture(plu, 1)), page == 0 ? 2 : 1, 2);
        }
    }
    @Test public void switchingGoodsRejectsAlreadyPostedOldPage() {
        Screen v = new Screen(); Source source = new Source(); QueuedExecutor queries = new QueuedExecutor(), images = new QueuedExecutor(), ui = new QueuedExecutor();
        ProductLearningPresenter p = new ProductLearningPresenter(v, queries, images, ui, source);
        p.changedGoods(goods("1")); images.next(); p.changedGoods(goods("2")); ui.drain(); assertTrue(v.rows.isEmpty());
        images.next(); ui.drain(); assertEquals("2", v.rows.get(0).getClazzName());
        p.select("1"); p.select("2"); queries.drain(); ui.drain(); assertEquals("2", v.selected.getPlu()); p.detach();
    }
    @Test public void deletedUidDoesNotReturnFromHistoryAndCursorRestarts() {
        Screen v = new Screen(); Source source = new Source(); QueuedExecutor images = new QueuedExecutor();
        ProductLearningPresenter p = new ProductLearningPresenter(v, new QueuedExecutor(), images, Runnable::run, source);
        p.changedGoods(goods("1")); images.next(); assertEquals(1, v.rows.size());
        StudyPicEntity removed = v.rows.remove(0); p.removed(removed); images.next(); assertTrue(v.rows.isEmpty());
        p.loadNext(); images.next(); assertFalse(p.hasMore()); p.detach();
    }
    @Test public void retryAndDetachKeepPaginationCallbacksSafe() {
        Screen v = new Screen(); Source source = new Source(); source.fail = true;
        QueuedExecutor images = new QueuedExecutor(), ui = new QueuedExecutor();
        ProductLearningPresenter p = new ProductLearningPresenter(v, new QueuedExecutor(), images, ui, source);
        p.changedGoods(goods("1")); p.loadNext(); assertEquals(1, images.pending());
        images.next(); ui.drain(); assertTrue(p.failed()); assertFalse(p.canAutoLoad());
        source.fail = false; p.loadNext(); images.next(); p.detach(); ui.drain(); assertEquals(0, v.events);
    }
}
