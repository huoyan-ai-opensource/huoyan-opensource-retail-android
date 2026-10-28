package cc.huoyan.retail.home;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.StatusSnapshot;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

/** 轮换快照、字段冲突及轮询恢复不能误开放首页收银入口。 */
public class HomeCheckoutStateTest {
    /** C50D 实际回执的授权与模型字段均为空，仍应准确显示待审核。 */
    @Test public void pendingWithUnknownAuthorizationBlocksCheckout() {
        StatusSnapshot status = snapshot(0, "KEY_ROTATION_PENDING", null, null);
        assertEquals(HomeCheckoutState.ROTATION_PENDING, HomeCheckoutState.from(status));
        assertFalse(HomeCheckoutState.from(status).actionable());
        assertNull(HomeStatusSource.activation(status));
        assertFalse(HomeStatusSource.starting(-110, status));
    }

    /** 轮换字段优先于残留授权，不因 modelReady=true 或证书无效放行或提示重新激活。 */
    @Test public void allRotationStatesOverrideModelAndCertificateFlags() {
        String[] states = {"KEY_ROTATION_PENDING", "KEY_ROTATION_REJECTED",
                "KEY_ROTATION_RATE_LIMITED", "KEY_ROTATION_RESTORING"};
        HomeCheckoutState[] expected = {HomeCheckoutState.ROTATION_PENDING, HomeCheckoutState.ROTATION_REJECTED,
                HomeCheckoutState.ROTATION_RATE_LIMITED, HomeCheckoutState.ROTATION_RESTORING};
        for (int i = 0; i < states.length; i++) {
            for (Boolean valid : new Boolean[]{null, false, true}) {
                HomeCheckoutState state = HomeCheckoutState.from(snapshot(0, states[i], true, valid));
                assertEquals(expected[i], state);
                assertTrue(state.isRotation());
                assertFalse(state.actionable());
                assertNull(state.authorization());
            }
        }
    }

    /** 失败、缺字段和未知状态均不可用；READY 也必须有明确的模型就绪回执。 */
    @Test public void unavailableSnapshotCannotBecomeReady() {
        assertEquals(HomeCheckoutState.UNAVAILABLE, HomeCheckoutState.from(null));
        for (String state : new String[]{null, "NEW_STATE", "BACKEND_UNAVAILABLE", "MODEL_NOT_READY"})
            assertFalse(HomeCheckoutState.from(snapshot(0, state, true, true)).actionable());
        assertFalse(HomeCheckoutState.from(snapshot(-1, "READY", true, true)).actionable());
        assertFalse(HomeCheckoutState.from(snapshot(0, "READY", null, true)).actionable());
        assertEquals(HomeCheckoutState.READY, HomeCheckoutState.from(snapshot(0, "READY", true, true)));
    }

    /** 离线本地事实齐备时开放收银，并继续读取证书参与临期提醒。 */
    @Test public void offlineReadyAllowsCheckoutAndCertificateQueries() {
        StatusSnapshot status = snapshot(0, "OFFLINE", true, true);
        HomeCheckoutState state = HomeCheckoutState.from(status);
        assertEquals(HomeCheckoutState.READY, state);
        assertTrue(state.actionable());
        assertEquals(Boolean.TRUE, HomeStatusSource.activation(status));
        assertFalse(HomeStatusSource.starting(0, status));
    }

    /** 可用态字符串不能替代证书、模型与私钥事实，未知授权也不能开放收银。 */
    @Test public void offlineAndOnlineReadyRequireCompleteLocalFacts() {
        for (String name : new String[]{"READY", "OFFLINE"}) {
            assertEquals(HomeCheckoutState.UNAVAILABLE, HomeCheckoutState.from(snapshot(0, name, true, null)));
            assertEquals(HomeCheckoutState.ACTIVATION_REQUIRED, HomeCheckoutState.from(snapshot(0, name, true, false)));
            for (Boolean ready : new Boolean[]{false, null})
                assertEquals(HomeCheckoutState.UNAVAILABLE, HomeCheckoutState.from(snapshot(0, name, ready, true)));
            StatusSnapshot lostKey = new StatusSnapshot(0, null, null, null, null, null,
                    true, false, name, true, false, null, null, true);
            assertEquals(HomeCheckoutState.UNAVAILABLE, HomeCheckoutState.from(lostKey));
            assertFalse(HomeCheckoutState.from(lostKey).actionable());
            assertNull(HomeStatusSource.activation(lostKey));
        }
    }

