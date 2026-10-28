package cc.huoyan.retail.settings;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.LicenseModel;
import cc.huoyan.sdk.client.model.LicenseModels;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/** 激活与切换的同一会话；所有状态在主线程访问，提交后不自动重放写操作。 */
final class ModelActivationPresenter {
    /** 会话阶段；选模之前不会调用激活接口。 */
    enum Stage { IDLE, INPUT, QUERYING, CHOOSING, ACTIVATING, FINISHED }
    /** 可重建的视图入口，不被后台数据源持有。 */
    interface View { /** 按当前会话状态渲染弹窗。 */ void render(); }
    /** 工作线程数据访问边界。 */
    interface Data {
        /** 固定当前通道并查询授权模型；切换时读取证书序列号。 */
        Options query(String code, boolean switching);
        /** 向查询时的通道提交选择，不自动重试。 */
        BaseResult activate(String code, String modelCode);
        /** 保留输入草稿，供取消选模后回填。 */
        void remember(String code);
    }
    /** 一次查询的模型列表及其授权凭据。 */
    static final class Options {
        /** 用于最终激活的序列号。 */
        final String code;
        /** 原始模型列表回执。 */
        final LicenseModels result;
        /** 固定授权上下文。 */
        Options(String code, LicenseModels result) { this.code = code; this.result = result; }
    }
    /** 后台执行器。 */
    private final Executor worker;
    /** 主线程执行器。 */
    private final Executor ui;
    /** 无页面引用的数据源。 */
    private final Data data;
    /** 当前可见视图。 */
    private View view;
    /** 当前阶段，配置变化时保留。 */
    Stage stage = Stage.IDLE;
    /** 本次是否来自切换入口。 */
    boolean switching;
    /** 当前授权凭据，不写日志。 */
    private String code;
    /** 缺少激活码时保留已选模型，输入确认后只提交该模型。 */
    private String selectedModelCode;
    /** 不可变模型列表快照。 */
    List<LicenseModel> models = Collections.emptyList();
    /** 等待页面消费的一次结果。 */
    BaseResult result;
    /** 查询代次；会话销毁时丢弃迟到回调。 */
    private int generation;
    /** 提交阶段是否已真正开始，用于区分查询失败与激活结果。 */
    boolean submitted;

    /** 注入执行器与数据源。 */
    ModelActivationPresenter(Executor worker, Executor ui, Data data) {
        this.worker = worker; this.ui = ui; this.data = data;
    }
    /** 页面恢复时绑定新视图，并重放当前状态而非重发请求。 */
    void attach(View view) { this.view = view; changed(); }
    /** 页面不可见时释放视图，保留进行中的会话。 */
    void detach() { view = null; }
    /** 最终销毁作废回调；后台已提交写操作继续完成。 */
    void destroy() { detach(); generation++; }
    /** 新入口仅允许在空闲状态打开。 */
    void open(boolean switching) {
        if (stage != Stage.IDLE) return;
        this.switching = switching; submitted = false; result = null; selectedModelCode = null;
        if (switching) query(null); else { stage = Stage.INPUT; changed(); }
    }
    /** 正常激活输入后查询；切换补码后提交已选模型，扫码解析由宿主完成。 */
    void input(String input) {
        if (stage != Stage.INPUT) return;
        String normalized = ActivationCode.normalize(input);
        if (normalized == null || normalized.trim().isEmpty()) return;
        data.remember(normalized);
        if (switching && selectedModelCode != null) {
            code = normalized;
            activateSelected(selectedModelCode);
        } else query(normalized);
    }
    /** 查询成功后才进入选择阶段；空列表或失败交给 Toast。 */
    private void query(String input) {
        stage = Stage.QUERYING; changed();
        int request = ++generation;
        boolean switchRequest = switching;
        worker.execute(() -> {
            Options options;
            try { options = data.query(input, switchRequest); }
            catch (RuntimeException error) { options = new Options(null, new LicenseModels(-1, "读取模型列表失败", null)); }
            Options snapshot = options;
            ui.execute(() -> {
                if (request != generation) return;
                LicenseModels reply = snapshot == null ? null : snapshot.result;
                if (reply == null || reply.getCode() != 0) {
                    finish(new BaseResult(reply == null ? -1 : reply.getCode(),
                            reply == null ? "读取模型列表失败，请重试" : reply.getReason()));
                    return;
                }
                if (reply.getModels().isEmpty()) { finish(new BaseResult(-1, "暂无可选模型")); return; }
                for (LicenseModel model : reply.getModels()) {
                    if (model == null || blank(model.getModelCode()) || blank(model.getModelName())) {
                        finish(new BaseResult(-1, "模型列表信息不完整，请重试")); return;
                    }
                }
                code = snapshot.code; models = reply.getModels(); stage = Stage.CHOOSING; changed();
            });
        });
    }
    /** 仅接收列表快照中的代码，当前模型和取消均不产生写请求。 */
    void select(String modelCode) {
        if (stage != Stage.CHOOSING) return;
        LicenseModel selected = null;
        for (LicenseModel model : models) if (model.getModelCode().equals(modelCode)) selected = model;
        if (selected == null) return;
        if (switching && selected.isCurrent()) { stage = Stage.IDLE; changed(); return; }
        selectedModelCode = modelCode;
        if (blank(code)) { stage = Stage.INPUT; changed(); return; }
        activateSelected(modelCode);
    }
    /** 已有选定模型与本次激活码后提交一次；输入阶段不再次查询或选模。 */
    private void activateSelected(String modelCode) {
        submitted = true; stage = Stage.ACTIVATING; changed();
        String activationCode = code;
        int request = ++generation;
        worker.execute(() -> {
            BaseResult reply;
            try { reply = data.activate(activationCode, modelCode); }
            catch (RuntimeException error) { reply = new BaseResult(HuoyanSdkClient.RESULT_UNKNOWN, "操作结果未知，请查看当前模型后再操作"); }
            BaseResult receipt = reply == null ? new BaseResult(HuoyanSdkClient.RESULT_UNKNOWN,
                    "操作结果未知，请查看当前模型后再操作") : reply;
            ui.execute(() -> { if (request == generation) finish(receipt); });
        });
    }
    /** 取消选模返回输入；取消输入结束本次会话。 */
    void cancel() {
        if (stage == Stage.CHOOSING) stage = switching ? Stage.IDLE : Stage.INPUT;
        else if (stage == Stage.INPUT) stage = Stage.IDLE;
        changed();
    }
    /** 消费一次结果；列表读取失败允许回到输入重试。 */
    void consumed() {
        if (stage != Stage.FINISHED) return;
        stage = !submitted && !switching ? Stage.INPUT : Stage.IDLE;
        result = null; changed();
    }
    /** 保存真实回执，页面未显示时延后消费。 */
    private void finish(BaseResult receipt) { result = receipt; stage = Stage.FINISHED; changed(); }
    /** 只调用当前绑定视图。 */
    private void changed() { if (view != null) view.render(); }
    /** 校验服务端必要文本。 */
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
