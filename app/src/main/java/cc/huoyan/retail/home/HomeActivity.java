package cc.huoyan.retail.home;
import cc.huoyan.retail.sdk.SdkChannelManager;

import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.goods.GoodsManagementActivity;
import cc.huoyan.retail.settings.SettingsPageActivity;

import android.content.Intent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.BaseActivity;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.sdk.client.session.SdkStartupController;
import cc.huoyan.retail.common.install.ApkInstallController;
import cc.huoyan.retail.common.PermissionUtil;

/** 启动功能首页，集中承载收银和设备配置入口。 */
public class HomeActivity extends BaseActivity implements HomePresenter.View {
    private final SdkStartupController sdkStartup = SdkChannelManager.getInstance().newStartupController();
    private ApkInstallController sdkInstall;
    private final android.os.Handler statusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService statusWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** 版本查询不与云状态共享阻塞队列；页面销毁时关闭。 */
    private final java.util.concurrent.ExecutorService versionWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** 协调前台状态轮询；设备安装和导航仍由页面负责。 */
    private HomePresenter presenter;
    /** 最近一次查询到的版本，主线程保存并在 SDK 连接后显示。 */
    private String sdkVersion = "—";
    /** 只表示正在等待 SDK 通道连接，与激活和模型状态无关。 */
    private boolean sdkLoading;
    /** 首帧即等待初始化快照，不能用通道连接或版本号代替。 */
    private boolean checkoutStarting = true;
    /** 当前入口状态；查询失败会清除之前的可用状态。 */
    private HomeCheckoutState checkoutState = HomeCheckoutState.STARTING;
    /** 主线程维护本机 SDK 缺失状态；局域网通道不依赖本机安装。 */
    private boolean sdkMissing;
    /** 重建时保存本次首页是否已经弹过提醒。 */
    private static final String EXPIRY_SHOWN = "home.expiryShown";

