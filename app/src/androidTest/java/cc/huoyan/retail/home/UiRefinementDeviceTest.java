package cc.huoyan.retail.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.settings.SettingsPageActivity;
import cc.huoyan.retail.settings.SdkConnectionFragment;
import cc.huoyan.sdk.client.SdkConfig;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 双方向验证弹窗动态高度和局域网专属控件；状态夹具只修改视图，不写店铺或连接配置。 */
@RunWith(AndroidJUnit4.class)
public class UiRefinementDeviceTest {
    /** 测试线程与主线程交互边界。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    /** 三种云端状态按方向排列，内容收缩且底部操作完整可达。 */
    @Test public void cloudContentFitsWithoutBlankFooter() throws Exception {
        HomeActivity home = (HomeActivity) launch(HomeActivity.class, null);
        CloudServiceDialog dialog = new CloudServiceDialog();
        try {
            capture("home");
            ins.runOnMainSync(() -> dialog.showNow(home.getSupportFragmentManager(), "cloudSizingTest"));
            settle();
            ins.runOnMainSync(() -> new ViewModelProvider(home).get(CloudServiceModel.class).presenter.detach());
            ViewGroup root = (ViewGroup) dialog.requireDialog().findViewById(R.id.close).getParent().getParent();
            for (int state = 0; state < 3; state++) {
                int fixture = state;
                ins.runOnMainSync(() -> {
                    root.findViewById(R.id.cloudMarketing).setVisibility(fixture == 0 ? View.VISIBLE : View.GONE);
                    root.findViewById(R.id.cloudAccount).setVisibility(fixture == 0 ? View.GONE : View.VISIBLE);
                    root.findViewById(R.id.cloudEnable).setVisibility(fixture == 1 ? View.VISIBLE : View.GONE);
                    root.findViewById(R.id.cloudRefresh).setVisibility(View.GONE);
                    root.findViewById(R.id.cloudSyncActions).setVisibility(fixture == 2 ? View.VISIBLE : View.GONE);
                    ((TextView) root.findViewById(R.id.cloudShopInfo)).setText("演示店铺");
                    ((TextView) root.findViewById(R.id.cloudSyncHint)).setText(fixture == 2 ? R.string.cloud_sync_hint : R.string.cloud_enable_hint);
                    root.findViewById(R.id.cloudSyncHint).setVisibility(View.VISIBLE);
                    ((TextView) root.findViewById(R.id.cloudState)).setText(fixture == 2 ? R.string.cloud_enabled : R.string.cloud_disabled);
                    dialog.updateWindowSize();
                });
                settle();
                visible(root.findViewById(R.id.close));
                visible(root.findViewById(state == 0 ? R.id.cloudLogin : state == 1 ? R.id.cloudEnable : R.id.cloudSyncImages));
                View body = root.findViewById(state == 0 ? R.id.cloudMarketing : R.id.cloudAccount);
                assertTrue("底部只有容器 padding", rect(root).bottom - rect(body).bottom <= root.getPaddingBottom() + 2);
                ViewGroup row = state == 0 ? (ViewGroup) ((ViewGroup) body).getChildAt(0) : (ViewGroup) body;
                boolean portrait = home.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_PORTRAIT;
                if (portrait) {
                    assertTrue("上下内容不重叠", rect(row.getChildAt(1)).top > rect(row.getChildAt(0)).bottom);
                    assertEquals("上下模块左边对齐", rect(row.getChildAt(0)).left, rect(row.getChildAt(1)).left);
                    assertEquals("上下模块右边对齐", rect(row.getChildAt(0)).right, rect(row.getChildAt(1)).right);
                } else {
                    assertEquals("左右顶部齐平", rect(row.getChildAt(0)).top, rect(row.getChildAt(1)).top);
                    assertEquals("左右底部齐平", rect(row.getChildAt(0)).bottom, rect(row.getChildAt(1)).bottom);
                    if (state == 0) {
                        ViewGroup details = root.findViewById(R.id.cloudMarketingDetails);
                        assertEquals("介绍末项与二维码模块齐底", rect(root.findViewById(R.id.cloudQrCard)).bottom,
                                rect(details.getChildAt(details.getChildCount() - 1)).bottom);
                        assertTextFits(details);
                    }
                    if (state > 0) assertEquals("按钮贴齐卡片底边", rect(row.getChildAt(0)).bottom,
                            rect(root.findViewById(state == 1 ? R.id.cloudEnable : R.id.cloudSyncImages)).bottom);
                }
                assertTextFits(body);
                capture("cloud-state-" + state);
                if (state > 0) {
                    ins.runOnMainSync(() -> root.findViewById(fixture == 1 ? R.id.cloudEnable : R.id.cloudSyncImages).performClick());
                    settle();
                    androidx.fragment.app.Fragment confirmation = dialog.getChildFragmentManager().findFragmentByTag("alertDialog");
                    assertTrue("开启确认复用应用 AlertDialog", confirmation instanceof cc.huoyan.retail.common.ui.AlertDialog);
                    visible(confirmation.requireView().findViewById(R.id.confirm));
                    visible(confirmation.requireView().findViewById(R.id.cancel));
                    capture(state == 1 ? "cloud-enable-confirm" : "cloud-images-confirm");
                    ins.runOnMainSync(() -> confirmation.requireView().findViewById(R.id.cancel).performClick());
                    settle();
                    assertNull(dialog.getChildFragmentManager().findFragmentByTag("alertDialog"));
                }
            }
            CloudServicePresenter presenter = new ViewModelProvider(home).get(CloudServiceModel.class).presenter;
            for (CloudServicePresenter.Action action : new CloudServicePresenter.Action[]{
                    CloudServicePresenter.Action.GOODS, CloudServicePresenter.Action.IMAGES}) {
                ins.runOnMainSync(() -> {
                    presenter.busy = true; presenter.action = action; dialog.render();
                });
                settle();
                androidx.fragment.app.Fragment loading = dialog.getChildFragmentManager().findFragmentByTag("cloudLoading");
                assertTrue("同步复用 LoadingDialog", loading instanceof cc.huoyan.retail.common.ui.LoadingDialog);
                visible(loading.requireView().findViewById(R.id.textView));
                capture("cloud-loading-" + action.name());
                ins.runOnMainSync(() -> {
                    presenter.busy = false; presenter.action = null; dialog.render();
                });
                settle();
                assertNull("结束后关闭 Loading", dialog.getChildFragmentManager().findFragmentByTag("cloudLoading"));
            }
        } finally {
            ins.runOnMainSync(() -> { dialog.dismissAllowingStateLoss(); home.finish(); });
        }
    }

