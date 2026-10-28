package cc.huoyan.retail.sdk;

import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.CertInfo;
import cc.huoyan.sdk.client.model.LicenseModels;
import cc.huoyan.sdk.client.model.StatusSnapshot;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 显式开启的日志实机驱动：业务客户端仅初始化、激活和查询，不直接调用日志入口。 */
@RunWith(AndroidJUnit4.class)
public class ClsBusinessFlowDeviceTest {
    /** 分别通过真实 AIDL/WS 调用注册与状态查询；未激活设备同样应具有设备身份。 */
    @Test public void initializeAndQuery() {
        enabled();
        for (SdkConfig config : new SdkConfig[] {SdkConfig.aidl(),
                SdkConfig.websocket("ws://127.0.0.1:18086/ws")}) {
            HuoyanSdkClient client = connect(config);
            try {
                BaseResult result = client.initialize();
                assertNotNull(result);
                assertEquals("初始化受理码", 0, result.getCode());
                StatusSnapshot status = awaitIdentity(client);
                Log.i("ClsBusinessFlow", "channel=" + config.getChannel()
                        + " operation=initialize deviceId=" + status.getDeviceId()
                        + " state=" + status.getSdkState() + " timeMs=" + System.currentTimeMillis());
            } finally { client.close(); }
        }
    }

    /** 激活码只从客户端私有临时文件读取，提交一次后删除，不写源码或诊断输出。 */
    @Test public void activateProvidedLicense() throws Exception {
        enabled();
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getFilesDir(), "cls-e2e-license");
        assertTrue("先通过私有文件提供本次授权的激活码", file.isFile());
        HuoyanSdkClient client = connect(SdkConfig.aidl());
        try {
            String license = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
            assertFalse("激活码不能为空", license.isEmpty());
            awaitIdentity(client);
            LicenseModels models = client.queryLicenseModels(license);
            assertNotNull(models);
            assertEquals("授权模型查询码", 0, models.getCode());
            assertFalse("服务端必须返回适用模型", models.getModels().isEmpty());
            String requested = InstrumentationRegistry.getArguments().getString("modelCode");
            String selected = requested == null ? models.getModels().get(0).getModelCode() : requested;
            assertTrue("所选模型须来自真实授权列表", models.getModels().stream()
                    .anyMatch(model -> selected.equals(model.getModelCode())));
            BaseResult result = client.activate(license, selected);
            assertNotNull(result);
            assertEquals("激活回执码", 0, result.getCode());
            CertInfo cert = client.getCertInfo();
            assertNotNull(cert);
            assertEquals("证书回执码", 0, cert.getCode());
            assertEquals("已验证证书必须对应所选模型", selected, cert.getModelCode());
            assertEquals("本地成功激活关联必须保存真实输入", license, cert.getActivationSerial());
            Log.i("ClsBusinessFlow", "operation=activate result=0 model_code=" + selected
                    + " timeMs=" + System.currentTimeMillis());
        } finally {
            client.close();
            if (file.exists() && !file.delete()) throw new AssertionError("临时激活码文件删除失败");
        }
    }

    /** 普通测试不触发设备注册或授权写入。 */
    private void enabled() {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("clsE2e")));
    }

    /** 使用业务客户端公开接口建立连接，不跨进程读取 SDK 内部状态。 */
    private HuoyanSdkClient connect(SdkConfig config) {
        HuoyanSdkClient client = HuoyanSdkClient.create(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), config);
        client.connect();
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (!client.isConnected() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        if (!client.isConnected()) { client.close(); fail("业务客户端连接 SDK 超时"); }
        return client;
    }

    /** 等待注册完成，模型是否可用不作为未激活日志测试的前置条件。 */
    private StatusSnapshot awaitIdentity(HuoyanSdkClient client) {
        long deadline = SystemClock.elapsedRealtime() + 60000;
        StatusSnapshot status = null;
        do {
            status = client.getStatus();
            if (status != null && status.getCode() == 0 && status.getDeviceId() != null
                    && !status.getDeviceId().isEmpty()
                    && !"INITIALIZING".equals(status.getSdkState())) return status;
            SystemClock.sleep(200);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("等待正式环境注册超时，state="
                + (status == null ? "no_response" : status.getSdkState()));
    }
}
