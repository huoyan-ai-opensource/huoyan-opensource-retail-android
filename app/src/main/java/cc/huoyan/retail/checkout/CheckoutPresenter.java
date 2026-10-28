package cc.huoyan.retail.checkout;

import cc.huoyan.retail.checkout.domain.CheckoutSession;
import cc.huoyan.retail.checkout.domain.CheckoutState;
import cc.huoyan.retail.checkout.domain.SalePrintJob;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.pricing.PricingGate;
import cc.huoyan.retail.pricing.PricingMode;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.checkout.CheckoutRecognition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 收银页交互协调器：主线程处理事件，业务规则委托给 CheckoutSession，不调用 Android API 或持有 Activity/控件。 */
public final class CheckoutPresenter {
    /** 页面提示语义，由 View 映射为本地化文案。 */
    public enum Message {
        /** 改品前需选中检测框。 */
        SELECT_BOX,
        /** 当前没有可操作商品。 */
        NO_GOODS,
        /** 弹窗所属会话已过期。 */
        MODIFY_EXPIRED,
        /** 称重关闭，拦截称重商品。 */
        WEIGHING_DISABLED,
        /** 混合计价要求拆分数量。 */
        SPLIT_COUNT,
        /** 整秤商品存在单价冲突。 */
        PRICE_CONFLICT,
        /** 已匹配商品没有有效价格。 */
        PRICE_MISSING,
        /** 所选商品已从本地删除。 */
        GOODS_REMOVED,
        /** 当前会话尚不具备结算资格。 */
        NOT_READY,
        /** 小票打印成功。 */
        PRINT_DONE,
        /** 小票打印失败。 */
        PRINT_FAILED,
        /** 已确认识别结果，反馈按既有链路异步提交。 */
        CONFIRM_DONE,
        /** 识别结果确认未完成。 */
        CONFIRM_FAILED,
        /** 已删除本条识别结果。 */
        RESULT_DELETED
    }
    /** 供语音、状态标签及业务日志使用的结算阶段。 */
    public enum SaleStatus {
        /** 同秤商品单价不一致。 */
        PRICE_CONFLICT,
        /** 已匹配商品没有有效价格。 */
        PRICE_MISSING,
        /** 已具备结算资格。 */
        READY,
        /** 等待识别或重量确认。 */
        WAITING,
        /** 当前小票已经打印。 */
        PRINTED,
        /** 已确认结果但未打印，沿用结算后的恢复规则。 */
        CONFIRMED
    }

    /** 收银页面渲染和交互能力，不向 Presenter 暴露控件。 */
    public interface View {
        /** 展示摘要、选择和按钮状态。 */
        void render(CheckoutViewState state);
        /** 更新图片、预览及清单；非锁定实时识别可仅更新预览。 */
        void renderRows(CheckoutViewState state, boolean updateList);
        /** 展示数量输入框并返回确认值。 */
        void showCountInput(int initial, IntConsumer confirm);
        /** 展示重量输入框并返回确认值。 */
        void showWeightInput(BigDecimal initial, Consumer<BigDecimal> confirm);
        /** 展示商品选择；确认返回 false 时保留弹窗。 */
        void showGoodsPicker(GoodsVo selected, Predicate<GoodsEntity> confirm);
        /** 展示操作结果，具体文案与样式由页面决定。 */
        void showMessage(Message message, String detail);
        /** 展示收银状态并保留原有语音和业务日志。 */
        void showSaleStatus(SaleStatus status, CheckoutState state);
        /** 回显人工重量。 */
        void showManualWeight(BigDecimal weight);
        /** 输出平台依赖的业务调试日志。 */
        void logBusiness(String message);
        /** 商品加载完成后处理页面专属的调试弹窗。 */
        void onGoodsLoaded(CheckoutRecognition response, List<GoodsVo> rows);
        /** 判断是否已有数量弹窗等待确认。 */
        boolean isCountInputShowing();
        /** 恢复无称重模式的相机识别调度。 */
        void resumeRecognition();
        /** 记录加载失败，保留现有清单。 */
        void showLoadingFailure(Exception error);
        /** 设置电子秤皮重；View 管理 Loading 与真实结果 Toast，完成回调在主线程执行。 */
        void applyAutomaticTare(BigDecimal tareKg, Consumer<Boolean> completion);
    }

