package cc.huoyan.retail.home;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.Capability;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** 验证首页版本查询不再依赖激活或证书接口。 */
public class HomeStatusSourceTest {
    /** 已连接但云状态不可用时，能力接口版本仍显示且继续查询云状态。 */
    @Test public void capabilityVersionDisplaysWithoutCertificateAndKeepsCloudPolling() {
        List<Runnable> timers = new ArrayList<>();
        List<HomePresenter.Status> rendered = new ArrayList<>();
        List<Boolean> stopped = new ArrayList<>();
        HuoyanSdkClient client = client(true, "2.0.0", -3);
        HomePresenter presenter = new HomePresenter(new HomePresenter.View() {
            public void showConnection() { }
            public void showSdkStartup(boolean loading) { }
            public void showStatus(HomePresenter.Status status) { rendered.add(status); }
        }, Runnable::run, Runnable::run, (task, delay) -> timers.add(task),
                () -> HomeStatusSource.read(client), client::isConnected, () -> stopped.add(true));
        presenter.resume();
        assertEquals("2.0.0", rendered.get(0).version);
        assertFalse(rendered.get(0).cloudValid);
        assertEquals(0, stopped.size());
        assertEquals(1, timers.size());
        presenter.detach();
    }

    /** 无效版本不能当作真实版本，未连接时也不得调用远程接口。 */
    @Test public void missingVersionOrDisconnectedShowsPlaceholder() {
        for (String version : new String[]{null, "", "  ", "unknown", " UNKNOWN "}) {
            assertEquals("—", HomeStatusSource.read(client(true, version, 0)).version);
        }
        assertEquals("—", HomeStatusSource.read(client(false, "2.0.0", 0)).version);
    }

    /** 云端断连或 SDK 初始化中不能误报未激活，确认证书无效时才能提示激活。 */
    @Test public void activationDistinguishesUnknownFromInvalidCertificate() {
        assertNull(HomeStatusSource.activation(null));
        assertNull(HomeStatusSource.activation(status(-1, "NOT_ACTIVATED", false, false)));
        assertNull(HomeStatusSource.activation(status(0, "INITIALIZING", false, null)));
        assertEquals(Boolean.FALSE, HomeStatusSource.activation(status(0, "READY", true, false)));
        assertEquals(Boolean.FALSE, HomeStatusSource.activation(status(0, "NOT_ACTIVATED", false, null)));
        assertEquals(Boolean.TRUE, HomeStatusSource.activation(status(0, "READY", true, true)));
        assertEquals(Boolean.TRUE, HomeStatusSource.activation(status(0, "OFFLINE", true, true)));
        assertNull(HomeStatusSource.activation(status(0, "OFFLINE", true, null)));
    }

    /** 版本与连接不能代替初始化回执，暂不可用继续等，明确终态结束等待。 */
    @Test public void startupWaitsForInitializationSnapshot() {
        assertTrue(HomeStatusSource.starting(0, null));
        assertTrue(HomeStatusSource.starting(0, status(-1, "READY", true, true)));
        assertTrue(HomeStatusSource.starting(HuoyanSdkClient.LOCAL_DISCONNECTED, status(0, "READY", true, true)));
        for (String state : new String[]{"NOT_INIT", "INITIALIZING", "MODEL_PREPARING", "CLEANUP_PENDING"})
            assertTrue(HomeStatusSource.starting(0, status(0, state, false, true)));
        for (String state : new String[]{"READY", "OFFLINE", "NOT_ACTIVATED", "REGISTERED", "STARTUP_FAILED"})
            assertFalse(HomeStatusSource.starting(0, status(0, state, false, null)));
        assertFalse(HomeStatusSource.starting(-111, null));
    }

    /** 构造授权状态，不依赖 Android 环境。 */
    private static cc.huoyan.sdk.client.model.StatusSnapshot status(int code, String state, Boolean ready, Boolean valid) {
        return new cc.huoyan.sdk.client.model.StatusSnapshot(code, null, null, null, null, null,
                null, null, state, ready, null, null, null, valid);
    }

    /** 仅允许连接、云状态和能力查询，调用证书接口会使测试失败。 */
    private static HuoyanSdkClient client(boolean connected, String version, int code) {
        return (HuoyanSdkClient) Proxy.newProxyInstance(HuoyanSdkClient.class.getClassLoader(),
                new Class<?>[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    if ("isConnected".equals(method.getName())) return connected;
                    assertTrue("未连接不能发起查询", connected);
                    if ("getCloudStatus".equals(method.getName()) || "getStatus".equals(method.getName())) return null;
                    if ("getCapabilities".equals(method.getName())) {
                        return new Capability(code, null, 2, false, false, false,
                                false, false, false, version, 1);
                    }
                    throw new AssertionError("不应调用 " + method.getName());
                });
    }
}
