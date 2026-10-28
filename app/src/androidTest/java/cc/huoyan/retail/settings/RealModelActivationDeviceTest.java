package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 显式启用的真实激活验收，会修改测试设备授权；普通测试运行一律跳过。 */
@RunWith(AndroidJUnit4.class)
public class RealModelActivationDeviceTest {
    /** 测试线程使用的仪器入口。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 仅在用户指定的实机验收命令中开启。 */
    private HuoyanSdkClient connected() {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("realModelTest")));
        HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
        client.connect();
        long end = SystemClock.elapsedRealtime() + 15000;
        while (!client.isConnected() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
        assertTrue("SDK 连接", client.isConnected());
        return client;
    }
    /** 通过公开 SDK 接口设置测试服务地址并查询实际回执。 */
    @Test public void configureEndpoint() {
        HuoyanSdkClient client = connected();
        BaseResult configured = client.setBaseUrl(InstrumentationRegistry.getArguments().getString("backendUrl"));
        assertNotNull(configured); assertEquals(configured.getReason(), 0, configured.getCode());
        // 换址自行触发运行时重建，等待收敛，不与壳的初始化争用。
        long end = SystemClock.elapsedRealtime() + 20000;
        while (SystemClock.elapsedRealtime() < end) {
            StatusSnapshot pending = client.getStatus();
            if (pending != null && ("READY".equals(pending.getSdkState())
                    || "NOT_ACTIVATED".equals(pending.getSdkState()) || "REGISTERED".equals(pending.getSdkState()))) break;
            SystemClock.sleep(200);
        }
        StatusSnapshot status = client.getStatus();
        Log.i("RealModelTest", "endpoint_configured state=" + status.getSdkState() + " device_id=" + status.getDeviceId());
        LicenseModels models = client.queryDeviceModels();
        assertNotNull(models);
        Log.i("RealModelTest", "query_code=" + models.getCode() + " count=" + models.getModels().size());
        for (LicenseModel model : models.getModels()) Log.i("RealModelTest", "model=" + model.getModelCode()
                + " name=" + model.getModelName() + " current=" + model.isCurrent());
    }
    /** 已有测试授权双查询只读验证，不提交激活。 */
    @Test public void bothModelQueriesAreReadOnly() {
        HuoyanSdkClient client = connected();
        CertInfo before = client.getCertInfo();
        String code = before.getActivationSerial();
        assertTrue("测试设备应有已保存的激活码", code != null && !code.trim().isEmpty());
        LicenseModels byCode = client.queryLicenseModels(code);
        LicenseModels byDevice = client.queryDeviceModels();
        assertEquals(0, byCode.getCode());
        assertEquals(0, byDevice.getCode());
        assertFalse(byCode.getModels().isEmpty());
        assertEquals(byDevice.getModels().size(), byCode.getModels().size());
        for (int i = 0; i < byCode.getModels().size(); i++)
            assertEquals(byDevice.getModels().get(i).getModelCode(), byCode.getModels().get(i).getModelCode());
        assertEquals(before.getActivationSerial(), client.getCertInfo().getActivationSerial());
    }

    /** 全新安装后通过真实输入按钮激活，再通过识别设置切换并核验证书和列表。 */
    @Test public void freshActivationThenSwitch() throws Exception {
        HuoyanSdkClient client = connected();
        String first = InstrumentationRegistry.getArguments().getString("firstModel");
        String second = InstrumentationRegistry.getArguments().getString("secondModel");
        File credential = new File(ins.getTargetContext().getFilesDir(), "model-test-license");
        String code = new String(Files.readAllBytes(credential.toPath()), StandardCharsets.UTF_8).trim().replace("-", "");
        assertTrue("激活码须适用于真实键盘", ActivationKeyboardInput.complete(code));
        StatusSnapshot before = client.getStatus();
        assertFalse("验收起点必须未激活", Boolean.TRUE.equals(before.getModelReady()));
        try (ActivityScenario<SettingsPageActivity> scenario = ActivityScenario.launch(new Intent(ins.getTargetContext(),
                SettingsPageActivity.class).putExtra("page", "authorization"))) {
            scenario.onActivity(a -> a.findViewById(R.id.activate).performClick());
            waitDialog(scenario, "activateCode");
            scenario.onActivity(a -> {
                android.view.View root = dialog(a, "activateCode").requireView();
                root.findViewById(R.id.activationClear).performClick();
                for (char c : code.toCharArray()) root.findViewWithTag("activation-key-" + c).performClick();
                root.findViewById(R.id.activate).performClick();
            });
            waitDialog(scenario, "selectOption");
            assertFalse("选模前不能激活", Boolean.TRUE.equals(client.getStatus().getModelReady()));
            screenshot("fresh-model-list");
            choose(scenario, first, client.queryLicenseModels(code));
            awaitReady(client, first);
            waitClosed(scenario);
        } finally { credential.delete(); }
        CertInfo initial = client.getCertInfo();
        assertNotNull("首次激活必须保存序列号供切换使用", initial.getActivationSerial());
        switchFrom(client, initial, second);
        Log.i("RealModelTest", "PASS fresh_activation=" + first + " switched=" + second
                + " expiry_preserved=true");
    }
    /** 独立复测已激活设备的真实切换入口，不进行首次激活。 */
    @Test public void switchActivatedDevice() throws Exception {
        HuoyanSdkClient client = connected();
        CertInfo initial = client.getCertInfo();
        assertNotNull(initial); assertEquals(0, initial.getCode());
        String second = InstrumentationRegistry.getArguments().getString("secondModel");
        assertNotEquals(initial.getModelCode(), second);
        switchFrom(client, initial, second);
        Log.i("RealModelTest", "PASS switch=" + initial.getModelCode() + "->" + second + " expiry_preserved=true");
    }
    /** SDK 重装并轮换后，选模才补码；取消不写入，成功后可直接切回原模型。 */
    @Test public void rotatedDeviceRequestsCodeAfterSelection() throws Exception {
        HuoyanSdkClient client = connected();
        CertInfo initial = client.getCertInfo();
        assertNotNull(initial); assertEquals(0, initial.getCode());
        assertTrue("轮换后本地不得有旧激活码", initial.getActivationSerial() == null
                || initial.getActivationSerial().trim().isEmpty());
        String second = InstrumentationRegistry.getArguments().getString("secondModel");
        assertNotEquals(initial.getModelCode(), second);
        File credential = new File(ins.getTargetContext().getFilesDir(), "model-test-license");
        String code = new String(Files.readAllBytes(credential.toPath()), StandardCharsets.UTF_8).trim().replace("-", "");
        assertTrue(ActivationKeyboardInput.complete(code));
        try (ActivityScenario<SettingsPageActivity> scenario = ActivityScenario.launch(new Intent(ins.getTargetContext(),
                SettingsPageActivity.class).putExtra("page", "recognition"))) {
            scenario.onActivity(a -> a.findViewById(R.id.switchModel).performClick());
            waitDialog(scenario, "selectOption");
            screenshot("rotation-model-list");
            choose(scenario, second, client.queryDeviceModels());
            waitDialog(scenario, "activateCode");
            scenario.onActivity(a -> {
                android.view.View root = dialog(a, "activateCode").requireView();
                root.findViewById(R.id.activationClear).performClick();
                assertFalse("空码禁止提交", root.findViewById(R.id.activate).isEnabled());
            });
            screenshot("rotation-missing-code");
            assertEquals(initial.getModelCode(), client.getCertInfo().getModelCode());
            scenario.onActivity(a -> dialog(a, "activateCode").requireView().findViewById(R.id.cancel).performClick());
            waitClosed(scenario);
            assertEquals("取消补码不得切换", initial.getModelCode(), client.getCertInfo().getModelCode());
            scenario.onActivity(a -> a.findViewById(R.id.switchModel).performClick());
            waitDialog(scenario, "selectOption");
            choose(scenario, second, client.queryDeviceModels());
            waitDialog(scenario, "activateCode");
            scenario.onActivity(a -> {
                android.view.View root = dialog(a, "activateCode").requireView();
                root.findViewById(R.id.activationClear).performClick();
                for (char c : code.toCharArray()) root.findViewWithTag("activation-key-" + c).performClick();
                root.findViewById(R.id.activate).performClick();
            });
            awaitReady(client, second);
            waitClosed(scenario);
            screenshot("rotation-switch-success");
            assertEquals(initial.getExpiresAt(), client.getCertInfo().getExpiresAt());
            assertNotNull("重新激活应保存本次输入", client.getCertInfo().getActivationSerial());
        } finally { credential.delete(); }
        switchFrom(client, client.getCertInfo(), initial.getModelCode());
        Log.i("RealModelTest", "PASS rotation_missing_code=true cancel_no_write=true switched=" + second
                + " restored=" + initial.getModelCode() + " expiry_preserved=true");
    }
    /** 从识别设置发起重新激活，并核对界面、证书与授权有效期。 */
    private void switchFrom(HuoyanSdkClient client, CertInfo initial, String second) throws Exception {
        try (ActivityScenario<SettingsPageActivity> scenario = ActivityScenario.launch(new Intent(ins.getTargetContext(),
                SettingsPageActivity.class).putExtra("page", "recognition"))) {
            SystemClock.sleep(1000);
            screenshot("before-switch");
            scenario.onActivity(a -> a.findViewById(R.id.switchModel).performClick());
            waitDialog(scenario, "selectOption");
            screenshot("switch-model-list");
            choose(scenario, second, client.queryDeviceModels());
            awaitReady(client, second);
            waitClosed(scenario);
            LicenseModels models = client.queryDeviceModels();
            assertEquals(models.getReason(), 0, models.getCode());
            LicenseModel current = null;
            for (LicenseModel model : models.getModels()) if (model.isCurrent()) {
                assertNull("只允许一个当前模型", current); current = model;
            }
            assertNotNull(current); assertEquals(second, current.getModelCode());
            String name = current.getModelName();
            scenario.onActivity(a -> assertEquals(name, ((TextView) a.findViewById(R.id.currentModel)).getText().toString()));
            screenshot("after-switch");
        }
        CertInfo switched = client.getCertInfo();
        assertEquals("切换不得重置有效期", initial.getExpiresAt(), switched.getExpiresAt());
    }
    /** 在真实可选列表中按业务编码定位，点击客户端列表控件。 */
    private void choose(ActivityScenario<SettingsPageActivity> scenario, String code, LicenseModels models) {
        assertNotNull(models); assertEquals(models.getReason(), 0, models.getCode());
        int target = -1;
        for (int i = 0; i < models.getModels().size(); i++) if (code.equals(models.getModels().get(i).getModelCode())) target = i;
        assertTrue("目标模型必须在授权内", target >= 0);
        int index = target;
        scenario.onActivity(a -> {
            ListView list = dialog(a, "selectOption").requireView().findViewById(R.id.selectOptions);
            list.performItemClick(list.getChildAt(index - list.getFirstVisiblePosition()), index, list.getAdapter().getItemId(index));
        });
    }
    /** 仅轮询状态和证书，写请求始终由客户端按钮发起一次。 */
    private void awaitReady(HuoyanSdkClient client, String expected) {
        long end = SystemClock.elapsedRealtime() + 240000;
        String last = null;
        while (SystemClock.elapsedRealtime() < end) {
            StatusSnapshot status = client.getStatus();
            CertInfo cert = client.getCertInfo();
            String state = status == null ? "no_response" : status.getSdkState();
            if (!java.util.Objects.equals(state, last)) {
                Log.i("RealModelTest", "target=" + expected + " state=" + state); last = state;
            }
            if (status != null && Boolean.TRUE.equals(status.getModelReady()) && cert != null
                    && cert.getCode() == 0 && expected.equals(cert.getModelCode())) return;
            SystemClock.sleep(500);
        }
        fail("等待模型就绪超时：" + expected + " state=" + last);
    }
    /** 从当前激活会话取得可见弹窗。 */
    private DialogFragment dialog(SettingsPageActivity activity, String tag) {
        Fragment flow = activity.getSupportFragmentManager().findFragmentByTag("modelActivation");
        return flow == null ? null : (DialogFragment) flow.getChildFragmentManager().findFragmentByTag(tag);
    }
    /** 等待异步模型列表或输入弹窗真正可见。 */
    private void waitDialog(ActivityScenario<SettingsPageActivity> scenario, String tag) {
        long end = SystemClock.elapsedRealtime() + 20000;
        AtomicBoolean shown = new AtomicBoolean();
        do {
            scenario.onActivity(a -> { DialogFragment d = dialog(a, tag); shown.set(d != null && d.getView() != null); });
            if (shown.get()) return;
            SystemClock.sleep(200);
        } while (SystemClock.elapsedRealtime() < end);
        fail("弹窗未出现：" + tag);
    }
    /** 验证完成后 Loading 和选择会话均已结束。 */
    private void waitClosed(ActivityScenario<SettingsPageActivity> scenario) {
        long end = SystemClock.elapsedRealtime() + 15000;
        AtomicBoolean closed = new AtomicBoolean();
        do {
            scenario.onActivity(a -> closed.set(dialog(a, "modelLoading") == null
                    && dialog(a, "selectOption") == null && dialog(a, "activateCode") == null));
            if (closed.get()) { SystemClock.sleep(1000); return; }
            SystemClock.sleep(200);
        } while (SystemClock.elapsedRealtime() < end);
        fail("激活会话未结束");
    }
    /** 保存真实模型名称与当前模型卡片用于视觉验收。 */
    private void screenshot(String name) throws Exception {
        ins.waitForIdleSync();
        SystemClock.sleep(300);
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        File dir = new File(ins.getTargetContext().getExternalFilesDir(null), "real-model-review"); dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { bitmap.recycle(); }
    }
}
