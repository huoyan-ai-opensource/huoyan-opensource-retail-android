package cc.huoyan.sdk.client.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.ServiceConnection;

import cc.huoyan.sdk.client.internal.SdkChannelFakes.CapturingScheduler;
import cc.huoyan.sdk.client.internal.SdkChannelFakes.RecordingBindOps;
import cc.huoyan.sdk.client.internal.SdkChannelFakes.RecordingEventLog;

import cc.huoyan.app.aidl.BaseResultParcel;

import org.junit.Test;

/**
 * {@link SdkCommBinding} 绑定状态机单测：退避计算（纯函数）+
 * 绑定/断线/关闭状态机（经 BindOps/Scheduler 注入内存替身，语义对齐 SDK 契约文档
 * 「业务客户端接入与重连」第 4/5/6 节要点）。
 */
public class SdkCommBindingTest {

    private final RecordingBindOps ops = new RecordingBindOps();
    private final CapturingScheduler scheduler = new CapturingScheduler();
    private final RecordingEventLog log = new RecordingEventLog();
    private final SdkCommBinding binding = new SdkCommBinding(ops, scheduler, log);

    // ---- 退避计算（纯函数） ----

    @Test
    public void backoffDelayMs_doublesFrom500ms_andCapsAt30s() {
        assertEquals(500L, SdkCommBinding.backoffDelayMs(0));
        assertEquals(1000L, SdkCommBinding.backoffDelayMs(1));
        assertEquals(2000L, SdkCommBinding.backoffDelayMs(2));
        assertEquals(4000L, SdkCommBinding.backoffDelayMs(3));
        assertEquals(8000L, SdkCommBinding.backoffDelayMs(4));
        assertEquals(16000L, SdkCommBinding.backoffDelayMs(5));
        assertEquals(30000L, SdkCommBinding.backoffDelayMs(6));
        assertEquals(30000L, SdkCommBinding.backoffDelayMs(7));
        assertEquals("大失败次数不得溢出", 30000L, SdkCommBinding.backoffDelayMs(1000));
    }

    // ---- 绑定发起 ----

    @Test
    public void bind_accepted_isIdempotentWhileInFlight() {
        binding.bind();
        binding.bind();
        assertEquals(1, ops.bindCount);
        assertFalse("未收到连接回调前不算已连接", binding.isConnected());
    }

    @Test
    public void bind_rejected_schedulesBackoffRetry_withDoublingDelays() {
        ops.bindAccepted = false;

        binding.bind();
        assertEquals(500L, (long) scheduler.delays.get(0));

        scheduler.runLastDelayed();   // 第一次重试失败 → 1000ms
        assertEquals(1000L, (long) scheduler.delays.get(0));

        scheduler.runLastDelayed();   // 第二次重试失败 → 2000ms
        assertEquals(2000L, (long) scheduler.delays.get(0));
        assertEquals("delays 仅承载在途任务", 1, scheduler.delays.size());

        assertEquals("首绑 + 两次重试", 3, ops.bindCount);
        assertEquals("bindService=false 时连接未注册，无需解绑", 0, ops.unbindCount);
    }

    // ---- 绑定发起异常折算（2026-08-26 C50 真机 bindService 抛 SecurityException 崩溃回归） ----

    @Test
    public void bind_bindOpsThrowsSecurityException_foldsToRejected_andSchedulesBackoff() {
        ThrowingBindOps throwingOps = new ThrowingBindOps(new SecurityException("signature check failed"));
        SdkCommBinding binding = new SdkCommBinding(throwingOps, scheduler, log);

        binding.bind();   // 不得上抛

        assertEquals(1, throwingOps.bindCount);
        assertEquals("异常折算为拒绝，退避重连承载", 1, scheduler.delayed.size());
        assertEquals(500L, (long) scheduler.delays.get(0));
        assertEquals("bind 抛异常时连接未注册，无需解绑", 0, throwingOps.unbindCount);
        assertTrue(log.messages.contains("[Biz] aidl_bind_error=SecurityException"));
        assertTrue(log.messages.contains("[Biz] aidl_connect=0"));
        assertFalse(binding.isConnected());

        scheduler.runLastDelayed();   // 退避到期重试仍抛：继续翻倍退避，不崩溃
        assertEquals(2, throwingOps.bindCount);
        assertEquals(1000L, (long) scheduler.delays.get(0));
    }

