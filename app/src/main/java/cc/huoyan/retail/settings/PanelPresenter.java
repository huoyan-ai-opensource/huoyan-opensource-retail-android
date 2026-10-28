package cc.huoyan.retail.settings;

import cc.huoyan.sdk.client.model.*;
import java.util.concurrent.*;

/** 授权页协调云开关与面板读取；激活会话由独立宿主管理。 */
final class PanelPresenter {
    /** 授权页展示能力。 */
    interface View {
        /** 根据可用值和忙碌状态回显云开关。 */
        void cloudChanged();
        /** 云请求失败，null 表示未收到回执。 */
        void cloudFailed(CloudStatus response);
        /** 刷新 SDK 状态、证书及版本信息。 */
        void panelLoaded(StatusSnapshot status, CertInfo cert, Capability capability);
    }
    /** SDK 后台访问，异常处理与业务日志在适配器中完成。 */
    interface Data {
        /** 读取或修改云状态，写失败时同时读取实际状态。 */
        CloudResult cloud(Boolean requested);
        /** 查询面板状态、证书及能力信息。 */
        PanelResult panel();
    }
    /** 云操作回执和最终实际状态。 */
    static final class CloudResult {
        /** 原请求的回执，失败时仍用于错误提示。 */
        final CloudStatus response;
        /** 成功写回执或失败后查询到的实际状态。 */
        final CloudStatus actual;
        /** 固定一次请求的回执与最终状态。 */
        CloudResult(CloudStatus response, CloudStatus actual) { this.response = response; this.actual = actual; }
    }
    /** 面板的一次查询快照。 */
    static final class PanelResult {
        /** 模型就绪状态及设备信息。 */
        final StatusSnapshot status;
        /** 授权有效期与授权类型。 */
        final CertInfo cert;
        /** 能力接口提供的 SDK 应用版本，不依赖证书可用性。 */
        final Capability capability;
        /** 固定状态、证书与能力查询结果。 */
        PanelResult(StatusSnapshot status, CertInfo cert, Capability capability) {
            this.status = status;
            this.cert = cert;
            this.capability = capability;
        }
    }
    /** 当前页面，视图销毁时释放。 */
    private View view;
    /** 后台 SDK 查询队列。 */
    private final Executor worker;
    /** 主线程状态提交器。 */
    private final Executor ui;
    /** SDK 数据适配。 */
    private final Data data;
    /** 当前云操作是否在途。 */
    private boolean cloudRunning;
    /** 云状态未知时为 null，按钮不可修改。 */
    private Boolean cloudEnabled;
    /** 云请求代次，恢复页面后的重新查询会作废旧结果。 */
    private int cloudGeneration;
    /** 面板读取代次，最后一次请求覆盖前次请求。 */
    private int panelGeneration;
    /** 注入后台队列、主线程与 SDK 访问。 */
    PanelPresenter(View view, Executor worker, Executor ui, Data data) {
        this.view = view; this.worker = worker; this.ui = ui; this.data = data;
    }
    /** 返回云请求是否在途，供控件禁用判断。 */
    boolean cloudRunning() { return cloudRunning; }
    /** 返回实际云开关值，未知为 null。 */
    Boolean cloudEnabled() { return cloudEnabled; }
    /** 恢复页面时强制查询真实状态，作废此前的云请求回写。 */
    void resume() { cloudRunning = false; cloud(null); }
    /** 提交云查询或已经确认的开关修改，保持单请求互斥。 */
    void cloud(Boolean requested) {
        if (view == null || cloudRunning) return;
        cloudRunning = true;
        int request = ++cloudGeneration;
        view.cloudChanged();
        worker.execute(() -> {
            CloudResult result = data.cloud(requested);
            ui.execute(() -> {
                if (view == null || request != cloudGeneration) return;
                cloudEnabled = result.actual != null && result.actual.getCode() == 0 ? result.actual.isEnabled() : null;
                cloudRunning = false; view.cloudChanged();
                if (result.response == null || result.response.getCode() != 0) view.cloudFailed(result.response);
            });
        });
    }
    /** 查询授权面板，销毁或后发请求使旧结果失效。 */
    void refresh() {
        if (view == null) return;
        int request = ++panelGeneration;
        worker.execute(() -> {
            PanelResult result = data.panel();
            ui.execute(() -> { if (view != null && request == panelGeneration) view.panelLoaded(result.status, result.cert, result.capability); });
        });
    }
    /** 断开视图，迟到查询不能访问旧页面。 */
    void detach() { view = null; ++cloudGeneration; ++panelGeneration; }
}