    /** 回到首页并关闭其上的收银或功能页，下次进入收银重新开始。 */
    public static void open(Context context) {
        context.startActivity(new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override
    protected int initContentView() {
        return R.layout.activity_home;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sdkInstall = new ApkInstallController(this, savedInstanceState);
        presenter = new HomePresenter(this, statusWorker, statusHandler::post,
                statusHandler::postDelayed, HomeStatusSource::read,
                () -> SdkChannelManager.getInstance().getClient().isConnected(), sdkStartup::stop);
        presenter.configureVersionLoader(versionWorker, HomeStatusSource::readVersion);
        if (savedInstanceState != null)
            presenter.restoreExpiryVisit(savedInstanceState.getBoolean(EXPIRY_SHOWN));
        findViewById(R.id.enterCheckout).setOnClickListener(v -> openCheckout());
        bindSettings(R.id.homeLearning, "learning");
        bindSettings(R.id.homeCamera, "camera");
        bindSettings(R.id.homeScale, "scale");
        bindSettings(R.id.homePrint, "print");
        bindSettings(R.id.homeRecognition, "recognition");
        bindSettings(R.id.homeSdk, "sdk");
        bindSettings(R.id.homeShop, "shop");
        bindSettings(R.id.homeGoods, "goods");
        bindSettings(R.id.homeMaintenance, "maintenance");
        findViewById(R.id.aboutDevice).setOnClickListener(v ->
                SettingsPageActivity.open(this, "authorization"));
        findViewById(R.id.homeCloudStatus).setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag("cloudInfo") == null)
                new cc.huoyan.retail.home.CloudServiceDialog().show(getSupportFragmentManager(), "cloudInfo");
        });
        ((TextView) findViewById(R.id.homeClientVersion)).setText(getString(R.string.home_client_version,
                cc.huoyan.retail.BuildConfig.VERSION_NAME));
        ((TextView) findViewById(R.id.homeSerial)).setText(getString(R.string.home_serial,
                cc.huoyan.retail.device.DeviceUtils.getSerialNumber()));
        android.widget.ScrollView features = findViewById(R.id.homeFeaturesScroll);
        View more = findViewById(R.id.homeMoreFeatures);
        if (features != null && more != null) {
            Runnable updateHint = () -> more.setVisibility(features.canScrollVertically(1)
                    ? View.VISIBLE : View.INVISIBLE);
            features.setOnScrollChangeListener((v, x, y, oldX, oldY) -> updateHint.run());
            features.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> features.post(updateHint));
            features.getChildAt(0).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> features.post(updateHint));
            features.post(updateHint);
        }
        PermissionUtil.verifyBasePermissions(this);
    }

    @Override protected void onResume() {
        super.onResume();
        presenter.resume();
        ((TextView) findViewById(R.id.homeStoreSubtitle)).setText(SPManager.getInstance()
                .getString(SPManager.SP_SHOP_NAME, getString(R.string.default_shop_name)));
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        boolean localShell = SdkChannelManager.getInstance().requiresLocalShell();
        boolean installed = sdkInstall.onResume(localShell);
        refreshSdkAvailability();
        if (!localShell || installed) {
            sdkStartup.start(presenter.beginSdkStartup());
        } else {
            showSdkStartup(false);
            showCheckoutStartup(false, HomeCheckoutState.UNAVAILABLE);
        }
    }

    @Override
    protected void onPause() {
        presenter.pause();
        statusHandler.removeCallbacksAndMessages(null);
        sdkInstall.onPause();
        sdkStartup.stop();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXPIRY_SHOWN, presenter.hasShownExpiry());
        sdkInstall.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        presenter.detach();
        statusWorker.shutdownNow();
        versionWorker.shutdownNow();
        sdkInstall.onDestroy();
        super.onDestroy();
    }

    /** 离开首页后移除旧提醒；横竖屏重建交给 FragmentManager 恢复。 */
    @Override protected void onStop() {
        if (!isChangingConfigurations()) {
            androidx.fragment.app.Fragment dialog = getSupportFragmentManager()
                    .findFragmentByTag(ActivationExpiryDialog.TAG);
            if (dialog instanceof ActivationExpiryDialog)
                ((ActivationExpiryDialog) dialog).dismissAllowingStateLoss();
        }
        super.onStop();
    }

    /** 首页就绪且没有其他弹窗时展示提醒；忙碌时保留本次展示机会。 */
    @Override public boolean showActivationExpiry(ActivationExpiry expiry) {
        androidx.fragment.app.FragmentManager manager = getSupportFragmentManager();
        if (manager.findFragmentByTag(ActivationExpiryDialog.TAG) != null) return true;
        if (sdkMissing || checkoutStarting || isFinishing() || manager.isStateSaved()
                || hasDialog(manager)) return false;
        ActivationExpiryDialog.create(this, expiry).showNow(manager, ActivationExpiryDialog.TAG);
        if (cc.huoyan.retail.BuildConfig.DEBUG)
            android.util.Log.d("HomeExpiry", "[Biz] activation_expiry_days=" + expiry.days);
        return true;
    }

    /** 递归检查激活流程的子弹窗，避免轮询提醒覆盖输入或扫码。 */
    private static boolean hasDialog(androidx.fragment.app.FragmentManager manager) {
        for (androidx.fragment.app.Fragment fragment : manager.getFragments()) {
            if (!fragment.isAdded()) continue;
            if (fragment instanceof androidx.fragment.app.DialogFragment
                    || hasDialog(fragment.getChildFragmentManager())) return true;
        }
        return false;
    }

    private void bindSettings(int id, String page) {
        View view = findViewById(id);
        if (view == null) return;
        view.setOnClickListener(v -> {
            if ("learning".equals(page)) {
                startActivity(new Intent(this, GoodsManagementActivity.class));
            } else {
                SettingsPageActivity.open(this, page);
            }
        });
    }

    /** 将状态快照转换为本地化文字和颜色。 */
    @Override public void showStatus(HomePresenter.Status status) {
        ((TextView) findViewById(R.id.homeCloudLabel)).setText(R.string.cloud_title);
        findViewById(R.id.homeCloudStatus).setContentDescription(getString(!status.cloudValid
                ? R.string.cloud_error : status.cloudEnabled ? R.string.cloud_enabled : R.string.cloud_disabled));
        ((TextView) findViewById(R.id.homeCloudDot)).setTextColor(android.graphics.Color.parseColor(
                !status.cloudValid ? "#8795A8" : status.cloudEnabled ? "#27835B" : "#D94B4B"));
        checkoutState = status.checkoutState;
        renderCheckoutEntry();
    }

    /** 版本单独返回即展示，慢速或空状态回执不清空已经取得的版本。 */
    @Override public void showSdkVersion(String version) {
        sdkVersion = version;
        if (cc.huoyan.retail.BuildConfig.DEBUG)
            android.util.Log.d("HomeStartup", "[Biz] sdk_version=" + version);
        renderSdkVersion();
    }

    /** 展示当前 SDK 接入通道。 */
    @Override public void showConnection() {
        refreshSdkAvailability();
        ((TextView) findViewById(R.id.homeConnection)).setText(getString(R.string.home_connection,
                getString(SdkChannelManager.getInstance().getChannel() == cc.huoyan.sdk.client.SdkConfig.Channel.AIDL
                        ? R.string.land_sdk_aidl : R.string.land_sdk_ws)));
    }

    /** 通道连接提示仅控制页脚，不代表 SDK 初始化完成。 */
    @Override public void showSdkStartup(boolean loading) {
        sdkLoading = loading;
        findViewById(R.id.homeSdkStartupProgress).setVisibility(loading ? View.VISIBLE : View.GONE);
        renderSdkVersion();
    }

    /** 主线程接收真实启动快照，原子更新等待态与授权入口。 */
    @Override public void showCheckoutStartup(boolean starting, HomeCheckoutState state) {
        if (cc.huoyan.retail.BuildConfig.DEBUG && checkoutStarting != starting) {
            android.util.Log.d("HomeStartup", "[Biz] checkout_starting=" + starting
                    + " state=" + state);
        }
        checkoutStarting = starting;
        checkoutState = state;
        renderCheckoutEntry();
    }

    /** 版本查询不能覆盖连接等待态，重复轮询不重复触发无障碍播报。 */
    private void renderSdkVersion() {
        TextView label = findViewById(R.id.homeSdkVersion);
        String text = sdkLoading ? getString(R.string.home_sdk_starting)
                : getString(R.string.home_sdk_version, sdkVersion);
        if (!text.contentEquals(label.getText())) label.setText(text);
    }

    /** 在主线程检查本机安装；返回首页及切换通道后立即刷新入口。 */
    private void refreshSdkAvailability() {
        sdkMissing = SdkChannelManager.getInstance().requiresLocalShell()
                && cc.huoyan.sdk.client.deployment.SdkShell.installedVersion(getPackageManager()) == null;
        renderCheckoutEntry();
    }

    /** 明确轮换状态优先展示；未知和等待状态不开放入口，安装仍可操作。 */
    private void renderCheckoutEntry() {
        HomeCheckoutState state = visibleCheckoutState();
        boolean warning = state == HomeCheckoutState.ACTIVATION_REQUIRED || state.isRotation();
        boolean ready = state == HomeCheckoutState.READY;
        boolean enabled = sdkMissing || state.actionable();
        int cardColor = getColor(sdkMissing ? R.color.home_checkout_missing
                : warning ? R.color.home_checkout_unactivated
                : ready ? R.color.home_checkout_primary : R.color.home_checkout_starting);
        int buttonColor = getColor(sdkMissing ? R.color.home_checkout_action_missing
                : warning ? R.color.home_checkout_action_unactivated
                : ready ? R.color.home_checkout_action_ready : R.color.home_checkout_action_starting);
        int foreground = getColor(!sdkMissing && ready ? R.color.home_checkout_primary : R.color.white);
        View entry = findViewById(R.id.enterCheckout);
        entry.setBackgroundTintList(ColorStateList.valueOf(cardColor));
        entry.setEnabled(enabled);
        ((TextView) findViewById(R.id.homeActivationHint)).setText(sdkMissing ? R.string.home_sdk_missing : state.hint);
        TextView action = findViewById(R.id.homeCheckoutAction);
        action.setText(sdkMissing ? R.string.home_install_now : state.action);
        action.setEnabled(enabled);
        action.setTextColor(foreground);
        action.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                findViewById(R.id.homeCheckoutArrow) == null && enabled ? R.drawable.land_icon_arrow_right : 0, 0);
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(action, ColorStateList.valueOf(foreground));
        View button = findViewById(R.id.homeCheckoutButton);
        (button == null ? action : button).setBackgroundTintList(ColorStateList.valueOf(buttonColor));
        ImageView arrow = findViewById(R.id.homeCheckoutArrow);
        if (arrow != null) {
            arrow.setImageTintList(ColorStateList.valueOf(foreground));
            arrow.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    /** 初始化最短等待与轮换业务状态共用渲染、点击判断，避免颜色与可操作性分离。 */
    private HomeCheckoutState visibleCheckoutState() {
        return checkoutStarting && !checkoutState.isRotation() ? HomeCheckoutState.STARTING : checkoutState;
    }

    /** 每次从首页创建新的识别会话。 */
    private void openCheckout() {
        refreshSdkAvailability();
        if (sdkMissing) {
            sdkInstall.downloadAndInstall(
                    cc.huoyan.sdk.client.deployment.SdkShell.DEFAULT_APK_URL, R.string.apk_name_sdk);
            return;
        }
        HomeCheckoutState state = visibleCheckoutState();
        if (!state.actionable()) return;
        if (state == HomeCheckoutState.ACTIVATION_REQUIRED) {
            cc.huoyan.retail.settings.ActivationFlowFragment.open(this);
            return;
        }
        startActivity(new Intent(this, MainActivity.class).putExtra("freshCheckout", true));
    }
}
