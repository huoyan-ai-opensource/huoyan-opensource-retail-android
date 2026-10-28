package cc.huoyan.retail.device.camera;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.checkout.CheckoutPresenter;
import cc.huoyan.retail.checkout.CheckoutRecognition;
import cc.huoyan.sdk.client.model.RecognizeResult;
import cc.huoyan.sdk.client.model.RecognitionItem;
import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.recognition.FrameChangeGate;
import cc.huoyan.retail.recognition.RecognitionLoop;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** C50D 真实预览取帧后受控回放，验证页面门控及真实 SDK 请求；不提交销售。 */
@RunWith(AndroidJUnit4.class)
public class FrameGateDeviceTest {
    /** 仪器线程执行识别，主线程仅操作页面生命周期。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 当前收银页，finally 关闭以释放相机。 */
    private MainActivity page;

    /** 最低档启动整轮、静止新帧续发、就绪唤醒、结果锁定及下一轮复位。 */
    @Test public void singleChangeStartsRoundAndStaticFramesContinueRealSdkRequests() throws Exception {
        SharedPreferences sp = ins.getTargetContext().getSharedPreferences("HuoYanApplication", Context.MODE_PRIVATE);
        Map<String, ?> before = new HashMap<>(sp.getAll());
        CameraController camera = CameraController.getInstance();
        AtomicReference<CameraFrame> live = new AtomicReference<>();
        try {
            // 使用最低档启动，取得真实帧后暂停循环并排空回调再受控回放。
            sp.edit().putBoolean(ModuleConfig.SP_WEIGHING_ENABLED, false)
                    .putBoolean(SPManager.SP_STABLE_RECOGNITION, true)
                    .putInt(SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL, 1).commit();
            camera.addListener("gateTestCapture", live::set);
            page = (MainActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(() -> live.get() != null, "真实摄像头必须交付 NV21 帧");
            await(() -> (Boolean) read(page, "sdkRecognitionReady"), "现场 SDK 模型必须可识别");
            ins.runOnMainSync(() -> invoke(page, "stopDetect"));
            String key = (String) read(page, "cameraListenerKey");
            Map<String, CameraController.FrameListener> listeners = (Map<String, CameraController.FrameListener>) read(camera, "listeners");
            CameraController.FrameListener callback = listeners.get(key);
            assertNotNull(callback);
            camera.removeListener(key);
            // 等真实帧线程经过两个回调边界，排空移除订阅前已进入的旧回调。
            java.util.concurrent.CountDownLatch drained = new java.util.concurrent.CountDownLatch(2);
            camera.addListener("gateTestDrain", frame -> drained.countDown());
            assertTrue("等待旧相机订阅退出", drained.await(5, java.util.concurrent.TimeUnit.SECONDS));
            camera.removeListener("gateTestDrain");
            ins.waitForIdleSync();
            CameraFrame captured = live.get();
            CameraFrame real = new CameraFrame(captured.getNv(), captured.getWidth(), captured.getHeight(),
                    (Long) read(camera, "generation"));
            assertTrue(camera.isCurrent(real));
            CheckoutPresenter presenter = (CheckoutPresenter) read(page, "presenter");
            ins.runOnMainSync(() -> {
                assertTrue(presenter.restart());
                invoke(page, "clearRecognitionData");
            });
            ins.runOnMainSync(() -> invoke(page, "sdkResumeInit"));
            ins.waitForIdleSync();
            assertEquals("连接状态不得显示成正在识别", "已连接SDK",
                    ((android.widget.TextView) page.findViewById(cc.huoyan.retail.R.id.recognizeText)).getText().toString());
            captureStatus("sdk-connected");
            assertEquals("最低档须换算成阈值 8", 8, read(page, "frameChangeThreshold"));
            write(page, "latestCameraFrame", null);
            write(page, "requestedFrame", null);
            ((FrameChangeGate) read(page, "frameChangeGate")).reset();
            CameraFrame baseline = withHashBits(real, 32);
            callback.onFrame(baseline);
            assertNull(read(page, "latestCameraFrame"));
            callback.onFrame(withHashBits(real, 32));
            assertNull("相同帧不放行", read(page, "latestCameraFrame"));
            callback.onFrame(withHashBits(real, 40));
            assertNull("距离等于阈值 8 不放行", read(page, "latestCameraFrame"));
            CameraFrame changed = withHashBits(real, 49);
            callback.onFrame(changed);
            assertSame("与上一帧距离 9 时放行", changed, read(page, "latestCameraFrame"));
            Method process = MainActivity.class.getDeclaredMethod("processLatestDetectFrame", BooleanSupplier.class);
            process.setAccessible(true);
            long sentBefore = (Long) read(page, "lastDetectRequestAt");
            Object outcome = process.invoke(page, (BooleanSupplier) () -> true);
            assertEquals("真实 SDK 请求必须成功", RecognitionLoop.Outcome.SUCCESS, outcome);
            assertSame(changed, read(page, "requestedFrame"));
            long sentAfter = (Long) read(page, "lastDetectRequestAt");
            assertTrue("超过阈值后必须收到 SDK 真实回执", sentAfter > sentBefore);
            assertEquals(RecognitionLoop.Outcome.IDLE, process.invoke(page, (BooleanSupplier) () -> true));
            assertEquals("同一帧不得重复请求", sentAfter, read(page, "lastDetectRequestAt"));
            CameraFrame unchanged = withHashBits(real, 49);
            callback.onFrame(unchanged);
            assertSame("本轮已启动，画面不变的新帧仍发布", unchanged, read(page, "latestCameraFrame"));
            assertEquals("静止新帧仍须收到真实 SDK 回执", RecognitionLoop.Outcome.SUCCESS,
                    process.invoke(page, (BooleanSupplier) () -> true));
            assertSame(unchanged, read(page, "requestedFrame"));
            assertTrue((Long) read(page, "lastDetectRequestAt") > sentAfter);
            ins.waitForIdleSync();
            ins.runOnMainSync(() -> invoke(page, "resetRecognitionResultWindow"));

            // 已有帧但 SDK 未就绪时休眠；就绪通知必须能消费同一帧，不等下一张帧。
            RecognitionLoop loop = (RecognitionLoop) read(page, "recognitionLoop");
            write(page, "sdkRecognitionReady", false);
            loop.start();
            CameraFrame readyFrame = withHashBits(real, 49);
            callback.onFrame(readyFrame);
            java.util.concurrent.ExecutorService worker = (java.util.concurrent.ExecutorService) read(loop, "executor");
            worker.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertSame("未就绪时不能提前消费待识别帧", unchanged, read(page, "requestedFrame"));
            write(page, "sdkRecognitionReady", true);
            loop.wakeUp();
            await(() -> read(page, "requestedFrame") == readyFrame, "就绪唤醒须发送已有静止帧");
            ins.waitForIdleSync();
            ins.runOnMainSync(() -> invoke(page, "stopDetect"));
            worker.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);

            // 真实 SDK 回执取决于现场商品；下面使用固定回执验证三次确认及锁定边界。
            write(page, "sdkRecognitionReady", false);
            CheckoutRecognition response = new CheckoutRecognition(new RecognizeResult(0, null,
                    java.util.Collections.singletonList(new RecognitionItem("round-test", 0, 1, 2, 3, 4))));
            Method success = MainActivity.class.getDeclaredMethod("handleDetectSuccess", CheckoutRecognition.class);
            success.setAccessible(true);
            ins.runOnMainSync(() -> {
                assertTrue(presenter.restart());
                invoke(page, "clearRecognitionData");
                loop.start();
                callback.onFrame(withHashBits(real, 32));
                callback.onFrame(withHashBits(real, 49));
                for (int i = 1; i <= 3; i++) {
                    CameraFrame stationary = withHashBits(real, 49);
                    callback.onFrame(stationary);
                    assertSame(stationary, read(page, "latestCameraFrame"));
                    try { success.invoke(page, response); }
                    catch (Exception error) { throw new AssertionError(error); }
                    assertEquals("第三次一致回执才锁定", i == 3, presenter.state().recognitionLocked);
                }
                assertEquals("锁定停止调度", false, read(loop, "running"));
                Object lockedFrame = read(page, "latestCameraFrame");
                callback.onFrame(withHashBits(real, 32));
                assertSame("锁定后相机事件不得续发", lockedFrame, read(page, "latestCameraFrame"));
                invoke(page, "restartRecognition");
                callback.onFrame(withHashBits(real, 49));
                callback.onFrame(withHashBits(real, 49));
                assertNull("新一轮静止帧不能沿用上一轮触发状态", read(page, "latestCameraFrame"));
                callback.onFrame(withHashBits(real, 32));
                assertNotNull("新变化重新启动一轮", read(page, "latestCameraFrame"));
            });
            write(page, "stableRecognition", false);
            callback.onFrame(unchanged);
            assertSame("关闭开关直接发布帧", unchanged, read(page, "latestCameraFrame"));
            write(page, "stableRecognition", true);
            ((FrameChangeGate) read(page, "frameChangeGate")).reset();
            ins.runOnMainSync(() -> {
                presenter.setWeighingEnabled(true);
                presenter.onWeight(new java.math.BigDecimal("0.500"), java.math.BigDecimal.ZERO, true);
            });
            CameraFrame weighing = withHashBits(real, 49);
            callback.onFrame(weighing);
            assertSame("称重识别轮次绕过画面变化门控", weighing, read(page, "latestCameraFrame"));
            loop.stop();
            worker.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
            write(page, "sdkRecognitionReady", true);
            ins.runOnMainSync(() -> {
                try {
                    Method failure = MainActivity.class.getDeclaredMethod("handleDetectError", int.class);
                    failure.setAccessible(true);
                    failure.invoke(page, cc.huoyan.sdk.client.HuoyanSdkClient.RESULT_UNKNOWN);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            ins.waitForIdleSync();
            assertEquals("请求错误必须显示失败", "识别失败",
                    ((android.widget.TextView) page.findViewById(cc.huoyan.retail.R.id.recognizeText)).getText().toString());
            captureStatus("recognition-failed");
            android.os.Bundle evidence = new android.os.Bundle();
            evidence.putString("stream", "\nframe_gate_device=passed size=" + real.getWidth() + "x"
                    + real.getHeight() + " distances=0,8,9 sdk_receipts=3 event_wakeup=true three_results_lock=true restart_reset=true connection_copy=true failure_copy=true\n");
            ins.sendStatus(0, evidence);
        } finally {
            camera.removeListener("gateTestCapture");
            camera.removeListener("gateTestDrain");
            if (page != null) { ins.runOnMainSync(page::finish); ins.waitForIdleSync(); }
            SharedPreferences.Editor editor = sp.edit();
            for (String key : new String[]{ModuleConfig.SP_WEIGHING_ENABLED, SPManager.SP_STABLE_RECOGNITION,
                    SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL}) {
                Object value = before.get(key);
                if (value == null) editor.remove(key);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else editor.putInt(key, (Integer) value);
            }
            editor.commit();
        }
    }

    /** 保存真机状态徽标，核对文案长度与主题样式，不修改业务数据。 */
    private void captureStatus(String name) {
        ins.runOnMainSync(() -> {
            android.view.View badge = page.findViewById(cc.huoyan.retail.R.id.recognizeStatus);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    badge.getWidth(), badge.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            canvas.drawColor(android.graphics.Color.rgb(40, 51, 66));
            badge.draw(canvas);
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                    new java.io.File(page.getExternalFilesDir(null), name + ".png"))) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            } catch (Exception error) { throw new AssertionError(error); }
            finally { bitmap.recycle(); }
        });
    }

