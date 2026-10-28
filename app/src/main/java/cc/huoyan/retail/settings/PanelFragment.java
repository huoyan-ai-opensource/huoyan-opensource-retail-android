package cc.huoyan.retail.settings;


import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Switch;


import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.BaseFragment;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.device.DeviceUtils;
import cc.huoyan.retail.common.ui.DialogUtil;
import cc.huoyan.retail.common.ui.AlertConfirmDialogListener;
import cc.huoyan.retail.common.IOThreadHelper;
import cc.huoyan.retail.common.ui.ToastEx;

import cc.huoyan.sdk.client.model.CertInfo;
import cc.huoyan.sdk.client.model.Capability;
import cc.huoyan.retail.sdk.SdkVersionDisplay;
import cc.huoyan.sdk.client.model.CloudStatus;
import cc.huoyan.sdk.client.model.StatusSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 授权面板视图；云状态和面板查询由 Presenter 管理，激活交给共享弹窗会话。 */
public class PanelFragment extends BaseFragment implements PanelPresenter.View {
    /** 授权页面的既有日志标签。 */
    private static final String LOG_TAG = "PanelFragment";
    /** 客户端版本展示。 */
    private TextView appVersionTv;
    /** 可复制的设备序列号展示。 */
    private TextView hardwareSerialTv;
    /** SDK 版本展示。 */
    private TextView sdkVersionTv;
    /** 模型实际就绪状态展示。 */
    private TextView activeStatusTv;
    /** 授权起始时间展示。 */
    private TextView serviceStartTimeTv;
    /** 授权到期时间展示。 */
    private TextView serviceExpireTimeTv;
    /** 授权类型展示。 */
    private TextView serialTypeTv;
    /** 打开激活码输入的入口。 */
    private TextView activateTv;
    /** 云服务开关控件。 */
    private Switch cloudServiceSwitch;
    /** 程序化回显时屏蔽开关监听，避免把渲染误当作用户操作。 */
    private boolean renderingCloudStatus;
    /** 云开关与授权快照协调者。 */
    private PanelPresenter presenter;


    /** 根据是否独立页面选择授权布局。 */
    @Override
    protected int getLayoutResId() {
        if (getArguments() != null && getArguments().getBoolean("standalone")) return R.layout.page_authorization;
        return R.layout.fragment_panel;
    }