    /** 离线与在线恢复均采用当前快照；准备中和过期事实不能沿用先前可用入口。 */
    @Test public void pollingTracksOfflineRecoveryWithoutOpeningRestrictedStates() {
        AtomicReference<StatusSnapshot> current = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger certificateReads = new java.util.concurrent.atomic.AtomicInteger();
        HuoyanSdkClient client = (HuoyanSdkClient) Proxy.newProxyInstance(
                HuoyanSdkClient.class.getClassLoader(), new Class<?>[]{HuoyanSdkClient.class},
                (proxy, method, args) -> {
                    if ("isConnected".equals(method.getName())) return true;
                    if ("getStatus".equals(method.getName())) return current.get();
                    if ("getCertInfo".equals(method.getName())) certificateReads.incrementAndGet();
                    return null;
                });
        String[] states = {"MODEL_PREPARING", "OFFLINE", "READY", "CERTIFICATE_EXPIRED", "OFFLINE", "DISABLED", "NEW_STATE"};
        HomeCheckoutState[] expected = {HomeCheckoutState.STARTING, HomeCheckoutState.READY, HomeCheckoutState.READY,
                HomeCheckoutState.ACTIVATION_REQUIRED, HomeCheckoutState.READY, HomeCheckoutState.UNAVAILABLE,
                HomeCheckoutState.UNAVAILABLE};
        for (int i = 0; i < states.length; i++) {
            current.set(snapshot(0, states[i], true, true));
            HomePresenter.Status result = HomeStatusSource.read(client);
            assertEquals(expected[i], result.checkoutState);
            assertEquals(expected[i].authorization(), result.activated);
        }
        assertEquals(3, certificateReads.get());
    }

    /** 使用真实状态读取逻辑反复轮询，可用→待审→查询失败→恢复中→可用不沿用旧值。 */
    @Test public void pollingPreservesRotationAndRecoversFromLatestSnapshot() {
        AtomicReference<StatusSnapshot> current = new AtomicReference<>();
        HuoyanSdkClient client = (HuoyanSdkClient) Proxy.newProxyInstance(
                HuoyanSdkClient.class.getClassLoader(), new Class<?>[]{HuoyanSdkClient.class},
                (proxy, method, args) -> {
                    if ("isConnected".equals(method.getName())) return true;
                    if ("getStatus".equals(method.getName())) return current.get();
                    if ("getCertInfo".equals(method.getName()))
                        assertEquals("只有可用时读取证书", "READY", current.get().getSdkState());
                    return null;
                });
        String[] states = {"READY", "KEY_ROTATION_PENDING", null, "KEY_ROTATION_RESTORING", "READY"};
        HomeCheckoutState[] expected = {HomeCheckoutState.READY, HomeCheckoutState.ROTATION_PENDING,
                HomeCheckoutState.UNAVAILABLE, HomeCheckoutState.ROTATION_RESTORING, HomeCheckoutState.READY};
        for (int i = 0; i < states.length; i++) {
            current.set(states[i] == null ? null : snapshot(0, states[i], "READY".equals(states[i]), true));
            HomePresenter.Status result = HomeStatusSource.read(client);
            assertEquals(expected[i], result.checkoutState);
            assertEquals(expected[i].authorization(), result.activated);
        }
    }

    /** 构造契约快照，不依赖 Android 环境或真实授权。 */
    private static StatusSnapshot snapshot(int code, String state, Boolean ready, Boolean valid) {
        return new StatusSnapshot(code, null, null, null, null, null,
                null, null, state, ready, null, null, null, valid);
    }
}
