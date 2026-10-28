package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.goods.AddGoodsDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 横竖屏共用的设置交互回归，验证真实存储、退出确认与设备选择入口。 */
@RunWith(AndroidJUnit4.class)
public class SettingsChangesDeviceTest {
    /** 当前测试线程的 UI 调度入口。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 本测试打开的设置页面，结束后关闭。 */
    private SettingsPageActivity page;

    /** 开关即时保存，参数需保存；恢复默认、返回确认均遵循同一边界。 */
    @Test public void recognitionSwitchesDefaultsAndExitGuard() throws Exception {
        SharedPreferences sp = preferences();
        Map<String, ?> before = new HashMap<>(sp.getAll());
        try {
            sp.edit().putBoolean(ModuleConfig.SP_WEIGHING_ENABLED, false)
                    .putBoolean(SPManager.SP_STABLE_RECOGNITION, false)
                    .putBoolean(SPManager.SP_DELETE_RECOGNITION_RESULT, false)
                    .putInt("recognition_confidence_percent", 58).putInt("recognition_iou_percent", 61)
                    .putInt(SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL, 5).commit();
            open("recognition");
            ins.runOnMainSync(() -> {
                ((Switch) page.findViewById(R.id.deleteRecognitionResult)).setChecked(true);
                ((Switch) page.findViewById(R.id.stableRecognition)).setChecked(true);
                assertFalse(settings().hasUnsavedSettings());
                page.findViewById(R.id.restoreRecognitionDefaults).performClick();
            }); settle();
            DialogFragment restoreDialog = restoreDialog();
            assertTrue(restoreDialog instanceof cc.huoyan.retail.common.ui.AlertDialog);
            assertEquals(58, ((SeekBar) page.findViewById(R.id.confSeekBar)).getProgress());
            capture("restore-default-confirm");
            ins.runOnMainSync(() -> restoreDialog.requireView().findViewById(R.id.cancel).performClick()); settle();
            assertFalse(settings().hasUnsavedSettings());
            ins.runOnMainSync(() -> page.findViewById(R.id.restoreRecognitionDefaults).performClick()); settle();
            ins.runOnMainSync(() -> restoreDialog().requireView().findViewById(R.id.confirm).performClick()); settle();
            assertTrue(settings().hasUnsavedSettings());
            assertEquals(45, ((SeekBar) page.findViewById(R.id.confSeekBar)).getProgress());
            assertEquals(45, ((SeekBar) page.findViewById(R.id.iouSeekBar)).getProgress());
            assertSensitivityLevel(3);
            assertEquals(5, sp.getInt(SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL, 0));
            assertTrue(sp.getBoolean(SPManager.SP_DELETE_RECOGNITION_RESULT, false));
            assertTrue(sp.getBoolean(SPManager.SP_STABLE_RECOGNITION, false));
            assertEquals(58, sp.getInt("recognition_confidence_percent", 0));
            capture("recognition-default-draft");
            ins.runOnMainSync(() -> page.findViewById(R.id.navBack).performClick()); settle();
            DialogFragment exit = exitDialog(); assertNotNull(exit);
            assertEquals(page.getString(R.string.settings_discard_exit), ((TextView) exit.requireView().findViewById(R.id.cancel)).getText().toString());
            assertEquals(page.getString(R.string.button_save_config), ((TextView) exit.requireView().findViewById(R.id.confirm)).getText().toString());
            capture("unsaved-settings");
            ins.runOnMainSync(exit::dismiss); settle();
            assertFalse(page.isFinishing());
            ins.runOnMainSync(() -> page.findViewById(R.id.navBack).performClick()); settle();
            ins.runOnMainSync(() -> exitDialog().requireView().findViewById(R.id.confirm).performClick()); settle();
            assertTrue(page.isFinishing());
            assertEquals(45, sp.getInt("recognition_confidence_percent", 0));
            assertEquals(45, sp.getInt("recognition_iou_percent", 0));
            assertEquals(3, sp.getInt(SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL, 0));
            close(); open("recognition");
            ins.runOnMainSync(() -> {
                assertFalse(settings().hasUnsavedSettings());
                ((SeekBar) page.findViewById(R.id.confSeekBar)).setProgress(80);
                page.onBackPressed();
            }); settle();
            assertNotNull(exitDialog());
            ins.runOnMainSync(() -> exitDialog().requireView().findViewById(R.id.cancel).performClick()); settle();
            assertTrue(page.isFinishing());
            assertEquals(45, sp.getInt("recognition_confidence_percent", 0));
            close();
            sp.edit().putBoolean(ModuleConfig.SP_WEIGHING_ENABLED, true).commit();
            open("recognition");
            assertFalse(page.findViewById(R.id.stableRecognition).isEnabled());
            assertTrue(sp.getBoolean(SPManager.SP_STABLE_RECOGNITION, false));
        } finally { close(); restore(sp, before); }
    }

