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
import java.util.*;
import static org.junit.Assert.*;

/** 同一台真机、同一原始相机帧，交替比较两通道两编码的识别和检测完整耗时。 */
@RunWith(AndroidJUnit4.class)
public class JpegChannelsDeviceTest {
    /** 每格 3 次预热、24 次测量；失败回执立即报错并保留已有数据。 */
    @Test
    public void compareRecognitionAndDetection() throws Exception {
        Instrumentation ins = InstrumentationRegistry.getInstrumentation();
        File dir = new File(ins.getTargetContext().getFilesDir(), "jpeg-channels-ab");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        JSONObject report = new JSONObject();
        JSONArray samples = new JSONArray();
        report.put("samples", samples).put("ws", "ws://127.0.0.1:18086/ws")
                .put("recognitionIncludesHash", true).put("warmupPerCell", 3).put("measuredPerCell", 24);
        HuoyanSdkClient aidl = HuoyanSdkClient.create(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(), cc.huoyan.sdk.client.SdkConfig.aidl());
        HuoyanSdkClient ws = HuoyanSdkClient.create(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(), cc.huoyan.sdk.client.SdkConfig.websocket("ws://" + "127.0.0.1" + ":18086/ws"));
        NV21ToBitmap converter = new NV21ToBitmap(ins.getTargetContext());
        try {
            MainActivity activity = (MainActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                    MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Field field = MainActivity.class.getDeclaredField("latestCameraFrame");
            field.setAccessible(true);
            CameraFrame frame = null;
            long deadline = SystemClock.elapsedRealtime() + 20000;
            while (frame == null && SystemClock.elapsedRealtime() < deadline) {
                frame = (CameraFrame) field.get(activity);
                if (frame == null) SystemClock.sleep(100);
            }
            assertNotNull("必须捕获真实 USB 相机帧", frame);
            byte[] nv21 = Arrays.copyOf(frame.getNv(), frame.getNv().length);
            int width = frame.getWidth(), height = frame.getHeight();
            Method stop = MainActivity.class.getDeclaredMethod("stopDetect");
            stop.setAccessible(true);
            ins.runOnMainSync(() -> {
                try { stop.invoke(activity); activity.finish(); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            SystemClock.sleep(1500);
            SdkChannelManager.getInstance().getClient().close();
            report.put("sourceWidth", width).put("sourceHeight", height);
            for (HuoyanSdkClient bridge : new HuoyanSdkClient[]{aidl, ws}) {
                bridge.connect();
                deadline = SystemClock.elapsedRealtime() + 15000;
                while (!bridge.isConnected() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
                assertTrue("通道连接 " + bridge.getClass().getSimpleName(), bridge.isConnected());
                BaseResult init = bridge.initialize();
                assertNotNull(init);
                StatusSnapshot status = bridge.getStatus();
                assertNotNull(status);
                assertEquals(0, status.getCode());
                assertEquals("READY", status.getSdkState());
                assertEquals(Boolean.TRUE, status.getModelReady());
            }
            Bitmap full = converter.nv21ToBitmap(nv21, width, height);
            Bitmap crop = FileUtil.corpRecognizeBitmap(full);
            try {
                report.put("width", crop.getWidth()).put("height", crop.getHeight());
                try (FileOutputStream out = new FileOutputStream(new File(dir, "reference.png"))) {
                    crop.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                SdkImage a = aidl.jpegImage(crop);
                SdkImage w = ws.jpegImage(crop);
                try {
                    assertEquals(SdkImage.FORMAT_JPEG, a.getFormat());
                    byte[] actual = a.getBytes();
                    assertArrayEquals("两个通道必须发送相同的 JPEG 字节", w.getBytes(), actual);
                    Bitmap decoded = BitmapFactory.decodeByteArray(actual, 0, actual.length);
                    assertNotNull(decoded);
                    assertEquals(crop.getWidth(), decoded.getWidth());
                    assertEquals(crop.getHeight(), decoded.getHeight());
                    decoded.recycle();
                    report.put("identicalJpegBytes", true).put("jpegBytes", actual.length);
                } finally { /* 图像快照无 native 资源，调用方无需释放。 */ }
            } finally { if (crop != full) crop.recycle(); full.recycle(); }
            report.put("startEpochMs", System.currentTimeMillis());
            for (int round = -3; round < 24; round++) {
                List<String> cells = new ArrayList<>(Arrays.asList("aidl/jpeg90/recognize", "ws/jpeg90/recognize",
                        "aidl/rgb/recognize", "ws/rgb/recognize", "aidl/jpeg90/detect", "ws/jpeg90/detect",
                        "aidl/rgb/detect", "ws/rgb/detect"));
                Collections.rotate(cells, Math.floorMod(round, cells.size()));
                if (round % 2 != 0) Collections.reverse(cells);
                for (String cell : cells) {
                    String[] parts = cell.split("/");
                    HuoyanSdkClient bridge = "aidl".equals(parts[0]) ? aidl : ws;
                    Bitmap bitmap = null, cropped = null;
                    SdkImage image = null;
                    long start = System.nanoTime();
                    try {
                        bitmap = converter.nv21ToBitmap(nv21, width, height);
                        cropped = FileUtil.corpRecognizeBitmap(bitmap);
                        long prepared = System.nanoTime();
                        if ("jpeg90".equals(parts[1])) image = bridge.jpegImage(cropped);
                        else {
                            int cw = cropped.getWidth(), ch = cropped.getHeight();
                            int[] pixels = new int[cw * ch];
                            cropped.getPixels(pixels, 0, cw, 0, 0, cw, ch);
                            image = SdkImage.rgb888(cropped);
                        }
                        assertNotNull(image);
                        int bytes = image.getSize();
                        long encoded = System.nanoTime();
                        int code;
                        String reason;
                        JSONArray items = new JSONArray();
                        long returned;
                        if ("recognize".equals(parts[2])) {
                            RecognizeResult result = bridge.recognize(image, 0);
                            image = null; // AIDL 同步返回时桥已释放载体。
                            returned = System.nanoTime();
                            assertNotNull(result);
                            code = result.getCode(); reason = result.getReason();
                            for (RecognitionItem item : result.getItems()) items.put(new JSONObject()
                                    .put("clazz", item.getClazz()).put("distance", item.getDistance())
                                    .put("left", item.getLeft()).put("top", item.getTop())
                                    .put("right", item.getRight()).put("bottom", item.getBottom()));
                        } else {
                            DetectResult result = bridge.detect(image);
                            image = null;
                            returned = System.nanoTime();
                            assertNotNull(result);
                            code = result.getCode(); reason = result.getReason();
                            for (DetectBox box : result.getBoxes()) items.put(new JSONObject()
                                    .put("left", box.getX()).put("top", box.getY())
                                    .put("right", box.getX() + box.getW()).put("bottom", box.getY() + box.getH())
                                    .put("score", box.getScore()));
                        }
                        JSONObject sample = new JSONObject().put("round", round).put("cell", cell)
                                .put("prepareMs", ms(prepared - start)).put("encodeMs", ms(encoded - prepared))
                                .put("callMs", ms(returned - encoded)).put("encodeAndCallMs", ms(returned - prepared))
                                .put("totalMs", ms(returned - start)).put("bytes", bytes)
                                .put("code", code).put("items", items);
                        samples.put(sample);
                        assertEquals(cell + ": " + reason, 0, code);
                    } finally {
                        if (cropped != null && cropped != bitmap) cropped.recycle();
                        if (bitmap != null) bitmap.recycle();
                    }
                }
                android.util.Log.i("JpegChannelsTest", "round=" + round + " samples=" + samples.length());
            }
            report.put("endEpochMs", System.currentTimeMillis());
        } finally {
            aidl.close(); ws.close(); converter.release();
            try (FileOutputStream out = new FileOutputStream(new File(dir, "results.json"))) {
                out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    private static double ms(long nanos) { return nanos / 1_000_000.0; }
}
