package cc.huoyan.retail.settings;

import cc.huoyan.retail.testing.QueuedExecutor;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** 打印页生命周期、重复点击及设备快照回归。 */
public class PrintSettingsPresenterTest {
    /** 只记录可观察交互，不依赖 Android 控件。 */
    static class Screen implements PrintSettingsPresenter.View {
        /** 连接回执次数。 */
        int results;
        /** 缺少选择提示次数。 */
        int missing;
        /** 打印回执次数。 */
        int printed;
        /** 等待提示次数。 */
        int waiting;
        /** 选项弹窗次数。 */
        int pickers;
        /** 最后选择。 */
        String selection;
        /** 最后错误。 */
        String error;
        /** 保存选择。 */
        public void showSelection(String device) { selection = device; }
        /** 记录选择弹窗。 */
        public void showDevices(List<String> devices, String selected) { pickers++; }
        /** 保存枚举错误。 */
        public void showError(String error) { this.error = error; }
        /** 记录缺少选择。 */
        public void noPrinter() { missing++; }
        /** 记录连接等待。 */
        public void connectionStarted() { waiting++; }
        /** 记录连接回执。 */
        public void connectionFinished(String error) { results++; this.error = error; }
        /** 记录打印等待。 */
        public void printStarted() { waiting++; }
        /** 记录打印回执。 */
        public void printFinished(String error) { printed++; this.error = error; }
    }

    /** 测试设备，不访问 USB 或配置。 */
    static class Device implements PrintSettingsPresenter.Device {
        /** 已提交打印次数。 */
        int prints;
        /** 已提交连接次数。 */
        int connections;
        /** 实際提交设备。 */
        String target;
        /** 实际提交的纸张快照。 */
        int paperWidth;
        /** 返回固定设备。 */
        public List<String> list() { return Collections.singletonList("USB"); }
        /** 恢复固定选择。 */
        public String selected(List<String> devices) { return "USB"; }
        /** 记录连接设备。 */
        public String connect(String selected) { connections++; target = selected; return null; }
        /** 记录打印设备。 */
        public String print(String selected, int paperWidthMm) { prints++; target = selected; paperWidth = paperWidthMm; return null; }
    }

    /** 进入页面只枚举，不能偷偷发起系统授权或出纸。 */
    @Test public void discoveryNeverConnectsOrPrints() {
        Screen view = new Screen(); Device device = new Device(); QueuedExecutor worker = new QueuedExecutor();
        PrintSettingsPresenter presenter = new PrintSettingsPresenter(view, worker, Runnable::run, device);
        presenter.refresh(false); presenter.refresh(false); assertEquals(1, worker.pending()); worker.drain();
        assertEquals("USB", view.selection); assertEquals(0, device.connections); assertEquals(0, device.prints);
        presenter.refresh(true); worker.drain(); assertEquals(1, view.pickers);
    }

    /** 多次点击打印及连接只能提交一个任务。 */
    @Test public void printingExcludesDuplicatePrintAndConnection() {
        Screen view = new Screen(); Device device = new Device(); QueuedExecutor worker = new QueuedExecutor();
        PrintSettingsPresenter presenter = new PrintSettingsPresenter(view, worker, Runnable::run, device);
        presenter.selectionChanged("USB");
        presenter.print(58); presenter.print(58); presenter.testConnection();
        assertEquals(1, worker.pending()); worker.drain();
        assertEquals(1, device.prints); assertEquals(0, device.connections); assertEquals(1, view.printed);
        assertFalse(presenter.isBusy());
    }

    /** 视图重建恢复同一任务等待，不重新打印；离开期间结果只回放一次。 */
    @Test public void recreationRestoresProgressAndDeliversResultOnce() {
        Screen old = new Screen(); Screen current = new Screen(); Device device = new Device();
        QueuedExecutor worker = new QueuedExecutor();
        PrintSettingsPresenter presenter = new PrintSettingsPresenter(old, worker, Runnable::run, device);
        presenter.selectionChanged("USB"); presenter.print(58); presenter.detach(); presenter.attach(current);
        assertEquals(1, current.waiting); presenter.detach(); worker.drain();
        assertFalse(presenter.isBusy()); assertEquals(1, device.prints); assertEquals(0, old.printed);
        presenter.attach(current); presenter.attach(current); assertEquals(1, current.printed);
    }

    /** 设备错误关闭等待并保留消息，允许用户重新发起测试。 */
    @Test public void failureReleasesStateAndKeepsError() {
        Screen view = new Screen(); Device device = new Device() {
            /** 模拟原生枚举或连接抛出异常。 */
            @Override public String connect(String selected) { connections++; throw new IllegalStateException("denied"); }
        };
        PrintSettingsPresenter presenter = new PrintSettingsPresenter(view, Runnable::run, Runnable::run, device);
        presenter.selectionChanged("USB");
        presenter.testConnection(); presenter.testConnection();
        assertEquals(2, device.connections); assertEquals(2, view.results); assertEquals("denied", view.error);
        assertFalse(presenter.isBusy());
    }

    /** 已排队任务使用提交时的设备，不能在后台执行前被新选择替换。 */
    @Test public void queuedPrintKeepsOriginalTarget() {
        Device device = new Device(); QueuedExecutor worker = new QueuedExecutor();
        PrintSettingsPresenter presenter = new PrintSettingsPresenter(new Screen(), worker, Runnable::run, device);
        presenter.selectionChanged("A"); presenter.print(80); presenter.selectionChanged("B"); worker.drain();
        assertEquals("A", device.target); assertEquals(80, device.paperWidth);
    }

    /** 晚到的枚举结果不能覆盖刚刚选择的新设备。 */
    @Test public void changedSelectionRejectsLateDiscovery() {
        Screen view = new Screen(); QueuedExecutor worker = new QueuedExecutor();
        PrintSettingsPresenter presenter = new PrintSettingsPresenter(view, worker, Runnable::run, new Device());
        presenter.refresh(false); presenter.selectionChanged("other"); worker.drain();
        assertEquals("other", view.selection);
    }

    /** 未选择设备不能提交后台操作。 */
    @Test public void noSelectionDoesNotSubmit() {
        Screen view = new Screen(); QueuedExecutor worker = new QueuedExecutor();
        PrintSettingsPresenter presenter = new PrintSettingsPresenter(view, worker, Runnable::run, new Device());
        presenter.print(58); presenter.testConnection();
        assertEquals(2, view.missing); assertEquals(0, worker.pending());
    }

    /** 设备异常没有 message 也不能被视为发送成功。 */
    @Test public void emptyExceptionMessageIsStillFailure() {
        Screen view = new Screen(); Device device = new Device() {
            /** 模拟缺少消息的运行时异常。 */
            @Override public String print(String selected, int paperWidthMm) { throw new IllegalStateException(); }
        };
        PrintSettingsPresenter presenter = new PrintSettingsPresenter(view, Runnable::run, Runnable::run, device);
        presenter.selectionChanged("USB"); presenter.print(58);
        assertEquals("IllegalStateException", view.error);
    }
}
