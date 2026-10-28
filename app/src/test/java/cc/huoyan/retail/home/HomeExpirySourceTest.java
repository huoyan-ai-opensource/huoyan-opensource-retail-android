package cc.huoyan.retail.home;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.CertInfo;
import cc.huoyan.sdk.client.model.StatusSnapshot;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

/** 验证有效授权才读取到期时间，失败不能使用旧证书或展示文本推算。 */
public class HomeExpirySourceTest {
    /** 读取原始秒级时间戳，失败和未激活都返回未知。 */
    @Test public void readsExpiryOnlyForValidActivatedStatus() {
        for (Boolean valid : new Boolean[]{true, false, null}) {
            for (int certificateCode : new int[]{0, -1}) {
                AtomicInteger calls = new AtomicInteger();
                HuoyanSdkClient client = (HuoyanSdkClient) Proxy.newProxyInstance(
                        HuoyanSdkClient.class.getClassLoader(), new Class<?>[]{HuoyanSdkClient.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "isConnected": return true;
                                case "getCloudStatus":
                                case "getCapabilities": return null;
                                case "getStatus": return new StatusSnapshot(0, null, null, "unparseable",
                                        null, null, null, null, "READY", valid, null, null, null, valid);
                                case "getCertInfo":
                                    calls.incrementAndGet();
                                    return new CertInfo(certificateCode, null, null, null, null,
                                            0, 0, 0, false, 0, false, 1800000000L);
                                default: throw new AssertionError(method.getName());
                            }
                        });
                HomePresenter.Status result = HomeStatusSource.read(client);
                boolean activated = Boolean.TRUE.equals(valid);
                assertEquals(activated ? 1 : 0, calls.get());
                assertEquals(activated && certificateCode == 0 ? 1800000000L : 0, result.expiresAt);
            }
        }
    }
}
