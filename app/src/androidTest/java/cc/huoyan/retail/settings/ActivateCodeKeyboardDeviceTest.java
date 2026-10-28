package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** 真机验证自定义键盘，不提交真实激活或写入激活码存储。 */
@RunWith(AndroidJUnit4.class)
public class ActivateCodeKeyboardDeviceTest {
    /** 验收主线程操作与截图的设备桥接。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 字符限制、光标编辑、清空、长按退格及提交出口。 */
    @Test public void customKeyboardEditsWithoutIme() throws Exception {
        SettingsPageActivity activity = launch();
        ActivateCodeDialog dialog = new ActivateCodeDialog();
        AtomicReference<String> submitted = new AtomicReference<>();
        try {
            ins.runOnMainSync(() -> {
                dialog.setActivateCodeListener(new ActivateCodeDialog.ActivateCodeListener() {
                    /** 截获合法输入，不进入真实授权流程。 */
                    @Override public void onActivate(String code) { submitted.set(code); }
                    /** 本用例不打开摄像头。 */
                    @Override public void onScan() { fail("意外扫码"); }
                });
                dialog.showNow(activity.getSupportFragmentManager(), "keyboard-test");
            });
            settle();
            View root = dialog.requireView();
            EditText edit = root.findViewById(R.id.editText);
            ins.runOnMainSync(() -> {
                root.findViewById(R.id.activationClear).performClick();
                assertFalse(root.findViewById(R.id.activate).isEnabled());
                for (char c : ActivationKeyboardInput.CHARACTERS.toCharArray()) visible(root.findViewWithTag("activation-key-" + c));
                assertNull(root.findViewWithTag("activation-key-0"));
                assertNull(root.findViewWithTag("activation-key-I"));
                assertNull(root.findViewWithTag("activation-key-O"));
                visible(root.findViewById(R.id.activate));
                visible(root.findViewById(R.id.scan));
                assertFalse(edit.getShowSoftInputOnFocus());
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
                assertTrue(insets == null || !insets.isVisible(WindowInsetsCompat.Type.ime()));
                type(root, "Z2FBW9L7M7");
                assertEquals("Z2FBW9L7M7", edit.getText().toString());
                assertEquals("W9L7", ((TextView) root.findViewById(R.id.text2)).getText().toString());
            });
            settle(); capture("input");
            ins.runOnMainSync(() -> {
                type(root, "8R586H");
                assertTrue(root.findViewById(R.id.activate).isEnabled());
                type(root, "A");
                assertEquals("Z2FBW9L7M78R586H", edit.getText().toString());
                edit.setSelection(2, 3);
                edit.getText().replace(2, 3, "O");
                assertEquals("Z2FBW9L7M78R586H", edit.getText().toString());
                root.findViewWithTag("activation-backspace").performClick();
                type(root, "F");
                assertEquals("Z2FBW9L7M78R586H", edit.getText().toString());
                edit.setSelection(16);
            });
            settle(); capture("complete");
            ins.runOnMainSync(() -> {
                View key = root.findViewWithTag("activation-backspace");
                key.setPressed(true);
                key.performLongClick();
            });
            SystemClock.sleep(360);
            ins.runOnMainSync(() -> {
                root.findViewWithTag("activation-backspace").setPressed(false);
                assertTrue(edit.length() < 15);
                root.findViewById(R.id.activationClear).performClick();
                assertEquals("", edit.getText().toString());
                type(root, "AB12");
                TextView first = root.findViewById(R.id.text1);
                float x = first.getLeft() + first.getWidth() / 2f;
                long now = SystemClock.uptimeMillis();
                MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, edit.getHeight() / 2f, 0);
                edit.dispatchTouchEvent(up); up.recycle();
                assertEquals(2, edit.getSelectionStart());
                type(root, "L");
                assertEquals("ABL12", edit.getText().toString());
                root.findViewById(R.id.activationClear).performClick();
                type(root, "Z2FBW9L7M78R586H");
                root.findViewById(R.id.activate).performClick();
                assertEquals("Z2FBW9L7M78R586H", submitted.get());
            });
        } finally { ins.runOnMainSync(() -> { dialog.dismissAllowingStateLoss(); activity.finish(); }); }
    }
    /** 重建恢复未提交输入和光标；取消与扫码不提交授权。 */
    @Test public void restoresDraftAndPreservesScanExit() {
        SettingsPageActivity activity = launch();
        AtomicReference<ActivateCodeDialog> current = new AtomicReference<>(new ActivateCodeDialog());
        AtomicReference<String> exit = new AtomicReference<>();
        try {
            ins.runOnMainSync(() -> current.get().showNow(activity.getSupportFragmentManager(), "restore-test"));
            settle();
            ins.runOnMainSync(() -> {
                EditText edit = current.get().requireView().findViewById(R.id.editText);
                edit.setText("AB12L"); edit.setSelection(2);
                androidx.fragment.app.Fragment.SavedState saved = activity.getSupportFragmentManager().saveFragmentInstanceState(current.get());
                current.get().dismiss(); activity.getSupportFragmentManager().executePendingTransactions();
                ActivateCodeDialog restored = new ActivateCodeDialog();
                restored.setInitialSavedState(saved);
                restored.setActivateCodeListener(new ActivateCodeDialog.ActivateCodeListener() {
                    /** 恢复和扫码都不应提交。 */
                    @Override public void onActivate(String code) { fail("意外提交"); }
                    /** 截获扫码入口，验证原有出口保留。 */
                    @Override public void onScan() { exit.set("scan"); }
                });
                restored.showNow(activity.getSupportFragmentManager(), "restore-test");
                current.set(restored);
                EditText next = restored.requireView().findViewById(R.id.editText);
                assertEquals("AB12L", next.getText().toString());
                assertEquals(2, next.getSelectionStart());
                restored.requireView().findViewById(R.id.scan).performClick();
                assertEquals("scan", exit.get());
            });
        } finally { ins.runOnMainSync(() -> { current.get().dismissAllowingStateLoss(); activity.finish(); }); }
    }
    /** Activity 真正重建后保留草稿，并重新连接授权页扫码入口。 */
    @Test public void activityRecreationRebindsInput() {
        SettingsPageActivity activity = launch();
        Instrumentation.ActivityMonitor monitor = ins.addMonitor(SettingsPageActivity.class.getName(), null, false);
        SettingsPageActivity restored = null;
        try {
            ins.runOnMainSync(() -> activity.findViewById(R.id.activate).performClick());
            settle();
            ins.runOnMainSync(() -> {
                ActivateCodeDialog input = (ActivateCodeDialog) activity.getSupportFragmentManager().findFragmentByTag("modelActivation").getChildFragmentManager().findFragmentByTag("activateCode");
                EditText edit = input.requireView().findViewById(R.id.editText);
                edit.setText("AB12L"); edit.setSelection(2);
                activity.recreate();
            });
            restored = (SettingsPageActivity) ins.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull(restored); settle();
            SettingsPageActivity host = restored;
            ins.runOnMainSync(() -> {
                ActivateCodeDialog input = (ActivateCodeDialog) host.getSupportFragmentManager().findFragmentByTag("modelActivation").getChildFragmentManager().findFragmentByTag("activateCode");
                EditText edit = input.requireView().findViewById(R.id.editText);
                assertEquals("AB12L", edit.getText().toString());
                assertEquals(2, edit.getSelectionStart());
                input.requireView().findViewById(R.id.scan).performClick();
                host.getSupportFragmentManager().findFragmentByTag("modelActivation").getChildFragmentManager().executePendingTransactions();
                androidx.fragment.app.DialogFragment scanner = (androidx.fragment.app.DialogFragment) host.getSupportFragmentManager().findFragmentByTag("modelActivation").getChildFragmentManager().findFragmentByTag("scanWechatQRCode");
                assertNotNull(scanner); scanner.dismiss();
            });
        } finally {
            ins.removeMonitor(monitor);
            SettingsPageActivity host = restored == null ? activity : restored;
            ins.runOnMainSync(host::finish);
        }
    }
    /** 启动授权页作为弹窗宿主。 */
    private SettingsPageActivity launch() {
        return (SettingsPageActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), SettingsPageActivity.class)
                .putExtra("page", "authorization").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    /** 通过真实字符键触发输入。 */
    private void type(View root, String value) {
        for (char c : value.toCharArray()) root.findViewWithTag("activation-key-" + c).performClick();
    }
    /** 触点必须完全位于可见窗口内。 */
    private void visible(View view) {
        Rect bounds = new Rect(); assertTrue(view.getGlobalVisibleRect(bounds));
        assertEquals(view.getWidth(), bounds.width()); assertEquals(view.getHeight(), bounds.height());
    }
    /** 等待窗口和输入法动画结束。 */
    private void settle() { ins.waitForIdleSync(); SystemClock.sleep(400); }
    /** 保存示例输入截图用于横竖屏视觉核对。 */
    private void capture(String name) throws Exception {
        File folder = new File(ins.getTargetContext().getExternalFilesDir(null), "activation-keyboard-qa");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }
}
