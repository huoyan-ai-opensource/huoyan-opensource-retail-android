package cc.huoyan.retail.checkout;

import cc.huoyan.retail.checkout.domain.CheckoutState;
import cc.huoyan.retail.checkout.domain.SalePrintJob;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.checkout.CheckoutRecognition;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CheckoutPresenterTest {
    private final FakeView view = new FakeView();
    private final FakeLoader loader = new FakeLoader();
    private final FakeOutput output = new FakeOutput();
    private final CheckoutPresenter presenter = new CheckoutPresenter(view, loader, output);

    private GoodsVo row(String plu, int unit, int count) {
        GoodsEntity goods = new GoodsEntity(); goods.setPlu(plu); goods.setName(plu); goods.setUnit(unit); goods.setPrice(100);
        RecognitionSelection item = new RecognitionSelection(new cc.huoyan.sdk.client.model.RecognitionItem(plu, 0, 1, 2, 3, 4));
        return new GoodsVo(count, 100, goods, Collections.singletonList(item));
    }

    private CheckoutRecognition load(GoodsVo... rows) {
        List<RecognitionSelection> selections = new ArrayList<>();
        for (GoodsVo row : rows) selections.addAll(row.getRecognitionSelection());
        CheckoutRecognition response = new CheckoutRecognition(selections);
        presenter.lockRecognition(response);
        loader.deliver(Arrays.asList(rows));
        return response;
    }

    @Test public void lockingWaitsForGoodsBeforeDecidingEmptyState() {
        GoodsVo goods = row("A", 5, 1);
        presenter.lockRecognition(new CheckoutRecognition(goods.getRecognitionSelection()));
        assertTrue(presenter.state().recognitionLocked);
        assertTrue(presenter.rows().isEmpty());
        assertFalse(view.page.waitingVisible);
        // 等待数据库回调期间，秤事件也会要求页面重绘。
        presenter.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, true);
        presenter.render();
        presenter.render();
        assertFalse(view.emptyStates.contains(true));
        assertFalse(view.page.sale.canPrint);
        assertFalse(view.page.waitingVisible);

        loader.deliver(Collections.singletonList(goods));
        assertEquals(1, view.page.rows.size());
        assertFalse(view.emptyStates.contains(true));
    }

    /** 放盘后及异步锁定查询期间均不能重新播放放盘动画，清空秤盘才恢复。 */
    @Test public void placementGuideOnlyAppearsForIdleEmptySession() {
        presenter.render();
        assertTrue(view.page.waitingVisible);
        presenter.onWeight(new BigDecimal("0.500"), java.math.BigDecimal.ZERO, false);
        presenter.clearRows();
        assertFalse(view.page.waitingVisible);
        presenter.lockRecognition(new CheckoutRecognition(Collections.emptyList()));
        assertFalse(view.page.waitingVisible);
        loader.deliver(Collections.emptyList());
        assertTrue(view.page.emptyVisible);
        assertFalse(view.page.waitingVisible);
        for (int i = 0; i < 3; i++) presenter.onWeight(BigDecimal.ZERO, java.math.BigDecimal.ZERO, true);
        presenter.clearRows();
        assertTrue(view.page.waitingVisible);
    }

    @Test public void successfulEmptyResultIsShownButNextLockStartsUnresolved() {
        presenter.lockRecognition(new CheckoutRecognition(Collections.emptyList()));
        assertFalse(view.page.emptyVisible);
        loader.deliver(Collections.emptyList());
        assertTrue(view.page.emptyVisible);

        view.emptyStates.clear();
        GoodsVo next = row("NEXT", 5, 1);
        presenter.lockRecognition(new CheckoutRecognition(next.getRecognitionSelection()));
        presenter.render();
        assertFalse(view.emptyStates.contains(true));
        loader.deliver(Collections.singletonList(next));
        assertFalse(view.emptyStates.contains(true));
    }

    @Test public void failedQueryIsNotAnEmptyResultAndRetryCanShowRealEmptyState() {
        CheckoutRecognition response = new CheckoutRecognition(Collections.emptyList());
        presenter.lockRecognition(response);
        Exception error = new IllegalStateException("database query failed");
        loader.failure.accept(error);
        presenter.render();
        assertSame(error, view.loadingFailure);
        assertFalse(view.emptyStates.contains(true));

        presenter.loadGoods(response);
        loader.deliver(Collections.emptyList());
        assertTrue(view.page.emptyVisible);
        presenter.clearRows();
        assertFalse(view.page.emptyVisible);
    }

    @Test public void countEditingKeepsLockAndRepricesWithoutReloadingRecognition() {
        presenter.setWeighingEnabled(false);
        GoodsVo row = row("A", 5, 1); load(row);
        presenter.editQuantity(row);
        assertEquals(1, view.initialCount);
        view.confirmCount.accept(3);
        assertEquals(300L, presenter.state().receipt.getTotalAmountCent());
        assertTrue(presenter.state().recognitionLocked);
        assertTrue(view.page.sale.canPrint);
        assertEquals(1, loader.loads);
        assertTrue(loader.invalidations > 0);
    }

    @Test public void dialogFromPreviousRecognitionCannotChangeNewRows() {
        presenter.setWeighingEnabled(false);
        GoodsVo old = row("A", 5, 1); load(old);
        presenter.editQuantity(old); IntConsumer confirm = view.confirmCount;
        GoodsVo next = row("B", 5, 2); load(next);
        confirm.accept(9);
        assertEquals(Integer.valueOf(2), next.getCount());
        assertEquals(200L, presenter.state().receipt.getTotalAmountCent());
    }

    @Test public void selectingAndReplacingAWholeRowPreservesManualQuantity() {
        presenter.setWeighingEnabled(false);
        GoodsVo original = row("A", 5, 7); load(original);
        presenter.toggleItem(original.getRecognitionSelection().get(0));
        assertTrue(view.page.modifyEnabled);
        presenter.modifySelected();
        assertTrue(view.confirmGoods.test(row("B", 5, 1).getGoods()));
        assertEquals("B", presenter.rows().get(0).getGoods().getPlu());
        assertEquals(Integer.valueOf(7), presenter.rows().get(0).getCount());
        assertTrue(view.page.selected.isEmpty());
        assertTrue(presenter.state().receipt.getItems().get(0).isCorrected());
    }

    @Test public void staleGoodsPickerCannotReplaceTheNextSale() {
        presenter.setWeighingEnabled(false);
        GoodsVo original = row("A", 5, 1); load(original);
        presenter.toggleItem(original.getRecognitionSelection().get(0)); presenter.modifySelected();
        Predicate<GoodsEntity> confirm = view.confirmGoods;
        load(row("B", 5, 1));
        assertFalse(confirm.test(row("C", 5, 1).getGoods()));
        assertEquals(CheckoutPresenter.Message.MODIFY_EXPIRED, view.message);
        assertEquals("B", presenter.rows().get(0).getGoods().getPlu());
    }

    @Test public void weightEditUsesWeightDialogAndPreservesScaleBaseline() {
        presenter.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, true);
        presenter.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, true);
        GoodsVo weighted = row("W", 0, 1); load(weighted);
        presenter.editQuantity(weighted);
        assertNotNull(view.confirmWeight); assertNull(view.confirmCount);
        view.confirmWeight.accept(new BigDecimal("1.200"));
        assertEquals(120L, presenter.state().receipt.getTotalAmountCent());
        presenter.onWeight(new BigDecimal("0.359"), java.math.BigDecimal.ZERO, true);
        assertTrue(presenter.state().recognitionLocked);
    }

    @Test public void printLocksEditingAndFailureRestoresTheCurrentSale() {
        presenter.setWeighingEnabled(false);
        GoodsVo row = row("A", 5, 2); load(row);
        presenter.editQuantity(row);
        IntConsumer pendingConfirmation = view.confirmCount;
        view.confirmCount = null;
        presenter.confirmResult(true);
        assertTrue(presenter.state().printing); assertFalse(view.page.sale.canEdit);
        pendingConfirmation.accept(9);
        assertEquals(Integer.valueOf(2), row.getCount());
        presenter.editQuantity(row); assertNull(view.confirmCount);
        presenter.confirmResult(true); assertEquals(1, output.submissions);
        output.finish.accept(new IllegalStateException("printer"));
        assertTrue(presenter.state().canEdit); assertTrue(presenter.state().canPrint);
        assertEquals(CheckoutPresenter.Message.PRINT_FAILED, view.message);
        assertTrue(output.printEnabled);
    }

    /** 不打印也固定保留与删除结果，完成后禁止重复确认，并继续沿用下一单恢复规则。 */
    @Test public void confirmationWithoutPrintingSubmitsFeedbackSnapshotOnce() {
        presenter.setWeighingEnabled(false);
        presenter.setDeletionEnabled(true);
        GoodsVo kept = row("KEEP", 5, 2), deleted = row("DELETE", 5, 1);
        CheckoutRecognition original = load(kept, deleted);
        presenter.deleteRow(deleted);
        presenter.confirmResult(false);
        assertFalse(output.printEnabled);
        assertSame(original, output.response);
        assertEquals(Collections.singletonList(kept), output.rows);
        assertEquals(Collections.singletonList(deleted), output.deleted);
        assertTrue(presenter.state().printing);
        assertFalse(presenter.state().canEdit);
        presenter.confirmResult(false);
        assertEquals(1, output.submissions);
        output.finish.accept(null);
        assertEquals(CheckoutState.Phase.SOLD_OR_REPORTED, presenter.state().phase);
        assertEquals(CheckoutPresenter.Message.CONFIRM_DONE, view.message);
        assertEquals(CheckoutPresenter.SaleStatus.CONFIRMED, view.status);
        presenter.confirmResult(false);
        assertEquals(1, output.submissions);
        presenter.restart();
        assertTrue(presenter.state().recognitionActive);
    }

    /** 无打印确认失败恢复编辑与重试，错误语义不能显示为打印失败。 */
    @Test public void confirmationFailureCanRetryWithoutPrinting() {
        presenter.setWeighingEnabled(false);
        load(row("A", 5, 1));
        presenter.confirmResult(false);
        output.finish.accept(new IllegalStateException("snapshot"));
        assertEquals(CheckoutPresenter.Message.CONFIRM_FAILED, view.message);
        assertTrue(presenter.state().canEdit);
        assertTrue(presenter.state().canPrint);
        presenter.confirmResult(false);
        assertEquals(2, output.submissions);
        assertFalse(output.printEnabled);
    }

    /** 关闭打印不能绕过空单、缺价与称重未就绪校验。 */
    @Test public void confirmationWithoutPrintingStillValidatesReceipt() {
        presenter.confirmResult(false);
        assertEquals(0, output.submissions);
        presenter.setWeighingEnabled(false);
        GoodsVo free = row("FREE", 5, 1);
        free.getGoods().setPrice(0);
        load(free);
        presenter.confirmResult(false);
        assertEquals(CheckoutPresenter.Message.PRICE_MISSING, view.message);
        assertEquals(0, output.submissions);
        presenter.restart();
        presenter.clearRows();
        presenter.setWeighingEnabled(true);
        load(row("WEIGHT", 0, 1));
        presenter.confirmResult(false);
        assertEquals(0, output.submissions);
    }

    /** 页面销毁不取消已提交的无打印确认。 */
    @Test public void confirmationWithoutPrintingFinishesAfterDetach() {
        presenter.setWeighingEnabled(false);
        load(row("A", 5, 1));
        presenter.confirmResult(false);
        int renders = view.renders;
        presenter.detach();
        output.finish.accept(null);
        assertEquals(renders, view.renders);
        assertEquals(CheckoutState.Phase.SOLD_OR_REPORTED, presenter.state().phase);
    }

    @Test public void oldPrintCompletesWithoutMarkingTheNextSaleSold() {
        presenter.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, true);
        presenter.onWeight(new BigDecimal("0.356"), java.math.BigDecimal.ZERO, true);
        GoodsVo oldRow = row("OLD", 5, 2);
        CheckoutRecognition oldResponse = load(oldRow);
        presenter.confirmResult(true);
        assertSame(oldResponse, output.response);
        assertSame(oldRow, output.rows.get(0));
        for (int i = 0; i < 3; i++) presenter.onWeight(BigDecimal.ZERO, java.math.BigDecimal.ZERO, true);
        presenter.clearRows();
        presenter.onWeight(new BigDecimal("0.700"), java.math.BigDecimal.ZERO, true);
        presenter.onWeight(new BigDecimal("0.700"), java.math.BigDecimal.ZERO, true);
        load(row("NEXT", 5, 4));
        output.finish.accept(null);
        assertEquals(CheckoutState.Phase.LOCKED_VALID, presenter.state().phase);
        assertEquals("NEXT", presenter.state().receipt.getItems().get(0).getPlu());
        assertEquals(400L, presenter.state().receipt.getTotalAmountCent());
        assertTrue(view.page.sale.canPrint);
        assertNotEquals(CheckoutPresenter.SaleStatus.PRINTED, view.status);
    }

    @Test public void detachedViewIsNotRetainedOrCalledByPrintAndLoadCompletion() {
        presenter.setWeighingEnabled(false); load(row("A", 5, 1)); presenter.confirmResult(true);
        Consumer<List<GoodsVo>> oldLoad = loader.commit;
        int renders = view.renders;
        presenter.detach();
        output.finish.accept(null);
        oldLoad.accept(Collections.singletonList(row("LATE", 5, 1)));
        assertEquals(renders, view.renders);
        assertTrue(loader.closed);
        assertEquals(CheckoutState.Phase.SOLD_OR_REPORTED, presenter.state().phase);
    }

    @Test public void blockedWeightRowStaysVisibleAndUnmatchedRowCannotBeSelected() {
        presenter.setWeighingEnabled(false);
        GoodsVo weighted = row("W", 0, 1); load(weighted);
        assertEquals(Collections.singleton("W"), view.page.blockedPlus);
        assertFalse(view.page.sale.canPrint); assertEquals(0, view.resumes);
        GoodsVo unknown = row("UNKNOWN", 0, 1); unknown.setUnmatched(true); load(unknown);
        presenter.toggleItem(unknown.getRecognitionSelection().get(0));
        assertTrue(view.page.selected.isEmpty());
        assertEquals(0, view.resumes);
        assertTrue(presenter.state().recognitionLocked);
        assertSame(unknown, presenter.rows().get(0));
        assertFalse(view.page.sale.canPrint);
    }

    /** 无称重时未匹配结果保持锁定，只有人工重新识别才开始下一轮。 */
    @Test public void unmatchedResultWithoutWeighingStaysLockedUntilRestart() {
        presenter.setWeighingEnabled(false);
        GoodsVo unknown = row("UNKNOWN", 0, 1);
        unknown.setUnmatched(true);
        load(unknown);

        presenter.render();
        presenter.renderSaleStatus();
        assertTrue(presenter.state().recognitionLocked);
        assertSame(unknown, view.page.rows.get(0));
        assertEquals(0, view.resumes);
        assertFalse(presenter.state().canPrint);

        assertTrue(presenter.restart());
        presenter.clearRows();
        assertFalse(presenter.state().recognitionLocked);
        assertTrue(presenter.state().recognitionActive);
        GoodsVo next = row("NEXT", 5, 2);
        load(next);
        assertTrue(presenter.state().recognitionLocked);
        assertEquals(200L, presenter.state().receipt.getTotalAmountCent());
    }

    /** 无称重时空清单查询完成后展示锁定空态，不立即进入下一轮识别。 */
    @Test public void emptyLoadedResultWithoutWeighingKeepsLockedEmptyState() {
        presenter.setWeighingEnabled(false);
        load();
        assertTrue(presenter.state().recognitionLocked);
        assertTrue(view.page.emptyVisible);
        assertFalse(presenter.state().canPrint);
        assertEquals(0, view.resumes);
    }

    /** 默认关闭、打印中与已打印均不能删除；失败后允许继续编辑。 */
    @Test public void deletionHonorsSwitchAndPrintLock() {
        presenter.setWeighingEnabled(false);
        GoodsVo a = row("A", 5, 2); load(a);
        presenter.deleteRow(a);
        assertEquals(1, presenter.rows().size());
        presenter.setDeletionEnabled(true);
        presenter.confirmResult(true); presenter.deleteRow(a);
        assertEquals(1, presenter.rows().size());
        output.finish.accept(new IllegalStateException("printer"));
        presenter.confirmResult(true); output.finish.accept(null); presenter.deleteRow(a);
        assertEquals(1, presenter.rows().size());
        assertEquals(0, output.deletionSubmissions);
    }

    /** 删除整行后重算金额、清选框并向输出传递删除记录，关闭开关不丢记录。 */
    @Test public void deletionRepricesAndTravelsWithPrintedFeedback() {
        presenter.setWeighingEnabled(false); presenter.setDeletionEnabled(true);
        GoodsVo a = row("A", 5, 3), b = row("B", 5, 2);
        CheckoutRecognition original = load(a, b);
        presenter.toggleRow(a); presenter.deleteRow(a); presenter.deleteRow(a);
        assertEquals(Collections.singletonList(b), presenter.rows());
        assertTrue(view.page.selected.isEmpty());
        assertEquals(2, view.page.itemCount);
        assertEquals(200L, presenter.state().receipt.getTotalAmountCent());
        assertEquals(0, output.deletionSubmissions);
        presenter.setDeletionEnabled(false); presenter.confirmResult(true);
        assertSame(original, output.response);
        assertEquals(Collections.singletonList(a), output.deleted);
        assertEquals(Collections.singletonList(b), output.rows);
    }

    /** 全部删空只反馈一次，返回页不复活旧行，新轮次清空删除记录。 */
    @Test public void deletingAllKeepsEmptyStateAndSnapshotsFeedbackAcrossRestart() {
        presenter.setWeighingEnabled(false); presenter.setDeletionEnabled(true);
        GoodsVo a = row("A", 5, 2); load(a);
        presenter.editQuantity(a); IntConsumer stale = view.confirmCount;
        presenter.deleteRow(a); presenter.deleteRow(a); stale.accept(9);
        assertEquals(Integer.valueOf(2), a.getCount());
        assertTrue(view.page.emptyVisible); assertFalse(presenter.state().canPrint);
        assertFalse(presenter.needsGoodsReload());
        presenter.pause(); presenter.refreshRows();
        assertTrue(view.page.emptyVisible); assertFalse(presenter.needsGoodsReload());
        assertEquals(1, output.deletionSubmissions);
        List<GoodsVo> snapshot = output.deleted;
        presenter.restart(); presenter.clearRows(); load(row("NEXT", 5, 1)); presenter.confirmResult(true);
        assertTrue(output.deleted.isEmpty()); assertEquals(Collections.singletonList(a), snapshot);
    }

    /** 改品后删除仍保留原始识别框；旧改品确认不能修改已删除结果。 */
    @Test public void deletingCorrectedRowRetainsOriginalDetection() {
        presenter.setWeighingEnabled(false); presenter.setDeletionEnabled(true);
        GoodsVo a = row("A", 5, 1); load(a);
        presenter.toggleRow(a); presenter.modifySelected();
        assertTrue(view.confirmGoods.test(row("B", 5, 1).getGoods()));
        GoodsVo b = presenter.rows().get(0);
        presenter.toggleRow(b); presenter.modifySelected();
        Predicate<GoodsEntity> stale = view.confirmGoods;
        presenter.deleteRow(b);
        assertEquals("A", output.deleted.get(0).getRecognitionSelection().get(0).getOriginalClazz());
        assertFalse(stale.test(row("C", 5, 1).getGoods()));
        assertTrue(presenter.rows().isEmpty());
    }

    private static class FakeLoader implements CheckoutPresenter.GoodsLoader {
        Consumer<List<GoodsVo>> commit; int loads, invalidations; boolean closed;
        Consumer<Exception> failure;
        public void load(CheckoutRecognition response, Consumer<List<GoodsVo>> commit, Consumer<Exception> failure) { loads++; this.commit = commit; this.failure = failure; }
        public void invalidate() { invalidations++; }
        public void close() { closed = true; }
        void deliver(List<GoodsVo> rows) { commit.accept(rows); }
    }
    private static class FakeOutput implements CheckoutPresenter.Output {
        /** 提交时固定的打印选择，用于验证不打印确认不会切换到硬件输出。 */
        boolean printEnabled;
        public void submitDeletion(CheckoutRecognition response, List<GoodsVo> deleted) { this.response = response; this.deleted = deleted; deletionSubmissions++; }
        Consumer<Exception> finish; int submissions; CheckoutRecognition response; List<GoodsVo> rows;
        List<GoodsVo> deleted; int deletionSubmissions;
        public void submit(SalePrintJob job, boolean printEnabled, CheckoutRecognition response, List<GoodsVo> rows, List<GoodsVo> deleted, Consumer<Exception> finish) { this.printEnabled = printEnabled; this.deleted = deleted; submissions++; this.finish = finish; this.response = response; this.rows = rows; }
    }
    private static class FakeView implements CheckoutPresenter.View {
        /** 捕获实际发给电子秤的皮重和异步回执。 */
        final List<BigDecimal> tareRequests = new ArrayList<>();
        /** 当前命令的设备完成回调。 */
        Consumer<Boolean> finishTare;
        /** 测试主动交付设备回执，避免模拟成功掩盖旧净重结算。 */
        public void applyAutomaticTare(BigDecimal tare, Consumer<Boolean> completion) {
            tareRequests.add(tare); finishTare = completion;
        }
        CheckoutViewState page; CheckoutPresenter.Message message; CheckoutPresenter.SaleStatus status;
        IntConsumer confirmCount; Consumer<BigDecimal> confirmWeight; Predicate<GoodsEntity> confirmGoods;
        int initialCount, renders, resumes;
        final List<Boolean> emptyStates = new ArrayList<>();
        Exception loadingFailure;
        public void render(CheckoutViewState page) { this.page = page; renders++; emptyStates.add(page.emptyVisible); }
        public void renderRows(CheckoutViewState page, boolean updateList) { this.page = page; emptyStates.add(page.emptyVisible); }
        public void showCountInput(int initial, IntConsumer confirm) { initialCount = initial; confirmCount = confirm; }
        public void showWeightInput(BigDecimal initial, Consumer<BigDecimal> confirm) { confirmWeight = confirm; }
        public void showGoodsPicker(GoodsVo selected, Predicate<GoodsEntity> confirm) { confirmGoods = confirm; }
        public void showMessage(CheckoutPresenter.Message message, String detail) { this.message = message; }
        public void showSaleStatus(CheckoutPresenter.SaleStatus status, CheckoutState state) { this.status = status; }
        public void showManualWeight(BigDecimal weight) {}
        public void logBusiness(String message) {}
        public void onGoodsLoaded(CheckoutRecognition response, List<GoodsVo> rows) {}
        public boolean isCountInputShowing() { return false; }
        public void resumeRecognition() { resumes++; }
        public void showLoadingFailure(Exception error) { loadingFailure = error; }
    }

    /** 不同价时不去皮，删除冲突行后按剩余识别数量设置皮重，净重回读后才能打印。 */
    @Test public void deletionAppliesRemainingWeightedTareAndWaitsForNetWeight() {
        presenter.setDeletionEnabled(true);
        presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        GoodsVo a = row("A", 0, 2), b = row("B", 0, 1);
        a.getGoods().setTareWeight(25);
        b.getGoods().setTareWeight(80);
        b.getGoods().setPrice(200);
        load(a, b);
        assertTrue(view.tareRequests.isEmpty());
        assertEquals(CheckoutState.Phase.LOCKED_PRICE_CONFLICT, presenter.state().phase);

        presenter.deleteRow(b);
        assertEquals(Collections.singletonList(new BigDecimal("0.050")), view.tareRequests);
        assertTrue(presenter.state().automaticTarePending);
        assertFalse(presenter.state().canPrint);
        presenter.confirmResult(true);
        assertEquals(0, output.submissions);
        assertFalse(presenter.restart());
        presenter.refreshRows();
        assertEquals(1, view.tareRequests.size());

        presenter.onWeight(new BigDecimal("0.450"), new BigDecimal("0.050"), true);
        view.finishTare.accept(true);
        assertFalse(presenter.state().canPrint);
        presenter.onWeight(new BigDecimal("0.450"), new BigDecimal("0.050"), true);
        assertTrue(presenter.state().recognitionLocked);
        assertTrue(presenter.state().canPrint);
        assertEquals(45L, presenter.state().receipt.getTotalAmountCent());
        assertEquals(new BigDecimal("0.450"), presenter.state().receipt.getWeightKg());
        presenter.confirmResult(true);
        assertEquals(1, output.submissions);
        assertEquals(Collections.singletonList(b), output.deleted);
    }

    /** 加载稳定识别结果前不去皮，商品加载早于重量稳定时等到稳定读数再提交。 */
    @Test public void automaticTareWaitsForLockedGoodsAndStableScale() {
        GoodsVo a = row("A", 0, 2);
        a.getGoods().setTareWeight(30);
        CheckoutRecognition response = new CheckoutRecognition(a.getRecognitionSelection());
        presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, false);
        presenter.loadGoods(response);
        loader.deliver(Collections.singletonList(a));
        assertTrue(view.tareRequests.isEmpty());
        load(a);
        assertTrue(view.tareRequests.isEmpty());
        presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        assertTrue(view.tareRequests.isEmpty());
        presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        assertEquals(Collections.singletonList(new BigDecimal("0.060")), view.tareRequests);
    }

    /** 关闭称重模块及计件商品即使带有皮重字段也不能触发自动去皮。 */
    @Test public void automaticTareIgnoresDisabledWeighingAndCountGoods() {
        presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        GoodsVo count = row("C", 5, 4);
        count.getGoods().setTareWeight(50);
        load(count);
        assertTrue(view.tareRequests.isEmpty());
        GoodsVo weight = row("W", 0, 4);
        weight.getGoods().setTareWeight(50);
        presenter.setWeighingEnabled(false);
        load(weight);
        assertTrue(view.tareRequests.isEmpty());
        assertFalse(presenter.state().canPrint);
    }

    /** 失败不随读数重复提交；删除改变目标后允许新命令，销毁页面不再渲染。 */
    @Test public void failedTareIsNotRetriedUntilTargetChangesAndDetachIgnoresUi() {
        presenter.setDeletionEnabled(true);
        presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        GoodsVo a = row("A", 0, 2), b = row("B", 0, 1);
        a.getGoods().setTareWeight(25); b.getGoods().setTareWeight(30);
        load(a, b);
        assertEquals(new BigDecimal("0.080"), view.tareRequests.get(0));
        view.finishTare.accept(false);
        for (int i = 0; i < 5; i++) presenter.onWeight(new BigDecimal("0.500"), BigDecimal.ZERO, true);
        presenter.refreshRows();
        assertEquals(1, view.tareRequests.size());
        assertFalse(presenter.state().canPrint);
        presenter.deleteRow(b);
        assertEquals(new BigDecimal("0.050"), view.tareRequests.get(1));
        int renders = view.renders;
        presenter.detach();
        view.finishTare.accept(false);
        assertEquals(renders, view.renders);
        assertFalse(presenter.state().automaticTarePending);
    }
}
