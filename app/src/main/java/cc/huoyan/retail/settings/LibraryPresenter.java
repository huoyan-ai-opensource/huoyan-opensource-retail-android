package cc.huoyan.retail.settings;

import cc.huoyan.sdk.client.model.BaseResult;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import cc.huoyan.sdk.client.model.RecognitionDataInfo;

/** 识别库备份恢复状态；跨 Fragment View 重建保留在途状态及未消费回执。 */
final class LibraryPresenter {
    /** 页面负责弹窗安全时机与本地化文案。 */
    interface View {
        /** 重新读取状态并渲染，页面不可见时可以延迟消费结果。 */
        void libraryChanged();
    }
    /** 可解绑重绑的视图；后台任务不持有 Fragment。 */
    private View view;
    /** 后台操作执行器，离开页面不撤销已提交操作。 */
    private final Executor worker;
    /** 主线程状态提交器。 */
    private final Executor ui;
    /** SDK 备份恢复操作。 */
    private final Function<Boolean, BaseResult> operation;
    /** 查询备份信息的 SDK 端口。 */
    private final Supplier<RecognitionDataInfo> infoSource;
    /** 查询学习商品数量的 SDK 端口。 */
    private final Supplier<Integer> countSource;
    /** 最新备份查询回执，null 表示不可用或未加载。 */
    private RecognitionDataInfo info;
    /** 学习商品数量，-1 表示不可用或未加载。 */
    private int count = -1;
    /** 备份信息请求代次，和持久写操作独立。 */
    private int infoGeneration;
    /** 商品数量请求代次，和备份查询独立。 */
    private int countGeneration;
    /** 当前操作是否进行中，主线程独占。 */
    private boolean running;
    /** 当前或最后一次操作是否为恢复。 */
    private boolean restoring;
    /** 尚未展示的回执，只有显式消费后清空。 */
    private BaseResult result;
    /** 注入后台操作及主线程调度，不直接触发 SDK。 */
    LibraryPresenter(Executor worker, Executor ui, Function<Boolean, BaseResult> operation,
                     Supplier<RecognitionDataInfo> infoSource, Supplier<Integer> countSource) {
        this.worker = worker; this.ui = ui; this.operation = operation;
        this.infoSource = infoSource; this.countSource = countSource;
    }
    /** 绑定新视图；页面自行选择安全时机首次渲染。 */
    void attach(View view) { this.view = view; }
    /** 释放旧视图，但继续保存后台回执。 */
    void detach() { view = null; ++infoGeneration; ++countGeneration; }
    /** 当前是否有库操作正在执行。 */
    boolean running() { return running; }
    /** 当前结果对应恢复还是备份。 */
    boolean restoring() { return restoring; }
    /** 查看未消费回执，不改变状态。 */
    BaseResult result() { return result; }
    /** 页面已经具备展示条件时消费回执，避免重复提示。 */
    void consumeResult() { result = null; }
    /** 和图片批量学习互斥；已提交任务执行完后再释放按钮。 */
    void run(boolean restore, boolean imageLearningRunning) {
        if (running || imageLearningRunning) return;
        restoring = restore; result = null; running = true;
        if (view != null) view.libraryChanged();
        worker.execute(() -> {
            BaseResult receipt = operation.apply(restore);
            ui.execute(() -> {
                result = receipt; running = false;
                if (view != null) view.libraryChanged();
            });
        });
    }
    /** 当前备份信息，页面只负责日期本地化。 */
    RecognitionDataInfo info() { return info; }
    /** 当前有效学习商品数量。 */
    int count() { return count; }
    /** 刷新备份信息；旧视图或旧请求的回执不覆盖当前状态。 */
    void refreshBackup() {
        int request = ++infoGeneration;
        worker.execute(() -> {
            RecognitionDataInfo loaded;
            try { loaded = infoSource.get(); } catch (RuntimeException error) { loaded = null; }
            RecognitionDataInfo result = loaded;
            ui.execute(() -> {
                if (view == null || request != infoGeneration) return;
                info = result; view.libraryChanged();
            });
        });
    }
    /** 独立刷新商品数量，失败显示不可用，不影响在途备份恢复。 */
    void refreshCount() {
        int request = ++countGeneration;
        worker.execute(() -> {
            int loaded;
            try { loaded = countSource.get(); } catch (RuntimeException error) { loaded = -1; }
            int result = loaded;
            ui.execute(() -> {
                if (view == null || request != countGeneration) return;
                count = result; view.libraryChanged();
            });
        });
    }
}
