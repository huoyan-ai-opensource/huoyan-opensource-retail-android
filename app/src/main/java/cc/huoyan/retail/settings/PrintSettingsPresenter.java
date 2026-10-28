package cc.huoyan.retail.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/** 打印页后台任务协调器；主线程管理状态，视图重建时恢复等待，已提交任务不取消。 */
final class PrintSettingsPresenter {
    /** 页面展示边界，不允许设备实现持有页面。 */
    interface View {
        /** 显示已选择的实际 USB 设备。 */
        void showSelection(String device);
        /** 展示刚刚枚举到的设备选项。 */
        void showDevices(List<String> devices, String selected);
        /** 一次性提示设备发现失败。 */
        void showError(String error);
        /** 提示先选择设备。 */
        void noPrinter();
        /** 显示连接等待并禁用配置及打印操作。 */
        void connectionStarted();
        /** 关闭等待，null 表示连接可用。 */
        void connectionFinished(String error);
        /** 显示打印等待并禁用配置及连接操作。 */
        void printStarted();
        /** 关闭等待，null 表示打印字节已发送。 */
        void printFinished(String error);
    }

    /** 后台硬件与配置边界。 */
    interface Device {
        /** 返回可用打印设备。 */
        List<String> list();
        /** 从本次枚举中解析已保存的 打印设备选择。 */
        String selected(List<String> devices);
        /** 检查固定设备的连接，返回错误或 null。 */
        String connect(String selected);
        /** 向固定设备提交示例小票，返回错误或 null。 */
        String print(String selected, int paperWidthMm);
    }

    /** 当前视图；每次销毁时断开，后台不捕获 Fragment。 */
    private View view;
    /** 当前页面的串行设备队列，由 ViewModel 关闭。 */
    private final Executor worker;
    /** 不捕获页面的主线程调度器。 */
    private final Executor ui;
    /** 只使用应用上下文的硬件实现。 */
    private final Device device;
    /** 最近一次设备枚举结果。 */
    private List<String> devices = Collections.emptyList();
    /** 当前已解析设备选择。 */
    private String selection;
    /** 枚举在途标记。 */
    private boolean loading;
    /** 连接或打印的共同互斥标记。 */
    private boolean busy;
    /** 当前或刚结束的操作是否为打印。 */
    private boolean printing;
    /** 查询代次，防止设备选项覆盖用户的新选择。 */
    private int generation;
    /** 视图暂时销毁时保留一次性结果。 */
    private boolean resultPending;
    /** 最近完成的错误，null 表示成功。 */
    private String resultError;

    /** 注入设备及调度器；view 可以为空，随后通过 attach 绑定。 */
    PrintSettingsPresenter(View view, Executor worker, Executor ui, Device device) {
        this.view = view;
        this.worker = worker;
        this.ui = ui;
        this.device = device;
    }

    /** 在主线程恢复视图及在途等待，不重复提交硬件任务。 */
    void attach(View view) {
        this.view = view;
        view.showSelection(selection);
        if (busy) {
            if (printing) view.printStarted(); else view.connectionStarted();
        } else {
            deliverResult();
        }
    }

    /** 返回最近一次设备选项，供选择回执校验。 */
    List<String> devices() { return Collections.unmodifiableList(devices); }

    /** 静默枚举或刷新后展示选择器；枚举绝不触发 USB 授权和出纸。 */
    void refresh(boolean showPicker) {
        if (view == null || loading || busy) return;
        loading = true;
        int expected = generation;
        worker.execute(() -> {
            List<String> found;
            String selected;
            String error = null;
            try {
                found = new ArrayList<>(device.list());
                selected = device.selected(found);
            } catch (RuntimeException failure) {
                found = Collections.emptyList();
                selected = null;
                error = errorMessage(failure);
            }
            List<String> discovered = found;
            String resolved = selected;
            String problem = error;
            ui.execute(() -> {
                loading = false;
                if (expected != generation) return;
                devices = discovered;
                selection = resolved;
                if (view == null) return;
                view.showSelection(selection);
                if (problem != null) view.showError(problem);
                else if (showPicker) view.showDevices(devices(), selection);
            });
        });
    }

    /** 明确点击后执行连接测试；未选择设备时直接提示。 */
    void testConnection() {
        if (view == null || busy) return;
        if (selection == null) { view.noPrinter(); return; }
        start(false, 58);
    }

    /** 明确点击后打印示例票；连接和打印互斥，连续点击不重复出纸。 */
    void print(int paperWidthMm) {
        if (view == null || busy) return;
        if (selection == null) { view.noPrinter(); return; }
        start(true, paperWidthMm);
    }

    /** 固定设备选择并提交后台任务，完成状态即使视图销毁也会更新。 */
    private void start(boolean print, int paperWidthMm) {
        busy = true;
        printing = print;
        resultPending = false;
        String selected = selection;
        if (print) view.printStarted(); else view.connectionStarted();
        worker.execute(() -> {
            String error;
            try { error = print ? device.print(selected, paperWidthMm) : device.connect(selected); }
            catch (RuntimeException failure) { error = errorMessage(failure); }
            String result = error;
            ui.execute(() -> {
                busy = false;
                resultPending = true;
                resultError = result;
                deliverResult();
            });
        });
    }

    /** 在可用视图上消费一次回执，重建不会重复弹出提示。 */
    private void deliverResult() {
        if (view == null || !resultPending) return;
        resultPending = false;
        if (printing) view.printFinished(resultError);
        else view.connectionFinished(resultError);
    }

    /** 配置改变使旧枚举过期，打印期间不允许改变任务设备。 */
    void selectionChanged(String selected) {
        if (busy) return;
        generation++;
        selection = selected;
        if (view != null) view.showSelection(selected);
    }

    /** 查询在途状态，恢复界面并阻止配置弹窗提交旧选择。 */
    boolean isBusy() { return busy; }

    /** 释放页面引用；在途任务继续，回执可在新视图恢复。 */
    void detach() { view = null; }

    /** 无文案异常仍返回非空失败消息，避免被误判为成功。 */
    private static String errorMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
