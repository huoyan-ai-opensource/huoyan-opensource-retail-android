package cc.huoyan.retail.checkout.domain;

import java.util.function.Consumer;

/** 一次出纸对应固定小票和会话；反馈失败不能把已经出纸的操作改判成打印失败。 */
public final class SalePrintJob {
    public interface Printer {
        /** 输出固定的小票内容，失败时抛出异常。 */
        void print(WeightedSaleResult receipt) throws Exception;
    }

    private final long generation;
    private final WeightedSaleResult receipt;
    private boolean started;

    SalePrintJob(long generation, WeightedSaleResult receipt) {
        this.generation = generation;
        this.receipt = receipt;
    }

    boolean belongsTo(long currentGeneration) { return generation == currentGeneration; }

    /** 执行一次打印；出纸后的反馈失败独立上报，不重试打印。 */
    public synchronized void execute(Printer printer, Runnable feedback, Consumer<Exception> feedbackFailure) throws Exception {
        if (started) throw new IllegalStateException("print job already started");
        started = true;
        printer.print(receipt);
        try {
            feedback.run();
        } catch (Exception e) {
            feedbackFailure.accept(e);
        }
    }
}
