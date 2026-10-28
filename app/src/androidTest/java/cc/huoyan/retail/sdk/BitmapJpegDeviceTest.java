package cc.huoyan.retail.sdk;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.device.camera.CameraFrame;
import cc.huoyan.retail.device.camera.NV21ToBitmap;

import cc.huoyan.retail.common.FileUtil;
import cc.huoyan.sdk.client.model.*;
import cc.huoyan.sdk.client.SdkImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.junit.Assert.*;

/** 同帧真机 A/B：真实相机捕获，保留尺寸及方向，错误回执不计为识别性能样本。 */
@RunWith(AndroidJUnit4.class)
public class BitmapJpegDeviceTest {
    /** 比较 RGB、旧 JPEG 和直接 JPEG 编码，并在可用真实 SDK 上交替识别。 */
    @Test
    public void compareSameFrame() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        File dir = new File(instrumentation.getTargetContext().getFilesDir(), "bitmap-jpeg-ab");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        JSONObject report = new JSONObject();
        JSONArray preparation = new JSONArray();
        JSONArray attempts = new JSONArray();
        JSONArray samples = new JSONArray();
        report.put("preparation", preparation).put("endpoints", attempts).put("samples", samples);
        Bitmap crop = null;
        HuoyanSdkClient bridge = null;
        try {
            Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainActivity activity = (MainActivity) instrumentation.startActivitySync(intent);
            Field field = MainActivity.class.getDeclaredField("latestCameraFrame");
            field.setAccessible(true);
            CameraFrame frame = null;
            long deadline = SystemClock.elapsedRealtime() + 20000;
            while (frame == null && SystemClock.elapsedRealtime() < deadline) {
                frame = (CameraFrame) field.get(activity);
                if (frame == null) SystemClock.sleep(100);
            }
            assertNotNull("真实 USB 摄像头帧", frame);
            byte[] nv21 = Arrays.copyOf(frame.getNv(), frame.getNv().length);
            int width = frame.getWidth(), height = frame.getHeight();
            Method stop = MainActivity.class.getDeclaredMethod("stopDetect");
            stop.setAccessible(true);
            instrumentation.runOnMainSync(() -> {
                try { stop.invoke(activity); } catch (Exception e) { throw new RuntimeException(e); }
            });
            NV21ToBitmap converter = new NV21ToBitmap(instrumentation.getTargetContext());
            Bitmap full = converter.nv21ToBitmap(nv21, width, height);
            try {
                Bitmap originalCrop = FileUtil.corpRecognizeBitmap(full);
                crop = originalCrop.copy(Bitmap.Config.ARGB_8888, false);
                if (originalCrop != full) originalCrop.recycle();
            } finally { full.recycle(); converter.release(); }
            report.put("sourceWidth", width).put("sourceHeight", height)
                    .put("width", crop.getWidth()).put("height", crop.getHeight());
            try (FileOutputStream out = new FileOutputStream(new File(dir, "reference.png"))) {
                crop.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            instrumentation.runOnMainSync(activity::finish);
            SystemClock.sleep(1000);
            HuoyanSdkClient encoder = HuoyanSdkClient.create(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(), cc.huoyan.sdk.client.SdkConfig.websocket("ws://127.0.0.1:18086/ws"));
            for (int i = -2; i < 16; i++) {
                for (String mode : new String[]{"rgb", "old_jpeg90", "direct_jpeg90"}) {
                    long start = System.nanoTime();
                    SdkImage image = encode(encoder, crop, mode);
                    double elapsed = (System.nanoTime() - start) / 1000000.0;
                    if (i >= 0) preparation.put(new JSONObject().put("round", i).put("mode", mode)
                            .put("encodeMs", elapsed).put("bytes", image.getSize()));
                }
            }
            SdkImage jpeg = encoder.jpegImage(crop);
            try (FileOutputStream out = new FileOutputStream(new File(dir, "direct.jpg"))) {
                out.write(jpeg.getBytes());
            }
            Bitmap decoded = BitmapFactory.decodeByteArray(jpeg.getBytes(), 0, jpeg.getSize());
            assertNotNull(decoded);
            assertEquals(crop.getWidth(), decoded.getWidth());
            assertEquals(crop.getHeight(), decoded.getHeight());
            decoded.recycle();
            assertFalse("编码不回收调用方 Bitmap", crop.isRecycled());
            String hosts = InstrumentationRegistry.getArguments().getString("hosts", "127.0.0.1,192.168.1.114");
            for (String host : hosts.split(",")) {
                HuoyanSdkClient candidate = HuoyanSdkClient.create(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(), cc.huoyan.sdk.client.SdkConfig.websocket("ws://" + host + ":18086/ws"));
                candidate.connect();
                deadline = SystemClock.elapsedRealtime() + 12000;
                while (!candidate.isConnected() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
                BaseResult init = candidate.isConnected() ? candidate.initialize() : null;
                attempts.put(new JSONObject().put("host", host).put("connected", candidate.isConnected())
                        .put("code", init == null ? -999 : init.getCode())
                        .put("reason", init == null ? "no connection" : init.getReason()));
                if (init != null && init.getCode() == 0) {
                    bridge = candidate;
                    report.put("selectedHost", host);
                    break;
                }
                candidate.close();
            }
            assertNotNull("无可用真实 SDK，编码已测量，但完整识别尚未验证", bridge);
            for (int i = -2; i < 16; i++) {
                String[] modes = i % 2 == 0 ? new String[]{"rgb", "direct_jpeg90"}
                        : new String[]{"direct_jpeg90", "rgb"};
                for (String mode : modes) {
                    long start = System.nanoTime();
                    SdkImage image = encode(bridge, crop, mode);
                    double encodeMs = (System.nanoTime() - start) / 1000000.0;
                    RecognizeResult result = bridge.recognize(image, 0);
                    double totalMs = (System.nanoTime() - start) / 1000000.0;
                    JSONObject sample = new JSONObject().put("round", i).put("mode", mode)
                            .put("encodeMs", encodeMs).put("totalMs", totalMs).put("bytes", image.getSize())
                            .put("code", result == null ? -999 : result.getCode());
                    JSONArray items = new JSONArray();
                    if (result != null) for (RecognitionItem item : result.getItems()) {
                        items.put(new JSONObject().put("clazz", item.getClazz()).put("distance", item.getDistance())
                                .put("left", item.getLeft()).put("top", item.getTop())
                                .put("right", item.getRight()).put("bottom", item.getBottom()));
                    }
                    sample.put("items", items);
                    samples.put(sample);
                    assertNotNull(result);
                    assertEquals(result.getReason(), 0, result.getCode());
                }
            }
        } finally {
            if (bridge != null) bridge.close();
            if (crop != null) crop.recycle();
            try (FileOutputStream out = new FileOutputStream(new File(dir, "results.json"))) {
                out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    private static SdkImage encode(HuoyanSdkClient bridge, Bitmap bitmap, String mode) {
        if ("direct_jpeg90".equals(mode)) return bridge.jpegImage(bitmap);
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        byte[] rgb = SdkImage.rgb888(bitmap).getBytes();
        if ("rgb".equals(mode)) return bridge.image(rgb, width, height);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xff000000 | ((rgb[i * 3] & 255) << 16)
                    | ((rgb[i * 3 + 1] & 255) << 8) | (rgb[i * 3 + 2] & 255);
        }
        Bitmap reconstructed = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        try { return bridge.jpegImage(reconstructed); }
        finally { reconstructed.recycle(); }
    }
}
