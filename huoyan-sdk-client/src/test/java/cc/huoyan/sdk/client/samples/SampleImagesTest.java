package cc.huoyan.sdk.client.samples;
import cc.huoyan.sdk.client.testing.SdkChannelFakes;
import cc.huoyan.sdk.client.model.ItemClazzList;
import org.junit.Test;
import static org.junit.Assert.*;
/** 样本缓存必须跟随会话身份与失效代次。 */
public class SampleImagesTest {
    @Test public void sameClassOnDifferentEndpointsDoesNotShareThumbnail() {
        SampleImages cache = new SampleImages(30000,3);
        SdkChannelFakes.FakeSdkChannelBridge a = client("https://a.test/1"), b = client("https://b.test/1");
        assertEquals("https://a.test/1",cache.thumbnail("1",a));
        long version = cache.version();
        assertEquals("https://b.test/1",cache.thumbnail("1",b));
        assertTrue(cache.version() > version);
    }
    @Test public void invalidationDuringQueryRejectsLateCacheUpdate() {
        SampleImages cache = new SampleImages(30000,3);
        int[] queries = {0};
        SdkChannelFakes.FakeSdkChannelBridge client = new SdkChannelFakes.FakeSdkChannelBridge() {
            @Override public boolean isConnected() { return true; }
            @Override public ItemClazzList listClasses(int page,int size) {
                if (++queries[0] == 1) cache.invalidate();
                return new ItemClazzList(0,null,new String[]{"1"},new long[]{1},1,1,size,1,new String[]{"https://a.test/1"});
            }
        };
        cache.refreshAddresses(client);
        cache.refreshAddresses(client);
        assertEquals(2,queries[0]);
        cache.refreshAddresses(client);
        assertEquals(2,queries[0]);
    }
    private SdkChannelFakes.FakeSdkChannelBridge client(String url) {
        return new SdkChannelFakes.FakeSdkChannelBridge() {
            @Override public boolean isConnected() { return true; }
            @Override public ItemClazzList listClasses(int page,int size) {
                return new ItemClazzList(0,null,new String[]{"1"},new long[]{1},1,1,size,1,new String[]{url});
            }
        };
    }
}
