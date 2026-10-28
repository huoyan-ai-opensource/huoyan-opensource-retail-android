package cc.huoyan.retail.recognition;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

/** 事件唤醒的单线程识别循环；合并在途通知，无新帧时不调度，错误时停止。 */
public final class RecognitionLoop {
    /** 本次取帧的处理结果；空闲和成功均等待下一次通知，错误终止本轮。 */
    public enum Outcome { SUCCESS, IDLE, ERROR }

    /** 在唯一工作线程上执行的取帧任务，不持有待处理帧队列。 */
    public interface FrameTask {
        /** 处理最新帧，并通过守卫判断本轮识别是否仍然有效。 */
        Outcome run(BooleanSupplier isCurrent);
    }

    /** 唯一工作线程，重启时也不与尚未返回的旧 SDK 请求并发。 */
    private final ExecutorService executor;
    /** 从页面读取最新帧并处理的任务。 */
    private final FrameTask task;
    /** 当前代次的已提交任务；以下可变字段均由实例锁保护。 */
    private Future<?> pending;
    /** 停止或重启后使旧回执及旧调度失效的代次。 */
    private long generation;
    /** 是否接受新帧及就绪通知。 */
    private boolean running;
    /** 当前代次是否已有排队或执行中的任务。 */
    private boolean scheduled;
    /** 在途期间是否收到新通知；多次通知合并为一次取最新帧。 */
    private boolean wakeRequested;

    /** 使用独立线程创建识别循环。 */
    public RecognitionLoop(FrameTask task) {
        this(task, Executors.newSingleThreadExecutor());
    }

    /** 注入串行执行器，供单元测试验证唤醒及生命周期边界。 */
    RecognitionLoop(FrameTask task, ExecutorService executor) {
        this.task = task;
        this.executor = executor;
    }

    /** 启动并检查已有帧；重复启动只唤醒，不创建并发请求。 */
    public synchronized void start() {
        if (executor.isShutdown()) return;
        if (!running) {
            running = true;
            generation++;
        }
        wakeUp();
    }

    /** 新帧、SDK 就绪或业务恢复时通知；停止状态下不会自动重启。 */
    public synchronized void wakeUp() {
        if (!running) return;
        wakeRequested = true;
        if (!scheduled) schedule(generation);
    }

    /** 停止调度并使在途识别结果失效，不打断同步 SDK 请求。 */
    public synchronized void stop() {
        running = false;
        generation++;
        wakeRequested = false;
        scheduled = false;
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }

    /** 关闭识别循环及其工作线程。 */
    public synchronized void close() {
        stop();
        executor.shutdownNow();
    }

    /** 失败提示仍属于当前代次；显式停止或重启才使排队中的回执失效。 */
    private synchronized boolean isCurrent(long token) {
        return generation == token;
    }

    /** 持有实例锁提交一次取帧任务，没有定时器或延迟重试。 */
    private void schedule(long token) {
        scheduled = true;
        pending = executor.submit(() -> runFrame(token));
    }

    /** 消费本次通知；处理期间到达的通知只安排一次后续取帧。 */
    private void runFrame(long token) {
        synchronized (this) {
            if (!running || !isCurrent(token)) return;
            wakeRequested = false;
        }
        Outcome outcome = task.run(() -> isCurrent(token));
        synchronized (this) {
            if (!running || !isCurrent(token)) return;
            scheduled = false;
            pending = null;
            if (outcome == Outcome.ERROR) {
                running = false;
                wakeRequested = false;
                return;
            }
            if (wakeRequested) schedule(token);
        }
    }
}