    /** 只替换亮度哈希采样点，保留真实帧尺寸、绝大多数像素和相机代次。 */
    private CameraFrame withHashBits(CameraFrame source, int bright) {
        byte[] nv = source.getNv().clone();
        for (int i = 0; i < 64; i++) {
            int x = (2 * (i % 8) + 1) * source.getWidth() / 16;
            int y = (2 * (i / 8) + 1) * source.getHeight() / 16;
            nv[y * source.getWidth() + x] = (byte) (i < bright ? 220 : 20);
        }
        return new CameraFrame(nv, source.getWidth(), source.getHeight(), source.generation);
    }
    /** 有界等待真实硬件和 SDK，不用固定睡眠认定成功。 */
    private void await(BooleanSupplier check, String message) {
        long end = SystemClock.elapsedRealtime() + 20000;
        while (!check.getAsBoolean() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
        assertTrue(message, check.getAsBoolean());
    }
    /** 读取页面内部边界用于集成断言，不向生产代码增加测试接口。 */
    private Object read(Object target, String name) {
        try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    /** 固定测试输入以避免现场随机抖动影响阈值边界断言。 */
    private void write(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    /** 暂停自动循环后由测试线程执行相同识别入口，避免并发消费测试帧。 */
    private void invoke(Object target, String name) {
        try { Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(target); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
