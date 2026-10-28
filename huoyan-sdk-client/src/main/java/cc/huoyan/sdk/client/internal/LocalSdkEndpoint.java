package cc.huoyan.sdk.client.internal;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

/** 判断 WS 是否指向本机；不解析远端主机名，避免冷启动等待 DNS。 */
public final class LocalSdkEndpoint {
    private LocalSdkEndpoint() {
    }

    /** 无 DNS 查询地判断回环或本机接口地址，供配置及传输共用。 */
    public static boolean isLocal(String host) {
        if (host == null) return false;
        if ("localhost".equalsIgnoreCase(host) || "::1".equals(host) || "[::1]".equals(host)
                || host.matches("127(?:\\.\\d{1,3}){3}")) {
            return true;
        }
        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return false;
            for (NetworkInterface network : Collections.list(interfaces)) {
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (host.equals(address.getHostAddress())) return true;
                }
            }
        } catch (SocketException ignored) {
            // 无法查询本机地址时仍允许 WS 自行连接。
        }
        return false;
    }
}
