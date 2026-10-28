package cc.huoyan.retail.sdk;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.device.camera.CameraFrame;
import cc.huoyan.retail.device.camera.NV21ToBitmap;
import cc.huoyan.retail.common.FileUtil;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.sdk.client.SdkImage;
import cc.huoyan.sdk.client.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.junit.Assert.*;

/** 使用业务端真实相机帧验证 JAR 公共 API；仅清理本测试新建的类别。 */
@RunWith(AndroidJUnit4.class)
public class JarClientFlowDeviceTest {
    /** 两个通道均完成真实检测、识别、学习、分页查询、遗忘和重新连接。 */
    @Test public void imageLearningRoundTripThroughJar() throws Exception {
        Bitmap bitmap = captureClientFrame();
        try {
            for (SdkConfig config : new SdkConfig[]{SdkConfig.aidl(),
                    SdkConfig.websocket("ws://127.0.0.1:18086/ws")}) {
                verify(config, bitmap);
            }
        } finally {
            bitmap.recycle();
        }
    }

    private void verify(SdkConfig config, Bitmap bitmap) {
        HuoyanSdkClient client = create(config);
        String clazz = "ModelTest" + System.currentTimeMillis();
        boolean ownsClass = false;
        try {
            connect(client);
            ItemClazzImagePage initial = client.listImages(clazz, 1, 100);
            assertNotNull(initial); assertEquals(initial.getReason(), 0, initial.getCode());
            assertEquals("禁止使用已有类别进行测试", 0, initial.getTotal());
            ownsClass = true;
            SdkImage frame = client.image(bitmap);
            DetectResult detection = client.detect(frame);
            assertNotNull(detection); assertEquals(detection.getReason(), 0, detection.getCode());
            assertFalse("真实画面必须有可检测商品", detection.getBoxes().isEmpty());
            RecognizeResult recognition = client.recognize(frame);
            assertNotNull(recognition); assertEquals(recognition.getReason(), 0, recognition.getCode());
            LearnResult learned = client.learn(frame, clazz);
            assertNotNull(learned); assertEquals(learned.getReason(), 0, learned.getCode());
            Log.i("JarClientFlow", "[Biz] jar_learn=" + config.getChannel() + " uid=" + learned.getUid());
            assertTrue("必须实际写入学习样本", learned.getUid() > 0);
            ItemClazzImagePage images = client.listImages(clazz, 1, 100);
            assertNotNull(images); assertEquals(0, images.getCode());
            assertTrue(images.getItems().stream().anyMatch(item -> item.getUid() == learned.getUid()));
            verifyImage(images.getItems().stream().filter(item -> item.getUid() == learned.getUid())
                    .findFirst().get());
            BaseResult forgotten = client.forgetSample(learned.getUid());
            assertNotNull(forgotten); assertEquals(forgotten.getReason(), 0, forgotten.getCode());
            ItemLearnBatch batch = client.batchLearn(frame, clazz);
            assertNotNull(batch); assertEquals(batch.getReason(), 0, batch.getCode());
            assertTrue("批量学习必须至少写入一个样本", batch.getBoxes().stream()
                    .anyMatch(box -> box.getCode() == 0 && box.getUid() > 0));
            RecognizeResult afterLearning = client.recognize(frame);
            assertNotNull(afterLearning); assertEquals(afterLearning.getReason(), 0, afterLearning.getCode());
            assertTrue("同一图像应识别出新学分类", afterLearning.getItems().stream()
                    .anyMatch(item -> clazz.equals(item.getClazz())));
            long count = client.listImages(clazz, 1, 100).getTotal();
            assertTrue(count > 0);
            client.close();
            assertFalse(client.isConnected());
            client = create(config);
            connect(client);
            assertEquals("新客户端能读到 SDK 保存的样本", count, client.listImages(clazz, 1, 100).getTotal());
            Log.i("JarClientFlow", "[Biz] jar_flow=" + config.getChannel()
                    + " detect=" + detection.getBoxes().size() + " recognize=" + recognition.getItems().size()
                    + " recognized_after_learning=" + afterLearning.getItems().size() + " learn_uid=" + learned.getUid() + " batch_samples=" + count + " reconnect=ok");
        } catch (AssertionError | RuntimeException failure) {
            Log.e("JarClientFlow", "[Biz] jar_failure=" + config.getChannel(), failure);
            throw failure;
        } finally {
            try {
                if (ownsClass) {
                    if (!client.isConnected()) {
                        client.close(); client = create(config); connect(client);
                    }
                    ItemClazzImagePage beforeCleanup = client.listImages(clazz, 1, 100);
                    assertNotNull(beforeCleanup); assertEquals(0, beforeCleanup.getCode());
                    if (beforeCleanup.getTotal() > 0) {
                        BaseResult removed = client.forgetClass(clazz);
                        assertNotNull(removed); assertEquals(removed.getReason(), 0, removed.getCode());
                    }
                    ItemClazzImagePage empty = client.listImages(clazz, 1, 100);
                    assertNotNull(empty); assertEquals(0, empty.getCode()); assertEquals(0, empty.getTotal());
                    Log.i("JarClientFlow", "[Biz] jar_cleanup=" + config.getChannel() + " clazz=" + clazz + " remaining=0");
                }
            } finally { client.close(); }
        }
    }

