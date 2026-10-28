package cc.huoyan.retail.settings;

import cc.huoyan.retail.device.scale.AclasScaleManager;
import cc.huoyan.retail.device.scale.AclasWeightSnapshot;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 秤设置的串口选择和测试会话；停止后旧秤回调不会覆盖新状态。 */
final class ScaleSettingsPresenter {
    /** 视图负责本地化文案和键盘。 */
    interface View {
        /** 正在读取可用串口。 */
        void readingPorts();
        /** 展示串口发现结果及错误消息。 */
        void portsFound(List<String> ports, String selected, String error);
        /** 展示已保存的串口配置。 */
        void portSaved(String port);
        /** 选择弹窗里的串口已不再可用。 */
        void unavailableSelection();
        /** 启动失败：missing 为 true 表示尚未选择，否则为串口不可用。 */
        void cannotStart(boolean missing);
        /** 尚未启动测试，无法置零或去皮。 */
        void startFirst();
        /** 测试开始，切换按钮并展示连接状态。 */
        void connecting(String port);
        /** 串口已经连接，等待第一笔读数。 */
        void connected(String port);
        /** 展示重量和稳定状态。 */
        void weight(String port, AclasWeightSnapshot snapshot);
        /** 测试终止；error 为 null 表示用户主动停止。 */
        void stopped(String error);
        /** 展示置零或去皮命令回执。 */
        void commandResult(boolean tare, boolean success, String message);
    }
    /** 当前 View 引用，销毁时断开。 */
    private View view;
    /** 本页独享的测试秤连接。 */
    private final AclasScaleManager scale;
    /** 后台串口枚举执行器。 */
    private final Executor worker;
    /** 主线程投递器，不捕获 Fragment。 */
    private final Executor ui;
    /** 硬件串口枚举边界。 */
    private final Supplier<List<String>> ports;
    /** 保存串口配置，不持有页面。 */
    private final Consumer<String> savePort;
    /** 最近发现的串口列表。 */
    private final List<String> available = new ArrayList<>();
    /** 当前已保存串口。 */
    private String selected;
    /** 是否存在活动的测试会话。 */
    private boolean running;
    /** 串口枚举期间拒绝重复刷新。 */
    private boolean refreshing;
    /** 测试代次，停止或换口后使旧回调失效。 */
    private int generation;
    /** 注入测试秤、配置和执行器。 */
    ScaleSettingsPresenter(View view, AclasScaleManager scale, Executor worker, Executor ui,
                           Supplier<List<String>> ports, Consumer<String> savePort, String selected) {
        this.view = view; this.scale = scale; this.worker = worker; this.ui = ui;
        this.ports = ports; this.savePort = savePort; this.selected = selected;
    }
    /** 返回只读串口选项。 */
    List<String> available() { return Collections.unmodifiableList(available); }
    /** 返回当前串口配置。 */
    String selected() { return selected; }
    /** 返回是否可发送测试命令。 */
    boolean running() { return running; }
    /** 后台刷新串口，视图销毁后忽略发现结果。 */
    void refresh() {
        if (view == null || refreshing) return;
        refreshing = true;
        view.readingPorts();
        worker.execute(() -> {
            List<String> found = new ArrayList<>();
            String error = "";
            try { List<String> result = ports.get(); if (result != null) found.addAll(result); }
            catch (Exception failure) { error = failure.getMessage(); }
            String message = error;
            ui.execute(() -> {
                if (view == null) return;
                refreshing = false;
                available.clear(); available.addAll(found); view.portsFound(available(), selected, message);
            });
        });
    }
    /** 只保存当前可用串口，换口前关闭正在运行的测试。 */
    void select(String port) {
        if (view == null) return;
        if (!available.contains(port)) { view.unavailableSelection(); return; }
        if (running) stop();
        selected = port; savePort.accept(port); view.portSaved(port);
    }
    /** 启停测试，所有秤回调在主线程按会话代次校验。 */
    void toggle() {
        if (view == null) return;
        if (running) { stop(); return; }
        if (selected == null || selected.isEmpty()) { view.cannotStart(true); return; }
        if (!available.contains(selected)) { view.cannotStart(false); return; }
        running = true;
        int request = ++generation;
        view.connecting(selected);
        scale.open(selected, new AclasScaleManager.Listener() {
            /** 把连接回执交给仍有效的测试会话。 */
            @Override public void onConnected(String port) { deliver(request, () -> view.connected(port)); }
            /** 把串口读数转交给主线程渲染。 */
            @Override public void onWeightRead(AclasWeightSnapshot snapshot) { deliver(request, () -> view.weight(selected, snapshot)); }
            /** 错误时关闭测试连接并恢复按钮。 */
            @Override public void onError(String message) {
                deliver(request, () -> { running = false; ++generation; scale.close(); view.stopped(message); });
            }
        });
    }
    /** 主动停止测试并使所有旧秤事件失效。 */
    void stop() {
        running = false; ++generation; scale.close();
        if (view != null) view.stopped(null);
    }
    /** 发送置零命令，未运行时只提示。 */
    void zero() {
        if (view == null) return;
        if (!running) { view.startFirst(); return; }
        int request = generation;
        scale.setZero((success, message) -> deliver(request, () -> view.commandResult(false, success, message)));
    }
    /** 去皮键盘确认后发送原数值，沿用秤管理器的校验。 */
    void tare(int value) {
        if (view == null) return;
        if (!running) { view.startFirst(); return; }
        int request = generation;
        scale.setPreTare(value, (success, message) -> deliver(request, () -> view.commandResult(true, success, message)));
    }
    /** 销毁视图即关闭测试秤，避免监听器保留页面。 */
    void detach() { view = null; running = false; ++generation; scale.release(); }
    /** 将硬件事件投递到主线程，并丢弃旧会话或离开页面后的结果。 */
    private void deliver(int request, Runnable action) {
        ui.execute(() -> { if (view != null && running && request == generation) action.run(); });
    }
}
