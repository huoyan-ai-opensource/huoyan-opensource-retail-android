package cc.huoyan.retail.checkout;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.device.scale.AclasScaleManager;
import cc.huoyan.retail.settings.ExitPasswordStore;
import cc.huoyan.retail.settings.SettingsPageActivity;
import cc.huoyan.retail.settings.ExitPasswordDialog;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** 在当前设备方向验收新识别页；不写商品，不发送真实秤命令。 */
@RunWith(AndroidJUnit4.class)
public class RecognitionDesignDeviceTest {
    /** 当前设备测试调度器。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 本用例创建的识别页面。 */
    private MainActivity main;
    /** 等待异步状态，最长八秒。 */
    private void await(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < end && !condition.getAsBoolean()) SystemClock.sleep(80);
        assertTrue(condition.getAsBoolean());
    }
    /** 主线程反射访问用于冻结设备输入，不改生产接口。 */
    private Object field(String name) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(main); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    /** 调用识别生命周期内部动作，仅用于固定测试画面。 */
    private void call(String name) {
        try { Method m = MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(main); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    /** 取得当前弹窗，未挂载时返回 null。 */
    private DialogFragment dialog(String tag) { return (DialogFragment) main.getSupportFragmentManager().findFragmentByTag(tag); }
    /** 查找维护页子 Fragment 中的密码弹窗。 */
    private DialogFragment setupDialog(SettingsPageActivity page) {
        for (androidx.fragment.app.Fragment fragment : page.getSupportFragmentManager().getFragments()) {
            androidx.fragment.app.Fragment found = fragment.getChildFragmentManager().findFragmentByTag("exitPassword");
            if (found instanceof DialogFragment) return (DialogFragment) found;
        }
        return null;
    }
    /** 保存真实运行截图供设计比对。 */
    private void shot(String name) throws Exception {
        ins.waitForIdleSync(); SystemClock.sleep(2600);
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        File dir = new File(ins.getTargetContext().getExternalFilesDir(null), "recognition-design"); dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle();
    }
    /** 回滚本用例私有偏好，保留此前所有用户配置。 */
    private void restore(SharedPreferences prefs, Map<String, ?> old) {
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : old.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) edit.putBoolean(entry.getKey(), (Boolean)value);
            else if (value instanceof String) edit.putString(entry.getKey(), (String)value);
            else if (value instanceof Integer) edit.putInt(entry.getKey(), (Integer)value);
        }
        edit.commit();
    }
    /** 布局、等待动画、去皮精度、自检、密码及维护入口的完整交互回归。 */
    @Test public void recognitionLayoutAndDialogs() throws Exception {
        SharedPreferences app = ins.getTargetContext().getSharedPreferences("HuoYanApplication", 0);
        boolean weighing = ModuleConfig.isWeighingEnabled(ins.getTargetContext());
        SharedPreferences guard = ins.getTargetContext().getSharedPreferences("checkout_exit_guard", 0);
        Map<String, ?> previous = guard.getAll();
        SettingsPageActivity settings = null;
        try {
            ModuleConfig.setWeighingEnabled(ins.getTargetContext(), true);
            ExitPasswordStore.setEnabled(ins.getTargetContext(), false);
            main = (MainActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ins.waitForIdleSync(); SystemClock.sleep(1200);
            ins.runOnMainSync(() -> {
                call("stopDetect"); call("closeScale");
                CheckoutPresenter presenter = (CheckoutPresenter) field("presenter");
                presenter.restart(); presenter.clearRows(); presenter.render();
            });
            await(() -> main.findViewById(R.id.trayGuide).isShown());
            SystemClock.sleep(500);
            ins.runOnMainSync(() -> {
                assertEquals("—", ((TextView)main.findViewById(R.id.weightTv)).getText().toString());
                View net = main.findViewById(R.id.netWeightCard), tare = main.findViewById(R.id.tareWeightCard), gross = main.findViewById(R.id.grossWeightCard);
                assertTrue(net.getWidth() > 0); assertEquals(net.getTop(), gross.getTop()); assertTrue(tare.getLeft() > net.getLeft());
                assertTrue(main.findViewById(R.id.checkoutSummary).isShown());
                assertFalse(main.findViewById(R.id.modifyGoods).isEnabled());
                assertFalse(main.findViewById(R.id.confirm).isEnabled());
                android.widget.ImageView image = main.findViewById(R.id.trayGuide);
                assertTrue(image.getDrawable() instanceof com.bumptech.glide.load.resource.gif.GifDrawable);
                assertTrue(((com.bumptech.glide.load.resource.gif.GifDrawable)image.getDrawable()).isRunning());
            });
            shot("waiting");
            ins.runOnMainSync(() -> {
                cc.huoyan.retail.checkout.domain.CheckoutSession transition = new cc.huoyan.retail.checkout.domain.CheckoutSession();
                transition.onWeight(new java.math.BigDecimal("0.500"), java.math.BigDecimal.ZERO, true);
                main.render(new CheckoutViewState(transition.state(), java.util.Collections.emptyList(), java.util.Collections.emptySet(), java.util.Collections.emptySet(), false));
                assertFalse(main.findViewById(R.id.emptyPanel).isShown());
                transition.lockRecognition();
                main.render(new CheckoutViewState(transition.state(), java.util.Collections.emptyList(), java.util.Collections.emptySet(), java.util.Collections.emptySet(), false));
                assertFalse(main.findViewById(R.id.emptyPanel).isShown());
                assertFalse(main.findViewById(R.id.trayGuide).isShown());
            });
            ins.runOnMainSync(() -> {
                cc.huoyan.retail.checkout.domain.CheckoutSession sale = new cc.huoyan.retail.checkout.domain.CheckoutSession();
                sale.lockRecognition();
                CheckoutViewState empty = new CheckoutViewState(sale.state(), java.util.Collections.emptyList(), java.util.Collections.emptySet(), java.util.Collections.emptySet(), true);
                main.render(empty);
                assertFalse(main.findViewById(R.id.trayGuide).isShown());
                assertEquals(main.getString(R.string.checkout_no_match), ((TextView) main.findViewById(R.id.emptyTitle)).getText().toString());
            });
            shot("no-match");
            ins.runOnMainSync(() -> {
                cc.huoyan.retail.data.GoodsEntity goods = new cc.huoyan.retail.data.GoodsEntity();
                goods.setPlu("ui-test"); goods.setName("验收商品"); goods.setPrice(1280); goods.setUnit(5);
                GoodsVo row = new GoodsVo(2, 2560, goods, java.util.Collections.emptyList());
                cc.huoyan.retail.checkout.domain.CheckoutSession sale = new cc.huoyan.retail.checkout.domain.CheckoutSession();
                sale.lockRecognition();
                sale.replaceLines(java.util.Collections.singletonList(new cc.huoyan.retail.checkout.domain.SaleLine("ui-test", "验收商品", 1280, 0, 2, cc.huoyan.retail.pricing.PricingMode.COUNT, false, false, null)));
                CheckoutViewState populated = new CheckoutViewState(sale.state(), java.util.Collections.singletonList(row), java.util.Collections.emptySet(), java.util.Collections.emptySet(), true);
                main.renderRows(populated, true); main.render(populated);
                assertFalse(main.findViewById(R.id.emptyPanel).isShown());
                assertEquals("¥ 25.60", ((TextView)main.findViewById(R.id.summaryAmount)).getText().toString());
            });
            shot("populated");
            ins.runOnMainSync(() -> main.findViewById(R.id.netWeightCard).performClick());
            await(() -> dialog("scaleOperations") != null && dialog("scaleOperations").getView() != null);
            ins.runOnMainSync(() -> {
                View root = dialog("scaleOperations").requireView();
                root.findViewById(R.id.clearInput).performClick();
                for (int id : new int[]{R.id.num0,R.id.dot,R.id.num1,R.id.num2,R.id.num3,R.id.num4}) root.findViewById(id).performClick();
                assertEquals("0.123", ((TextView)root.findViewById(R.id.tareInput)).getText().toString());
                root.findViewById(R.id.backspace).performClick();
                assertEquals("0.12", ((TextView)root.findViewById(R.id.tareInput)).getText().toString());
                root.findViewById(R.id.num3).performClick();
            });
            shot("scale");
            ins.runOnMainSync(() -> dialog("scaleOperations").requireView().findViewById(R.id.confirmTare).performClick());
            await(() -> !((ScaleOperationsDialog.Operation)new androidx.lifecycle.ViewModelProvider(dialog("scaleOperations")).get(ScaleOperationsDialog.Operation.class)).busy);
            ins.runOnMainSync(() -> dialog("scaleOperations").dismiss()); ins.waitForIdleSync();
            ins.runOnMainSync(() -> main.findViewById(R.id.recognizeStatus).performClick());
            await(() -> dialog("sdkStatus") != null && dialog("sdkStatus").getView() != null);
            await(() -> !new androidx.lifecycle.ViewModelProvider(dialog("sdkStatus")).get(SdkStatusDialog.StatusModel.class).busy);
            shot("sdk");
            ins.runOnMainSync(() -> {
                DialogFragment sdk = dialog("sdkStatus");
                SdkStatusDialog.StatusModel model = new androidx.lifecycle.ViewModelProvider(sdk).get(SdkStatusDialog.StatusModel.class);
                model.connected = false;
                model.failure = String.join("\n", java.util.Collections.nCopies(30, "原始异常：设备服务断开，等待重新连接"));
                model.snapshot.setValue(null);
            });
            ins.waitForIdleSync();
            ins.runOnMainSync(() -> {
                View root = dialog("sdkStatus").requireView();
                assertEquals(main.getString(R.string.checkout_sdk_abnormal), ((TextView)root.findViewById(R.id.sdkHealth)).getText().toString());
                android.graphics.Rect visible = new android.graphics.Rect();
                View refresh = root.findViewById(R.id.refreshSdk);
                assertTrue(refresh.getGlobalVisibleRect(visible));
                assertEquals(refresh.getHeight(), visible.height());
                assertTrue(((TextView)root.findViewById(R.id.sdkDetails)).getText().toString().contains("原始异常"));
            });
            shot("sdk-error");
            ins.runOnMainSync(() -> dialog("sdkStatus").dismiss()); ins.waitForIdleSync();
            assertTrue(ExitPasswordStore.save(ins.getTargetContext(), "1357", true));
            ins.runOnMainSync(() -> { main.findViewById(R.id.version).performClick(); main.findViewById(R.id.version).performClick(); });
            assertNull(dialog("exitPassword"));
            ins.runOnMainSync(() -> main.findViewById(R.id.version).performClick());
            await(() -> dialog("exitPassword") != null && dialog("exitPassword").getView() != null);
            ins.runOnMainSync(() -> {
                View root = dialog("exitPassword").requireView(); root.findViewById(R.id.num9).performClick(); root.findViewById(R.id.confirmPassword).performClick();
                assertFalse(main.isFinishing()); root.findViewById(R.id.dot).performClick();
            });
            shot("password");
            ins.runOnMainSync(() -> {
                View root = dialog("exitPassword").requireView();
                for (int id : new int[]{R.id.num1,R.id.num3,R.id.num5,R.id.num7}) root.findViewById(id).performClick();
                root.findViewById(R.id.confirmPassword).performClick();
            });
            await(() -> main.isDestroyed());
            settings = (SettingsPageActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), SettingsPageActivity.class)
                    .putExtra("page", "maintenance").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ins.waitForIdleSync(); SettingsPageActivity page = settings;
            guard.edit().clear().commit();
            ins.runOnMainSync(() -> {
                android.widget.Switch toggle = page.findViewById(R.id.exitPasswordEnabled);
                assertTrue(toggle instanceof cc.huoyan.retail.common.ui.LandscapeSwitch);
                toggle.setChecked(false); toggle.performClick();
                assertFalse(toggle.isChecked());
            });
            await(() -> setupDialog(page) != null && setupDialog(page).getView() != null);
            shot("password-setup");
            ins.runOnMainSync(() -> setupDialog(page).requireView().findViewById(R.id.closeDialog).performClick());
            ins.waitForIdleSync();
            assertFalse(ExitPasswordStore.isEnabled(page));
            ins.runOnMainSync(() -> page.findViewById(R.id.exitPasswordEnabled).performClick());
            await(() -> setupDialog(page) != null && setupDialog(page).getView() != null);
            ins.runOnMainSync(() -> {
                View root = setupDialog(page).requireView();
                root.findViewById(R.id.num1).performClick(); root.findViewById(R.id.num2).performClick();
                root.findViewById(R.id.passwordRepeat).performClick();
                root.findViewById(R.id.num1).performClick(); root.findViewById(R.id.num2).performClick();
                root.findViewById(R.id.confirmPassword).performClick();
            });
            ins.waitForIdleSync();
            assertTrue(ExitPasswordStore.isEnabled(page)); assertTrue(ExitPasswordStore.verify(page, "12"));
            ins.runOnMainSync(() -> assertTrue(((android.widget.Switch)page.findViewById(R.id.exitPasswordEnabled)).isChecked()));
            shot("maintenance");
        } finally {
            if (main != null && !main.isDestroyed()) ins.runOnMainSync(main::finish);
            if (settings != null) { SettingsPageActivity page = settings; ins.runOnMainSync(page::finish); }
            restore(guard, previous);
            ModuleConfig.setWeighingEnabled(ins.getTargetContext(), weighing);
        }
    }
}
