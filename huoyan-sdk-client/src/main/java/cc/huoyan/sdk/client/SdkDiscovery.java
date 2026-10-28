package cc.huoyan.sdk.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** SDK WebSocket 服务发现工具；只验证 SDK 协议，不保存连接或业务状态。 */
public final class SdkDiscovery {
    /** SDK WebSocket 服务端口。 */
    public static final int DEFAULT_PORT = 18086;
    /** SDK WebSocket 服务路径。 */
    public static final String DEFAULT_PATH = "/ws";

    private SdkDiscovery() {
    }

    /**
     * 将 IPv4 地址转换为 SDK WebSocket 地址。
     *
     * @param input IPv4 地址，可包含首尾空白
     * @return 固定端口和路径的 WebSocket 地址
     * @throws IllegalArgumentException 地址不是合法 IPv4 地址
     */
    public static String endpointForIp(String input) {
        String value = input == null ? "" : input.trim();
        if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
            throw new IllegalArgumentException("invalid IPv4 address");
        }
        StringJoiner ip = new StringJoiner(".");
        for (String part : value.split("\\.")) {
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                throw new IllegalArgumentException("invalid IPv4 address");
            }
            ip.add(String.valueOf(octet));
        }
        return "ws://" + ip + ":" + DEFAULT_PORT + DEFAULT_PATH;
    }

    /**
     * 从已保存的 WebSocket 地址提取 IPv4 地址。
     *
     * @param endpoint WebSocket 地址
     * @return 可显示和再次编辑的 IPv4 地址；无效地址返回 127.0.0.1
     */
    public static String ipFromEndpoint(String endpoint) {
        try {
            String host = endpoint == null ? null : URI.create(endpoint).getHost();
            return URI.create(endpointForIp(host)).getHost();
        } catch (IllegalArgumentException e) {
            return "127.0.0.1";
        }
    }

    /**
     * 连接指定地址并验证 SDK 状态回执。
     *
     * @param endpoint WebSocket 地址
     * @return 收到 code=0 且包含 sdk_state、device_id 的 SDK 回执时返回 true
     */
    public static boolean probe(String endpoint) {
        CountDownLatch response = new CountDownLatch(1);
        AtomicBoolean found = new AtomicBoolean();
        WebSocketClient client = new WebSocketClient(
                URI.create(SdkConfig.websocket(endpoint).getWsEndpoint())) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                send("{\"requestId\":\"sdk-discovery\",\"cmd\":\"getStatus\"}");
            }

            @Override
            public void onMessage(String message) {
                try {
                    JSONObject root = JSON.parseObject(message);
                    JSONObject data = root.getJSONObject("data");
                    if ("sdk-discovery".equals(root.getString("requestId"))) {
                        found.set(root.containsKey("code") && root.getIntValue("code") == 0
                                && data != null && data.containsKey("sdk_state")
                                && data.containsKey("device_id"));
                        response.countDown();
                    }
                } catch (RuntimeException ignored) {
                    // 探测失败由超时/关闭路径统一处理。
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                response.countDown();
            }

            @Override
            public void onError(Exception error) {
                response.countDown();
            }
        };
        try {
            if (client.connectBlocking(500, TimeUnit.MILLISECONDS)) {
                response.await(800, TimeUnit.MILLISECONDS);
            }
            return found.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            client.close();
        }
    }

    /**
     * 扫描本机局域网 IPv4 网段并返回能够响应 SDK 状态的地址。
     *
     * @param preferredIp 优先探测的 IPv4 地址
     * @return 发现的 IPv4 地址列表，按字符串排序
     * @throws SocketException 无法读取本机网络接口
     */
    public static List<String> discover(String preferredIp) throws SocketException {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(URI.create(endpointForIp(preferredIp)).getHost());
        candidates.add("127.0.0.1");
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()
                    || !(iface.getName().startsWith("wlan") || iface.getName().startsWith("eth"))) {
                continue;
            }
            for (InetAddress address : Collections.list(iface.getInetAddresses())) {
                if (!(address instanceof Inet4Address) || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()) {
                    continue;
                }
                String ip = address.getHostAddress();
                String prefix = ip.substring(0, ip.lastIndexOf('.') + 1);
                for (int i = 1; i <= 254; i++) {
                    candidates.add(prefix + i);
                }
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(24);
        CompletionService<String> completed = new ExecutorCompletionService<>(pool);
        List<String> found = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        try {
            for (String ip : candidates) {
                completed.submit(() -> probe(endpointForIp(ip)) ? ip : null);
            }
            for (int i = 0; i < candidates.size(); i++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                Future<String> result = completed.poll(remaining, TimeUnit.NANOSECONDS);
                if (result == null) {
                    break;
                }
                try {
                    String ip = result.get();
                    if (ip != null) {
                        found.add(ip);
                    }
                } catch (ExecutionException ignored) {
                    // 单个地址失败不影响其他地址探测。
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        Collections.sort(found);
        return found;
    }
}
