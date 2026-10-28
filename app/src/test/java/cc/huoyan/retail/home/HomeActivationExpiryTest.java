package cc.huoyan.retail.home;

import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** 验证首页轮询、退出和重建时临期提醒的消费时机。 */
public class HomeActivationExpiryTest {
    /** 提醒展示受阻时可重试，成功后轮询不再重复，重新进入再判断。 */
    @Test public void retriesBusyViewButShowsOnlyOncePerVisit() {
        Fixture f = new Fixture();
        f.view.available = false;
        f.presenter.resume(); f.worker.next();
        assertEquals(0, f.view.shown);
        f.view.available = true; f.poll();
        assertEquals(1, f.view.shown); f.poll();
        assertEquals(1, f.view.shown);
        f.presenter.pause(); f.presenter.resume(); f.worker.next();
        assertEquals(2, f.view.shown);
    }

    /** 页面重建保持已提醒标记，之后正常离开再进入仍会提示。 */
    @Test public void recreationPreservesConsumedVisit() {
        Fixture first = new Fixture();
        first.presenter.resume(); first.worker.next();
        Fixture restored = new Fixture();
        restored.presenter.restoreExpiryVisit(first.presenter.hasShownExpiry());
        first.presenter.detach();
        restored.presenter.resume(); restored.worker.next();
        assertEquals(0, restored.view.shown);
        restored.presenter.pause(); restored.presenter.resume(); restored.worker.next();
        assertEquals(1, restored.view.shown);
    }

    /** 暂停、销毁或旧代次在途结果不能弹窗。 */
    @Test public void rejectsLateResultsAfterPauseAndNewVisit() {
        Fixture f = new Fixture();
        f.presenter.resume(); f.presenter.pause(); f.presenter.resume();
        f.worker.next(); assertEquals(0, f.view.shown);
        f.worker.next(); assertEquals(1, f.view.shown);
        f.presenter.pause(); f.presenter.resume(); f.presenter.detach();
        f.worker.next(); assertEquals(1, f.view.shown);
    }

    /** 续期后重新进入采用新有效期；无效快照不消费提示机会。 */
    @Test public void renewalAndInitiallyUnknownStatusUseLatestSnapshot() {
        Fixture f = new Fixture();
        f.status = new HomePresenter.Status(false, false, null);
        f.presenter.resume(); f.worker.next(); assertEquals(0, f.view.shown);
        f.status = new HomePresenter.Status(false, false, null, HomeCheckoutState.READY, 100001);
        f.poll(); assertEquals(1, f.view.shown);
        f.presenter.pause();
        f.status = new HomePresenter.Status(false, false, null, HomeCheckoutState.READY, 900000);
        f.presenter.resume(); f.worker.next(); assertEquals(1, f.view.shown);
    }

    /** 隔离后台队列、时钟与轮询的测试夹具。 */
    private static final class Fixture {
        /** 手动完成的后台查询。 */
        final QueuedExecutor worker = new QueuedExecutor();
        /** 当前首页的延迟轮询。 */
        final List<Runnable> timers = new ArrayList<>();
        /** 可控制弹窗是否被其他操作阻挡的视图。 */
        final ExpiryView view = new ExpiryView();
        /** 下一次 SDK 返回的快照。 */
        HomePresenter.Status status = new HomePresenter.Status(true, false, null, HomeCheckoutState.READY, 100001);
        /** 使用真实首页协调逻辑的被测对象。 */
        final HomePresenter presenter = new HomePresenter(view, worker, Runnable::run,
                (task, delay) -> timers.add(task), () -> status, () -> true, () -> { });
        /** 固定墙上时钟。 */
        Fixture() { presenter.epochSeconds = () -> 100000; }
        /** 完成下一次轮询。 */
        void poll() { timers.remove(0).run(); worker.next(); }
    }

    /** 仅记录成功展示次数的 View 替身。 */
    private static final class ExpiryView implements HomePresenter.View {
        /** 当前是否可以展示弹窗。 */
        boolean available = true;
        /** 已成功展示次数。 */
        int shown;
        /** 连接渲染不参与本组断言。 */
        @Override public void showConnection() { }
        /** 云状态渲染不参与本组断言。 */
        @Override public void showStatus(HomePresenter.Status status) { }
        /** 启动渲染不参与本组断言。 */
        @Override public void showSdkStartup(boolean loading) { }
        /** 模拟展示受阻或成功。 */
        @Override public boolean showActivationExpiry(ActivationExpiry expiry) {
            if (available) shown++;
            return available;
        }
    }
}
