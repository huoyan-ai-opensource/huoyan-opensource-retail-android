package cc.huoyan.retail.settings;
import cc.huoyan.retail.R;
import cc.huoyan.retail.testing.QueuedExecutor;
import cc.huoyan.sdk.client.model.BindingResult;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShopBindingPresenterTest {
    static class Screen implements ShopBindingPresenter.View {
        int rejected, finished, message;
        public void bindingStarted() { }
        public void bindingFinished(boolean success, int text, String reason) { finished++; message = text; }
        public void bindingRejected(int text) { rejected++; message = text; }
    }
    static class Data implements ShopBindingPresenter.Data {
        int writes; String id; BindingResult receipt = new BindingResult(0, null, null, "account", null, "shop", null, null, null, null);
        public ShopBindingPresenter.Result bind(String id) { this.id = id; return new ShopBindingPresenter.Result(receipt, 0); }
        public void save(BindingResult receipt) { writes++; }
    }
    @Test public void preservesExternalIdAndPersistsSubmittedSuccessAfterDetach() {
        Screen v = new Screen(); Data d = new Data(); QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
        ShopBindingPresenter p = new ShopBindingPresenter(v, worker, ui, d);
        p.bind("  "); assertEquals(R.string.bind_id_required, v.message); assertEquals(0, worker.pending());
        p.bind("007"); p.detach(); p.attach(v); p.bind("repeat"); assertEquals(R.string.bind_in_progress, v.message);
        worker.next(); p.detach(); ui.drain(); assertEquals("007", d.id); assertEquals(1, d.writes); assertEquals(0, v.finished);
    }
    @Test public void incompleteReceiptCannotChangeOwnership() {
        Screen v = new Screen(); Data d = new Data(); d.receipt = new BindingResult(0, null, null, "", null, "shop", null, null, null, null);
        ShopBindingPresenter p = new ShopBindingPresenter(v, Runnable::run, Runnable::run, d);
        p.bind("id"); assertEquals(0, d.writes); assertEquals(R.string.bind_incomplete, v.message);
    }
}