    /** 商品异步接入端口；实现负责合并查询并在 invalidate 后丢弃过期结果。 */
    public interface GoodsLoader {
        /** 加载本轮识别商品，回调在主线程执行。 */
        void load(CheckoutRecognition response, Consumer<List<GoodsVo>> commit, Consumer<Exception> failure);
        /** 作废在途查询及尚未提交的结果。 */
        void invalidate();
        /** 关闭页面所属查询任务。 */
        void close();
    }

    /** 固定销售快照后的外部输出边界。 */
    public interface Output {
        /** 按提交时的打印开关输出固定结果与反馈，在主线程返回确认结果；页面销毁不取消已提交任务。 */
        void submit(SalePrintJob job, boolean printEnabled, CheckoutRecognition response, List<GoodsVo> rows, List<GoodsVo> deletedRows, Consumer<Exception> completion);
        /** 全部删空时仅提交删除反馈，不打印空单；同步固定快照后可跨页面完成。 */
        void submitDeletion(CheckoutRecognition response, List<GoodsVo> deletedRows);
    }

    /** 收银业务会话的唯一可变入口，发布不可变快照。 */
    private final CheckoutSession checkout = new CheckoutSession();
    /** 页面所属的异步商品加载端口，暂停时作废旧结果。 */
    private final GoodsLoader loader;
    /** 可解绑页面引用，销毁后所有展示操作跳过。 */
    private View view;
    /** 打印和反馈输出端口，销毁时释放可能捕获页面的实现。 */
    private Output output;
    /** 当前可编辑展示行，所有结构修改由 Presenter 完成。 */
    private List<GoodsVo> rows = new ArrayList<>();
    /** 当前识别来源，用于改品和反馈关联。 */
    private CheckoutRecognition response;
    /** 本轮删除行，保留原始框身份供反馈；重新识别时清空。 */
    private final List<GoodsVo> deletedRows = new ArrayList<>();
    /** 已保存的删除开关，默认关闭，主线程刷新。 */
    private boolean deletionEnabled;
    /** 按对象身份记录用户选框，避免内容相同的检测框混淆。 */
    private final Set<RecognitionSelection> selected = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 已人工改品的检测框身份集合。 */
    private final Set<RecognitionSelection> corrected = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 当前计价前置校验拦截的 PLU 集合。 */
    private Set<String> blockedPlus = Collections.emptySet();
    /** 本轮商品查询是否已成功完成；主线程维护，未完成或失败不能显示无商品空态。 */
    private boolean goodsResolved;
    /** 页面暂停期间不发起新设备命令，恢复连接后允许继续。 */
    private boolean paused;

    /** 注入页面及接入端口，便于纯 JVM 验证交互流程。 */
    public CheckoutPresenter(View view, GoodsLoader loader, Output output) {
        this.view = view;
        this.loader = loader;
        this.output = output;
    }

    /** 读取业务快照；识别线程不访问可变页面清单。 */
    public CheckoutState state() { return checkout.state(); }

    /** 返回当前展示行的只读列表，行的修改必须通过 Presenter 操作。 */
    public List<GoodsVo> rows() { return Collections.unmodifiableList(rows); }

    /** 刷新模块配置；由页面完成对应设备和控件显隐。 */
    public void setWeighingEnabled(boolean enabled) { checkout.setWeighingEnabled(enabled); }

    /** 更新删除开关；关闭不撤销本轮已完成的删除。 */
    public void setDeletionEnabled(boolean enabled) { deletionEnabled = enabled; }

    /** 返回已保存的删除功能开关。 */
    public boolean isDeletionEnabled() { return deletionEnabled; }

    /** 暂停后仅重试未完成查询，已删空的结果不能重新加载。 */
    public boolean needsGoodsReload() { return !goodsResolved; }

    /** 接收秤连接通知。 */
    public void scaleConnected() { paused = false; checkout.scaleConnected(); }

    /** 断秤后撤销结算资格并刷新展示。 */
    public void scaleDisconnected() {
        checkout.scaleDisconnected();
        refreshSaleStatus();
        render();
    }

