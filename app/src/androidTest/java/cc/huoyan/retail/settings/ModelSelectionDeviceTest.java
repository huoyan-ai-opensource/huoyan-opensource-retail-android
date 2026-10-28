package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.*;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 真机横竖屏与配置重建验收；拦截所有激活写调用，绝不改变真实设备授权。 */
@RunWith(AndroidJUnit4.class)
public class ModelSelectionDeviceTest {
    /** 控件与截图测试入口。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 动态选项快照，包含超过一屏的列表。 */
    private LicenseModels models() {
        ArrayList<LicenseModel> models = new ArrayList<>();
        for (int i = 0; i < 14; i++) models.add(new LicenseModel("future-" + i,
                i == 0 ? "动态识别模型 A" : "动态识别模型 " + i + "（长名称换行测试）", "chip", i == 0));
        return new LicenseModels(0, null, models);
    }
    /** 只读核对模型查询与授权快照；不打印激活码，查询失败不能推断未激活。 */
    @Test public void actualDeviceModelQueryReturnsReceipt() {
        HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
        client.connect();
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (!client.isConnected() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        StatusSnapshot status = client.getStatus();
        CertInfo cert = client.getCertInfo();
        android.util.Log.i("ModelSelectionTest", "status_code=" + (status == null ? null : status.getCode())
                + " state=" + (status == null ? null : status.getSdkState())
                + " certificate_valid=" + (status == null ? null : status.getCertificateValid())
                + " model_ready=" + (status == null ? null : status.getModelReady())
                + " model_version=" + (status == null ? null : status.getModelVersion())
                + " recent_error=" + (status == null ? null : status.getRecentError()));
        android.util.Log.i("ModelSelectionTest", "cert_code=" + (cert == null ? null : cert.getCode())
                + " model_code=" + (cert == null ? null : cert.getModelCode())
                + " has_serial=" + (cert != null && cert.getActivationSerial() != null)
                + " expires_at=" + (cert == null ? null : cert.getExpiresAt()));
        LicenseModels reply = client.queryDeviceModels();
        assertNotNull("模型查询须返回回执", reply);
        android.util.Log.i("ModelSelectionTest", "model_query_code=" + reply.getCode()
                + " reason=" + reply.getReason()
                + " model_count=" + reply.getModels().size());
    }

    /** 卡片、当前项、取消、长列表及提交中的旋转恢复。 */
    @Test public void switchingLayoutAndSessionSurviveBothOrientations() throws Exception {
        SdkChannelManager manager = SdkChannelManager.getInstance();
        HuoyanSdkClient real = manager.getClient();
        Field sessionField = SdkChannelManager.class.getDeclaredField("session"); sessionField.setAccessible(true);
        Object connection = sessionField.get(manager);
        Field clientField = connection.getClass().getDeclaredField("client"); clientField.setAccessible(true);
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<CountDownLatch> release = new AtomicReference<>(new CountDownLatch(0));
        HuoyanSdkClient fake = (HuoyanSdkClient) Proxy.newProxyInstance(real.getClass().getClassLoader(),
                new Class[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isConnected": return true;
                        case "queryDeviceModels": return models();
                        case "getCertInfo": return new CertInfo(0, null, "future-0", "stable", null,
                                0, 0, 0, false, 0, false, 0, null, 0, "TEST-LICENSE");
                        case "activate":
                            assertEquals("TEST-LICENSE", args[0]); assertEquals("future-13", args[1]);
                            writes.incrementAndGet(); release.get().await(15, TimeUnit.SECONDS);
                            return new BaseResult(0, null);
                        default:
                            try { return method.invoke(real, args); }
                            catch (InvocationTargetException error) { throw error.getCause(); }
                    }
                });
        try {
            synchronized (connection) { clientField.set(connection, fake); }
            int targetOrientation = "portrait".equals(InstrumentationRegistry.getArguments().getString("modelOrientation"))
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            for (int orientation : new int[]{targetOrientation}) {
                Intent intent = new Intent(ins.getTargetContext(), SettingsPageActivity.class).putExtra("page", "recognition");
                try (ActivityScenario<SettingsPageActivity> scenario = ActivityScenario.launch(intent)) {
                    scenario.onActivity(activity -> activity.setRequestedOrientation(orientation)); settle();
                    scenario.onActivity(activity -> {
                        assertEquals(orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                ? Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE,
                                activity.getResources().getConfiguration().orientation);
                        View name = activity.findViewById(R.id.currentModel), version = activity.findViewById(R.id.modelVersion);
                        assertSame(name.getParent(), version.getParent());
                        visible(name); visible(version); visible(activity.findViewById(R.id.switchModel));
                        visible(activity.findViewById(R.id.saveSettings));
                        assertEquals("动态识别模型 A", ((TextView) name).getText().toString());
                    });
                    capture("settings-" + orientation);
                    scenario.onActivity(activity -> activity.findViewById(R.id.switchModel).performClick()); settle();
                    scenario.onActivity(activity -> {
                        DialogFragment choice = dialog(activity, "selectOption"); assertNotNull(choice);
                        ListView list = choice.requireView().findViewById(R.id.selectOptions);
                        assertEquals(14, list.getCount()); assertEquals(0, list.getCheckedItemPosition());
                        visible(choice.requireView().findViewById(R.id.selectCancel));
                        click(list, 0);
                    }); settle(); assertEquals(0, writes.get());
                    scenario.onActivity(activity -> activity.findViewById(R.id.switchModel).performClick()); settle();
                    scenario.recreate(); settle();
                    scenario.onActivity(activity -> {
                        DialogFragment choice = dialog(activity, "selectOption"); assertNotNull(choice);
                        choice.requireView().findViewById(R.id.selectCancel).performClick();
                    }); settle();
                    scenario.onActivity(activity -> activity.findViewById(R.id.switchModel).performClick()); settle();
                    scenario.onActivity(activity -> ((ListView) dialog(activity, "selectOption").requireView()
                            .findViewById(R.id.selectOptions)).setSelection(13)); settle();
                    capture("model-list-" + orientation);
                    release.set(new CountDownLatch(1));
                    scenario.onActivity(activity -> click(dialog(activity, "selectOption").requireView().findViewById(R.id.selectOptions), 13));
                    settle(); scenario.recreate(); settle();
                    scenario.onActivity(activity -> {
                        assertNotNull(dialog(activity, "modelLoading"));
                        assertNull(dialog(activity, "selectOption"));
                    });
                    release.get().countDown(); settle();
                    scenario.onActivity(activity -> assertNull(dialog(activity, "modelLoading")));
                }
            }
            assertEquals(1, writes.get());
        } finally {
            release.get().countDown();
            synchronized (connection) { clientField.set(connection, real); }
        }
    }
    /** 读取统一会话内的弹窗。 */
    private DialogFragment dialog(SettingsPageActivity activity, String tag) {
        Fragment flow = activity.getSupportFragmentManager().findFragmentByTag("modelActivation");
        return flow == null ? null : (DialogFragment) flow.getChildFragmentManager().findFragmentByTag(tag);
    }
    /** 使用列表真实回调，值映射由被测弹窗执行。 */
    private void click(ListView list, int position) {
        list.performItemClick(list.getChildAt(position - list.getFirstVisiblePosition()), position, list.getAdapter().getItemId(position));
    }
    /** 固定等待主线程布局，不等待真实授权服务写入。 */
    private void settle() { ins.waitForIdleSync(); SystemClock.sleep(800); ins.waitForIdleSync(); }
    /** 验证关键控件完整可见。 */
    private void visible(View view) {
        Rect rect = new Rect(); assertTrue(view.getGlobalVisibleRect(rect));
        assertEquals(view.getWidth(), rect.width()); assertEquals(view.getHeight(), rect.height());
    }
    /** 导出真实截图，便于与设计稿逐项比较。 */
    private void capture(String name) throws Exception {
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        File dir = new File(ins.getTargetContext().getExternalFilesDir(null), "model-selection-review"); dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { bitmap.recycle(); }
    }
}
