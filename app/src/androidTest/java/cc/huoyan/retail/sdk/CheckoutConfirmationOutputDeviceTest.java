package cc.huoyan.retail.sdk;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.checkout.CheckoutRecognition;
import cc.huoyan.retail.checkout.GoodsVo;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.checkout.domain.CheckoutSession;
import cc.huoyan.retail.checkout.domain.SaleLine;
import cc.huoyan.retail.checkout.domain.SalePrintJob;
import cc.huoyan.retail.checkout.integration.CheckoutOutput;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.pricing.PricingMode;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkImage;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.Feedback;
import cc.huoyan.sdk.client.model.FeedbackItem;
import cc.huoyan.sdk.client.model.PhotoUploadResult;
import cc.huoyan.sdk.client.model.RecognitionItem;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 使用真实输出、图片文件及 SDK 反馈组装；仅替换 SDK 远程接口，不连接打印机或云端。 */
@RunWith(AndroidJUnit4.class)
public class CheckoutConfirmationOutputDeviceTest {
    /** 测试线程与主线程之间的调度入口。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    /** 不打印确认仍上传原图并提交保留、改品、删除反馈，完成后释放位图。 */
    @Test public void disabledPrinterStillUploadsPhotoAndFeedback() throws Exception {
        CountDownLatch uploaded = new CountDownLatch(1), completed = new CountDownLatch(1);
        AtomicInteger uploads = new AtomicInteger();
        AtomicReference<Feedback> sent = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        HuoyanSdkClient client = (HuoyanSdkClient) Proxy.newProxyInstance(HuoyanSdkClient.class.getClassLoader(),
                new Class<?>[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isConnected": return true;
                        case "photoImage": return SdkImage.encoded((byte[]) args[0], 0, 0, (Integer) args[1]);
                        case "uploadPhoto":
                            uploads.incrementAndGet();
                            return new PhotoUploadResult(0, null, "https://example.test/confirmation.jpg");
                        case "submitFeedback":
                            sent.set((Feedback) args[0]);
                            uploaded.countDown();
                            return new BaseResult(0, null);
                        case "connect": case "close": return null;
                        default: throw new AssertionError("Unexpected SDK call: " + method.getName());
                    }
                });
        SdkChannelManager manager = SdkChannelManager.createForTest(new SdkChannelManager.ChannelStore() {
            /** 本测试只使用内存 AIDL 通道标识。 */
            public String get() { return "aidl"; }
            /** 测试不写入通道偏好。 */
            public void put(String value) { }
        }, channel -> client);
        Field singleton = SdkChannelManager.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        try {
            singleton.set(null, manager);
            GoodsVo kept = row("A"), modified = row("B"), deleted = row("C");
            modified.getRecognitionSelection().get(0).select("D");
            modified.getGoods().setPlu("D");
            modified.getGoods().setGoodsId("D");
            CheckoutRecognition response = new CheckoutRecognition(Arrays.asList(kept.getRecognitionSelection().get(0),
                    modified.getRecognitionSelection().get(0), deleted.getRecognitionSelection().get(0)));
            CheckoutSession session = new CheckoutSession();
            session.setWeighingEnabled(false);
            session.lockRecognition();
            session.replaceLines(Collections.singletonList(new SaleLine("A", "测试商品", 100, 0, 1,
                    PricingMode.COUNT, false, false, null)));
            SalePrintJob job = session.beginPrint();
            Bitmap photo = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            ins.runOnMainSync(() -> new CheckoutOutput(ins.getTargetContext()).submit(job, false, response,
                    Arrays.asList(kept, modified), Collections.singletonList(deleted), photo, error -> {
                        failure.set(error);
                        completed.countDown();
                    }));
            assertTrue("输出应完成", completed.await(10, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertTrue("应提交 SDK 反馈", uploaded.await(10, TimeUnit.SECONDS));
            assertTrue(photo.isRecycled());
            assertEquals(1, uploads.get());
            assertEquals(3, sent.get().getItems().size());
            assertEquals(FeedbackItem.NORMAL, sent.get().getItems().get(0).getItemType());
            assertEquals(FeedbackItem.MODIFIED, sent.get().getItems().get(1).getItemType());
            assertEquals("D", sent.get().getItems().get(1).getResult().getClazz());
            assertEquals(FeedbackItem.DELETED, sent.get().getItems().get(2).getItemType());
        } finally {
            singleton.set(null, previous);
        }
    }

    /** 构造有 SDK ID 和原始识别框的计件商品，不写商品库。 */
    private GoodsVo row(String plu) {
        GoodsEntity goods = new GoodsEntity();
        goods.setPlu(plu); goods.setGoodsId(plu); goods.setName(plu); goods.setUnit(5); goods.setPrice(100);
        RecognitionSelection selection = new RecognitionSelection(new RecognitionItem(plu, 0, 1, 2, 20, 20));
        return new GoodsVo(1, 100, goods, Collections.singletonList(selection));
    }
}
