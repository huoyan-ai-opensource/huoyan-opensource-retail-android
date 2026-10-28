package cc.huoyan.retail.settings;
import cc.huoyan.retail.testing.QueuedExecutor;
import cc.huoyan.sdk.client.model.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelPresenterTest {
    static class Screen implements PanelPresenter.View {
        int failures, completions, changes; BaseResult result;
        Capability capability;
        public void cloudChanged() { changes++; }
        public void cloudFailed(CloudStatus response) { failures++; }
        public void panelLoaded(StatusSnapshot status, CertInfo cert, Capability capability) { this.capability = capability; }
    }
    static class Data implements PanelPresenter.Data {
        boolean connected; int activations;
        public PanelPresenter.CloudResult cloud(Boolean requested) {
            return new PanelPresenter.CloudResult(new CloudStatus(-1, "denied", false), new CloudStatus(0, null, true));
        }
        public PanelPresenter.PanelResult panel() { return new PanelPresenter.PanelResult(null, null, null); }
    }
    /** 无证书时仍把独立的能力版本交给页面；销毁后不回写旧结果。 */
    @Test public void panelForwardsCapabilityWithoutCertificate() {
        QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
        Screen view = new Screen();
        Capability capability = new Capability(0, null, 2, false, false, false,
                false, false, false, "2.0.0", 7);
        Data data = new Data() {
            public PanelPresenter.PanelResult panel() {
                return new PanelPresenter.PanelResult(null, null, capability);
            }
        };
        PanelPresenter presenter = new PanelPresenter(view, worker, ui,
                data);
        try {
            presenter.refresh(); worker.next(); ui.next();
            assertSame(capability, view.capability);
            assertEquals("2.0.0", cc.huoyan.retail.sdk.SdkVersionDisplay.name(view.capability));
            view.capability = null;
            presenter.refresh(); worker.next(); presenter.detach(); ui.next();
            assertNull(view.capability);
        } finally { presenter.detach(); }
    }

    @Test public void cloudFailureUsesActualStateAndOldResultsCannotOverrideResume() {
        QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor(); Screen v = new Screen(); Data data = new Data();
        PanelPresenter p = new PanelPresenter(v, worker, ui, data);
        try {
            p.cloud(false); p.cloud(true); assertEquals(1, worker.pending()); worker.next();
            p.resume(); ui.drain(); assertNull(p.cloudEnabled()); assertEquals(0, v.failures);
            worker.next(); ui.drain(); assertEquals(Boolean.TRUE, p.cloudEnabled()); assertFalse(p.cloudRunning()); assertEquals(1, v.failures);
        } finally { p.detach(); }
    }
}
