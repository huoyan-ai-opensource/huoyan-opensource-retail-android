package cc.huoyan.sdk.client.session;
import cc.huoyan.sdk.client.*;
import cc.huoyan.sdk.client.testing.SdkChannelFakes;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
/** 通道/端点变化只释放旧连接，不迁移或重放请求。 */
public class SdkSessionTest {
    @Test public void switchAndEndpointReplacementCloseOnlyOldClient() {
        List<String> closed = new ArrayList<>();
        SdkSession session = new SdkSession(SdkConfig.aidl(), config -> new SdkChannelFakes.FakeSdkChannelBridge() {
            @Override public void close() { closed.add(config.getChannel().name()); }
        });
        HuoyanSdkClient first = session.getClient();
        session.configure(SdkConfig.aidl()); assertSame(first,session.getClient());
        session.configure(SdkConfig.websocket("ws://192.0.2.1:18086/ws"));
        HuoyanSdkClient second = session.getClient(); assertNotSame(first,second);
        assertFalse(session.getConfig().requiresLocalShell());
        session.configure(SdkConfig.websocket("ws://127.0.0.1:18086/ws"));
        assertNotSame(second,session.getClient()); assertTrue(session.getConfig().requiresLocalShell());
        assertEquals(Arrays.asList("AIDL","WS"),closed);
        session.close(); session.close(); assertEquals(3,closed.size());
    }
}
