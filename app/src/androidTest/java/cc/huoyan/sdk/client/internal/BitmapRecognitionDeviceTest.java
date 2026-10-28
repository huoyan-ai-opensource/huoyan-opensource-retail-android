package cc.huoyan.sdk.client.internal;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import cc.huoyan.app.aidl.ImageRequestParcel;
import cc.huoyan.app.aidl.RecognizeResultParcel;
import cc.huoyan.sdk.client.SdkImage;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** 真机验证 RGB 快速路径的像素保真、尺寸切换和在途缓冲区所有权。 */
@RunWith(AndroidJUnit4.class)
public class BitmapRecognitionDeviceTest {
    /** 校验不同颜色和尺寸的输出，固定尺寸时复用缓冲区。 */
    @Test public void matchesSnapshotAcrossSizesAndReusesBuffer() {
        AtomicReference<Bitmap> expected = new AtomicReference<>();
        AtomicReference<byte[]> first = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        DefaultSdkClient client = client((method, args) -> {
            if (method.equals("createImageRequest")) {
                byte[] rgb = (byte[]) args[0];
                assertArrayEquals(SdkImage.rgb888(expected.get()).getBytes(), rgb);
                if (calls.get() == 0) first.set(rgb);
                if (calls.get() == 1) assertSame(first.get(), rgb);
                calls.incrementAndGet();
                return new ImageRequestParcel(null, rgb, rgb.length, (int) args[1], (int) args[2], 1);
            }
            if (method.equals("recognize")) {
                assertEquals(0, args[1]);
                return new RecognizeResultParcel(0, null, Collections.emptyList());
            }
            return null;
        });
        for (int width : new int[]{3, 3, 7, 2}) {
            Bitmap bitmap = Bitmap.createBitmap(width, 2, Bitmap.Config.ARGB_8888);
            try {
                for (int y = 0; y < 2; y++) for (int x = 0; x < width; x++) {
                    bitmap.setPixel(x, y, 0xff000000 | (x * 71 << 16) | (y * 83 << 8) | calls.get() * 53);
                }
                expected.set(bitmap);
                assertEquals(0, client.recognize(bitmap).getCode());
                assertFalse(bitmap.isRecycled());
            } finally { bitmap.recycle(); }
        }
        assertEquals(4, calls.get());
    }

    /** 并发请求须等前一请求消费完内联字节后才能覆盖缓冲区。 */
    @Test public void concurrentFrameCannotOverwriteInFlightInlineBytes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        DefaultSdkClient client = client((method, args) -> {
            if (method.equals("createImageRequest")) {
                byte[] rgb = (byte[]) args[0];
                return new ImageRequestParcel(null, rgb, rgb.length, 2, 2, 1);
            }
            if (method.equals("recognize")) {
                byte[] rgb = ((ImageRequestParcel) args[0]).getBytes();
                byte[] before = rgb.clone();
                if (calls.incrementAndGet() == 1) {
                    entered.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    assertArrayEquals(before, rgb);
                }
                return new RecognizeResultParcel(0, null, Collections.emptyList());
            }
            return null;
        });
        Bitmap red = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Bitmap blue = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        red.eraseColor(0xffff0000); blue.eraseColor(0xff0000ff);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = executor.submit(() -> client.recognize(red));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<?> two = executor.submit(() -> { secondStarted.countDown(); return client.recognize(blue); });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> two.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            one.get(5, TimeUnit.SECONDS); two.get(5, TimeUnit.SECONDS);
            assertEquals(2, calls.get());
        } finally {
            release.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
            red.recycle(); blue.recycle();
        }
    }

    private interface Call { Object invoke(String method, Object[] args) throws Exception; }
    private DefaultSdkClient client(Call call) {
        SdkChannelBridge transport = (SdkChannelBridge) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SdkChannelBridge.class},
                (proxy, method, args) -> call.invoke(method.getName(), args));
        return new DefaultSdkClient(transport, false, "1.2.3");
    }
}