    /** 输入真实秤读数，返回需要相机与超时调度响应的事件。 */
    public CheckoutSession.WeightEvent onWeight(BigDecimal kg, BigDecimal tareKg, boolean stable) {
        CheckoutSession.WeightEvent event = checkout.onWeight(kg, tareKg, stable);
        applyAutomaticTare();
        return event;
    }

    /** 请求重开会话，打印期间返回 false。相机重启由 Activity 按生命周期执行。 */
    public boolean restart() { return checkout.restart(); }

    /** 锁定识别、清空旧选择并加载本轮商品。 */
    public void lockRecognition(CheckoutRecognition next) {
        if (view == null) return;
        checkout.lockRecognition();
        clearRows();
        loadGoods(next);
    }

    /** 清空当前页面清单；不会重复重置业务状态机。 */
    public void clearRows() {
        goodsResolved = false;
        rows = new ArrayList<>();
        response = null;
        selected.clear();
        corrected.clear();
        deletedRows.clear();
        refreshRows();
    }

    /** 查询实时或已锁定识别对应的商品。 */
    public void loadGoods(CheckoutRecognition next) {
        if (view == null) return;
        goodsResolved = false;
        response = next;
        render();
        loader.load(next, loaded -> applyLoadedRows(next, loaded), error -> {
            if (view != null) view.showLoadingFailure(error);
        });
    }

    /** 接受有效加载结果并更新计价及展示；查询结果不解除识别锁定。 */
    private void applyLoadedRows(CheckoutRecognition source, List<GoodsVo> loaded) {
        if (view == null) return;
        rows = new ArrayList<>(loaded);
        goodsResolved = true;
        blockedPlus = blockedRows(false);
        refreshSaleStatus();
        view.renderRows(snapshot(), state().recognitionLocked || CheckoutRows.containsVirtualGoods(rows));
        view.onGoodsLoaded(source, rows());
        if (view == null) return;
        if (!blockedPlus.isEmpty()) view.logBusiness("gate_blocked_count=" + blockedPlus.size());
        render();
    }

    /** 手工修改或返回页面后重绘清单，并让旧查询失效。 */
    public void refreshRows() {
        if (view == null) return;
        loader.invalidate();
        blockedPlus = blockedRows(true);
        refreshSaleStatus();
        view.renderRows(snapshot(), true);
        render();
    }

    /** 收集不符合计价前置条件的商品；可选择包含未匹配占位行。 */
    private Set<String> blockedRows(boolean includeUnmatched) {
        Set<String> blocked = new LinkedHashSet<>();
        for (GoodsVo row : rows) if (row != null && row.getGoods() != null
                && (includeUnmatched || !row.isUnmatched()) && pricingBlocked(row.getGoods())) blocked.add(row.getGoods().getPlu());
        return blocked;
    }

    /** 使用当前称重开关检查商品计价是否允许。 */
    private boolean pricingBlocked(GoodsEntity goods) {
        return PricingGate.check(CheckoutRows.resolvePricingMode(goods), state().weighingEnabled) == PricingGate.GateResult.BLOCK;
    }

    /** 切换一个识别框的选择状态，无匹配商品不可选择。 */
    public void toggleItem(RecognitionSelection item) {
        if (view == null || !state().canEdit || item == null || findGoods(item) == null) return;
        for (GoodsVo row : rows) if (row.isUnmatched() && row.getRecognitionSelection() != null
                && row.getRecognitionSelection().contains(item)) return;
        if (!selected.remove(item)) selected.add(item);
        render();
    }

    /** 切换整行的识别框选择，保持原有整行选择语义。 */
    public void toggleRow(GoodsVo row) {
        if (!canEdit(row) || row.getRecognitionSelection() == null) return;
        List<RecognitionSelection> items = row.getRecognitionSelection().stream().filter(item -> item != null).collect(Collectors.toList());
        if (items.isEmpty()) return;
        if (selected.containsAll(items)) selected.removeAll(items);
        else selected.addAll(items);
        render();
    }

