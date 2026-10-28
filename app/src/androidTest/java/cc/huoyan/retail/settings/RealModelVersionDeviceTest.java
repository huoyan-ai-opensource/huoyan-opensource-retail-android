package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.StatusSnapshot;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 使用已激活测试机核对版本双通道回传及现有设置页，不修改授权或模型。 */
@RunWith(AndroidJUnit4.class)
public class RealModelVersionDeviceTest {
    /** 仪器线程入口，UI 访问通过 ActivityScenario 分派到主线程。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    /** 明确启用后，查询真实 AIDL/WS 回执并验证客户端显示相同版本。 */
    @Test public void currentModelVersionReachesClient() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("realModelVersionTest")));
        String version = query(SdkConfig.aidl());
        assertEquals("AIDL 与 WS 必须返回同一模型版本", version,
                query(SdkConfig.websocket("ws://127.0.0.1:18086/ws")));
        connect(SdkChannelManager.getInstance().getClient());
        try (ActivityScenario<SettingsPageActivity> scenario = ActivityScenario.launch(
                new Intent(ins.getTargetContext(), SettingsPageActivity.class).putExtra("page", "recognition"))) {
            String expected = ins.getTargetContext().getString(R.string.model_version, version);
            AtomicReference<String> displayed = new AtomicReference<>();
            long deadline = SystemClock.elapsedRealtime() + 20000;
            do {
                scenario.onActivity(activity -> displayed.set(
                        ((TextView) activity.findViewById(R.id.modelVersion)).getText().toString()));
                if (expected.equals(displayed.get())) break;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < deadline);
            assertEquals("现有识别设置页必须显示 SDK 实际版本", expected, displayed.get());
            ins.waitForIdleSync();
            Bitmap image = ins.getUiAutomation().takeScreenshot();
            assertNotNull(image);
            File output = new File(ins.getTargetContext().getExternalFilesDir(null), "model-version.png");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
            } finally { image.recycle(); }
            Log.i("RealModelVersionTest", "[Biz] model_version=" + version
                    + " aidl=ok ws=ok ui=" + displayed.get());
        }
    }

    /** 独立客户端只读版本；结束时释放本测试持有的连接。 */
    private String query(SdkConfig config) {
        HuoyanSdkClient client = HuoyanSdkClient.create(ins.getTargetContext(), config);
        try {
            connect(client);
            BaseResult initialized = client.initialize();
            assertNotNull(initialized);
            assertEquals(initialized.getReason(), 0, initialized.getCode());
            long deadline = SystemClock.elapsedRealtime() + 45000;
            StatusSnapshot status;
            do {
                status = client.getStatus();
                if (status != null && status.getCode() == 0 && "READY".equals(status.getSdkState())) break;
                SystemClock.sleep(200);
            } while (SystemClock.elapsedRealtime() < deadline);
            assertNotNull(status);
            assertEquals(status.getReason(), 0, status.getCode());
            assertEquals(status.getRecentError(), "READY", status.getSdkState());
            String version = status.getModelVersion();
            assertNotNull("当前测试模型必须返回展示版本", version);
            assertFalse("不能用空串代替真实版本", version.isEmpty());
            Log.i("RealModelVersionTest", "[Biz] channel=" + config.getChannel() + " version=" + version);
            return version;
        } finally { client.close(); }
    }

    /** 等待跨进程连接完成，不触发激活或模型切换。 */
    private void connect(HuoyanSdkClient client) {
        client.connect();
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (!client.isConnected() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        assertTrue("SDK 连接超时", client.isConnected());
    }
}
