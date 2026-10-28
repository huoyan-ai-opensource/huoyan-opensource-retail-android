package cc.huoyan.sdk.client.samples;
import cc.huoyan.sdk.client.testing.SdkChannelFakes;

import org.junit.Test;
import cc.huoyan.sdk.client.model.ItemClazzList;
import static org.junit.Assert.*;

public class LearnedClazzPagesTest {
    @Test
    public void loadsEveryPageBeyondOneHundredClasses() {
        SdkChannelFakes.FakeSdkChannelBridge bridge = new SdkChannelFakes.FakeSdkChannelBridge() {
            @Override
            public ItemClazzList listClasses(int page, int size) {
                assertEquals(100, size);
                assertTrue(page == 1 || page == 2);
                int length = page == 1 ? 100 : 1;
                String[] classes = new String[length];
                long[] counts = new long[length];
                String[] thumbnails = new String[length];
                for (int i = 0; i < length; i++) {
                    classes[i] = Integer.toString((page - 1) * 100 + i + 1);
                    counts[i] = 3;
                    thumbnails[i] = "/images/" + classes[i];
                }
                return new ItemClazzList(0, null, classes, counts, 101, page, size, 2, thumbnails);
            }
        };
        ItemClazzList result = LearnedClazzPages.loadAll(bridge);
        assertEquals(101, result.getClazz().length);
        assertEquals("101", result.getClazz()[100]);
        assertEquals(3, result.getUidCount()[100]);
        assertEquals("/images/101", result.getThumbnails()[100]);
    }

    @Test
    public void secondPageFailureDoesNotPublishPartialSuccess() {
        ItemClazzList failure = new ItemClazzList(-111, "查询失败", null, null);
        SdkChannelFakes.FakeSdkChannelBridge bridge = new SdkChannelFakes.FakeSdkChannelBridge() {
            @Override
            public ItemClazzList listClasses(int page, int size) {
                return page == 1 ? new ItemClazzList(0, null, new String[]{"1"},
                        new long[]{2}, 2, page, size, 2, null) : failure;
            }
        };
        assertSame(failure, LearnedClazzPages.loadAll(bridge));
    }

    @Test
    public void wrongPageAndDisconnectAreNotEmptySuccess() {
        assertNull(LearnedClazzPages.loadAll(new SdkChannelFakes.FakeSdkChannelBridge()));
        assertEquals(-1, LearnedClazzPages.loadAll(new SdkChannelFakes.FakeSdkChannelBridge() {
            @Override
            public ItemClazzList listClasses(int page, int size) {
                return new ItemClazzList(0, null, null, null, 100, 9, size, 10, null);
            }
        }).getCode());
    }

    @Test
    public void emptyLibraryIsSuccessful() {
        ItemClazzList result = LearnedClazzPages.loadAll(new SdkChannelFakes.FakeSdkChannelBridge() {
            @Override
            public ItemClazzList listClasses(int page, int size) {
                return new ItemClazzList(0, null, null, null, 0, 1, size, 0, null);
            }
        });
        assertEquals(0, result.getCode());
        assertEquals(0, result.getClazz().length);
    }
}