    /** 真实触摸跨过五档刻度；草稿不落库，保存退出后重开保留档位，放弃不写入。 */
    @Test public void sensitivitySliderSnapsAndSaves() throws Exception {
        SharedPreferences sp = preferences();
        Map<String, ?> before = new HashMap<>(sp.getAll());
        try {
            sp.edit().remove(SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL).commit();
            open("recognition");
            assertSensitivityLevel(3);
            assertFalse(settings().hasUnsavedSettings());
            capture("sensitivity-default-level-3");
            for (int level : new int[]{1, 5, 2, 4, 3, 5}) {
                slideSensitivityTo(level);
                assertSensitivityLevel(level);
                assertFalse("滑动不能提前保存", sp.contains(SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL));
            }
            assertTrue(settings().hasUnsavedSettings());
            capture("sensitivity-level-5-draft");
            ins.runOnMainSync(page::onBackPressed); settle();
            ins.runOnMainSync(() -> exitDialog().requireView().findViewById(R.id.confirm).performClick()); settle();
            assertTrue(page.isFinishing());
            assertEquals(5, sp.getInt(SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL, 0));
            close(); open("recognition");
            assertSensitivityLevel(5);
            assertFalse(settings().hasUnsavedSettings());
            slideSensitivityTo(1);
            ins.runOnMainSync(page::onBackPressed); settle();
            ins.runOnMainSync(() -> exitDialog().requireView().findViewById(R.id.cancel).performClick()); settle();
            assertTrue(page.isFinishing());
            assertEquals(5, sp.getInt(SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL, 0));
        } finally { close(); restore(sp, before); }
    }

    /** 同时核验整数刻度位置、档位名称以及可读的无障碍标签。 */
    private void assertSensitivityLevel(int level) {
        SeekBar slider = page.findViewById(R.id.recognitionSensitivity);
        assertEquals(4, slider.getMax());
        assertEquals(level - 1, slider.getProgress());
        SeekBar confidence = page.findViewById(R.id.confSeekBar);
        int[] sliderPosition = new int[2], confidencePosition = new int[2];
        slider.getLocationOnScreen(sliderPosition);
        confidence.getLocationOnScreen(confidencePosition);
        assertEquals("轨道左端与上方滑条对齐", confidencePosition[0] + confidence.getPaddingLeft(),
                sliderPosition[0] + slider.getPaddingLeft());
        assertEquals("轨道右端与上方滑条对齐", confidencePosition[0] + confidence.getWidth() - confidence.getPaddingRight(),
                sliderPosition[0] + slider.getWidth() - slider.getPaddingRight());
        String[] names = page.getResources().getStringArray(R.array.recognition_sensitivity_names);
        android.view.ViewGroup labels = page.findViewById(R.id.recognitionSensitivityLabels);
        int index = 0;
        for (int child = 0; child < labels.getChildCount(); child++) {
            if (!(labels.getChildAt(child) instanceof TextView)) continue;
            TextView label = (TextView) labels.getChildAt(child);
            boolean selected = index == level - 1;
            assertEquals(names[index++], label.getText().toString());
            assertEquals(selected, label.isSelected());
            assertEquals(Color.parseColor(selected ? "#3F3EED" : "#6E7C90"), label.getCurrentTextColor());
            assertEquals(selected, label.getTypeface().isBold());
        }
        assertEquals(5, index);
        assertTrue(slider.getContentDescription().toString().contains(names[level - 1]));
    }

    /** 从当前滑块位置注入连续触摸，校验横向拖动不会被页面滚动容器吞掉。 */
    private void slideSensitivityTo(int level) {
        SeekBar slider = page.findViewById(R.id.recognitionSensitivity);
        ins.runOnMainSync(() -> slider.requestRectangleOnScreen(
                new android.graphics.Rect(0, 0, slider.getWidth(), slider.getHeight()), true));
        settle();
        int[] location = new int[2];
        ins.runOnMainSync(() -> slider.getLocationOnScreen(location));
        float left = location[0] + slider.getPaddingLeft();
        float span = slider.getWidth() - slider.getPaddingLeft() - slider.getPaddingRight();
        float from = left + span * slider.getProgress() / 4f;
        float to = left + span * (level - 1) / 4f;
        float y = location[1] + slider.getHeight() / 2f;
        long down = SystemClock.uptimeMillis();
        sensitivityPointer(down, android.view.MotionEvent.ACTION_DOWN, from, y);
        for (int i = 1; i <= 12; i++) {
            sensitivityPointer(down, android.view.MotionEvent.ACTION_MOVE, from + (to - from) * i / 12f, y);
            SystemClock.sleep(16);
        }
        sensitivityPointer(down, android.view.MotionEvent.ACTION_UP, to, y);
        settle();
    }

