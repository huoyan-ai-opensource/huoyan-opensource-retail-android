package cc.huoyan.sdk.client.internal;

import org.junit.Test;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.Collections;
import static org.junit.Assert.*;

/** 同机 WS 识别不依赖远程 DNS。 */
public class LocalSdkEndpointTest {
    @Test public void loopbackNeedsLocalServiceButRemoteDoesNot() {
        for (String host : new String[]{"localhost", "LOCALHOST", "127.0.0.1", "127.0.0.2", "::1", "[::1]"}) {
            assertTrue(host, LocalSdkEndpoint.isLocal(host));
        }
        assertFalse(LocalSdkEndpoint.isLocal(null));
        assertFalse(LocalSdkEndpoint.isLocal("sdk.example.invalid"));
        assertFalse(LocalSdkEndpoint.isLocal("192.0.2.10"));
    }

    @Test public void localNetworkInterfaceAddressAlsoNeedsService() throws Exception {
        java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return;
        for (NetworkInterface network : Collections.list(interfaces)) {
            for (InetAddress address : Collections.list(network.getInetAddresses())) {
                assertTrue(LocalSdkEndpoint.isLocal(address.getHostAddress()));
            }
        }
    }
}
