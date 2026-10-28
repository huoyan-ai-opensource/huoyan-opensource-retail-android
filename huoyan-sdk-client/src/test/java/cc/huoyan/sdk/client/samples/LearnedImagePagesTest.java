package cc.huoyan.sdk.client.samples;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import cc.huoyan.sdk.client.model.ItemClazzImagePage;
import cc.huoyan.sdk.client.model.ItemClazzImage;
import static org.junit.Assert.*;

/** 图片分页及缺图 UID 保留回归。 */
public class LearnedImagePagesTest {
    @Test(expected = IllegalStateException.class)
    public void finalPageNumberMustMatchEvenIfReportedAsLast() {
        cc.huoyan.sdk.client.testing.SdkChannelFakes.FakeSdkChannelBridge client = new cc.huoyan.sdk.client.testing.SdkChannelFakes.FakeSdkChannelBridge() {
            @Override public ItemClazzImagePage listImages(String clazz, int page, int size) {
                return new ItemClazzImagePage(0, null, 0, 9, size, 1, java.util.Collections.emptyList());
            }
        };
        LearnedImagePages.loadAll(client, "7");
    }

    @Test public void loadsEveryPageAndRetainsMissingImageUid() {
        HuoyanSdkClient bridge = (HuoyanSdkClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    int page = (int) args[1];
                    assertEquals("77", args[0]);
                    return new ItemClazzImagePage(0, null, 2, page, 100, 2,
                            Collections.singletonList(new ItemClazzImage(page, page == 1,
                                    page == 1 ? "http://192.168.1.2:43210/images/1" : null, "image/jpeg")));
                });
        List<ItemClazzImage> images = LearnedImagePages.loadAll(bridge, "77");
        assertEquals(2, images.size());
        assertEquals("http://192.168.1.2:43210/images/1", images.get(0).getImageUrl());
        assertEquals(2, images.get(1).getUid());
        assertFalse(images.get(1).isImageAvailable());
    }
    @Test(expected = IllegalStateException.class) public void failedPageDoesNotReturnPartialGallery() {
        HuoyanSdkClient bridge = (HuoyanSdkClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    int page = (int) args[1];
                    return page == 1 ? new ItemClazzImagePage(0, null, 2, 1, 100, 2,
                            Arrays.asList(new ItemClazzImage(1, false, null, null))) : null;
                });
        LearnedImagePages.loadAll(bridge, "77");
    }
    @Test public void switchingGoodsStopsFurtherPages() {
        java.util.concurrent.atomic.AtomicBoolean current = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        HuoyanSdkClient bridge = (HuoyanSdkClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    calls.incrementAndGet();
                    current.set(false);
                    return new ItemClazzImagePage(0, null, 2, 1, 100, 2,
                            Collections.singletonList(new ItemClazzImage(1, false, null, null)));
                });
        try { LearnedImagePages.loadAll(bridge, "77", current::get); fail("旧商品请求应取消"); }
        catch (java.util.concurrent.CancellationException expected) { }
        assertEquals(1, calls.get());
    }

    @Test public void hugeGalleryReadsOnlyMetadataAndNewestPage() {
        java.util.List<Integer> requested = new java.util.ArrayList<>();
        HuoyanSdkClient client = (HuoyanSdkClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    int page = (int) args[1];
                    requested.add(page);
                    assertEquals(24, args[2]);
                    return new ItemClazzImagePage(0, null, 24000, page, 24, 1000,
                            Collections.singletonList(new ItemClazzImage(page * 24L, false, null, null)));
                });
        ItemClazzImagePage newest = LearnedImagePages.loadNewestPage(client, "77", 24, () -> true);
        assertEquals(Arrays.asList(1, 1000), requested);
        assertEquals(24000, newest.getItems().get(0).getUid());
        LearnedImagePages.loadPage(client, "77", newest.getCurrent() - 1, 24, () -> true);
        assertEquals(Arrays.asList(1, 1000, 999), requested);
    }

    @Test public void switchingGoodsDiscardsFirstPageBeforeRequestingLast() {
        java.util.concurrent.atomic.AtomicBoolean current = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        HuoyanSdkClient client = (HuoyanSdkClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    calls.incrementAndGet();
                    current.set(false);
                    return new ItemClazzImagePage(0, null, 24000, 1, 24, 1000,
                            Collections.singletonList(new ItemClazzImage(1, false, null, null)));
                });
        try { LearnedImagePages.loadNewestPage(client, "77", 24, current::get); fail(); }
        catch (java.util.concurrent.CancellationException expected) { }
        assertEquals(1, calls.get());
    }

    @Test public void emptyGalleryNeedsOnlyOneRequest() {
        HuoyanSdkClient client = (HuoyanSdkClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    assertEquals(1, args[1]);
                    return new ItemClazzImagePage(0, null, 0, 1, 24, 0, Collections.emptyList());
                });
        assertTrue(LearnedImagePages.loadNewestPage(client, "77", 24, () -> true).getItems().isEmpty());
    }

    @Test(expected = IllegalStateException.class) public void wrongPageIsRejectedEvenAtEnd() {
        HuoyanSdkClient client = (HuoyanSdkClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{HuoyanSdkClient.class}, (proxy, method, args) ->
                        new ItemClazzImagePage(0, null, 24, 1, 24, 1,
                                Collections.singletonList(new ItemClazzImage(1, false, null, null))));
        LearnedImagePages.loadPage(client, "77", 2, 24, () -> true);
    }

    @Test public void previewsSkipMissingUrlsAndChooseNewestSamples() {
        List<ItemClazzImage> images = Arrays.asList(
                new ItemClazzImage(1, true, "http://device/images/1", "image/jpeg"),
                new ItemClazzImage(2, false, "http://device/images/2", "image/jpeg"),
                new ItemClazzImage(3, true, null, "image/jpeg"),
                new ItemClazzImage(4, true, "", "image/jpeg"),
                new ItemClazzImage(5, true, "/images/5", "image/jpeg"),
                new ItemClazzImage(Long.MAX_VALUE, true, "http://device/images/latest", "image/png"),
                new ItemClazzImage(7, true, "https://device/images/7", "image/jpeg"),
                new ItemClazzImage(6, true, "http://device/images/6", "image/jpeg"));
        assertEquals(Arrays.asList("http://device/images/latest", "https://device/images/7", "http://device/images/6"),
                LearnedImagePages.latestUrls(images, 3));
        assertTrue(LearnedImagePages.latestUrls(images, 0).isEmpty());
    }

    @Test public void imageUrlRejectsNullBlankAndRelativePaths() {
        assertFalse(LearnedImagePages.isHttpUrl(null));
        assertFalse(LearnedImagePages.isHttpUrl(""));
        assertFalse(LearnedImagePages.isHttpUrl("  "));
        assertFalse(LearnedImagePages.isHttpUrl("/images/1"));
        assertTrue(LearnedImagePages.isHttpUrl("http://device:12345/images/1"));
        assertTrue(LearnedImagePages.isHttpUrl("https://device/images/1"));
    }
}