    /** 三种入口来回切换时颜色、文案与箭头同步恢复；夹具不安装或卸载真实 SDK。 */
    @Test public void homeCheckoutStatesRestoreColorsAndLabels() throws Exception {
        HomeActivity home = (HomeActivity) launch(HomeActivity.class, null);
        java.lang.reflect.Field missing = HomeActivity.class.getDeclaredField("sdkMissing");
        java.lang.reflect.Field presenterField = HomeActivity.class.getDeclaredField("presenter");
        missing.setAccessible(true);
        presenterField.setAccessible(true);
        HomePresenter presenter = (HomePresenter) presenterField.get(home);
        try {
            ins.runOnMainSync(() -> { presenter.pause(); home.showCheckoutStartup(false, HomeCheckoutState.UNAVAILABLE); });
            int[] colors = {R.color.home_checkout_missing, R.color.home_checkout_unactivated,
                    R.color.home_checkout_primary, R.color.home_checkout_missing};
            int[] labels = {R.string.home_install_now, R.string.home_activate_now,
                    R.string.home_enter_checkout, R.string.home_install_now};
            for (int state = 0; state < colors.length; state++) {
                int fixture = state;
                ins.runOnMainSync(() -> {
                    try { missing.setBoolean(home, fixture == 0 || fixture == 3); }
                    catch (IllegalAccessException error) { throw new AssertionError(error); }
                    home.showStatus(new HomePresenter.Status(true, false, "—", fixture != 1 ? HomeCheckoutState.READY : HomeCheckoutState.ACTIVATION_REQUIRED));
                    TextView action = home.findViewById(R.id.homeCheckoutAction);
                    int foreground = home.getColor(fixture == 2 ? R.color.home_checkout_primary : R.color.white);
                    assertEquals(home.getString(labels[fixture]), action.getText().toString());
                    assertEquals(foreground, action.getCurrentTextColor());
                    assertEquals(home.getColor(colors[fixture]), home.findViewById(R.id.enterCheckout)
                            .getBackgroundTintList().getDefaultColor());
                    android.widget.ImageView arrow = home.findViewById(R.id.homeCheckoutArrow);
                    assertEquals(foreground, arrow == null ? action.getCompoundDrawableTintList().getDefaultColor()
                            : arrow.getImageTintList().getDefaultColor());
                });
                settle();
                assertTextFits(home.findViewById(R.id.enterCheckout));
                capture("home-state-" + state);
            }
        } finally { ins.runOnMainSync(home::finish); }
    }

