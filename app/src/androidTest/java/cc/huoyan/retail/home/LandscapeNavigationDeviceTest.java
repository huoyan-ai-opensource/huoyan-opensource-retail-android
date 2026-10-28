package cc.huoyan.retail.home;

import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.goods.GoodsManagementActivity;
import cc.huoyan.retail.settings.SettingsPageActivity;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import androidx.fragment.app.DialogFragment;
import cc.huoyan.retail.settings.ActivateCodeDialog;
import cc.huoyan.retail.device.camera.CameraUtils;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.device.camera.CameraUsbManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** 在真机加载每个横屏入口并记录截图及原生控件边界。 */
@RunWith(AndroidJUnit4.class)
public class LandscapeNavigationDeviceTest {
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    @Test public void launcherHomeRoutesAndCheckoutNavigation() throws Exception {
        Intent launcher = ins.getTargetContext().getPackageManager().getLaunchIntentForPackage(ins.getTargetContext().getPackageName());
        assertNotNull(launcher);
        android.content.pm.ActivityInfo entry = ins.getTargetContext().getPackageManager()
                .getActivityInfo(launcher.getComponent(), 0);
        assertEquals(HomeActivity.class.getName(), entry.targetActivity == null ? entry.name : entry.targetActivity);
        launcher.setClass(ins.getTargetContext(), HomeActivity.class);
        HomeActivity home = (HomeActivity) ins.startActivitySync(launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForLayout(home); SystemClock.sleep(700); capture("home", home);
        int[] ids = {R.id.homeCamera, R.id.homeScale, R.id.homePrint, R.id.homeRecognition,
                R.id.homeSdk, R.id.homeShop, R.id.homeGoods, R.id.homeMaintenance, R.id.aboutDevice};
        String[] pages = {"camera", "scale", "print", "recognition", "sdk", "shop", "goods", "maintenance", "authorization"};
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            Instrumentation.ActivityMonitor monitor = ins.addMonitor(SettingsPageActivity.class.getName(), null, false);
            ins.runOnMainSync(() -> home.findViewById(id).performClick());
            SettingsPageActivity page = (SettingsPageActivity) ins.waitForMonitorWithTimeout(monitor, 5000);
            ins.removeMonitor(monitor); assertNotNull(pages[i], page);
            assertEquals(pages[i], page.getIntent().getStringExtra("page"));
            waitForLayout(page); SystemClock.sleep("camera".equals(pages[i]) ? 3000 : 900);
            if ("camera".equals(pages[i])) assertCameraConfiguration(page);
            assertMissingTip(page.getWindow().getDecorView());
            capture(pages[i], page);
            if ("authorization".equals(pages[i])) assertActivationEntry(page);
            assertEquals(page.getString(R.string.home_back), ((TextView) page.findViewById(R.id.navBack)).getText().toString());
            ins.runOnMainSync(() -> page.findViewById(R.id.navBack).performClick());
            ins.waitForIdleSync(); SystemClock.sleep(200);
            assertTrue(page.isFinishing()); assertFalse(home.isDestroyed());
        }
        Instrumentation.ActivityMonitor managementMonitor = ins.addMonitor(GoodsManagementActivity.class.getName(), null, false);
        ins.runOnMainSync(() -> home.findViewById(R.id.homeLearning).performClick());
        GoodsManagementActivity management = (GoodsManagementActivity) ins.waitForMonitorWithTimeout(managementMonitor, 5000);
        ins.removeMonitor(managementMonitor); assertNotNull(management);
        waitForLayout(management); capture("management", management);
        ins.runOnMainSync(() -> management.findViewById(R.id.navBack).performClick());
        ins.waitForIdleSync(); assertTrue(management.isFinishing());
        Instrumentation.ActivityMonitor checkoutMonitor = ins.addMonitor(MainActivity.class.getName(), null, false);
        ins.runOnMainSync(() -> home.findViewById(R.id.enterCheckout).performClick());
        MainActivity main = (MainActivity) ins.waitForMonitorWithTimeout(checkoutMonitor, 5000);
        ins.removeMonitor(checkoutMonitor); assertNotNull(main); waitForLayout(main); SystemClock.sleep(3500);
        ViewGroup nav = main.findViewById(R.id.checkoutNavigation);
        assertEquals("导航栏仅保留返回首页和店铺名称", 2, nav.getChildCount());
        assertNotSame(nav, main.findViewById(R.id.recognizeStatus).getParent());
        assertSame(main.findViewById(R.id.surfaceView).getParent(), main.findViewById(R.id.version).getParent());
        assertEquals(main.findViewById(R.id.main).getWidth(), nav.getWidth());
        assertNotNull(main.findViewById(R.id.checkoutClock));
        View results = main.findViewById(R.id.recognitionResults);
        assertEquals(main.findViewById(R.id.main).getHeight() - nav.getHeight(), results.getHeight());
        assertEquals(0, results.getTop());
        assertNotNull(main.findViewById(R.id.recognizeStatus).getBackground());
        assertNull(main.findViewById(R.id.version).getBackground());
        capture("main", main);
        ins.runOnMainSync(() -> main.onBackPressed());
        ins.waitForIdleSync(); assertTrue(main.isFinishing()); assertFalse(home.isDestroyed());
        ins.runOnMainSync(home::finish);
    }

