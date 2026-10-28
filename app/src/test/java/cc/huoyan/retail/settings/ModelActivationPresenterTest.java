package cc.huoyan.retail.settings;

import cc.huoyan.retail.testing.QueuedExecutor;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/** 验证选模之前不写、取消无副作用、重建不重发及回执确定性。 */
public class ModelActivationPresenterTest {
    /** 隔离 SDK 和主线程，显式推进异步结果。 */
    private static final class Fixture implements ModelActivationPresenter.Data {
        /** 后台待执行任务。 */
        final QueuedExecutor worker = new QueuedExecutor();
        /** UI 待提交回调。 */
        final QueuedExecutor ui = new QueuedExecutor();
        /** 被测会话。 */
        final ModelActivationPresenter session = new ModelActivationPresenter(worker, ui, this);
        /** 服务端返回的动态列表。 */
        LicenseModels models = new LicenseModels(0, null, Arrays.asList(
                new LicenseModel("arbitrary-A", "新模型甲", "chip", true),
                new LicenseModel("arbitrary-B", "新模型乙", "chip", false)));
        /** 提交回执可注入空值与异常。 */
        BaseResult receipt = new BaseResult(0, null);
        /** 已发送查询次数和写次数。 */
        int queries, writes;
        /** 请求参数及保留输入。 */
        String queryCode, saved, activatedCode, activatedModel;
        /** 查询是否为当前设备范围。 */
        boolean switching;
        /** 测试中可模拟缺少当前激活序列号。 */
        String serial = "device-license";
        /** 测试中可模拟调用已发送后的异常。 */
        boolean writeThrows;
        /** 固定测试授权范围，不硬编码四类业态。 */
        public ModelActivationPresenter.Options query(String code, boolean switching) {
            queries++; queryCode = code; this.switching = switching;
            return new ModelActivationPresenter.Options(switching ? serial : code, models);
        }
        /** 记录实际写入并返回注入回执。 */
        public BaseResult activate(String code, String model) {
            writes++; activatedCode = code; activatedModel = model;
            if (writeThrows) throw new IllegalStateException();
            return receipt;
        }
        /** 保存取消后可重新输入的草稿。 */
        public void remember(String code) { saved = code; }
        /** 推进一次后台请求及其主线程回调。 */
        void complete() { worker.next(); ui.drain(); }
        /** 从手输进入已取得列表的阶段。 */
        void choose() { session.open(false); session.input("ABCD1234EFGH5678"); complete(); }
    }
    /** 输入只查询，未知代码不能提交，点选模型后才精确写一次。 */
    @Test public void inputQueriesThenSelectionSubmitsExactModelOnce() {
        Fixture f = new Fixture(); f.choose();
        assertEquals(ModelActivationPresenter.Stage.CHOOSING, f.session.stage);
        assertEquals("ABCD-1234-EFGH-5678", f.queryCode);
        assertEquals(0, f.writes);
        f.session.select("not-in-list"); assertEquals(0, f.worker.pending());
        f.session.select("arbitrary-B"); f.session.select("arbitrary-B"); f.session.open(true);
        assertEquals(1, f.worker.pending()); f.complete();
        assertEquals(1, f.writes); assertEquals(f.queryCode, f.activatedCode);
        assertEquals("arbitrary-B", f.activatedModel);
        assertEquals(0, f.session.result.getCode());
    }
    /** 激活时即使列表标记 current 也必须完成本次用户请求。 */
    @Test public void activationMaySelectCurrentModel() {
        Fixture f = new Fixture(); f.choose(); f.session.select("arbitrary-A"); f.complete();
        assertEquals(1, f.writes);
    }
    /** 取消选模保留草稿并回到输入，未发任何激活。 */
    @Test public void cancelSelectionKeepsInputAndNeverWrites() {
        Fixture f = new Fixture(); f.choose(); f.session.cancel();
        assertEquals(ModelActivationPresenter.Stage.INPUT, f.session.stage);
        assertEquals("ABCD-1234-EFGH-5678", f.saved); assertEquals(0, f.writes);
        f.session.cancel(); assertEquals(ModelActivationPresenter.Stage.IDLE, f.session.stage);
    }
    /** 切换查询当前设备范围，当前项仅关闭，不写。 */
    @Test public void switchingCurrentAndCancelDoNotActivate() {
        Fixture f = new Fixture(); f.session.open(true); f.complete();
        assertNull(f.queryCode); assertTrue(f.switching);
        f.session.select("arbitrary-A"); assertEquals(ModelActivationPresenter.Stage.IDLE, f.session.stage);
        f.session.open(true); f.complete(); f.session.cancel();
        assertEquals(ModelActivationPresenter.Stage.IDLE, f.session.stage); assertEquals(0, f.writes);
    }
    /** 切换使用证书序列号和点选的动态代码。 */
    @Test public void switchUsesDeviceLicenseAndSelectedModel() {
        Fixture f = new Fixture(); f.session.open(true); f.complete();
        f.session.select("arbitrary-B"); f.complete();
        assertEquals("device-license", f.activatedCode); assertEquals("arbitrary-B", f.activatedModel);
    }
    /** 读取失败和空列表不能产生激活，消费错误后可重试输入。 */
    @Test public void failedOrEmptyQueryReturnsToInput() {
        for (LicenseModels models : Arrays.asList(null, new LicenseModels(503, "unavailable", null),
                new LicenseModels(0, null, Collections.emptyList()))) {
            Fixture f = new Fixture(); f.models = models; f.choose();
            assertEquals(ModelActivationPresenter.Stage.FINISHED, f.session.stage);
            assertNotEquals(0, f.session.result.getCode()); assertEquals(0, f.writes);
            f.session.consumed(); assertEquals(ModelActivationPresenter.Stage.INPUT, f.session.stage);
        }
    }
    /** 无本地码时先选模型再输入；确认只激活一次，不重复查询。 */
    @Test public void missingDeviceLicensePromptsAfterSelection() {
        Fixture f = new Fixture(); f.serial = null; f.session.open(true); f.complete();
        assertEquals(ModelActivationPresenter.Stage.CHOOSING, f.session.stage);
        f.session.select("arbitrary-B");
        assertEquals(ModelActivationPresenter.Stage.INPUT, f.session.stage);
        assertEquals(0, f.writes);
        f.session.input(" "); assertEquals(0, f.worker.pending());
        f.session.detach(); f.session.attach(() -> { });
        assertEquals(ModelActivationPresenter.Stage.INPUT, f.session.stage);
        f.session.input("ABCD1234EFGH5678"); f.session.input("ABCD1234EFGH5678");
        assertEquals(1, f.worker.pending()); f.complete();
        assertEquals(1, f.queries); assertEquals(1, f.writes);
        assertEquals("arbitrary-B", f.activatedModel);
        assertEquals("ABCD-1234-EFGH-5678", f.activatedCode);
    }
    /** 补码取消不提交，下一次重新选模不能复用已取消的目标。 */
    @Test public void cancellingMissingCodeNeverSubmits() {
        Fixture f = new Fixture(); f.serial = null; f.session.open(true); f.complete();
        f.session.select("arbitrary-B"); f.session.cancel();
        assertEquals(ModelActivationPresenter.Stage.IDLE, f.session.stage);
        f.session.input("ABCD1234EFGH5678");
        assertEquals(0, f.worker.pending()); assertEquals(0, f.writes);
    }
    /** 页面暂时分离后仍保留查询/写结果，新视图不会重发。 */
    @Test public void rotationRestoresSessionWithoutReplayingRequest() {
        Fixture f = new Fixture(); int[] old = {0}, fresh = {0};
        f.session.attach(() -> old[0]++); f.session.open(true); f.session.detach();
        int previous = old[0]; f.complete(); assertEquals(previous, old[0]);
        f.session.attach(() -> fresh[0]++); assertEquals(1, f.queries);
        assertEquals(ModelActivationPresenter.Stage.CHOOSING, f.session.stage);
        f.session.select("arbitrary-B"); f.session.detach(); f.complete();
        f.session.attach(() -> fresh[0]++);
        assertEquals(ModelActivationPresenter.Stage.FINISHED, f.session.stage);
        assertEquals(1, f.writes); assertTrue(fresh[0] > 0);
    }
    /** 已提交写完成但最终销毁的会话不再回写旧视图。 */
    @Test public void destroyDropsCallbackButDoesNotCancelSubmittedWrite() {
        Fixture f = new Fixture(); f.choose(); f.session.select("arbitrary-B");
        int[] renders = {0}; f.session.attach(() -> renders[0]++); f.session.destroy(); f.complete();
        assertEquals(1, renders[0]); assertEquals(1, f.writes);
    }
    /** 回执丢失和异常明确保留未知结果，不自动重试。 */
    @Test public void missingAndThrownWriteResultsRemainUnknown() {
        for (boolean throwsError : new boolean[]{false, true}) {
            Fixture f = new Fixture(); f.receipt = null; f.writeThrows = throwsError;
            f.choose(); f.session.select("arbitrary-B"); f.complete();
            assertEquals(HuoyanSdkClient.RESULT_UNKNOWN, f.session.result.getCode());
            assertEquals(1, f.writes); assertEquals(0, f.worker.pending());
        }
    }
}
