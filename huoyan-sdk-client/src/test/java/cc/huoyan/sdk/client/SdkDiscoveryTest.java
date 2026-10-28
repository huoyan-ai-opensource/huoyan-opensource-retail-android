package cc.huoyan.sdk.client;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** SDK WebSocket 服务发现的协议和地址验证。 */
public class SdkDiscoveryTest {
    @Test
    public void buildsFixedEndpointFromIpOnly() {
        assertEquals("ws://192.168.1.2:18086/ws", SdkDiscovery.endpointForIp(" 192.168.1.2 "));
        assertEquals("ws://127.0.0.1:18086/ws", SdkDiscovery.endpointForIp("127.0.0.1"));
        assertEquals("ws://192.168.1.2:18086/ws", SdkDiscovery.endpointForIp("192.168.001.002"));
        for (String invalid : new String[]{null, "", "localhost", "192.168.1", "256.1.1.1", "1..1.1",
                "1.2.3.-1", "192.168.1.2:18086", "ws://192.168.1.2:18086/ws", "192.168.1.2/ws"}) {
            try {
                SdkDiscovery.endpointForIp(invalid);
                fail(invalid);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void savedEndpointShowsOnlyIpAndUsesFixedPortOnSave() {
        String ip = SdkDiscovery.ipFromEndpoint("ws://192.168.1.2:1234/custom");
        assertEquals("192.168.1.2", ip);
        assertEquals("ws://192.168.1.2:18086/ws", SdkDiscovery.endpointForIp(ip));
        assertEquals("127.0.0.1", SdkDiscovery.ipFromEndpoint(null));
        assertEquals("127.0.0.1", SdkDiscovery.ipFromEndpoint(""));
        assertEquals("127.0.0.1", SdkDiscovery.ipFromEndpoint("not a URI"));
    }

    @Test(timeout = 10000)
    public void probeRequiresSdkStatusNotJustAnOpenPort() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean sdkResponse = new AtomicBoolean();
        WebSocketServer server = new WebSocketServer(new InetSocketAddress("127.0.0.1", 0)) {
            @Override
            public void onOpen(WebSocket socket, ClientHandshake handshake) {
            }

            @Override
            public void onClose(WebSocket socket, int code, String reason, boolean remote) {
            }

            @Override
            public void onMessage(WebSocket socket, String message) {
                assertTrue(message.contains("getStatus"));
                socket.send("{\"requestId\":\"sdk-discovery\",\"code\":0,\"data\":"
                        + (sdkResponse.get() ? "{\"sdk_state\":\"READY\",\"device_id\":\"test\"}" : "{}") + "}");
            }

            @Override
            public void onError(WebSocket socket, Exception error) {
            }

            @Override
            public void onStart() {
                started.countDown();
            }
        };
        server.start();
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            String endpoint = "ws://127.0.0.1:" + server.getPort() + "/ws";
            assertFalse(SdkDiscovery.probe(endpoint));
            sdkResponse.set(true);
            assertTrue(SdkDiscovery.probe(endpoint));
        } finally {
            server.stop(1000);
        }
    }
}
