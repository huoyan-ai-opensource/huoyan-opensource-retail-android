package cc.huoyan.retail.checkout.integration;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** 合并尚未开始的查询，并在主线程提交前丢弃被新结果、人工修改或销毁取代的结果。 */
final class LatestRefresh<T> {
    private final Executor worker;
    private final Executor main;
    private long revision;
    private boolean running;
    private boolean closed;
    private Runnable pending;

    LatestRefresh(Executor worker, Executor main) {
        this.worker = worker;
        this.main = main;
    }

    // submit/invalidate/close 与提交回调均由主线程调用。
    synchronized void submit(Callable<T> query, Consumer<T> commit, Consumer<Exception> failure) {
        if (closed) return;
        long version = ++revision;
        pending = () -> {
            try {
                T result = query.call();
                main.execute(() -> { if (isCurrent(version)) commit.accept(result); });
            } catch (Exception e) {
                main.execute(() -> { if (isCurrent(version)) failure.accept(e); });
            }
        };
        if (!running) {
            running = true;
            worker.execute(this::drain);
        }
    }

    synchronized void invalidate() {
        revision++;
        pending = null;
    }

    synchronized void close() {
        closed = true;
        invalidate();
    }

    private synchronized boolean isCurrent(long version) {
        return !closed && revision == version;
    }

    private void drain() {
        while (true) {
            Runnable next;
            synchronized (this) {
                next = pending;
                pending = null;
                if (next == null) {
                    running = false;
                    return;
                }
            }
            next.run();
        }
    }
}