    /** 启动等待优先于旧授权，禁止导航；连接完成恢复按钮，SDK 缺失优先显示安装。 */
    @Test public void homeStartupBlocksCheckoutAndRestoresEntry() throws Exception {
        HomeActivity home = (HomeActivity) launch(HomeActivity.class, null);
        java.lang.reflect.Field missing = HomeActivity.class.getDeclaredField("sdkMissing");
        java.lang.reflect.Field presenterField = HomeActivity.class.getDeclaredField("presenter");
        missing.setAccessible(true);
        presenterField.setAccessible(true);
        HomePresenter presenter = (HomePresenter) presenterField.get(home);
        Instrumentation.ActivityMonitor monitor = ins.addMonitor(cc.huoyan.retail.checkout.MainActivity.class.getName(), null, false);
        try {
            ins.runOnMainSync(presenter::pause);
            for (HomeCheckoutState state : new HomeCheckoutState[]{HomeCheckoutState.UNAVAILABLE, HomeCheckoutState.ACTIVATION_REQUIRED, HomeCheckoutState.READY}) {
                ins.runOnMainSync(() -> {
                    try { missing.setBoolean(home, false); }
                    catch (IllegalAccessException error) { throw new AssertionError(error); }
                    home.showCheckoutStartup(true, HomeCheckoutState.STARTING);
                    home.showStatus(new HomePresenter.Status(false, false, "—", state));
                    View card = home.findViewById(R.id.enterCheckout);
                    TextView action = home.findViewById(R.id.homeCheckoutAction);
                    assertFalse(card.isEnabled());
                    assertEquals(home.getColor(R.color.home_checkout_starting), card.getBackgroundTintList().getDefaultColor());
                    assertEquals(home.getString(R.string.home_checkout_starting), action.getText().toString());
                    assertEquals(home.getColor(R.color.white), action.getCurrentTextColor());
                    View arrow = home.findViewById(R.id.homeCheckoutArrow);
                    if (arrow == null) assertNull(action.getCompoundDrawablesRelative()[2]);
                    else assertEquals(View.GONE, arrow.getVisibility());
                    card.performClick();
                });
                settle();
                assertEquals(0, monitor.getHits());
                assertNull(home.getSupportFragmentManager().findFragmentByTag("modelActivation"));
                assertTextFits(home.findViewById(R.id.enterCheckout));
                capture("home-starting");
                ins.runOnMainSync(() -> {
                    home.showCheckoutStartup(false, state);
                    home.showSdkStartup(false);
                    home.showSdkVersion("2.0.0");
                    home.showStatus(new HomePresenter.Status(false, false, "—", state));
                    assertEquals(home.getString(R.string.home_sdk_version, "2.0.0"),
                            ((TextView) home.findViewById(R.id.homeSdkVersion)).getText().toString());
                    assertEquals(state.actionable(), home.findViewById(R.id.enterCheckout).isEnabled());
                    assertEquals(home.getString(state.action),
                            ((TextView) home.findViewById(R.id.homeCheckoutAction)).getText().toString());
                    try { missing.setBoolean(home, true); }
                    catch (IllegalAccessException error) { throw new AssertionError(error); }
                    home.showCheckoutStartup(true, HomeCheckoutState.STARTING);
                    assertTrue(home.findViewById(R.id.enterCheckout).isEnabled());
                    assertEquals(home.getString(R.string.home_install_now),
                            ((TextView) home.findViewById(R.id.homeCheckoutAction)).getText().toString());
                });
            }
        } finally { ins.removeMonitor(monitor); ins.runOnMainSync(home::finish); }
    }

