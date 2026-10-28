package cc.huoyan.retail.checkout.integration;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import static org.junit.Assert.*;

public class LatestRefreshTest {
    private final Queue<Runnable> worker = new ArrayDeque<>();
    private final Queue<Runnable> main = new ArrayDeque<>();
    private final List<Integer> commits = new ArrayList<>();
    private final LatestRefresh<Integer> refresh = new LatestRefresh<>(worker::add, main::add);

    @Test public void newRequestSupersedesResultAlreadyQueuedOnMain() {
        refresh.submit(() -> 1, commits::add, e -> fail());
        worker.remove().run();
        refresh.submit(() -> 2, commits::add, e -> fail());
        main.remove().run();
        worker.remove().run();
        main.remove().run();
        assertEquals(java.util.Collections.singletonList(2), commits);
    }

    @Test public void newFrameDuringQueryWinsAndOlderQueryNeverCommits() {
        refresh.submit(() -> {
            refresh.submit(() -> 2, commits::add, e -> fail());
            refresh.submit(() -> 3, commits::add, e -> fail());
            return 1;
        }, commits::add, e -> fail());
        worker.remove().run();
        while (!main.isEmpty()) main.remove().run();
        assertEquals(java.util.Collections.singletonList(3), commits);
    }

    @Test public void resetOrManualEditInvalidatesInFlightResult() {
        refresh.submit(() -> 1, commits::add, e -> fail());
        worker.remove().run();
        refresh.invalidate();
        main.remove().run();
        assertTrue(commits.isEmpty());
    }

    @Test public void coalescesPendingFramesAndIgnoresDestroyedPage() {
        for (int i = 0; i < 100; i++) {
            final int value = i;
            refresh.submit(() -> value, commits::add, e -> fail());
        }
        assertEquals(1, worker.size());
        worker.remove().run();
        refresh.close();
        main.remove().run();
        assertTrue(commits.isEmpty());
        refresh.submit(() -> 101, commits::add, e -> fail());
        assertTrue(worker.isEmpty());
    }

    @Test public void recoversAfterFailedQuery() {
        refresh.submit(() -> { throw new IllegalStateException(); }, commits::add, e -> {});
        worker.remove().run();
        main.remove().run();
        refresh.submit(() -> 2, commits::add, e -> fail());
        worker.remove().run();
        main.remove().run();
        assertEquals(java.util.Collections.singletonList(2), commits);
    }
}