    /** 创建当前视图的 Presenter 并绑定界面操作。 */
    @Override
    protected void initView(View view) {
        presenter = new PanelPresenter(this, IOThreadHelper.getInstance()::post,
                new android.os.Handler(android.os.Looper.getMainLooper())::post,
                new PanelSdkSource());
        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                ActivationFlowFragment.RESULT, getViewLifecycleOwner(), (key, result) -> presenter.refresh());
        activateTv = view.findViewById(R.id.activate);
        activateTv.setOnClickListener(v -> ActivationFlowFragment.open(requireActivity()));
        appVersionTv = view.findViewById(R.id.app_version);
        hardwareSerialTv = view.findViewById(R.id.hardware_serial);
        // 本机型号无需等待 SDK 回执；兼容不含该行的旧面板布局。
        TextView deviceModel = view.findViewById(R.id.device_model);
        if (deviceModel != null) {
            String model = DeviceUtils.getPhoneType();
            deviceModel.setText(TextUtils.isEmpty(model) ? getString(R.string.text_unavailable) : model);
        }
        sdkVersionTv = view.findViewById(R.id.sdk_version);
        activeStatusTv = view.findViewById(R.id.active_status);
        serviceStartTimeTv = view.findViewById(R.id.service_start_time);
        serviceExpireTimeTv = view.findViewById(R.id.service_expire_time);
        serialTypeTv = view.findViewById(R.id.serial_type);
        hardwareSerialTv.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("serial", hardwareSerialTv.getText()));
            ToastEx.showSuccessToast(requireContext(), R.string.land_serial_copied);
        });
        cloudServiceSwitch = view.findViewById(R.id.cloudServiceSwitch);
        cloudServiceSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (renderingCloudStatus || presenter.cloudRunning() || presenter.cloudEnabled() == null || checked == presenter.cloudEnabled()) return;
            renderCloudStatus();
            if (checked) {
                if (!checkCloudBinding()) return;
                DialogUtil.showAlertDialog(getChildFragmentManager(),
                        getString(R.string.panel_cloud_service), getString(R.string.panel_cloud_enable_confirm),
                        getString(R.string.button_confirm), getString(R.string.button_cancel),
                        new AlertConfirmDialogListener() {
                            /** 用户确认后提交云服务开关变更。 */
                            @Override
                            public void onConfirm() {
                                requestCloudStatus(true);
                            }

                            /** 取消确认时保持云服务原状态。 */
                            @Override
                            public void onCancel() {
                            }
                        });
            } else {
                requestCloudStatus(false);
            }
        });

    }

    /** 开启云服务仍须先确认门店绑定；读取和关闭不受此条件限制。 */
    private void requestCloudStatus(Boolean requested) {
        if (Boolean.TRUE.equals(requested) && !checkCloudBinding()) return;
        presenter.cloud(requested);
    }
    /** 回显真实云状态，程序赋值不触发写接口。 */
    @Override public void cloudChanged() {
        renderCloudStatus();
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "[Biz] cloud_enabled=" + presenter.cloudEnabled());
    }
    /** 显示云接口失败原因。 */
    @Override public void cloudFailed(CloudStatus response) {
        String reason = response == null ? getString(R.string.sdk_error_not_connected) : response.getReason();
        if (reason == null || reason.isEmpty()) reason = String.valueOf(response.getCode());
        ToastEx.createToast(requireContext(), getString(R.string.panel_cloud_failed, reason));
    }

    /** 回显 Presenter 的真实云状态和忙碌状态，不触发写请求。 */
    private void renderCloudStatus() {
        cloudServiceSwitch.setEnabled(false);
        // 回显不调用写接口；监听器通过请求中标记与终态值过滤程序赋值。
        renderingCloudStatus = true;
        cloudServiceSwitch.setChecked(Boolean.TRUE.equals(presenter.cloudEnabled()));
        renderingCloudStatus = false;
        cloudServiceSwitch.setEnabled(!presenter.cloudRunning() && presenter.cloudEnabled() != null);
    }

    /** 保留开启云服务前的门店绑定提示。 */
    private boolean checkCloudBinding() {
        if (SPManager.getInstance().hasBoundShop()) return true;
        ToastEx.createToast(requireContext(), getString(R.string.cloud_bind_shop_first));
        return false;
    }

    /** 返回控制面板时重新读取 SDK 的云端服务状态。 */
    @Override
    public void onResume() {
        super.onResume();
        presenter.resume();
    }

    /** 展示 SDK 与证书快照。 */
    @Override public void panelLoaded(StatusSnapshot status, CertInfo cert, Capability capability) { updatePanelUi(status, cert, capability); }

    /** 将 SDK 状态和证书快照映射为本地化展示。 */
    private void updatePanelUi(StatusSnapshot status, CertInfo certInfo, Capability capability) {
        appVersionTv.setText(String.format("v%s", BuildConfig.VERSION_NAME));
        if (mContext != null) {
            hardwareSerialTv.setText(DeviceUtils.getSerialNumber());
        }
        boolean hasCert = certInfo != null && certInfo.getCode() == 0;
        String sdkVersion = SdkVersionDisplay.name(capability);
        sdkVersionTv.setText(sdkVersion != null ? sdkVersion : getString(R.string.text_unavailable));
        // 只有模型真正就绪才视为完成激活；模型准备中/未就绪仍显示“未激活”。
        boolean activated = cc.huoyan.sdk.client.status.SdkStatus.modelReady(status);
        activeStatusTv.setText(activated
                ? getString(R.string.panel_activation_status_active)
                : getString(R.string.panel_activation_status_inactive));
        serviceStartTimeTv.setText(hasCert ? formatEpochSeconds(certInfo.getActivatedAt()) : "");
        serviceExpireTimeTv.setText(hasCert ? formatEpochSeconds(certInfo.getExpiresAt()) : "");
        // “授权版本”对应证书视图的 license_type；此前这里被固定清空，导致激活成功后始终无显示。
        String licenseType = hasCert ? certInfo.getLicenseType() : null;
        serialTypeTv.setText(TextUtils.isEmpty(licenseType)
                ? getString(R.string.text_unavailable) : licenseType);
    }

    /**
     * 面板时间字段格式化：v2 证书回执的 activatedAt/expiresAt
     * 为秒级时间戳，0 或负值视为无数据回退空串。
     *
     * @param epochSeconds 秒级时间戳
     * @return yyyy/MM/dd HH:mm:ss 格式化文本；无数据返回空串
     */
    static String formatEpochSeconds(long epochSeconds) {
        if (epochSeconds <= 0) {
            return "";
        }
        return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(new Date(epochSeconds * 1000L));
    }

    /** 请求最新面板与证书信息。 */
    @Override
    protected void loadData() {
        presenter.refresh();
        activateTv.setText(getArguments() != null && getArguments().getBoolean("standalone")
                ? R.string.land_activate_device : R.string.panel_activation_button);
    }

    /** 解绑 Presenter，阻止旧查询回写。 */
    @Override
    public void onDestroyView() {
        presenter.detach();
        super.onDestroyView();
    }
}
