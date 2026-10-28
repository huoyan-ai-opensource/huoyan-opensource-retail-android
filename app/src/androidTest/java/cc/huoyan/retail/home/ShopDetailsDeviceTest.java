package cc.huoyan.retail.home;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import androidx.test.core.app.ActivityScenario;
import java.util.concurrent.atomic.AtomicReference;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.sdk.client.model.BaseResult;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 显式启用的真实门店资料验收；测试参数必须指向设备当前绑定门店，避免切换识别库维度。 */
@RunWith(AndroidJUnit4.class)
public final class ShopDetailsDeviceTest {
    /** 仪器入口；控件和协调器操作只在主线程执行。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 跟踪旋转重建后的首页，并在每次验收后关闭。 */
    private ActivityScenario<HomeActivity> scenario;
    /** 外部传入的门店、通道、方向及后端期望值。 */
    private final Bundle args = InstrumentationRegistry.getArguments();

    /** 走实际登录协调器、客户端 JAR 和 SDK 壳，验证持久化及弹窗。 */
    @Test public void bindAndDisplay() throws Exception {
        enabled();
        SdkChannelManager manager = SdkChannelManager.getInstance();
        SdkConfig.Channel original = manager.getChannel();
        manager.setChannel(SdkConfig.Channel.fromValue(args.getString("channel", "aidl")));
        try {
            HuoyanSdkClient client = manager.getClient();
            client.connect();
            await("SDK 连接", client::isConnected);
            BaseResult init = client.initialize();
            assertNotNull(init);
            assertEquals(init.getReason(), 0, init.getCode());
            HomeActivity activity = openDialog();
            try {
                CloudServicePresenter presenter = new ViewModelProvider(activity).get(CloudServiceModel.class).presenter;
                awaitUi("查询完成", () -> !presenter.busy);
                long generation = SPManager.getInstance().getBindGeneration();
                ins.runOnMainSync(() -> presenter.run(CloudServicePresenter.Action.BIND, args.getString("externalShopId")));
                awaitUi("真实绑定回执", () -> !presenter.busy
                        && SPManager.getInstance().getBindGeneration() > generation);
                assertStored();
                assertDisplayed(activity);
                CloudServicePresenter.Result rejected = new CloudServiceSource().run(CloudServicePresenter.Action.BIND, " ");
                assertFalse(rejected.success);
                assertStored();
                screenshot("bound");
            } finally { scenario.close(); }
        } finally { manager.setChannel(original); }
    }

    /** 单独启动一次 instrumentation，验证客户端进程重启后仍可显示已保存资料，不重新绑定。 */
    @Test public void persistedDetailsAfterRestart() throws Exception {
        enabled();
        assertStored();
        HomeActivity activity = openDialog();
        try { assertDisplayed(activity); screenshot("persisted"); }
        finally { scenario.close(); }
    }

    /** 明确 opt-in，并要求操作者先核对设备当前门店与参数。 */
    private void enabled() {
        assumeTrue("true".equals(args.getString("realShopDetailsTest")));
        assertNotNull(args.getString("expectedShopId"));
        assertNotNull(args.getString("externalShopId"));
    }

    /** 打开首页真实入口，通过 ActivityScenario 获取旋转重建后的当前 Activity。 */
    private HomeActivity openDialog() {
        scenario = ActivityScenario.launch(new Intent(ins.getTargetContext(), HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean portrait = "port".equals(args.getString("orientation"));
        scenario.onActivity(activity -> activity.setRequestedOrientation(portrait
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        AtomicReference<HomeActivity> current = new AtomicReference<>();
        await("页面旋转完成", () -> {
            scenario.onActivity(activity -> {
                if (activity.getResources().getConfiguration().orientation == (portrait
                        ? Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE)) current.set(activity);
            });
            return current.get() != null;
        });
        HomeActivity activity = current.get();
        ins.waitForIdleSync();
        ins.runOnMainSync(() -> activity.findViewById(R.id.homeCloudStatus).performClick());
        awaitUi("云端弹窗", () -> dialogRoot(activity) != null);
        return activity;
    }

    /** 核对归属与资料来自本次真实回执，包含空字符串覆盖旧资料的情形。 */
    private void assertStored() {
        SPManager store = SPManager.getInstance();
        assertEquals(args.getString("expectedShopId"), store.getString(SPManager.SP_SHOP_ID));
        assertEquals(args.getString("shopAddress", ""), store.getString(SPManager.SP_SHOP_ADDRESS));
        assertEquals(args.getString("shopPhone", ""), store.getString(SPManager.SP_SHOP_PHONE));
    }

    /** 核对现有控件展示实际资料或未填写占位；关闭按钮必须可用。 */
    private void assertDisplayed(HomeActivity activity) {
        ins.runOnMainSync(() -> {
            View root = dialogRoot(activity);
            String address = args.getString("shopAddress", "");
            String phone = args.getString("shopPhone", "");
            String empty = activity.getString(R.string.cloud_not_filled);
            assertEquals(activity.getString(R.string.cloud_shop_address, address.isEmpty() ? empty : address),
                    ((TextView) root.findViewById(R.id.cloudShopAddress)).getText().toString());
            assertEquals(activity.getString(R.string.cloud_shop_phone, phone.isEmpty() ? empty : phone),
                    ((TextView) root.findViewById(R.id.cloudShopPhone)).getText().toString());
            assertTrue(root.findViewById(R.id.close).isShown());
        });
    }

    /** 读取当前弹窗，不缓存已销毁的 View。 */
    private View dialogRoot(HomeActivity activity) {
        CloudServiceDialog dialog = (CloudServiceDialog) activity.getSupportFragmentManager().findFragmentByTag("cloudInfo");
        return dialog == null || dialog.getDialog() == null ? null : dialog.requireDialog().getWindow().getDecorView();
    }

    /** 等待异步链路，超时失败，不把未完成视为通过。 */
    private void await(String label, BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 30000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        assertTrue(label, condition.getAsBoolean());
    }

    /** 在主线程检查页面状态，避免跨线程读取 Fragment。 */
    private void awaitUi(String label, BooleanSupplier condition) {
        await(label, () -> {
            AtomicBoolean ready = new AtomicBoolean();
            ins.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            return ready.get();
        });
    }

    /** 保存设备实际显示截图，供人工核对布局与文本。 */
    private void screenshot(String phase) throws Exception {
        ins.waitForIdleSync();
        File directory = new File(ins.getTargetContext().getExternalFilesDir(null), "shop-details");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File file = new File(directory, args.getString("channel", "aidl") + "-"
                + args.getString("orientation", "land") + "-" + phase + ".png");
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream out = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { bitmap.recycle(); }
    }
}
