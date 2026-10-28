package cc.huoyan.retail.settings;

import cc.huoyan.retail.R;
import cc.huoyan.sdk.client.model.BindingResult;
import java.util.concurrent.Executor;

/** 门店绑定协调；完整成功回执才允许更新本地归属，不自动重试。 */
final class ShopBindingPresenter {
    /** 绑定页的提示和加载展示。 */
    interface View {
        /** 展示已受理的绑定请求。 */
        void bindingStarted();
        /** 关闭加载提示，success 区分成功提示样式，reason 可为空。 */
        void bindingFinished(boolean success, int text, String reason);
        /** 未提交请求时展示输入错误或忙碌提示。 */
        void bindingRejected(int text);
    }
    /** SDK 绑定与本地归属保存边界。 */
    interface Data {
        /** 提交一次绑定，包含连接等待但不重试绑定本身。 */
        Result bind(String shopId);
        /** 保存经过完整性验证的成功回执。 */
        void save(BindingResult result);
    }
    /** SDK 回执或调用前失败资源，二者不会同时生效。 */
    static final class Result {
        /** SDK 返回值，连接失败时为空。 */
        final BindingResult receipt;
        /** 本地调用失败文案资源；0 表示使用 SDK 回执。 */
        final int failureText;
        /** 固定单次绑定结果。 */
        Result(BindingResult receipt, int failureText) { this.receipt = receipt; this.failureText = failureText; }
    }
    /** 当前页面，视图销毁后不再展示回执。 */
    private View view;
    /** 后台绑定队列。 */
    private final Executor worker;
    /** 主线程状态提交器，不捕获 Fragment。 */
    private final Executor ui;
    /** SDK 及归属存储适配器。 */
    private final Data data;
    /** 提交至收口期间互斥，重复扫码不会重复绑定。 */
    private boolean running;
    /** 注入页面和数据访问；构造时不执行外部操作。 */
    ShopBindingPresenter(View view, Executor worker, Executor ui, Data data) {
        this.view = view; this.worker = worker; this.ui = ui; this.data = data;
    }
    /** 验证非空外部标识，保留前导零、空格及字母原值。 */
    static String parseShopQrShopId(String raw) { return raw == null || raw.trim().isEmpty() ? null : raw; }
    /** 仅包含账号和门店两个非空标识的成功回执可写入归属。 */
    static boolean hasBindingScope(BindingResult receipt) {
        return receipt != null && receipt.hasBindingScope();
    }
    /** 接受扫码或手动标识；无效或在途时不提交 SDK。 */
    void bind(String raw) {
        if (view == null) return;
        String id = parseShopQrShopId(raw);
        if (id == null) { view.bindingRejected(R.string.bind_id_required); return; }
        if (running) { view.bindingRejected(R.string.bind_in_progress); return; }
        running = true; view.bindingStarted();
        worker.execute(() -> {
            Result result = data.bind(id);
            ui.execute(() -> {
                running = false;
                // 已提交的绑定不能撤回，即使页面退出也要保存合法归属。
                if (result.failureText == 0 && hasBindingScope(result.receipt)) data.save(result.receipt);
                if (view == null) return;
                if (result.failureText != 0) { view.bindingFinished(false, result.failureText, null); return; }
                BindingResult receipt = result.receipt;
                if (receipt == null) { view.bindingFinished(false, R.string.bind_no_result, null); return; }
                if (receipt.getCode() != 0) {
                    String reason = receipt.getReason();
                    view.bindingFinished(false, R.string.bind_failed,
                            reason == null || reason.isEmpty() ? String.valueOf(receipt.getCode()) : reason);
                } else view.bindingFinished(hasBindingScope(receipt), hasBindingScope(receipt) ? R.string.bind_success : R.string.bind_incomplete, null);
            });
        });
    }
    /** 视图重建后复用当前在途状态，避免重复绑定。 */
    void attach(View view) { this.view = view; }
    /** 视图销毁后禁止回写 UI，已提交绑定仍收口持久化。 */
    void detach() { view = null; }
}
