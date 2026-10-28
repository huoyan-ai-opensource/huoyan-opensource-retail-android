package cc.huoyan.retail.testing;

import java.util.*;
import java.util.concurrent.*;

/** 手动推进后台和主线程队列，确定性验证迟到回调和销毁竞态。 */
public final class QueuedExecutor extends AbstractExecutorService {
    /** 未执行任务，顺序对应真实串行执行器。 */
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    /** 关闭后拒绝新任务。 */
    private boolean closed;
    /** 执行下一项，允许测试在后台与 UI 提交之间插入生命周期事件。 */
    public void next() { tasks.removeFirst().run(); }
    /** 执行全部当前及嵌套提交任务。 */
    public void drain() { while (!tasks.isEmpty()) next(); }
    /** 返回尚未推进的任务数。 */
    public int pending() { return tasks.size(); }
    /** 仅排队，不隐式创建线程。 */
    @Override public void execute(Runnable task) { if (closed) throw new RejectedExecutionException(); tasks.addLast(task); }
    /** 拒绝新任务，但保留已排队工作。 */
    @Override public void shutdown() { closed = true; }
    /** 丢弃未执行任务，模拟销毁查询队列。 */
    @Override public List<Runnable> shutdownNow() { closed = true; List<Runnable> pending = new ArrayList<>(tasks); tasks.clear(); return pending; }
    /** 返回关闭标记。 */
    @Override public boolean isShutdown() { return closed; }
    /** 关闭且无任务时视为终止。 */
    @Override public boolean isTerminated() { return closed && tasks.isEmpty(); }
    /** 不等待真实线程，仅返回当前终止状态。 */
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
}