    @Test
    public void bind_bindOpsThrowsIllegalArgument_foldsToRejected() {
        ThrowingBindOps throwingOps = new ThrowingBindOps(new IllegalArgumentException("malformed intent"));
        SdkCommBinding binding = new SdkCommBinding(throwingOps, scheduler, log);

        binding.bind();   // 不得上抛

        assertEquals(1, scheduler.delayed.size());
        assertTrue(log.messages.contains("[Biz] aidl_bind_error=IllegalArgumentException"));
    }

    // ---- 连接 / 断线 ----

    @Test
    public void connected_cachesProxy_andResetsBackoff() {
        ops.bindAccepted = false;
        binding.bind();
        scheduler.runLastDelayed();
        scheduler.runLastDelayed();
        assertEquals(2000L, (long) scheduler.delays.get(0));

        ops.bindAccepted = true;
        scheduler.runLastDelayed();   // 第三次重试成功
        assertEquals(0, scheduler.delayed.size());

        FakeSdkCommService service = new FakeSdkCommService();
        binding.connected(service);
        assertTrue(binding.isConnected());

        binding.onRemoteException();   // Binder 调用失败 → 重连
        scheduler.runPosted();
        assertEquals("断线重绑先解旧绑定", 1, ops.unbindCount);
        assertEquals("连接成功后退避清零", 500L, (long) scheduler.delays.get(0));
    }

    @Test
    public void serviceDisconnected_clearsProxy_unbindsOldBinding_andReconnects() {
        binding.bind();
        FakeSdkCommService service = new FakeSdkCommService();
        binding.connected(service);
        assertTrue(binding.isConnected());

        binding.serviceDisconnected();
        assertFalse("断线后死代理必须清空", binding.isConnected());
        scheduler.runPosted();
        assertEquals("旧绑定解掉再重绑", 1, ops.unbindCount);
        assertEquals(1, scheduler.delayed.size());
    }

    @Test
    public void overlappingDisconnectEvents_keepSinglePendingReconnect() {
        binding.bind();
        binding.connected(new FakeSdkCommService());

        binding.serviceDisconnected();
        binding.serviceDisconnected();
        binding.onRemoteException();

        scheduler.runPosted();
        assertEquals("叠加断线事件只留一个在途退避任务", 1, scheduler.delayed.size());
        assertEquals("旧绑定只解一次", 1, ops.unbindCount);

        binding.onRemoteException();   // 在途任务未消化前再叠加
        scheduler.runPosted();
        assertEquals(1, scheduler.delayed.size());
        assertEquals(1, ops.unbindCount);
    }

    @Test
    public void nullBinding_unbinds_andStopsReconnectForever() {
        binding.bind();
        binding.connected(new FakeSdkCommService());
        assertTrue(binding.isConnected());

        binding.nullBinding();
        assertFalse(binding.isConnected());
        assertEquals(1, ops.unbindCount);

        binding.bind();   // 契约级故障后永不再连
        assertEquals(1, ops.bindCount);
        binding.onRemoteException();
        scheduler.runPosted();
        assertEquals(0, scheduler.delayed.size());
    }

    // ---- 关闭 ----

    @Test
    public void close_unbinds_cancelsPendingReconnect_andNeverRebinds() {
        binding.bind();
        binding.serviceDisconnected();   // posted=[重连调度]

        binding.close();
        assertEquals(1, ops.unbindCount);
        assertFalse(binding.isConnected());

        binding.onRemoteException();   // close 后迟来的断线：closed 守卫直接返回
        scheduler.runPosted();
        assertEquals(0, scheduler.delayed.size());

        binding.bind();   // close 后 bind 不再发起
        assertEquals(1, ops.bindCount);
    }