    /** 轮换状态覆盖原可用卡片，禁用导航；查询失败清除旧状态，恢复后重新开放入口。 */
    @Test public void homeRotationBlocksNavigationAndRecovers() throws Exception {
        HomeActivity home = (HomeActivity) launch(HomeActivity.class, null);
        java.lang.reflect.Field presenterField = HomeActivity.class.getDeclaredField("presenter");
        java.lang.reflect.Field startupField = HomeActivity.class.getDeclaredField("sdkStartup");
        presenterField.setAccessible(true);
        startupField.setAccessible(true);
        HomePresenter presenter = (HomePresenter) presenterField.get(home);
        cc.huoyan.sdk.client.session.SdkStartupController startup =
                (cc.huoyan.sdk.client.session.SdkStartupController) startupField.get(home);
        Instrumentation.ActivityMonitor monitor = ins.addMonitor(
                cc.huoyan.retail.checkout.MainActivity.class.getName(), null, false);
        try {
            ins.runOnMainSync(() -> {
                presenter.pause(); startup.stop();
                home.showCheckoutStartup(false, HomeCheckoutState.READY);
            });
            for (HomeCheckoutState state : new HomeCheckoutState[]{HomeCheckoutState.READY,
                    HomeCheckoutState.ROTATION_PENDING, HomeCheckoutState.UNAVAILABLE,
                    HomeCheckoutState.ROTATION_REJECTED, HomeCheckoutState.ROTATION_RATE_LIMITED,
                    HomeCheckoutState.ROTATION_RESTORING, HomeCheckoutState.READY}) {
                ins.runOnMainSync(() -> {
                    home.showStatus(new HomePresenter.Status(true, false, "2.0", state));
                    View card = home.findViewById(R.id.enterCheckout);
                    TextView action = home.findViewById(R.id.homeCheckoutAction);
                    assertEquals(state.actionable(), card.isEnabled());
                    assertEquals(state.actionable(), action.isEnabled());
                    assertEquals(home.getString(state.hint),
                            ((TextView) home.findViewById(R.id.homeActivationHint)).getText().toString());
                    assertEquals(home.getString(state.action), action.getText().toString());
                    int color = state.isRotation() ? R.color.home_checkout_unactivated
                            : state == HomeCheckoutState.READY ? R.color.home_checkout_primary : R.color.home_checkout_starting;
                    assertEquals(home.getColor(color), card.getBackgroundTintList().getDefaultColor());
                    if (!state.actionable()) {
                        View arrow = home.findViewById(R.id.homeCheckoutArrow);
                        if (arrow == null) assertNull(action.getCompoundDrawablesRelative()[2]);
                        else assertEquals(View.GONE, arrow.getVisibility());
                        card.performClick();
                        assertNull(home.getSupportFragmentManager().findFragmentByTag("modelActivation"));
                    }
                });
                settle();
                assertEquals(0, monitor.getHits());
                assertTextFits(home.findViewById(R.id.enterCheckout));
                if (state == HomeCheckoutState.ROTATION_PENDING) capture("home-rotation-pending");
            }
        } finally { ins.removeMonitor(monitor); ins.runOnMainSync(home::finish); }
    }

