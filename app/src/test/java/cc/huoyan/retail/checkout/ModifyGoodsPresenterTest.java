package cc.huoyan.retail.checkout;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.sdk.client.model.*;
import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** 候选顺序、支持数、选择持久性及搜索生命周期回归。 */
public class ModifyGoodsPresenterTest {
    private final QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
    private List<RecognizeModifyCandidate> shown;
    private String selection;
    private GoodsEntity confirmed;
    private int closed;
    private boolean accept;
    private final GoodsEntity a = goods("1", "Apple"), b = goods("2", "Banana"), c = goods("3", "Bread");
    private final ModifyGoodsPresenter.Source source = new ModifyGoodsPresenter.Source() {
        public List<GoodsEntity> similar(Set<String> plus) { return Arrays.asList(a, b, a); }
        public List<GoodsEntity> search(String query) { return Arrays.asList(c, b, a); }
    };
    private final ModifyGoodsPresenter.View view = new ModifyGoodsPresenter.View() {
        public void candidates(List<RecognizeModifyCandidate> candidates, String selectedPlu) { shown = candidates; selection = selectedPlu; }
        public void selection(String plu) { selection = plu; }
        public void selectionRequired() { fail("selection should exist"); }
        public void failed(String reason) { fail(reason); }
        public boolean confirm(GoodsEntity goods) { confirmed = goods; return accept; }
        public void close() { closed++; }
    };
    private static GoodsEntity goods(String plu, String name) { GoodsEntity g = new GoodsEntity(); g.setPlu(plu); g.setName(name); return g; }
    @Test public void firstSeenSimilarOrderAndRepeatedSupportRemainUnchanged() {
        RecognitionCandidate first = new RecognitionCandidate("2", 0, 0, 0);
        RecognitionCandidate second = new RecognitionCandidate("1", 0, 0, 0);
        RecognitionSelection item = new RecognitionSelection(new cc.huoyan.sdk.client.model.RecognitionItem("1", 0, 1, 2, 3, 4, 0, 0, Arrays.asList(first, second, first)));
        GoodsVo vo = new GoodsVo(1, 1, a, Collections.singletonList(item));
        ModifyGoodsPresenter p = new ModifyGoodsPresenter(view, vo, source, worker, ui);
        p.search(""); worker.drain(); ui.drain();
        assertEquals("2", shown.get(0).getGoods().getPlu()); assertEquals(2, shown.get(0).getSupportCount());
        assertEquals(1, shown.get(0).getTotalCount()); assertEquals("1", shown.get(1).getGoods().getPlu());
        assertEquals("3", shown.get(2).getGoods().getPlu()); assertEquals("1", selection);
        assertTrue(shown.get(0).isSimilarCandidate()); assertTrue(shown.get(1).isSimilarCandidate());
        assertFalse(shown.get(2).isSimilarCandidate());
        p.search("Banana"); worker.drain(); ui.drain();
        assertNull(confirmed);
        p.select(shown.get(0));
        assertEquals("2", confirmed.getPlu()); assertEquals(0, closed);
        accept = true; p.select(shown.get(0)); assertEquals(1, closed);
    }
    @Test public void staleSearchCannotSelectFirstCandidateAndDetachCannotRender() {
        ModifyGoodsPresenter p = new ModifyGoodsPresenter(view, null, source, worker, ui);
        p.search("Apple"); worker.drain(); p.search("Bread"); worker.drain(); ui.drain();
        assertEquals("3", selection); assertEquals(1, shown.size());
        p.search("Apple"); worker.drain(); p.detach(); ui.drain(); assertEquals("3", selection);
    }

    /** 字母键盘可以用中文首字母和不区分大小写的英文检索，搜索不提交商品，点击结果立即提交。 */
    @Test public void keyboardSearchMatchesInitialsAndUppercaseEnglish() {
        c.setName("全麦吐司面包");
        ModifyGoodsPresenter p = new ModifyGoodsPresenter(view, new GoodsVo(1, 1, a, Collections.emptyList()),
                source, worker, ui);
        p.search(" qm "); worker.drain(); ui.drain();
        assertEquals(1, shown.size()); assertEquals("3", shown.get(0).getGoods().getPlu());
        p.search("APPLE"); worker.drain(); ui.drain();
        assertEquals(1, shown.size()); assertEquals("1", shown.get(0).getGoods().getPlu());
        assertNull(confirmed);
        p.select(shown.get(0)); assertEquals(a, confirmed);
    }
}
