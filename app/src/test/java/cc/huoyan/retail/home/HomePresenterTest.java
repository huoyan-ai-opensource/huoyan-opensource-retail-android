package cc.huoyan.retail.home;
import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class HomePresenterTest {
    @Test public void pollsOnlyAfterCompletionAndRejectsPausedResult() {
        QueuedExecutor worker = new QueuedExecutor(), ui = new QueuedExecutor();
        List<Runnable> delayed = new ArrayList<>();
        List<HomePresenter.Status> rendered = new ArrayList<>();
        HomePresenter p = new HomePresenter(new HomePresenter.View() {
            public void showConnection() { }
            public void showSdkStartup(boolean loading) { }
            public void showStatus(HomePresenter.Status status) { rendered.add(status); }
        }, worker, ui, (task, delay) -> { assertEquals(5000L, (long) delay); delayed.add(task); },
                () -> new HomePresenter.Status(true, false, "—"), () -> true, () -> { });
        p.resume(); assertEquals(1, worker.pending()); assertTrue(delayed.isEmpty());
        worker.next(); p.pause(); ui.drain(); assertTrue(rendered.isEmpty()); assertTrue(delayed.isEmpty());
        p.resume(); worker.next(); ui.next(); assertEquals(1, rendered.size()); assertEquals(1, delayed.size());
        delayed.remove(0).run(); assertEquals(1, worker.pending());
        worker.next(); p.detach(); ui.drain(); assertEquals(1, rendered.size()); assertTrue(delayed.isEmpty());
    }
    @Test public void oldTimerCannotStartQueryAfterNewResume() {
        QueuedExecutor worker = new QueuedExecutor(); List<Runnable> timers = new ArrayList<>();
        HomePresenter p = new HomePresenter(new HomePresenter.View() {
            public void showConnection() { }
            public void showSdkStartup(boolean loading) { }
            public void showStatus(HomePresenter.Status status) { }
        }, worker, Runnable::run, (task, delay) -> timers.add(task), () -> new HomePresenter.Status(false, false, "—"), () -> false, () -> { });
        p.resume(); worker.next(); p.pause(); p.resume(); timers.get(0).run();
        assertEquals(1, worker.pending()); p.detach();
    }

    /** 连接成功即可结束加载，不依赖后台云查询或模型回执。 */
    @Test public void connectionAloneEndsLoading() {
        StartupView view = new StartupView();
        List<Runnable> timers = new ArrayList<>();
        AtomicBoolean connected = new AtomicBoolean(false);
        HomePresenter presenter = startupPresenter(view, timers, connected);
        presenter.resume(); presenter.beginSdkStartup();
        assertTrue(view.loading);
        timers.remove(0).run(); assertTrue(view.loading);
        connected.set(true); timers.remove(0).run();
        assertFalse(view.loading); assertTrue(timers.isEmpty());
    }

    /** 已连接时再次进入首页，不闪现加载态。 */
    @Test public void existingConnectionSkipsLoading() {
        StartupView view = new StartupView();
        List<Runnable> timers = new ArrayList<>();
        HomePresenter presenter = startupPresenter(view, timers, new AtomicBoolean(true));
        presenter.resume(); presenter.beginSdkStartup();
        assertFalse(view.loading); assertTrue(timers.isEmpty());
    }

    /** 旧连接轮询不能操作新页面或已释放的页面。 */
    @Test public void oldConnectionPollCannotChangeNewSession() {
        StartupView view = new StartupView();
        List<Runnable> timers = new ArrayList<>();
        AtomicBoolean connected = new AtomicBoolean(false);
        HomePresenter presenter = startupPresenter(view, timers, connected);
        presenter.resume(); presenter.beginSdkStartup();
        presenter.pause(); presenter.resume(); presenter.beginSdkStartup();
        connected.set(true); timers.remove(0).run();
        assertTrue(view.loading); assertEquals(1, timers.size());
        presenter.detach(); timers.remove(0).run();
        assertTrue(view.loading); assertTrue(timers.isEmpty());
    }

    /** 版本可读不能停止初始化；旧连接计时器失效，云端轮询继续。 */
    @Test public void versionDoesNotStopInitializationOrCloudPolling() {
        QueuedExecutor worker = new QueuedExecutor();
        List<Runnable> timers = new ArrayList<>();
        List<Boolean> stops = new ArrayList<>();
        StartupView view = new StartupView();
        AtomicBoolean connected = new AtomicBoolean(false);
        AtomicBoolean cloud = new AtomicBoolean(false);
        HomePresenter presenter = new HomePresenter(view, worker, Runnable::run,
                (task, delay) -> timers.add(task),
                () -> new HomePresenter.Status(true, cloud.get(), "2.0"), connected::get,
                () -> stops.add(true));
        presenter.resume(); presenter.beginSdkStartup(); connected.set(true); worker.next();
        assertFalse(view.loading); assertEquals(0, stops.size());
        timers.remove(0).run(); assertEquals(1, timers.size());
        cloud.set(true); timers.remove(0).run(); worker.next();
        assertEquals(0, stops.size()); assertEquals(1, timers.size());
        presenter.pause(); timers.remove(0).run(); assertEquals(0, worker.pending());
    }

    /** 版本缺失或连接已断开时继续查询，避免把无效快照当作加载完成。 */
    @Test public void missingVersionOrDisconnectedKeepsPolling() {
        for (String version : Arrays.asList(null, "", "  ", "—", "2.0")) {
            QueuedExecutor worker = new QueuedExecutor();
            List<Runnable> timers = new ArrayList<>();
            HomePresenter presenter = new HomePresenter(new StartupView(), worker, Runnable::run,
                    (task, delay) -> timers.add(task),
                    () -> new HomePresenter.Status(true, true, version),
                    () -> !"2.0".equals(version),
                    () -> fail("无版本或未连接时不能停止观察"));
            presenter.resume();
            worker.next();
            assertEquals(1, timers.size());
            timers.remove(0).run();
            assertEquals(1, worker.pending());
            presenter.detach();
            worker.next();
            assertTrue(timers.isEmpty());
        }
    }

    /** 已连接且可读版本时仍等待初始化，明确未激活和就绪才释放入口。 */
    @Test public void connectedVersionCannotReleaseInitializingCheckout() {
        for (String terminal : Arrays.asList("READY", "OFFLINE", "NOT_ACTIVATED")) {
            StartupView view = new StartupView();
            List<Boolean> stops = new ArrayList<>();
            HomePresenter presenter = new HomePresenter(view, Runnable::run, Runnable::run,
                    (task, delay) -> { }, () -> new HomePresenter.Status(true, false, "2.0"),
                    () -> true, () -> stops.add(true));
            presenter.resume();
            java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong();
            presenter.clock = time::get;
            cc.huoyan.sdk.client.session.SdkStartupController.Listener listener = presenter.beginSdkStartup();
            assertTrue(view.checkoutStarting);
            assertFalse(view.loading);
            assertTrue(stops.isEmpty());
            for (String state : Arrays.asList("NOT_INIT", "INITIALIZING", "MODEL_PREPARING")) {
                listener.onStatus(0, snapshot(state), false);
                assertTrue(view.checkoutStarting);
                assertTrue(stops.isEmpty());
            }
            time.set(600);
            listener.onStatus(0, snapshot(terminal), !"NOT_ACTIVATED".equals(terminal));
            assertFalse(view.checkoutStarting);
            assertEquals(HomeCheckoutState.from(snapshot(terminal)), view.checkoutState);
            assertEquals(1, stops.size());
            presenter.pause();
            presenter.resume();
            presenter.beginSdkStartup();
            listener.onStatus(0, snapshot(terminal), true);
            assertFalse(view.checkoutStarting);
        }
    }

    /** 快速完成时稳定展示 600ms，离开页面使延迟切换失效。 */
    @Test public void fastStartupHasBoundedDisplayTimeAndRejectsPausedFinish() {
        StartupView view = new StartupView();
        List<Runnable> timers = new ArrayList<>();
        java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong();
        HomePresenter p = new HomePresenter(view, new QueuedExecutor(), Runnable::run,
                (task, delay) -> { assertEquals(500L, (long) delay); timers.add(task); },
                () -> new HomePresenter.Status(false, false, "—"), () -> true, () -> { });
        p.clock = time::get;
        p.resume();
        cc.huoyan.sdk.client.session.SdkStartupController.Listener listener = p.beginSdkStartup();
        time.set(100);
        listener.onStatus(0, snapshot("READY"), true);
        assertTrue(view.checkoutStarting);
        timers.remove(0).run();
        assertFalse(view.checkoutStarting);
        p.pause();
        p.resume();
        listener = p.beginSdkStartup();
        assertFalse(view.checkoutStarting);
        listener.onStatus(0, snapshot("INITIALIZING"), false);
        assertTrue(view.checkoutStarting);
        time.set(200);
        listener.onStatus(0, snapshot("READY"), true);
        p.pause();
        timers.remove(0).run();
        assertTrue(view.checkoutStarting);
    }

    /** 返回首页静默刷新就绪或未激活状态，真实断连仍立即恢复等待。 */
    @Test public void returningHomeOnlyWaitsForActualRestart() {
        for (String terminal : Arrays.asList("READY", "OFFLINE", "NOT_ACTIVATED")) {
            StartupView view = new StartupView();
            List<Runnable> timers = new ArrayList<>();
            AtomicBoolean connected = new AtomicBoolean(true);
            java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong();
            HomePresenter p = new HomePresenter(view, new QueuedExecutor(), Runnable::run,
                    (task, delay) -> timers.add(task),
                    () -> new HomePresenter.Status(false, false, "—"), connected::get, () -> { });
            p.clock = time::get;
            p.resume();
            cc.huoyan.sdk.client.session.SdkStartupController.Listener first = p.beginSdkStartup();
            time.set(600);
            first.onStatus(0, snapshot(terminal), !"NOT_ACTIVATED".equals(terminal));
            assertFalse(view.checkoutStarting);
            p.pause(); p.resume();
            cc.huoyan.sdk.client.session.SdkStartupController.Listener returned = p.beginSdkStartup();
            assertFalse(view.checkoutStarting);
            first.onStatus(0, snapshot("INITIALIZING"), false);
            assertFalse(view.checkoutStarting);
            returned.onStatus(0, snapshot(terminal), !"NOT_ACTIVATED".equals(terminal));
            assertFalse(view.checkoutStarting);
            assertEquals(HomeCheckoutState.from(snapshot(terminal)), view.checkoutState);
            assertTrue(timers.isEmpty());
            p.pause(); connected.set(false); p.resume(); p.beginSdkStartup();
            assertTrue(view.checkoutStarting);
        }
    }

    /** 云查询仍阻塞时，连接后的版本可独立返回；离开页面的回执丢弃。 */
    @Test public void versionDoesNotWaitForCloudOrFiveSecondPoll() {
        StartupView view = new StartupView();
        QueuedExecutor cloud = new QueuedExecutor(), versions = new QueuedExecutor();
        List<Runnable> timers = new ArrayList<>();
        AtomicBoolean connected = new AtomicBoolean(false);
        HomePresenter p = new HomePresenter(view, cloud, Runnable::run,
                (task, delay) -> timers.add(task), () -> new HomePresenter.Status(false, false, "—"),
                connected::get, () -> { });
        p.configureVersionLoader(versions, () -> "2.0");
        p.resume(); p.beginSdkStartup();
        assertEquals(0, versions.pending());
        connected.set(true);
        for (Runnable timer : new ArrayList<>(timers)) timer.run();
        versions.next();
        assertEquals("2.0", view.version);
        assertEquals(1, cloud.pending());
        view.version = null;
        p.beginSdkStartup(); p.pause(); versions.next();
        assertNull(view.version);
    }

    /** 启动回调的最短等待结束时，采用期间轮询到的待审核状态。 */
    @Test public void delayedReadyDoesNotOverwriteNewPendingSnapshot() {
        StartupView view = new StartupView();
        QueuedExecutor worker = new QueuedExecutor();
        List<Runnable> timers = new ArrayList<>();
        HomePresenter p = new HomePresenter(view, worker, Runnable::run,
                (task, delay) -> timers.add(task),
                () -> new HomePresenter.Status(true, false, "2.0", HomeCheckoutState.ROTATION_PENDING),
                () -> true, () -> { });
        p.clock = () -> 0L;
        p.resume();
        p.beginSdkStartup().onStatus(0, snapshot("READY"), true);
        worker.next();
        timers.remove(0).run();
        assertFalse(view.checkoutStarting);
        assertEquals(HomeCheckoutState.ROTATION_PENDING, view.checkoutState);
    }

    /** 构造真实 SDK 初始化与授权回执。 */
    private static cc.huoyan.sdk.client.model.StatusSnapshot snapshot(String state) {
        boolean ready = "READY".equals(state) || "OFFLINE".equals(state);
        return new cc.huoyan.sdk.client.model.StatusSnapshot(0, null, null, null, null, null,
                false, null, state, ready, null, null, null, ready);
    }

    /** 状态查询留在队列中，独立验证连接轮询。 */
    private static HomePresenter startupPresenter(StartupView view, List<Runnable> timers, AtomicBoolean connected) {
        return new HomePresenter(view, new QueuedExecutor(), Runnable::run,
                (task, delay) -> { assertEquals(200L, (long) delay); timers.add(task); },
                () -> new HomePresenter.Status(false, false, "—"), connected::get, () -> { });
    }

    /** 只记录连接等待结果的 View 替身。 */
    private static final class StartupView implements HomePresenter.View {
        /** 最近一次展示是否转圈。 */
        boolean loading;
        /** 收银入口的初始化等待，不等同于连接状态。 */
        boolean checkoutStarting;
        /** 启动完成时的授权结果。 */
        HomeCheckoutState checkoutState;
        /** 独立版本回执。 */
        String version;
        /** 保存独立版本回执。 */
        @Override public void showSdkVersion(String value) { version = value; }
        /** 保存真实初始化等待与授权。 */
        @Override public void showCheckoutStartup(boolean starting, HomeCheckoutState state) {
            checkoutStarting = starting; checkoutState = state;
        }
        /** 本组测试不观察连接名称。 */
        @Override public void showConnection() { }
        /** 本组测试不观察云状态。 */
        @Override public void showStatus(HomePresenter.Status status) { }
        /** 保存展示结果供时序断言使用。 */
        @Override public void showSdkStartup(boolean loading) { this.loading = loading; }
    }
}
