package cc.huoyan.retail.home;

import cc.huoyan.retail.testing.QueuedExecutor;
import cc.huoyan.sdk.client.model.CloudStatus;
import org.junit.Test;
import static org.junit.Assert.*;

/** 云状态失败与重开弹窗的在途互斥测试。 */
public class CloudServicePresenterTest {
    /** 可控的 SDK 替身，不访问 Android 或真实云端。 */
    private static class Source implements CloudServicePresenter.Source {
        /** 当前查询回执。 */ CloudStatus status = new CloudStatus(0, null, true);
        /** 实际提交次数。 */ int calls;
        /** 返回受控快照。 */ public CloudStatus cloud() { return status; }
        /** 记录提交，不修改外部状态。 */ public CloudServicePresenter.Result run(CloudServicePresenter.Action action, String input) { calls++; return new CloudServicePresenter.Result(true, "done"); }
    }
    /** 错误回执中默认 false 不能被当作关闭。 */
    @Test public void failedStatusIsUnknownNotDisabled() {
        Source source = new Source();
        CloudServicePresenter p = new CloudServicePresenter(Runnable::run, Runnable::run, source);
        p.refresh(); assertEquals(Boolean.TRUE, p.enabled);
        source.status = new CloudStatus(-1, "offline", false); p.refresh(); assertNull(p.enabled);
        p.run(CloudServicePresenter.Action.ENABLE, null); assertEquals(0, source.calls);
        source.status = new CloudStatus(0, null, true); p.refresh(); assertEquals(Boolean.TRUE, p.enabled);
    }
    /** 关闭重开仍共享同次提交，已销毁的视图不能收到结果。 */
    @Test public void reopeningDoesNotDuplicateInFlightSync() {
        Source source = new Source(); QueuedExecutor worker = new QueuedExecutor();
        CloudServicePresenter p = new CloudServicePresenter(worker, Runnable::run, source);
        p.refresh(); worker.drain();
        p.run(CloudServicePresenter.Action.GOODS, null); p.detach();
        NoticeView view = new NoticeView(); p.attach(view); p.run(CloudServicePresenter.Action.GOODS, null);
        assertEquals(1, worker.pending()); worker.drain();
        assertEquals(1, source.calls); assertFalse(p.busy); assertEquals("done", view.receipt.message); assertEquals(1, view.calls);
    }
    /** 退出首页后允许已提交操作完成，但不能向关闭的线程池续排查询。 */
    @Test public void closingOwnerDoesNotEnqueueFollowupRead() {
        Source source = new Source(); QueuedExecutor worker = new QueuedExecutor();
        CloudServicePresenter p = new CloudServicePresenter(worker, Runnable::run, source);
        p.refresh(); worker.drain();
        p.run(CloudServicePresenter.Action.GOODS, null); p.close(); worker.drain();
        assertEquals(1, source.calls); assertEquals(0, worker.pending()); assertFalse(p.busy);
    }
    /** 登录成功、失败及异常都用一次性提示，后续刷新不能恢复底部结果。 */
    @Test public void bindingReceiptsAreTransientAndRetainSuccessFlag() {
        for (int outcome = 0; outcome < 3; outcome++) {
            final int fixture = outcome;
            Source source = new Source() {
                /** 模拟 SDK 成功、失败和异常，不访问真实店铺。 */
                @Override public CloudServicePresenter.Result run(CloudServicePresenter.Action action, String input) {
                    if (fixture == 2) throw new IllegalStateException("offline");
                    return new CloudServicePresenter.Result(fixture == 0, fixture == 0 ? "bound" : "rejected");
                }
            };
            CloudServicePresenter p = new CloudServicePresenter(Runnable::run, Runnable::run, source);
            NoticeView view = new NoticeView(); p.attach(view);
            p.run(CloudServicePresenter.Action.BIND, "shop");
             assertEquals(1, view.calls);
            assertEquals(fixture == 0, view.receipt.success);
            assertEquals(fixture == 0 ? "bound" : fixture == 1 ? "rejected" : "offline", view.receipt.message);
            p.refresh(); p.detach(); p.attach(view);
            assertEquals(1, view.calls); 
        }
    }
    /** 开启回执在关闭期间完成时暂存，重开仅提示一次且不留下底部小字。 */
    @Test public void enablingWhileDetachedDeliversOneNoticeOnReopen() {
        Source source = new Source(); source.status = new CloudStatus(0, null, false);
        QueuedExecutor worker = new QueuedExecutor();
        CloudServicePresenter p = new CloudServicePresenter(worker, Runnable::run, source);
        NoticeView view = new NoticeView(); p.attach(view); p.refresh(); worker.drain();
        p.run(CloudServicePresenter.Action.ENABLE, null); p.detach(); worker.drain();
        assertEquals(0, view.calls); 
        p.attach(view); assertEquals(1, view.calls); assertTrue(view.receipt.success);
        p.detach(); p.attach(view); assertEquals(1, view.calls); 
    }
    /** 捕获一次性提示，确保重绘和回执交付互不混淆。 */
    private static final class NoticeView implements CloudServicePresenter.View {
        /** 交付次数。 */ int calls;
        /** 最近一次回执。 */ CloudServicePresenter.Result receipt;
        /** 本测试不依赖 Android 渲染。 */ @Override public void render() { }
        /** 记录收到的真实成功标记和文案。 */
        @Override public void notifyResult(CloudServicePresenter.Result value) { calls++; receipt = value; }
    }
}