    /** 通过设备输入通道派发触摸，并回收事件对象。 */
    private void sensitivityPointer(long down, int action, float x, float y) {
        android.view.MotionEvent event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        ins.sendPointerSync(event);
        event.recycle();
    }

    /** 新增记住计价类型；未绑定学习使用项目确认框，扫码提供自定义选择列表。 */
    @Test public void goodsPreferenceBindingPromptAndScanPicker() throws Exception {
        SharedPreferences sp = preferences();
        Map<String, ?> before = new HashMap<>(sp.getAll());
        try {
            sp.edit().remove(SPManager.SP_SHOP_ID).remove("last_new_goods_pricing_count").commit();
            open("goods");
            AddGoodsDialog first = new AddGoodsDialog();
            ins.runOnMainSync(() -> first.showNow(page.getSupportFragmentManager(), "newGoods")); settle();
            ins.runOnMainSync(() -> ((RadioGroup) first.requireView().findViewById(R.id.pricingType)).check(R.id.pricingTypeCount));
            ins.runOnMainSync(first::dismiss); settle();
            AddGoodsDialog second = new AddGoodsDialog();
            ins.runOnMainSync(() -> second.showNow(page.getSupportFragmentManager(), "newGoods")); settle();
            assertEquals(R.id.pricingTypeCount, ((RadioGroup) second.requireView().findViewById(R.id.pricingType)).getCheckedRadioButtonId());
            ins.runOnMainSync(second::dismiss); settle();
            ins.runOnMainSync(() -> page.findViewById(R.id.learnImagePackage).performClick()); settle();
            DialogFragment binding = (DialogFragment) page.getSupportFragmentManager().findFragmentById(R.id.pageContent)
                    .getChildFragmentManager().findFragmentByTag("learningShopRequired");
            assertTrue(binding instanceof cc.huoyan.retail.common.ui.AlertDialog);
            capture("learning-unbound");
            ins.runOnMainSync(binding::dismiss); settle();
            ScanWechatQRCodeDialog scan = ScanWechatQRCodeDialog.create(1);
            ins.runOnMainSync(() -> scan.showNow(page.getSupportFragmentManager(), "scan")); settle();
            awaitScanFrames();
            capture("scan-switch-camera");
            ins.runOnMainSync(() -> scan.requireView().findViewById(R.id.scanSwitchCamera).performClick()); settle();
            assertNotNull(scan.getChildFragmentManager().findFragmentByTag("selectOption"));
            capture("scan-camera-picker");
            ins.runOnMainSync(() -> {
                cc.huoyan.retail.device.camera.CameraController controller = cc.huoyan.retail.device.camera.CameraController.getInstance();
                java.util.List<cc.huoyan.retail.device.camera.CameraDeviceInfo> devices = controller.getDevices();
                assertFalse("扫码设备列表不能为空", devices.isEmpty());
                if ("true".equals(InstrumentationRegistry.getArguments().getString("requireMultipleCameras")))
                    assertTrue("本次要求验证两台真实摄像头切换", devices.size() >= 2);
                android.util.Log.i("SettingsChangesDeviceTest", "camera_picker_devices=" + devices.size());
                int target = devices.size() > 1 && devices.get(0).id.equals(controller.getCurrentCameraId()) ? 1 : 0;
                DialogFragment picker = (DialogFragment) scan.getChildFragmentManager().findFragmentByTag("selectOption");
                android.widget.ListView list = picker.requireView().findViewById(R.id.selectOptions);
                list.performItemClick(list.getChildAt(target), target, target);
                assertEquals(devices.get(target).id, controller.getCurrentCameraId());
            }); settle();
            long frameDeadline = SystemClock.elapsedRealtime() + 10000;
            while (!cc.huoyan.retail.device.camera.CameraController.getInstance().isCameraOpen()
                    && SystemClock.elapsedRealtime() < frameDeadline) SystemClock.sleep(100);
            assertTrue("选择摄像头后必须保留可用预览",
                    cc.huoyan.retail.device.camera.CameraController.getInstance().isCameraOpen());
            awaitScanFrames();
            capture("scan-switched");
            ins.runOnMainSync(scan::dismiss); settle();
        } finally { close(); restore(sp, before); }
    }

