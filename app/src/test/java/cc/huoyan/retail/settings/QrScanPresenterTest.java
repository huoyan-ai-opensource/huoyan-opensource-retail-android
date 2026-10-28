package cc.huoyan.retail.settings;

import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** 验证帧节流、单次交付和销毁后资源收尾，不依赖 Android。 */
public class QrScanPresenterTest {
    private final QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
    private long now;
    private int captures, releases;
    private String decoded;
    private boolean throwDecode;
    private final List<String> delivered = new ArrayList<>();
    private final QrScanPresenter presenter = new QrScanPresenter(new QrScanPresenter.View() {
        public QrScanPresenter.Frame capture(int width, int height) {
            captures++;
            return new QrScanPresenter.Frame() {
                public String decode() { if (throwDecode) throw new IllegalStateException(); return decoded; }
                public void close() { releases++; }
            };
        }
        public void scanned(String value) { delivered.add(value); }
    }, worker, ui, () -> now);
    @Test public void cameraBurstCoalescesAndOnlyOneDecodeCanRun() {
        for (int i = 0; i < 100; i++) presenter.frameAvailable(640, 480);
        assertEquals(1, ui.pending()); ui.drain();
        now = 500; presenter.frameAvailable(640, 480); ui.drain(); assertEquals(1, captures);
        worker.drain(); ui.drain(); assertEquals(1, releases);
    }
    @Test public void emptyResultRetriesOnlyAfterFrameInterval() {
        presenter.frameAvailable(640, 480); ui.drain(); worker.drain(); ui.drain();
        now = 249; presenter.frameAvailable(640, 480); ui.drain(); assertEquals(1, captures);
        now = 250; presenter.frameAvailable(640, 480); ui.drain(); assertEquals(2, captures);
    }
    @Test public void resultIsDeliveredExactlyOnceAndFrameIsReleased() {
        decoded = "code"; presenter.frameAvailable(640, 480); ui.drain(); worker.drain(); ui.drain();
        now = 500; presenter.frameAvailable(640, 480); ui.drain(); worker.drain(); ui.drain();
        assertEquals(Collections.singletonList("code"), delivered); assertEquals(1, releases);
    }
    @Test public void detachBeforeDecodeStillReleasesFrameButDoesNotDeliver() {
        decoded = "late"; presenter.frameAvailable(640, 480); ui.drain(); presenter.detach(); worker.drain(); ui.drain();
        assertEquals(1, releases); assertTrue(delivered.isEmpty());
    }
    @Test public void detachBeforeSnapshotDoesNotTouchView() {
        presenter.frameAvailable(640, 480); presenter.detach(); ui.drain(); assertEquals(0, captures);
    }
    @Test public void decoderFailureReleasesFrameAndAllowsNextAttempt() {
        throwDecode = true; presenter.frameAvailable(640, 480); ui.drain(); worker.drain(); ui.drain();
        assertEquals(1, releases); throwDecode = false; decoded = "retry"; now = 250;
        presenter.frameAvailable(640, 480); ui.drain(); worker.drain(); ui.drain();
        assertEquals(Collections.singletonList("retry"), delivered); assertEquals(2, releases);
    }
    /** 切换时旧解码即使成功也不能交付，新相机可继续扫码。 */
    @Test public void switchingCameraRejectsOldDecodeAndAcceptsNewFrame() {
        decoded = "old-camera";
        presenter.frameAvailable(640, 480); ui.drain();
        presenter.cameraChanged();
        worker.drain(); ui.drain();
        assertTrue(delivered.isEmpty());
        assertEquals(1, releases);
        decoded = "new-camera";
        presenter.frameAvailable(1280, 720); ui.drain(); worker.drain(); ui.drain();
        assertEquals(Collections.singletonList("new-camera"), delivered);
        assertEquals(2, releases);
    }
}
