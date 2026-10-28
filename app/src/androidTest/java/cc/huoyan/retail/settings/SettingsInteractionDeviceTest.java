package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.device.camera.AutoFitTextureView;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.device.camera.ResizableDashedBoxView;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** 真机验证设置页触摸、连接状态、分辨率回显和扫码比例，兼容横竖屏。 */
@RunWith(AndroidJUnit4.class)
public class SettingsInteractionDeviceTest {
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    private SettingsPageActivity page;

    /** 参数默认值、保存回显、只读模型类型及真实首帧裁剪比例回归；结束时还原偏好。 */
    @Test public void recognitionDefaultsAndCameraAreaFollowPreview() throws Exception {
        android.content.SharedPreferences sp = ins.getTargetContext().getSharedPreferences(
                "HuoYanApplication", android.content.Context.MODE_PRIVATE);
        String[] keys = {"recognition_confidence_percent", "recognition_iou_percent",
                SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL, SPManager.SP_RECOGNIZE_AREA};
        java.util.Map<String, ?> before = new java.util.HashMap<>(sp.getAll());
        try {
            sp.edit().remove(keys[0]).remove(keys[1]).remove(keys[3]).commit();
            open("recognition");
            assertEquals("0.45", text(R.id.confText));
            assertEquals("0.45", text(R.id.iouText));
            assertFalse(page.findViewById(R.id.currentModel).isClickable());
            assertTrue(page.findViewById(R.id.switchModel).isClickable());
            capture("recognition-defaults");
            ins.runOnMainSync(() -> {
                ((SeekBar) page.findViewById(R.id.confSeekBar)).setProgress(58);
                ((SeekBar) page.findViewById(R.id.iouSeekBar)).setProgress(61);
                page.findViewById(R.id.saveSettings).performClick();
            });
            close(); open("recognition");
            assertEquals("0.58", text(R.id.confText));
            assertEquals("0.61", text(R.id.iouText));
            close();
            sp.edit().remove(SPManager.SP_RECOGNIZE_AREA).commit();
            open("camera");
            await(() -> text(R.id.cameraStatus).contains("已连接"));
            settle();
            cc.huoyan.retail.device.camera.RecognizeAreaEntity area = SPManager.getInstance().getRecognizeArea();
            assertEquals(area.getWidth() + " × " + area.getHeight(), text(R.id.cameraResolution));
            assertEquals(area.getWidth() * .25f, area.getPoints().get(0).x, 1f);
            assertEquals(area.getHeight() * .25f, area.getPoints().get(0).y, 1f);
            assertEquals(area.getWidth() * .75f, area.getPoints().get(2).x, 1f);
            assertEquals(area.getHeight() * .75f, area.getPoints().get(2).y, 1f);
            capture("camera-defaults");
            close(); open("goods"); capture("library-buttons");
            cc.huoyan.retail.learning.GoodsSearchDialog dialog = cc.huoyan.retail.learning.GoodsSearchDialog.create("");
            ins.runOnMainSync(() -> dialog.showNow(page.getSupportFragmentManager(), "keyboardColors"));
            settle(); capture("learning-keyboard");
            ins.runOnMainSync(dialog::dismiss);
            close(); open("sdk"); capture("sdk-ip-help");
        } finally {
            close();
            android.content.SharedPreferences.Editor editor = sp.edit();
            for (String key : keys) {
                Object value = before.get(key);
                if (value == null) editor.remove(key);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof String) editor.putString(key, (String) value);
            }
            editor.commit();
        }
    }

    /** 拖动角点不滚动页面，分辨率使用稳定值回显，扫码画面不拉伸。 */
    @Test public void cameraGesturesSelectionAndScanKeepGeometry() throws Exception {
        String area = SPManager.getInstance().getString(SPManager.SP_RECOGNIZE_AREA);
        try {
            open("camera");
            await(() -> text(R.id.cameraStatus).contains("已连接"));
            ins.runOnMainSync(() -> page.findViewById(R.id.cameraResolutionLayout).performClick());
            settle();
            Fragment camera = page.getSupportFragmentManager().findFragmentById(R.id.pageContent);
            DialogFragment selector = (DialogFragment) camera.getChildFragmentManager().findFragmentByTag("selectOption");
            assertNotNull(selector);
            ins.runOnMainSync(() -> {
                ListView list = selector.requireView().findViewById(R.id.selectOptions);
                assertTrue("分辨率列表必须勾选当前帧尺寸", list.getCheckedItemPosition() >= 0);
                assertEquals(text(R.id.cameraResolution), list.getAdapter().getItem(list.getCheckedItemPosition()));
            });
            capture("resolution");
            ins.runOnMainSync(selector::dismiss);
            settle();
            ResizableDashedBoxView box = page.findViewById(R.id.boxView);
            ScrollView scroll = scrollAncestor(box);
            if (scroll != null) ins.runOnMainSync(() -> scroll.fullScroll(View.FOCUS_DOWN));
            settle();
            int oldScroll = scroll == null ? 0 : scroll.getScrollY();
            float[] xy = new float[2];
            float before = SPManager.getInstance().getRecognizeArea().getPoints().get(0).y;
            ins.runOnMainSync(() -> {
                PointF point = box.convertPointsFromOriginalToImageView(
                        SPManager.getInstance().getRecognizeArea().getPoints()).get(0);
                int[] location = new int[2]; box.getLocationOnScreen(location);
                xy[0] = location[0] + point.x; xy[1] = location[1] + point.y;
            });
            long down = SystemClock.uptimeMillis();
            pointer(down, MotionEvent.ACTION_DOWN, xy[0], xy[1]);
            for (int i = 1; i <= 8; i++) {
                pointer(down, MotionEvent.ACTION_MOVE, xy[0] + i * 3, xy[1] + i * 6);
                SystemClock.sleep(25);
            }
            pointer(down, MotionEvent.ACTION_UP, xy[0] + 24, xy[1] + 48);
            settle();
            if (scroll != null) assertEquals("拖动角点不能带动 ScrollView", oldScroll, scroll.getScrollY());
            assertTrue(Math.abs(before - SPManager.getInstance().getRecognizeArea().getPoints().get(0).y) > 1);
            capture("camera-drag");
            ScanWechatQRCodeDialog scan = ScanWechatQRCodeDialog.create(1);
            ins.runOnMainSync(() -> scan.showNow(page.getSupportFragmentManager(), "scanGeometry"));
            settle();
            awaitScanFrames();
            capture("scan");
            ins.runOnMainSync(() -> {
                AutoFitTextureView preview = scan.requireView().findViewById(R.id.surfaceView);
                View bounds = scan.requireView().findViewById(R.id.scanPreview);
                assertTrue(preview.isAvailable());
                assertTrue(preview.getWidth() > 0 && preview.getHeight() > 0);
                assertEquals((double) preview.getRatioWidth() / preview.getRatioHeight(),
                        (double) preview.getWidth() / preview.getHeight(), .01);
                assertTrue("预览宽 " + preview.getWidth() + " 超过容器 " + bounds.getWidth(), preview.getWidth() <= bounds.getWidth() + 1);
                assertTrue("预览高 " + preview.getHeight() + " 超过容器 " + bounds.getHeight(), preview.getHeight() <= bounds.getHeight() + 1);
            });
            ins.runOnMainSync(scan::dismiss);
        } finally {
            close();
            SPManager.getInstance().putString(SPManager.SP_RECOGNIZE_AREA, area);
        }
    }

    /** 进入打印页仅枚举设备且不展示模块内结果，参数条保持原触摸行为。 */
    @Test public void printerDiscoveryIsSilentAndSlidersTrackTouches() throws Exception {
        try {
            open("print");
            assertEquals(View.GONE, page.findViewById(R.id.printerStatus).getVisibility());
            assertEquals(page.getString(R.string.printer_escpos), text(R.id.printerTypeName));
            assertTrue(page.findViewById(R.id.communicationTest).isEnabled());
            capture("print");
            close(); open("recognition");
            capture("recognition");
            SeekBar bar = page.findViewById(R.id.confSeekBar);
            ScrollView scroll = scrollAncestor(bar);
            if (scroll != null) ins.runOnMainSync(() -> scroll.fullScroll(View.FOCUS_DOWN));
            settle();
            ins.runOnMainSync(() -> {
                assertNull(bar.getBackground());
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bar.getWidth() * .2f, bar.getHeight() / 2f, 0);
                bar.dispatchTouchEvent(down); down.recycle();
                MotionEvent move = MotionEvent.obtain(now, now + 100, MotionEvent.ACTION_MOVE, bar.getWidth() * .8f, bar.getHeight() / 2f, 0);
                bar.dispatchTouchEvent(move); move.recycle();
                assertTrue(bar.getProgress() >= 75);
            });
            settle(); capture("slider-pressed");
        } finally { close(); }
    }

    /** 获取 SDK 直接展示下载弹窗，关闭后取消任务，不打开本地文件选择器。 */
    @Test public void maintenanceStartsOnlineDownload() throws Exception {
        try {
            open("maintenance"); capture("maintenance");
            ins.runOnMainSync(() -> page.findViewById(R.id.handleInstall).performClick());
            settle();
            DialogFragment progress = (DialogFragment) page.getSupportFragmentManager().findFragmentByTag("sdkInstallProgress");
            assertNotNull(progress);
            capture("sdk-download");
            ins.runOnMainSync(() -> progress.requireView().findViewById(R.id.close).performClick());
            settle();
            assertNull(page.getSupportFragmentManager().findFragmentByTag("sdkInstallProgress"));
        } finally { close(); }
    }

    /** 等待真实扫码帧，避免设备异步枚举未完成时测试点击空列表。 */
    private void awaitScanFrames() throws Exception {
        cc.huoyan.retail.device.camera.CameraController camera = cc.huoyan.retail.device.camera.CameraController.getInstance();
        java.util.concurrent.CountDownLatch frames = new java.util.concurrent.CountDownLatch(2);
        camera.addListener("scanTestReady", frame -> frames.countDown());
        try { assertTrue("扫码相机须交付真实帧", frames.await(15, java.util.concurrent.TimeUnit.SECONDS)); }
        finally { camera.removeListener("scanTestReady"); }
        settle();
    }

    private void open(String name) {
        page = (SettingsPageActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), SettingsPageActivity.class)
                .putExtra("page", name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        settle();
    }
    private void close() { if (page != null) { ins.runOnMainSync(page::finish); settle(); page = null; } }
    private String text(int id) { return ((TextView) page.findViewById(id)).getText().toString(); }
    private void settle() { ins.waitForIdleSync(); SystemClock.sleep(400); }
    private ScrollView scrollAncestor(View view) {
        while (view.getParent() instanceof View) { view = (View) view.getParent(); if (view instanceof ScrollView) return (ScrollView) view; }
        return null;
    }
    private void pointer(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        ins.sendPointerSync(event); event.recycle();
    }
    private void await(BooleanSupplier condition) {
        long end = SystemClock.elapsedRealtime() + 15000;
        while (SystemClock.elapsedRealtime() < end) { if (condition.getAsBoolean()) return; SystemClock.sleep(100); }
        assertTrue("等待设备状态超时", condition.getAsBoolean());
    }
    private void capture(String name) throws Exception {
        File dir = new File(ins.getTargetContext().getExternalFilesDir(null),
                "settings-fixes-" + page.getResources().getConfiguration().orientation);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { bitmap.recycle(); }
    }
}
