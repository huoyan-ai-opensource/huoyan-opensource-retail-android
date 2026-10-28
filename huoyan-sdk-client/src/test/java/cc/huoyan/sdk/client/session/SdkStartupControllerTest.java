package cc.huoyan.sdk.client.session;
import cc.huoyan.sdk.client.testing.SdkChannelFakes;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.StatusSnapshot;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SdkStartupControllerTest {
    private final Queue<Runnable> work = new ArrayDeque<>();
    private final Queue<Runnable> callbacks = new ArrayDeque<>();
    private final StartupClient client = new StartupClient();

    private SdkStartupController controller(Executor callbackExecutor) {
        return new SdkStartupController(() -> client, work::add, callbackExecutor,
                (tag, message) -> {});
    }

    /** 首页直接连接及初始化，不下发后端地址，重复启动不创建第二个任务。 */
    @Test
    public void homeStartsSdkWithoutRecognitionPage() {
        SdkStartupController controller = controller(callbacks::add);
        controller.start(null);
        controller.start(null);
        assertEquals(1, work.size());
        work.remove().run();
        assertEquals(Arrays.asList("connect", "initialize", "status"), client.calls);
        assertTrue(callbacks.isEmpty());
        controller.stop();
        assertTrue(client.connected);
    }

    /** 异步连接完成前不调用初始化，完成后自动进入初始化链路。 */
    @Test
    public void waitsForConnectionBeforeInitializing() {
        client.connected = false;
        SdkStartupController controller = controller(Runnable::run);
        List<Boolean> readiness = new ArrayList<>();
        controller.start((code, status, ready) -> {
            readiness.add(ready);
            if (!ready) {
                assertEquals(HuoyanSdkClient.LOCAL_DISCONNECTED, code);
                assertEquals(Arrays.asList("connect"), client.calls);
                client.connected = true;
            }
        });
        work.remove().run();
        assertEquals(Arrays.asList(false, true), readiness);
    }

    /** READY 状态仍须明确 modelReady，避免模型未就绪时提前识别。 */
    @Test
    public void readyStateWithoutReadyModelKeepsPolling() {
        client.status = status(false);
        SdkStartupController controller = controller(Runnable::run);
        List<Boolean> readiness = new ArrayList<>();
        controller.start((code, snapshot, ready) -> {
            readiness.add(ready);
            client.status = status(true);
        });
        work.remove().run();
        assertEquals(Arrays.asList(false, true), readiness);
        assertEquals(1, client.calls.stream().filter("initialize"::equals).count());
    }

    /** 本地模型完成后即使身份尚未云端校准，也结束启动等待，不反复初始化。 */
    @Test
    public void offlineReadinessCompletesAfterModelPreparation() {
        client.status = new StatusSnapshot(0, null, null, null, null, null, false, false,
                "MODEL_PREPARING", false, false, null, null, true);
        SdkStartupController controller = controller(Runnable::run);
        List<Boolean> readiness = new ArrayList<>();
        controller.start((code, snapshot, ready) -> {
            readiness.add(ready);
            if (!ready) client.status = new StatusSnapshot(0, null, null, null, null, null, false, false,
                    "OFFLINE", true, false, null, null, true);
        });
        work.remove().run();
        assertEquals(Arrays.asList(false, true), readiness);
        assertEquals(1, client.calls.stream().filter("initialize"::equals).count());
    }

    /** 页面离开后，已排队的旧回调不能污染再次进入页面时的状态。 */
    @Test
    public void resumeIgnoresOldQueuedCallback() {
        SdkStartupController controller = controller(callbacks::add);
        List<String> events = new ArrayList<>();
        controller.start((code, status, ready) -> events.add("old"));
        work.remove().run();
        controller.stop();
        controller.start((code, status, ready) -> events.add("new"));
        work.remove().run();
        while (!callbacks.isEmpty()) callbacks.remove().run();
        assertEquals(Arrays.asList("new"), events);
    }

    /** 页面在后台任务开始前离开，不应继续拉起 SDK。 */
    @Test
    public void stopCancelsQueuedStartup() {
        SdkStartupController controller = controller(callbacks::add);
        controller.start(null);
        controller.stop();
        work.remove().run();
        assertTrue(client.calls.isEmpty());
    }

    /** 旧初始化返回时不能通知已离开的页面，也不能取消新一轮启动。 */
    @Test
    public void resumeDuringInitializationKeepsNewSession() {
        SdkStartupController controller = controller(callbacks::add);
        List<Boolean> readiness = new ArrayList<>();
        client.onInitialize = () -> {
            controller.stop();
            controller.start((code, status, ready) -> readiness.add(ready));
        };
        controller.start((code, status, ready) -> readiness.add(false));
        work.remove().run();
        assertTrue(callbacks.isEmpty());
        assertFalse(work.isEmpty());
        work.remove().run();
        callbacks.remove().run();
        assertEquals(Arrays.asList(true), readiness);
    }

    private static StatusSnapshot status(boolean ready) {
        return new StatusSnapshot(0, null, null, null, null, null, null, null,
                "READY", ready, false, null, null, null);
    }

    private static final class StartupClient extends SdkChannelFakes.FakeSdkChannelBridge {
        final List<String> calls = new ArrayList<>();
        Runnable onInitialize;

        StartupClient() {
            connected = true;
            status = status(true);
        }

        @Override public void connect() { calls.add("connect"); }

        @Override public BaseResult setBaseUrl(String url) {
            throw new AssertionError("客户端不得向 SDK 下发后端地址");
        }

        @Override public BaseResult initialize() {
            calls.add("initialize");
            if (onInitialize != null) {
                Runnable action = onInitialize;
                onInitialize = null;
                action.run();
            }
            return new BaseResult(0, null);
        }

        @Override public StatusSnapshot getStatus() {
            calls.add("status");
            return status;
        }
    }
}