    /** 删除当前锁定结果的整行及选框；删空仅上传纠错反馈，保持空态等待重新识别。 */
    public void deleteRow(GoodsVo row) {
        if (!deletionEnabled || !canEdit(row)) return;
        deletedRows.add(row);
        if (row.getRecognitionSelection() != null) {
            selected.removeAll(row.getRecognitionSelection());
            corrected.removeAll(row.getRecognitionSelection());
        }
        rows.remove(row);
        refreshRows();
        view.logBusiness("recognition_deleted=true remaining_rows=" + rows.size());
        view.showMessage(Message.RESULT_DELETED, null);
        if (rows.isEmpty()) output.submitDeletion(response, new ArrayList<>(deletedRows));
    }

    /** 根据商品计价方式选择数量或重量输入。 */
    public void editQuantity(GoodsVo row) {
        if (!canEdit(row)) return;
        if (CheckoutRows.resolvePricingMode(row.getGoods()) == PricingMode.COUNT) {
            view.showCountInput(row.getCount() == null ? 1 : row.getCount(), count -> confirmCount(row, count));
        } else {
            BigDecimal weight = state().hasStableWeight ? state().saleWeightKg : state().currentWeightKg;
            view.showWeightInput(weight, value -> {
                if (!canEdit(row)) return;
                checkout.setManualWeight(value);
                view.showManualWeight(value);
                view.logBusiness("weight_input=" + value.toPlainString());
                refreshRows();
            });
        }
    }

    /** 确认数量；过期弹窗、打印期间或已售出时不改变清单。 */
    public boolean confirmCount(GoodsVo row, int count) {
        if (!canEdit(row)) return false;
        row.setCount(count);
        refreshRows();
        return true;
    }

    /** 只有当前会话可编辑且行仍在清单中时接受弹窗操作。 */
    private boolean canEdit(GoodsVo row) { return view != null && state().canEdit && row != null && rows.contains(row); }

    /** 为当前选择打开改品弹窗，并固定来源用于确认时的过期校验。 */
    public void modifySelected() {
        if (view == null || !state().canEdit) return;
        if (selected.isEmpty()) { view.showMessage(Message.SELECT_BOX, null); return; }
        List<RecognitionSelection> items = new ArrayList<>(selected);
        GoodsEntity source = findGoods(items.get(0));
        if (source == null) { view.showMessage(Message.NO_GOODS, null); return; }
        GoodsVo selection = new GoodsVo(items.size(), source.getPrice(), source, items);
        CheckoutRecognition original = response;
        view.showGoodsPicker(selection, target -> {
            if (view == null) return false;
            if (!state().canEdit || response != original || !selected.containsAll(items)) {
                view.logBusiness("modify_goods=stale");
                view.showMessage(Message.MODIFY_EXPIRED, null);
                return false;
            }
            return replaceSelected(target);
        });
    }

    /** 按检测框对象身份查找其所在行的商品。 */
    private GoodsEntity findGoods(RecognitionSelection item) {
        for (GoodsVo row : rows) if (row.getGoods() != null && row.getRecognitionSelection() != null
                && row.getRecognitionSelection().contains(item)) return row.getGoods();
        return null;
    }

    /** 改品前再次校验会话，按选框重组商品并刷新计价。 */
    private boolean replaceSelected(GoodsEntity target) {
        if (target == null) return false;
        if (pricingBlocked(target)) {
            view.logBusiness("modify_goods=blocked plu=" + target.getPlu() + " reason=weighing_disabled");
            view.showMessage(Message.WEIGHING_DISABLED, null);
            render();
            return false;
        }
        int count = selected.size();
        Set<RecognitionSelection> changed = selected.stream()
                .filter(item -> item != null && !target.getPlu().equals(item.getSelectedPlu())).collect(Collectors.toSet());
        try {
            rows = GoodsRegrouper.replace(rows, selected, target);
        } catch (IllegalArgumentException error) {
            view.showMessage(Message.SPLIT_COUNT, null);
            return false;
        }
        corrected.addAll(changed);
        selected.clear();
        refreshRows();
        view.logBusiness("modify_goods=applied plu=" + target.getPlu() + " count=" + count);
        return true;
    }

    /** 无称重模式处理完无有效计件结果后恢复识别。 */
    public void resumeContinuousRecognition() {
        if (view == null || state().weighingEnabled) return;
        checkout.resumeContinuousRecognition();
        view.resumeRecognition();
    }

