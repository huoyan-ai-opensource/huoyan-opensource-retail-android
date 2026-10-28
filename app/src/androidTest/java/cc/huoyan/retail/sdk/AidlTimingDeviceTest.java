package cc.huoyan.retail.sdk;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.sdk.client.model.RecognizeResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 在同一张已保存的真实相机帧上交替比较快照路径与缓冲区复用路径，不修改学习数据。 */
@RunWith(AndroidJUnit4.class)
public class AidlTimingDeviceTest {
    /** 预热后采样二十对请求，验证完整回执一致并记录客户端总耗时。 */
    @Test public void compareSavedCameraFrame() {
        File reference = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                "bitmap-jpeg-ab/reference.png");
        assumeTrue("需先由 BitmapJpegDeviceTest 保存真实相机帧", reference.isFile());
        Bitmap bitmap = BitmapFactory.decodeFile(reference.getAbsolutePath());
        assertNotNull(bitmap);
        HuoyanSdkClient client = HuoyanSdkClient.create(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                SdkConfig.aidl().withLog((tag, message) -> Log.d(tag, message)));
        try {
            client.connect();
            long deadline = SystemClock.elapsedRealtime() + 10000;
            while (!client.isConnected() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
            assertTrue("AIDL 已连接", client.isConnected());
            assertEquals(0, client.initialize().getCode());
            assertEquals("READY", SdkDeviceReadiness.await(client).getSdkState());
            for (int round = -3; round < 20; round++) {
                RecognizeResult baseline = null;
                for (int step = 0; step < 2; step++) {
                    boolean fast = ((round + step) & 1) == 0;
                    long started = System.nanoTime();
                    RecognizeResult result = fast ? client.recognize(bitmap)
                            : client.recognize(client.image(bitmap), 0);
                    long elapsedUs = (System.nanoTime() - started) / 1000;
                    assertNotNull(result);
                    assertEquals(result.getReason(), 0, result.getCode());
                    if (baseline != null) assertEquals("同帧结果必须保真", baseline, result);
                    baseline = result;
                    if (round >= 0) Log.i("AidlTimingAB", "[Biz] aidl_timing_ab round=" + round
                            + " mode=" + (fast ? "reused" : "snapshot") + " totalUs=" + elapsedUs
                            + " items=" + result.getItems().size()
                            + " width=" + bitmap.getWidth() + " height=" + bitmap.getHeight());
                }
            }
        } finally { client.close(); bitmap.recycle(); }
    }
}