    @Test
    public void close_beforeAnyBind_doesNotUnbind() {
        binding.close();
        assertEquals(0, ops.unbindCount);
    }

    // ---- 初始化动作回执及断连重试 ----

    @Test
    public void init_disconnected_returnsLocalSentinelSnapshot() {
        BaseResultParcel result = binding.init(null);
        assertNotNull("Issue #66 占位为哨兵快照（永非 null）", result);
        assertEquals(SdkCommBinding.LOCAL_DISCONNECTED, result.getCode());
        assertNotNull("哨兵快照 reason 带诊断", result.getReason());
    }

    @Test
    public void init_connected_passesThroughAllStates() {
        FakeSdkCommService service = new FakeSdkCommService();
        binding.connected(service);

        service.initResult = FakeSdkCommService.readyResult(0, null);
        assertEquals(0, binding.init("2.3.4").getCode());
        assertEquals("2.3.4", service.lastAppVersion);
        service.initResult = FakeSdkCommService.readyResult(-110, "密钥轮换待审核");
        BaseResultParcel pending = binding.init(null);
        assertEquals(-110, pending.getCode());
        assertEquals("Issue #66：失败 reason 一次带回", "密钥轮换待审核", pending.getReason());
        service.initResult = FakeSdkCommService.readyResult(-111, "SDK 启动初始化失败：xx");
        assertEquals(-111, binding.init(null).getCode());
        service.initResult = FakeSdkCommService.readyResult(-1, "初始化配置错误");
        assertEquals(-1, binding.init(null).getCode());
        service.initResult = FakeSdkCommService.readyResult(40004, "权限拒绝");
        assertEquals(40004, binding.init(null).getCode());
        assertTrue("就绪回执须打点（平台 assert_value 依赖）",
                log.messages.contains("[Biz] aidl_init=-110"));
    }

    @Test
    public void init_actionResult_passedThroughUntouched() {
        FakeSdkCommService service = new FakeSdkCommService();
        BaseResultParcel shellResult = new BaseResultParcel(-111,
                "SDK 启动初始化失败：模型加载异常");
        service.initResult = shellResult;
        binding.connected(service);

        assertSame("SDK 回执对象原样透传（code/reason 不改写）",
                shellResult, binding.init(null));
    }

    @Test
    public void init_remoteException_returnsSentinel_andTriggersReconnect() {
        FakeSdkCommService service = new FakeSdkCommService();
        service.throwRemote = true;
        binding.connected(service);

        assertEquals(cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN, binding.init(null).getCode());
        assertEquals("RemoteException 须触发重连", 1, scheduler.posted.size());
    }

    // ---- 打点（[Biz] 约定） ----

    @Test
    public void lifecycleEvents_emitBizLogs() {
        binding.bind();
        assertTrue(log.messages.contains("[Biz] aidl_connect=1"));

        ops.bindAccepted = false;
        binding.serviceDisconnected();
        scheduler.runPosted();
        assertTrue(log.messages.contains("[Biz] aidl_disconnected=1"));
        assertTrue(log.messages.stream().anyMatch(m -> m.startsWith("[Biz] aidl_reconnect delay_ms=500")));

        binding.close();
        assertTrue(log.messages.contains("[Biz] aidl_disconnect=close"));
    }

    /** 抛异常绑定替身：模拟 Context.bindService 抛准入异常（SecurityException / IllegalArgumentException 等）。 */
    private static final class ThrowingBindOps implements SdkCommBinding.BindOps {

        private final RuntimeException failure;
        int bindCount;
        int unbindCount;

        ThrowingBindOps(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public boolean bind(ServiceConnection connection) {
            bindCount++;
            throw failure;
        }

        @Override
        public void unbind(ServiceConnection connection) {
            unbindCount++;
        }
    }
}