    private void assertCameraConfiguration(SettingsPageActivity page) {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        CameraUsbManager camera = CameraUtils.getInstance().getCameraUsbManager();
        boolean[] loaded = {false};
        do {
            ins.runOnMainSync(() -> {
                String device = camera.getCurrentCameraId();
                String saved = SPManager.getInstance().getString("fbl_" + device);
                String resolution = ((TextView) page.findViewById(R.id.cameraResolution)).getText().toString();
                loaded[0] = camera.isCameraOpen() && camera.cameraSize != null
                        && resolution.equals(camera.cameraSize.width + " × " + camera.cameraSize.height)
                        && (saved == null || saved.isEmpty() || resolution.equals(saved.replace("*", " × ")))
                        && !((TextView) page.findViewById(R.id.cameraName)).getText().toString()
                                .equals(page.getString(R.string.settings_camera_select))
                        && ((TextView) page.findViewById(R.id.cameraStatus)).getText().toString().contains("已连接");
            });
            if (loaded[0]) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("首页首次进入相机设置必须显示当前设备、已保存分辨率及连接状态");
    }

    private void assertActivationEntry(SettingsPageActivity page) {
        ins.runOnMainSync(() -> page.findViewById(R.id.activate).performClick());
        ins.waitForIdleSync();
        ins.runOnMainSync(() -> {
            ActivateCodeDialog dialog = (ActivateCodeDialog) page.getSupportFragmentManager().findFragmentByTag("modelActivation").getChildFragmentManager().findFragmentByTag("activateCode");
            assertNotNull(dialog);
            EditText edit = dialog.requireView().findViewById(R.id.editText);
            edit.setText("AB12");
            assertEquals("AB12", ((TextView) dialog.requireView().findViewById(R.id.text1)).getText().toString());
            dialog.requireView().findViewById(R.id.scan).performClick();
        });
        ins.waitForIdleSync();
        SystemClock.sleep(700);
        ins.runOnMainSync(() -> {
            DialogFragment scanner = (DialogFragment) page.getSupportFragmentManager().findFragmentByTag("modelActivation").getChildFragmentManager().findFragmentByTag("scanWechatQRCode");
            assertNotNull("原有扫码激活入口", scanner);
            assertTrue(scanner.requireDialog().isShowing());
            scanner.dismiss();
        });
        ins.waitForIdleSync();
    }

    private void assertMissingTip(View view) {
        if (view instanceof TextView) assertNotEquals("每次进入收银，开始新的识别", ((TextView) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) assertMissingTip(group.getChildAt(i));
        }
    }

    private Activity launch(Class<? extends Activity> type, String page) {
        Intent intent = new Intent(ins.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (page != null) intent.putExtra("page", page);
        return ins.startActivitySync(intent);
    }

    private void waitForLayout(Activity activity) {
        ins.waitForIdleSync();
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (activity.getWindow().getDecorView().getWidth() == 0 && SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(50);
        assertTrue(activity.getWindow().getDecorView().getWidth() > 0);
    }

    private void capture(String name, Activity activity) throws Exception {
        capture(name, activity, activity.getWindow().getDecorView());
    }

    private void capture(String name, Activity activity, View decor) throws Exception {
        File directory = new File(ins.getTargetContext().getExternalFilesDir(null), "landscape-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap screenshot = ins.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        screenshot.recycle();
        JSONArray tree = new JSONArray();
        ins.runOnMainSync(() -> collect(decor, tree));
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".json"))) {
            output.write(tree.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void collect(View view, JSONArray result) {
        if (view.getVisibility() != View.VISIBLE) return;
        try {
            JSONObject node = new JSONObject();
            int[] xy = new int[2]; view.getLocationOnScreen(xy);
            node.put("class", view.getClass().getSimpleName());
            if (view.getId() != View.NO_ID) node.put("id", view.getResources().getResourceEntryName(view.getId()));
            node.put("x", xy[0]); node.put("y", xy[1]);
            node.put("width", view.getWidth()); node.put("height", view.getHeight());
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                node.put("text", text.getText()); node.put("fontSize", text.getTextSize());
            }
            result.put(node);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), result);
            }
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