    /** 模型名称与版本位于同一卡片内，保存配置入口仍完整可见。 */
    @Test public void modelVersionSharesCurrentModelCard() throws Exception {
        Activity page = launch(SettingsPageActivity.class, "recognition");
        try {
            View version = page.findViewById(R.id.modelVersion);
            visible(version);
            View model = page.findViewById(R.id.currentModel);
            assertSame(model.getParent(), version.getParent());
            assertTrue(rect(version).top >= rect(model).bottom);
            visible(page.findViewById(R.id.switchModel));
            visible(page.findViewById(R.id.saveSettings));
            capture("recognition-model-version");
        } finally { ins.runOnMainSync(page::finish); }
    }

    /** 逐项检查可见文字的真实排版高度，避免两列等高掩盖末行裁切。 */
    private void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            visible(text);
            assertNotNull(text.getLayout());
            assertTrue("文字未裁切：" + text.getText(), text.getLayout().getHeight()
                    <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) assertTextFits(group.getChildAt(index));
        }
    }

    /** Tabs 只切换展示；测试不保存通道，不触发探测和同步。 */
    @Test public void sdkTabsKeepNetworkControlsOnLan() throws Exception {
        SettingsPageActivity page = (SettingsPageActivity) launch(SettingsPageActivity.class, "sdk");
        try {
            SdkConnectionFragment fragment = (SdkConnectionFragment) page.getSupportFragmentManager().getFragments().get(0);
            ins.runOnMainSync(() -> fragment.showChannel(SdkConfig.Channel.AIDL));
            settle();
            assertEquals(View.GONE, page.findViewById(R.id.sdkLanContent).getVisibility());
            assertEquals(View.GONE, page.findViewById(R.id.confirm).getVisibility());
            visible(page.findViewById(R.id.sdkLocalContent));
            capture("sdk-local");
            ins.runOnMainSync(() -> page.findViewById(R.id.sdkWs).performClick());
            settle();
            assertEquals(View.GONE, page.findViewById(R.id.sdkLocalContent).getVisibility());
            for (int id : new int[]{R.id.wsAddress, R.id.wsProbe, R.id.wsDiscover, R.id.confirm}) visible(page.findViewById(id));
            ins.runOnMainSync(() -> fragment.queryResult(true, "", true, java.util.Arrays.asList(
                    "192.168.1.100", "192.168.1.101", "192.168.1.102", "192.168.1.103",
                    "192.168.1.104", "192.168.1.105", "192.168.1.106", "192.168.1.107")));
            settle();
            android.widget.ListView list = page.findViewById(R.id.wsResults);
            capture("sdk-lan");
            assertTrue("结果首屏至少完整显示4条，高度=" + list.getHeight(), list.getHeight() >= Math.round(210 * page.getResources().getDisplayMetrics().density));
            boolean landscape = page.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            if (landscape) {
                assertEquals(rect(page.findViewById(R.id.sdkIpCard)).bottom, rect(page.findViewById(R.id.sdkDiscoveryCard)).bottom);
                assertEquals(rect(page.findViewById(R.id.confirm)).top, rect(page.findViewById(R.id.wsProbe)).top);
            }
            capture("sdk-lan");
            ins.runOnMainSync(() -> list.setSelection(7)); settle();
            assertEquals("列表末条可达", 7, list.getLastVisiblePosition());
            visible(page.findViewById(R.id.confirm)); capture("sdk-lan-last");
        } finally { ins.runOnMainSync(page::finish); }
    }

    /** 表单只输入不保存；指导弹窗只查看，不改变自动提示偏好。 */
    @Test public void productFormAndGuideRemainReachable() throws Exception {
        HomeActivity home = (HomeActivity) launch(HomeActivity.class, null);
        cc.huoyan.retail.goods.AddGoodsDialog form = new cc.huoyan.retail.goods.AddGoodsDialog();
        cc.huoyan.retail.learning.LearningGuideDialog guide = new cc.huoyan.retail.learning.LearningGuideDialog();
        try {
            ins.runOnMainSync(() -> form.showNow(home.getSupportFragmentManager(), "formLayoutTest"));
            settle();
            ins.runOnMainSync(() -> {
                ((TextView) form.requireView().findViewById(R.id.plu)).setText("ABC123!中文");
                assertEquals("ABC123", ((TextView) form.requireView().findViewById(R.id.plu)).getText().toString());
            });
            visible(form.requireView().findViewById(R.id.confirm)); capture("product-form");
            ins.runOnMainSync(form::dismiss); settle();
            ins.runOnMainSync(() -> guide.showNow(home.getSupportFragmentManager(), "guideLayoutTest"));
            settle();
            visible(guide.requireView().findViewById(R.id.confirm));
            visible(guide.requireView().findViewById(R.id.learningGuideDontShowAgain));
            View first = guide.requireView().findViewById(R.id.learningGuideStep1);
            View second = guide.requireView().findViewById(R.id.learningGuideStep2);
            View third = guide.requireView().findViewById(R.id.learningGuideStep3);
            assertEquals("步骤2和3沿用同一间距", rect(second).top - rect(first).bottom, rect(third).top - rect(second).bottom);
            capture("learning-guide");
        } finally {
            ins.runOnMainSync(() -> { if (form.isAdded()) form.dismissAllowingStateLoss(); if (guide.isAdded()) guide.dismissAllowingStateLoss(); home.finish(); });
        }
    }

    /** 首页立即激活直接出现原弹窗；只打开和取消，不提交激活或修改授权。 */
    @Test public void homeActivationUsesExistingDialogWithoutNavigation() throws Exception {
        HomeActivity home = (HomeActivity) launch(HomeActivity.class, null);
        Instrumentation.ActivityMonitor monitor = ins.addMonitor(SettingsPageActivity.class.getName(), null, false);
        try {
            ins.runOnMainSync(() -> {
                home.showCheckoutStartup(false, HomeCheckoutState.UNAVAILABLE);
                home.showStatus(new HomePresenter.Status(true, false, "—", HomeCheckoutState.ACTIVATION_REQUIRED));
                home.findViewById(R.id.enterCheckout).performClick();
            });
            settle();
            androidx.fragment.app.Fragment flow = home.getSupportFragmentManager().findFragmentByTag("modelActivation");
            assertNotNull(flow);
            androidx.fragment.app.Fragment dialog = flow.getChildFragmentManager().findFragmentByTag("activateCode");
            assertTrue(dialog instanceof cc.huoyan.retail.settings.ActivateCodeDialog);
            assertEquals("不跳设置页", 0, monitor.getHits());
            assertNotNull(dialog.requireView().findViewById(R.id.editText));
            capture("home-activation");
            ins.runOnMainSync(() -> dialog.requireView().findViewById(R.id.cancel).performClick());
        } finally { ins.removeMonitor(monitor); ins.runOnMainSync(home::finish); }
    }

    /** 启动只读验收页面。 */
    private Activity launch(Class<? extends Activity> type, String page) {
        Intent intent = new Intent(ins.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (page != null) intent.putExtra("page", page);
        Activity activity = ins.startActivitySync(intent); settle(); return activity;
    }
    /** 获取屏幕上的实际矩形。 */
    private Rect rect(View view) {
        int[] xy = new int[2]; view.getLocationOnScreen(xy);
        return new Rect(xy[0], xy[1], xy[0] + view.getWidth(), xy[1] + view.getHeight());
    }
    /** 关键操作必须完整可见。 */
    private void visible(View view) {
        Rect bounds = new Rect(); assertTrue(view.getGlobalVisibleRect(bounds));
        assertEquals(view.getWidth(), bounds.width()); assertEquals(view.getHeight(), bounds.height());
    }
    /** 等待布局完成，不等待外部网络完成。 */
    private void settle() { ins.waitForIdleSync(); SystemClock.sleep(400); }
    /** 保存设备实际渲染供人工检查；夹具截图不代表真实云端状态。 */
    private void capture(String name) throws Exception {
        File folder = new File(ins.getTargetContext().getExternalFilesDir(null), "refinement-qa");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }
}