    /** 校验并确认当前结果；固定本次打印开关，关闭时仅提交反馈，主线程防止重复确认。 */
    public void confirmResult(boolean printEnabled) {
        if (view == null || state().printing) return;
        CheckoutState state = state();
        if (state.phase == CheckoutState.Phase.LOCKED_PRICE_MISSING) { view.showMessage(Message.PRICE_MISSING, null); return; }
        if (state.phase == CheckoutState.Phase.LOCKED_PRICE_CONFLICT) { view.showMessage(Message.PRICE_CONFLICT, null); return; }
        if (!state.canPrint) {
            boolean removed = state.receipt != null && state.receipt.getUnitPriceCent() > 0
                    && state.currentWeightKg.compareTo(new BigDecimal("0.020")) < 0;
            view.showMessage(removed ? Message.GOODS_REMOVED : Message.NOT_READY, null);
            return;
        }
        SalePrintJob job = checkout.beginPrint();
        loader.invalidate();
        render();
        output.submit(job, printEnabled, response, new ArrayList<>(rows), new ArrayList<>(deletedRows),
                failure -> finishConfirmation(job, printEnabled, failure));
    }

    /** 按提交时的会话和打印开关收口确认，页面销毁后仍更新业务状态；不将确认完成误报为反馈上传成功。 */
    private void finishConfirmation(SalePrintJob job, boolean printEnabled, Exception failure) {
        checkout.finishPrint(job, failure == null);
        if (view == null) return;
        if (failure == null && state().phase == CheckoutState.Phase.SOLD_OR_REPORTED)
            view.showSaleStatus(printEnabled ? SaleStatus.PRINTED : SaleStatus.CONFIRMED, state());
        else refreshSaleStatus();
        render();
        view.showMessage(failure == null
                        ? (printEnabled ? Message.PRINT_DONE : Message.CONFIRM_DONE)
                        : (printEnabled ? Message.PRINT_FAILED : Message.CONFIRM_FAILED),
                failure == null ? null : failure.getMessage());
    }

    /** 将现有清单交给业务层计价，通知页面状态文案、语音及日志。 */
    public void refreshSaleStatus() {
        checkout.replaceLines(CheckoutRows.toSaleLines(rows, corrected));
        applyAutomaticTare();
        renderSaleStatus();
    }

    /** 清单变化及稳定秤读数共用此入口，状态机负责去重、冲突拦截和失败抑制。 */
    private void applyAutomaticTare() {
        if (view == null || paused) return;
        CheckoutSession.TareCommand command = checkout.beginAutomaticTare();
        if (command == null) return;
        view.logBusiness("automatic_tare_kg=" + command.tareKg.toPlainString());
        render();
        view.applyAutomaticTare(command.tareKg, success -> {
            checkout.finishAutomaticTare(command, success);
            if (view == null || paused) return;
            view.logBusiness("automatic_tare_success=" + success);
            renderSaleStatus();
            render();
        });
    }

    /** 根据已有业务快照展示状态，不重复计算金额。 */
    public void renderSaleStatus() {
        CheckoutState state = state();
        if (view == null || state.printing || !state.recognitionLocked || state.phase == CheckoutState.Phase.SOLD_OR_REPORTED) return;
        SaleStatus status = state.phase == CheckoutState.Phase.LOCKED_PRICE_MISSING ? SaleStatus.PRICE_MISSING
                : state.phase == CheckoutState.Phase.LOCKED_PRICE_CONFLICT ? SaleStatus.PRICE_CONFLICT
                : state.phase == CheckoutState.Phase.LOCKED_VALID ? SaleStatus.READY : SaleStatus.WAITING;
        view.showSaleStatus(status, state);
    }

    /** 将当前展示状态交给页面，渲染不反向修改业务状态。 */
    public void render() { if (view != null) view.render(snapshot()); }

    /** 组合业务快照与展示选择，不暴露可变集合结构。 */
    private CheckoutViewState snapshot() { return new CheckoutViewState(state(), rows, selected, blockedPlus, goodsResolved); }

    /** 离开页面时取消过期查询，保留已锁定清单与手改数量。 */
    public void pause() { paused = true; loader.invalidate(); }

    /** 解除页面及截图接入引用；已提交打印仍可完成，但不再访问页面。 */
    public void detach() {
        if (view == null) return;
        view = null;
        output = null;
        loader.close();
    }
}