    private HuoyanSdkClient create(SdkConfig config) {
        return HuoyanSdkClient.create(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                config.withLog((tag, message) -> Log.d(tag, message)));
    }

    private void verifyImage(ItemClazzImage image) {
        assertTrue("学习样本必须有可读取图片", image.isImageAvailable());
        assertNotNull(image.getImageUrl());
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(image.getImageUrl()).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            assertEquals("SDK 图片应可下载", 200, connection.getResponseCode());
            try (java.io.InputStream stream = connection.getInputStream()) {
                Bitmap decoded = android.graphics.BitmapFactory.decodeStream(stream);
                assertNotNull("SDK 图片必须可解码", decoded);
                Log.i("JarClientFlow", "[Biz] jar_image=ok uid=" + image.getUid()
                        + " width=" + decoded.getWidth() + " height=" + decoded.getHeight());
                decoded.recycle();
            }
        } catch (java.io.IOException e) {
            throw new AssertionError("SDK 图片读取失败", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void connect(HuoyanSdkClient client) {
        client.connect();
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (!client.isConnected() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
        assertTrue("SDK 连接超时", client.isConnected());
        BaseResult init = client.initialize();
        assertNotNull(init); assertEquals(init.getReason(), 0, init.getCode());
        StatusSnapshot status = SdkDeviceReadiness.await(client);
        assertNotNull(status); assertEquals("READY", status.getSdkState());
    }

    private Bitmap captureClientFrame() throws Exception {
        Instrumentation ins = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = (MainActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        NV21ToBitmap converter = new NV21ToBitmap(ins.getTargetContext());
        try {
            Field latest = MainActivity.class.getDeclaredField("latestCameraFrame"); latest.setAccessible(true);
            CameraFrame frame = null;
            long deadline = SystemClock.elapsedRealtime() + 20000;
            while (frame == null && SystemClock.elapsedRealtime() < deadline) {
                frame = (CameraFrame) latest.get(activity);
                if (frame == null) SystemClock.sleep(100);
            }
            assertNotNull("业务端须提供真实相机帧", frame);
            byte[] bytes = Arrays.copyOf(frame.getNv(), frame.getNv().length);
            Bitmap full = converter.nv21ToBitmap(bytes, frame.getWidth(), frame.getHeight());
            Bitmap crop = FileUtil.corpRecognizeBitmap(full);
            try { return crop.copy(Bitmap.Config.ARGB_8888, false); }
            finally { if (crop != full) crop.recycle(); full.recycle(); }
        } finally {
            Method stop = MainActivity.class.getDeclaredMethod("stopDetect"); stop.setAccessible(true);
            ins.runOnMainSync(() -> {
                try { stop.invoke(activity); activity.finish(); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            converter.release();
        }
    }
}
