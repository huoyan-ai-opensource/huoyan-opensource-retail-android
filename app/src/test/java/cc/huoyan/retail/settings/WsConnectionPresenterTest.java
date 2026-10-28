package cc.huoyan.retail.settings;

import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** 两个连接入口共用的查询取消与无副作用保存回归。 */
public class WsConnectionPresenterTest {
    private final QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
    private final List<String> delivered = new ArrayList<>();
    private int invalid, probes;
    private final WsConnectionPresenter presenter = new WsConnectionPresenter(new WsConnectionPresenter.View() {
        public void invalidAddress() { invalid++; }
        public void querying(boolean scan, String ip) { }
        public void queryResult(boolean scan, String ip, boolean connected, List<String> matches) { delivered.add(ip); }
    }, worker, ui, new WsConnectionPresenter.Discovery() {
        public boolean probe(String endpoint) { probes++; return true; }
        public List<String> discover(String ip) { return Collections.singletonList(ip); }
    });
    @Test public void changingChannelDiscardsAlreadyQueuedResult() {
        presenter.query(false, "127.0.0.1"); worker.drain(); presenter.cancel(); ui.drain();
        assertTrue(delivered.isEmpty()); assertEquals(1, probes);
    }
    @Test public void newerRequestWinsAndDetachDiscardsResult() {
        presenter.query(false, "127.0.0.1"); worker.drain();
        presenter.query(true, "127.0.0.2"); worker.drain(); ui.drain();
        assertEquals(Collections.singletonList("127.0.0.2"), delivered);
        presenter.query(false, "127.0.0.1"); worker.drain(); presenter.detach(); ui.drain();
        assertEquals(1, delivered.size());
    }
    @Test public void savingAddressOnlyWritesEndpointWithoutNetworkQuery() {
        List<String> saved = new ArrayList<>();
        assertFalse(presenter.saveAddress("invalid", saved::add)); assertEquals(1, invalid);
        assertTrue(presenter.saveAddress("127.0.0.1", saved::add));
        assertEquals(Collections.singletonList("ws://127.0.0.1:18086/ws"), saved);
        assertEquals(0, probes); assertEquals(0, worker.pending());
    }
}
