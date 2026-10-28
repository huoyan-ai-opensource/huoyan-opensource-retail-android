package cc.huoyan.retail.sdk;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SharedMemory;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import cc.huoyan.sdk.client.model.DetectResult;
import cc.huoyan.sdk.client.SdkImage;
import cc.huoyan.sdk.client.model.RecognizeResult;
import cc.huoyan.sdk.client.model.StatusSnapshot;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

/** 验证业务共用工厂实际选择的格式及真实 SDK 对识别、检测两种调用的接收能力。 */
@RunWith(AndroidJUnit4.class)
public class SdkImageFormatDeviceTest {
    /** AIDL 完整 RGB888 共享内存和 WS JPEG 都能执行 recognize / detect。 */
    @Test
    public void channelDefaultsReachRealSdk() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(1076, 756, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(17, 119, 204));
        try {
            for (HuoyanSdkClient bridge : new HuoyanSdkClient[]{HuoyanSdkClient.create(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(), cc.huoyan.sdk.client.SdkConfig.aidl()), HuoyanSdkClient.create(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(), cc.huoyan.sdk.client.SdkConfig.websocket("ws://127.0.0.1:18086/ws"))}) {
                try {
                    bridge.connect();
                    long deadline = SystemClock.elapsedRealtime() + 15000;
                    while (!bridge.isConnected() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
                    assertTrue(bridge.isConnected());
                    bridge.initialize();
                    StatusSnapshot status = SdkDeviceReadiness.await(bridge);
                    assertNotNull(status);
                    assertEquals(0, status.getCode());
                    assertEquals("READY", status.getSdkState());
                    assertEquals(Boolean.TRUE, status.getModelReady());
                    for (boolean recognize : new boolean[]{true, false}) {
                        SdkImage image = bridge.image(bitmap);
                        assertNotNull(image);
                        try {
                            assertEquals(1076, image.getWidth());
                            assertEquals(756, image.getHeight());
                            if (image.getFormat() == SdkImage.FORMAT_RGB888) {
                                assertEquals(SdkImage.FORMAT_RGB888, image.getFormat());
                                assertEquals(1076 * 756 * 3, image.getSize());
                                ByteBuffer bytes = ByteBuffer.wrap(image.getBytes());
                                {
                                    for (int i = 0; i < image.getSize(); i += 3) {
                                        assertEquals(17, bytes.get() & 255);
                                        assertEquals(119, bytes.get() & 255);
                                        assertEquals(204, bytes.get() & 255);
                                    }
                                }
                            } else {
                                assertEquals(SdkImage.FORMAT_JPEG, image.getFormat());
                                assertEquals(255, image.getBytes()[0] & 255);
                                assertEquals(216, image.getBytes()[1] & 255);
                                assertTrue(image.getSize() < 1076 * 756 * 3);
                            }
                        } catch (Throwable failure) {
                            throw failure;
                        }
                        if (recognize) {
                            RecognizeResult result = bridge.recognize(image, 0);
                            assertNotNull(result);
                            assertEquals(result.getReason(), 0, result.getCode());
                        } else {
                            DetectResult result = bridge.detect(image);
                            assertNotNull(result);
                            assertEquals(result.getReason(), 0, result.getCode());
                        }
                        android.util.Log.i("SdkImageFormatTest", "channel=" + bridge.getClass().getSimpleName()
                                + " method=" + (recognize ? "recognize" : "detect")
                                + " format=" + image.getFormat() + " bytes=" + image.getSize() + " code=0");
                    }
                    assertFalse(bitmap.isRecycled());
                } finally { bridge.close(); }
            }
        } finally { bitmap.recycle(); }
    }
}
