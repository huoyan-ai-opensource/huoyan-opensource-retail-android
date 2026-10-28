package cc.huoyan.retail.learning;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** 手动推进索引、防抖与 UI 队列，无真实时钟等待。 */
public class GoodsSearchPresenterTest {
    private final Timer worker = new Timer();
    private final QueuedExecutor ui = new QueuedExecutor();
    private final List<List<GoodsEntity>> shown = new ArrayList<>();
    private int errors;
    private final GoodsSearchPresenter.View view = new GoodsSearchPresenter.View() {
        public void loading() { }
        public void results(List<GoodsEntity> goods) { shown.add(goods); }
        public void failed(String reason) { errors++; }
    };
    private static GoodsEntity goods(String plu) { GoodsEntity g = new GoodsEntity(); g.setPlu(plu); g.setName(plu); return g; }
    @Test public void typingDuringLoadUsesLatestQueryAndTypingDebounces() {
        GoodsSearchPresenter p = new GoodsSearchPresenter(view, () -> Arrays.asList(goods("123"), goods("456")), worker, ui);
        p.load("123"); p.search("456", false); worker.queue.drain(); ui.drain(); worker.queue.drain(); ui.drain();
        assertEquals("456", shown.get(0).get(0).getPlu());
        p.search("123", false); assertEquals(100, worker.delayMs); p.search("none", false);
        worker.queue.drain(); ui.drain(); assertEquals(2, shown.size()); assertTrue(shown.get(1).isEmpty());
    }
    @Test public void queuedOldResultAndDetachedResultCannotRender() {
        GoodsSearchPresenter p = new GoodsSearchPresenter(view, () -> Collections.singletonList(goods("123")), worker, ui);
        p.load(""); worker.queue.drain(); ui.drain(); worker.queue.drain();
        p.search("none", true); ui.drain(); assertTrue(shown.isEmpty());
        worker.queue.drain(); p.detach(); ui.drain(); assertTrue(shown.isEmpty());
    }
    @Test public void loadFailureIsNotReplacedWithEmptySearchSuccess() {
        GoodsSearchPresenter p = new GoodsSearchPresenter(view, () -> { throw new IllegalStateException("offline"); }, worker, ui);
        p.load(""); worker.queue.drain(); ui.drain(); p.search("123", false); worker.queue.drain(); ui.drain();
        assertEquals(1, errors); assertTrue(shown.isEmpty());
    }
    /** 替代索引与防抖使用的两个调度 API，不创建真实线程。 */
    private static final class Timer extends ScheduledThreadPoolExecutor {
        /** 待手动运行的后台任务。 */
        final QueuedExecutor queue = new QueuedExecutor();
        /** 最近请求延迟，用于验证防抖契约。 */
        long delayMs;
        /** 父类线程池不启动，所有入口均转入手动队列。 */
        Timer() { super(1); }
        /** 普通索引任务不自动运行。 */
        @Override public void execute(Runnable command) { queue.execute(command); }
        /** 记录防抖延迟，用 FutureTask 保留取消语义。 */
        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            delayMs = unit.toMillis(delay); Task task = new Task(command); queue.execute(task); return task;
        }
    }
    /** 可取消的手动定时任务，无需等待真实时间。 */
    private static final class Task extends FutureTask<Void> implements ScheduledFuture<Void> {
        /** 将操作包装为可取消的 Future。 */
        Task(Runnable runnable) { super(runnable, null); }
        /** 手动队列不自动使用剩余延迟。 */
        @Override public long getDelay(TimeUnit unit) { return 0; }
        /** 手动队列按插入顺序执行，不参与定时排序。 */
        @Override public int compareTo(Delayed other) { return 0; }
    }
}