    /** 保存退出遇到无效 SDK 地址时保留页面；放弃按钮退出且不写入草稿。 */
    @Test public void invalidSdkSettingsDoNotExit() throws Exception {
        SharedPreferences sp = preferences();
        Map<String, ?> before = new HashMap<>(sp.getAll());
        try {
            open("sdk");
            ins.runOnMainSync(() -> {
                page.findViewById(R.id.sdkWs).performClick();
                ((android.widget.EditText) page.findViewById(R.id.wsAddress)).setText("invalid-address");
                page.onBackPressed();
            }); settle();
            assertNotNull(exitDialog());
            ins.runOnMainSync(() -> exitDialog().requireView().findViewById(R.id.confirm).performClick()); settle();
            assertFalse(page.isFinishing());
            assertTrue(settings().hasUnsavedSettings());
            assertEquals(before.get(SPManager.SP_WS_ENDPOINT), sp.getAll().get(SPManager.SP_WS_ENDPOINT));
            ins.runOnMainSync(() -> page.onBackPressed()); settle();
            ins.runOnMainSync(() -> exitDialog().requireView().findViewById(R.id.cancel).performClick()); settle();
            assertTrue(page.isFinishing());
        } finally { close(); restore(sp, before); }
    }

    /** 返回当前识别页中的恢复确认框。 */
    private DialogFragment restoreDialog() {
        return (DialogFragment) page.getSupportFragmentManager().findFragmentById(R.id.pageContent)
                .getChildFragmentManager().findFragmentByTag("restoreRecognitionDefaults");
    }

    /** 未选择硬件时使用红色选择提示，覆盖共用两方向布局。 */
    @Test public void hardwareSelectionIsHighlighted() throws Exception {
        SharedPreferences sp = preferences();
        Map<String, ?> before = new HashMap<>(sp.getAll());
        try {
            sp.edit().remove(SPManager.SP_ACLAS_SCALE_PORT).remove("printer_device").commit();
            open("scale");
            assertEquals(Color.parseColor("#C93636"), ((TextView) page.findViewById(R.id.selectedScalePort)).getCurrentTextColor());
            capture("scale-selection"); close();
            open("print");
            assertEquals(Color.parseColor("#C93636"), ((TextView) page.findViewById(R.id.printerModel)).getCurrentTextColor());
            capture("print-selection");
        } finally { close(); restore(sp, before); }
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

    /** 返回当前参数页的退出保护状态。 */
    private PendingSettings settings() { return (PendingSettings) page.getSupportFragmentManager().findFragmentById(R.id.pageContent); }
    /** 返回已显示的项目退出弹窗。 */
    private DialogFragment exitDialog() { return (DialogFragment) page.getSupportFragmentManager().findFragmentByTag("unsavedSettings"); }
    /** 读取测试设备的应用偏好，测试后恢复。 */
    private SharedPreferences preferences() { return ins.getTargetContext().getSharedPreferences("HuoYanApplication", Context.MODE_PRIVATE); }
    /** 只打开当前待验证页面。 */
    private void open(String name) {
        page = (SettingsPageActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), SettingsPageActivity.class)
                .putExtra("page", name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        settle();
    }
    /** 关闭测试页面并等待生命周期完成。 */
    private void close() { if (page != null) { ins.runOnMainSync(page::finish); settle(); page = null; } }
    /** 等待布局及 Fragment 事务稳定。 */
    private void settle() { ins.waitForIdleSync(); SystemClock.sleep(450); }
    /** 恢复原偏好，避免测试改变设备业务配置。 */
    private void restore(SharedPreferences sp, Map<String, ?> before) {
        SharedPreferences.Editor editor = sp.edit().clear();
        for (Map.Entry<String, ?> entry : before.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof java.util.Set) editor.putStringSet(key, new java.util.HashSet<>((java.util.Set<String>) value));
        }
        editor.commit();
    }
    /** 保存真实窗口截图，供双方向布局核验。 */
    private void capture(String name) throws Exception {
        File dir = new File(ins.getTargetContext().getExternalFilesDir(null),
                "settings-changes-" + page.getResources().getConfiguration().orientation);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { bitmap.recycle(); }
    }
}
