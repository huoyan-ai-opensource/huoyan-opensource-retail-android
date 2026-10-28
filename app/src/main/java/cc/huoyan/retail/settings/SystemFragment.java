package cc.huoyan.retail.settings;

import static cc.huoyan.retail.RetailConstants.SCAN_WE_CHART_QRCODE_DIALOG_TYPE_LOGIN;
import static cc.huoyan.retail.data.SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL;
import static cc.huoyan.retail.data.SPManager.SP_SHOP_ADDRESS;
import static cc.huoyan.retail.data.SPManager.SP_SHOP_NAME;
import static cc.huoyan.retail.data.SPManager.SP_SHOP_PHONE;
import static cc.huoyan.retail.data.SPManager.SP_STABLE_RECOGNITION;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import cc.huoyan.retail.R;
import cc.huoyan.retail.recognition.RecognitionSensitivity;
import cc.huoyan.retail.common.ui.SelectOptionDialog;

import cc.huoyan.retail.common.ui.BaseFragment;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.retail.common.install.ApkInstallController;
import cc.huoyan.retail.common.ui.DebounceClickListener;
import cc.huoyan.retail.common.ui.DialogUtil;
import cc.huoyan.retail.common.IOThreadHelper;
import cc.huoyan.retail.diagnostics.Logger;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.retail.common.LocalePreferenceUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class SystemFragment extends BaseFragment implements ShopBindingPresenter.View, RecognitionSettingsPresenter.View, PendingSettings {
    private EditText shopNameEd;
    private EditText shopAddressEd;
    private EditText shopPhoneEd;
    private static final String LOG_TAG = "SystemFragment";

    /** 系统维护的 SDK 与客户端下载共用一个会话，随视图销毁释放。 */
    private ApkInstallController apkInstall;
    /** 五档滑动器，进度 0–4 对应档位 1–5；仅持有当前视图。 */
    private SeekBar recognitionSensitivity;
    private Switch stableRecognition;
    /** 删除结果开关，选择后立即保存；视图销毁时释放。 */
    private Switch deleteRecognitionResult;
    private TextView sdkChannelPicker;
    /** 滑块下方五个档位标签，当前选择使用主题色；视图销毁时释放。 */
    private android.view.ViewGroup recognitionSensitivityLabels;
    /** 置信度 UI 百分值，仅本地保存，等待 SDK 提供设置接口。 */
    private cc.huoyan.retail.common.ui.FloatSeekBar confidence;
    /** 交并比 UI 百分值，仅本地保存，等待 SDK 提供设置接口。 */
    private cc.huoyan.retail.common.ui.FloatSeekBar iou;
    /** 授权模型读取协调器，销毁视图后丢弃回调。 */
    private RecognitionSettingsPresenter recognition;
    /** 本地置信度 UI 配置键，不代表已下发 SDK。 */
    private static final String SP_CONFIDENCE = "recognition_confidence_percent";
    /** 识别阈值的原始默认百分值。 */
    private static final int DEFAULT_THRESHOLD = 45;
    /** 本地交并比 UI 配置键，不代表已下发 SDK。 */
    private static final String SP_IOU = "recognition_iou_percent";

    private TextView topSettings;
    private TextView topShop;
    private TextView topOther;
    private LinearLayout settingsLayout;
    private LinearLayout otherLayout;
    private LinearLayout shopSettingsLayout;
    /** 门店绑定的输入校验、互斥与回执协调。 */
    private ShopBindingPresenter binding;
    /** 标记本页创建的绑定等待提示，销毁时只关闭自身弹窗。 */
    private boolean bindingDialogShowing;

    @Override
    protected int getLayoutResId() {
        String page = getArguments() == null ? "" : getArguments().getString("page", "");
        switch (page) {
            case "recognition": return R.layout.page_recognition;
            case "shop": return R.layout.page_shop;
            case "maintenance": return R.layout.page_maintenance;
            default: return R.layout.fragment_system;
        }
    }

    @Override
    protected void initView(View view) {
        if (binding == null) binding = new ShopBindingPresenter(this, IOThreadHelper.getInstance()::post,
                new android.os.Handler(android.os.Looper.getMainLooper())::post, new ShopBindingSource());
        else binding.attach(this);
        SelectOptionDialog.listen(getChildFragmentManager(), this, "language", (position, value) -> {
            if (!value.equals(LocalePreferenceUtil.getLanguageCode(requireContext()))) {
                LocalePreferenceUtil.setLanguageCode(requireContext(), value);
                Intent intent = new Intent(requireContext(), cc.huoyan.retail.home.HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
        if (view.findViewById(R.id.recognitionSensitivity) != null) initRecognition(view);
        if (view.findViewById(R.id.shop_name) != null) initShop(view);
        if (view.findViewById(R.id.handleInstall) != null) initMaintenance(view);
        sdkChannelPicker = view.findViewById(R.id.sdkChannelPicker);
        if (sdkChannelPicker != null) {
            initSdkChannelViews();
            view.findViewById(R.id.wsSettings).setOnClickListener(v ->
                    new cc.huoyan.retail.settings.WsConnectionDialog()
                            .show(getChildFragmentManager(), "wsConnection"));
        }
        topShop = view.findViewById(R.id.topShop);
        topSettings = view.findViewById(R.id.topSettings);
        topOther = view.findViewById(R.id.topOther);
        settingsLayout = view.findViewById(R.id.settingsLayout);
        otherLayout = view.findViewById(R.id.otherLayout);
        shopSettingsLayout = view.findViewById(R.id.shopSettingsLayout);
        if (topSettings != null) {
            topSettings.setOnClickListener(v -> changeView(0));
            topShop.setOnClickListener(v -> changeView(1));
            topOther.setOnClickListener(v -> changeView(2));
        }
    }

    /** 同一卡片展示当前动态模型名称及版本，缺失值使用占位。 */
    @Override public void showModel(RecognitionSettingsPresenter.Model model) {
        if (getView() == null) return;
        TextView name = getView().findViewById(R.id.currentModel);
        TextView version = getView().findViewById(R.id.modelVersion);
        if (name != null) name.setText(model == null || android.text.TextUtils.isEmpty(model.name) ? "—" : model.name);
        if (version != null) version.setText(getString(R.string.model_version,
                model == null || android.text.TextUtils.isEmpty(model.version) ? "—" : model.version));
    }

    /** 初始化本地参数 UI，并将门店类型保留为 SDK 授权模型的只读展示。 */
    private void initRecognition(View view) {
        confidence = bindThreshold(view, R.id.confSeekBar, R.id.confText, SP_CONFIDENCE);
        iou = bindThreshold(view, R.id.iouSeekBar, R.id.iouText, SP_IOU);
        recognition = new RecognitionSettingsPresenter(this, IOThreadHelper.getInstance()::post,
                new android.os.Handler(android.os.Looper.getMainLooper())::post, new RecognitionSettingsSource());
        recognitionSensitivityLabels = view.findViewById(R.id.recognitionSensitivityLabels);
        recognitionSensitivity = view.findViewById(R.id.recognitionSensitivity);
        recognitionSensitivity.setProgress(savedSensitivityLevel() - 1);
        showSensitivityLevel(recognitionSensitivity.getProgress() + 1);
        recognitionSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                showSensitivityLevel(value + 1);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        deleteRecognitionResult = view.findViewById(R.id.deleteRecognitionResult);
        deleteRecognitionResult.setChecked(SPManager.getInstance().getBoolean(SPManager.SP_DELETE_RECOGNITION_RESULT));
        deleteRecognitionResult.setOnCheckedChangeListener((button, checked) -> {
            SPManager.getInstance().putBoolean(SPManager.SP_DELETE_RECOGNITION_RESULT, checked);
            Logger.logDebug(LOG_TAG, "[Biz] delete_recognition_result=" + checked);
        });
        stableRecognition = view.findViewById(R.id.stableRecognition);
        stableRecognition.setChecked(SPManager.getInstance().getBoolean(SP_STABLE_RECOGNITION));
        stableRecognition.setOnCheckedChangeListener((button, checked) ->
                SPManager.getInstance().putBoolean(SP_STABLE_RECOGNITION, checked));
        view.findViewById(R.id.restoreRecognitionDefaults).setOnClickListener(v -> confirmRestoreDefaults());
        view.findViewById(R.id.saveSettings).setOnClickListener(v -> savePendingSettings());
        View switchModel = view.findViewById(R.id.switchModel);
        if (switchModel != null) switchModel.setOnClickListener(v -> ActivationFlowFragment.switchModel(requireActivity()));
        requireActivity().getSupportFragmentManager().setFragmentResultListener(
                ActivationFlowFragment.RESULT, getViewLifecycleOwner(), (key, result) -> recognition.refresh());
    }

    /** 主线程读取合法档位；页面展示与识别线程使用同一默认值。 */
    private int savedSensitivityLevel() {
        return RecognitionSensitivity.validLevel(SPManager.getInstance().getInt(
                SP_RECOGNITION_SENSITIVITY_LEVEL, RecognitionSensitivity.DEFAULT_LEVEL));
    }

    /** 主线程回显档位草稿，并为无障碍朗读提供业务含义。 */
    private void showSensitivityLevel(int level) {
        String[] names = getResources().getStringArray(R.array.recognition_sensitivity_names);
        int index = 0;
        for (int child = 0; child < recognitionSensitivityLabels.getChildCount(); child++) {
            View view = recognitionSensitivityLabels.getChildAt(child);
            if (!(view instanceof TextView)) continue;
            TextView label = (TextView) view;
            boolean selected = index == level - 1;
            label.setText(names[index++]);
            label.setSelected(selected);
            label.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(requireContext(),
                    selected ? R.font.retail_sans_bold : R.font.retail_sans_regular));
        }
        recognitionSensitivity.setContentDescription(getString(R.string.recognition_sensitivity_title) + "，" + names[level - 1]);
    }

    /** 默认参数确认框的固定标识，避免重复点击叠加弹窗。 */
    private static final String RESTORE_TAG = "restoreRecognitionDefaults";

    /** 恢复仅修改表单；用户确认后仍需保存配置。 */
    private void confirmRestoreDefaults() {
        if (getChildFragmentManager().isStateSaved()
                || getChildFragmentManager().findFragmentByTag(RESTORE_TAG) != null) return;
        new cc.huoyan.retail.common.ui.AlertDialog(getString(R.string.recognition_restore_title),
                getString(R.string.recognition_restore_message), getString(R.string.recognition_restore_confirm),
                getString(R.string.button_cancel), restoreListener()).show(getChildFragmentManager(), RESTORE_TAG);
    }

    /** 当前视图有效期间才填回默认值，取消不改变草稿。 */
    private cc.huoyan.retail.common.ui.AlertConfirmDialogListener restoreListener() {
        return new cc.huoyan.retail.common.ui.AlertConfirmDialogListener() {
            /** 确认后只重置识别参数，保留即时保存的开关。 */
            @Override public void onConfirm() {
                if (recognitionSensitivity == null) return;
                recognitionSensitivity.setProgress(RecognitionSensitivity.DEFAULT_LEVEL - 1);
                if (confidence != null) confidence.setProgress(DEFAULT_THRESHOLD);
                if (iou != null) iou.setProgress(DEFAULT_THRESHOLD);
            }
            /** 取消恢复时保留当前表单。 */
            @Override public void onCancel() { }
        };
    }

    /** 保存本地识别参数，供保存按钮和保存后退出共用。 */
    @Override public boolean savePendingSettings() {
        if (recognitionSensitivity == null) return false;
        SPManager.getInstance().putInt(SP_RECOGNITION_SENSITIVITY_LEVEL, recognitionSensitivity.getProgress() + 1);
        if (confidence != null && iou != null) {
            SPManager.getInstance().putInt(SP_CONFIDENCE, confidence.getProgress());
            SPManager.getInstance().putInt(SP_IOU, iou.getProgress());
            Logger.logDebug(LOG_TAG, "[Biz] recognition_ui_confidence=" + confidence.getFloatValue()
                    + " iou=" + iou.getFloatValue() + " sdk_applied=false");
        }
        ToastEx.showSuccessToast(requireContext(), R.string.toast_local_config_saved);
        return true;
    }

    /** 绑定滑动条数值；未配置时立即显示 0.45，拖动时实时回显两位小数。 */
    private cc.huoyan.retail.common.ui.FloatSeekBar bindThreshold(View root, int barId, int textId, String key) {
        cc.huoyan.retail.common.ui.FloatSeekBar bar = root.findViewById(barId);
        TextView label = root.findViewById(textId);
        if (bar == null || label == null) return null;
        bar.setOnFloatChangeListener(value -> label.setText(String.format(java.util.Locale.ROOT, "%.2f", value)));
        bar.setProgress(SPManager.getInstance().getInt(key, DEFAULT_THRESHOLD));
        label.setText(String.format(java.util.Locale.ROOT, "%.2f", bar.getFloatValue()));
        return bar;
    }

    /** 只比较尚未保存的参数；开关已自动保存，不触发退出确认。 */
    @Override public boolean hasUnsavedSettings() {
        if (recognitionSensitivity == null) return false;
        SPManager store = SPManager.getInstance();
        return recognitionSensitivity.getProgress() + 1 != savedSensitivityLevel()
                || (confidence != null && confidence.getProgress() != store.getInt(SP_CONFIDENCE, DEFAULT_THRESHOLD))
                || (iou != null && iou.getProgress() != store.getInt(SP_IOU, DEFAULT_THRESHOLD));
    }

    private void initShop(View view) {
        shopNameEd = view.findViewById(R.id.shop_name);
        shopAddressEd = view.findViewById(R.id.shopAddress);
        shopPhoneEd = view.findViewById(R.id.shopPhone);
        initPrintShopData();
        watchShopField(shopNameEd, SP_SHOP_NAME);
        watchShopField(shopAddressEd, SP_SHOP_ADDRESS);
        watchShopField(shopPhoneEd, SP_SHOP_PHONE);
        view.findViewById(R.id.shopLogin).setOnClickListener(new DebounceClickListener() {
            @Override public void onDebouncedClick(View v) { showShopBindScanDialog(); }
        });
        View save = view.findViewById(R.id.saveShop);
        if (save != null) save.setOnClickListener(v ->
                ToastEx.showSuccessToast(requireContext(), R.string.toast_save_config_success));
    }

    private void watchShopField(EditText field, String key) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                SPManager.getInstance().putString(key, s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void initMaintenance(View view) {
        Switch exitGuard = view.findViewById(R.id.exitPasswordEnabled);
        if (exitGuard != null) {
            exitGuard.setChecked(ExitPasswordStore.isEnabled(requireContext()));
            exitGuard.setOnCheckedChangeListener((button, enabled) -> {
                if (enabled && !ExitPasswordStore.hasPassword(requireContext())) {
                    button.setChecked(false);
                    ExitPasswordDialog.showSetup(getChildFragmentManager(), true);
                } else ExitPasswordStore.setEnabled(requireContext(), enabled);
            });
            view.findViewById(R.id.setExitPassword).setOnClickListener(v ->
                    ExitPasswordDialog.showSetup(getChildFragmentManager(), ExitPasswordStore.isEnabled(requireContext())));
            getChildFragmentManager().setFragmentResultListener("exitPasswordSaved", getViewLifecycleOwner(),
                    (key, result) -> exitGuard.setChecked(ExitPasswordStore.isEnabled(requireContext())));
        }
        view.findViewById(R.id.handleInstall).setOnClickListener(v -> apkInstall.downloadAndInstall(
                cc.huoyan.sdk.client.deployment.SdkShell.DEFAULT_APK_URL, R.string.apk_name_sdk));
        View clientInstall = view.findViewById(R.id.handleClientInstall);
        if (clientInstall != null) clientInstall.setOnClickListener(v -> apkInstall.downloadAndInstall(
                cc.huoyan.retail.RetailConstants.CLIENT_LATEST_APK_URL, R.string.apk_name_client));

        view.findViewById(R.id.handleQuit).setOnClickListener(v -> requireActivity().finishAffinity());
        String languageMode = LocalePreferenceUtil.getLanguageCode(requireContext());
        ((TextView) view.findViewById(R.id.languageValue)).setText(
                LocalePreferenceUtil.CODE_SYSTEM.equals(languageMode) ? R.string.language_system
                        : LocalePreferenceUtil.CODE_ZH.equals(languageMode)
                        ? R.string.language_simplified_chinese : R.string.language_english);
        view.findViewById(R.id.languageSetting).setOnClickListener(v -> showLanguageDialog());
        View authorization = view.findViewById(R.id.openAuthorization);
        if (authorization != null) authorization.setOnClickListener(v ->
                cc.huoyan.retail.settings.SettingsPageActivity.open(requireContext(), "authorization"));
    }

    /** 使用项目单选弹窗切换语言模式；跟随系统始终放在首项。 */
    private void showLanguageDialog() {
        SelectOptionDialog.show(getChildFragmentManager(), "language", getString(R.string.language_setting),
                Arrays.asList(getString(R.string.language_system), getString(R.string.language_simplified_chinese),
                        getString(R.string.language_english)),
                Arrays.asList(LocalePreferenceUtil.CODE_SYSTEM, LocalePreferenceUtil.CODE_ZH, LocalePreferenceUtil.CODE_EN),
                LocalePreferenceUtil.getLanguageCode(requireContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        androidx.fragment.app.Fragment restore = getChildFragmentManager().findFragmentByTag(RESTORE_TAG);
        if (restore instanceof cc.huoyan.retail.common.ui.AlertDialog)
            ((cc.huoyan.retail.common.ui.AlertDialog) restore).setConfirmListener(restoreListener());
        if (apkInstall != null) apkInstall.onResume(false);
        initPrintShopData();
        if (recognition != null) recognition.refresh();
        if (stableRecognition != null) {
            stableRecognition.setEnabled(!cc.huoyan.retail.config.ModuleConfig.isWeighingEnabled(requireContext()));
        }
    }

    /** 初始化 SDK 对接方式，回显不重复写配置。 */
    private void initSdkChannelViews() {
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (cc.huoyan.sdk.client.SdkConfig.Channel channel : SDK_CHANNEL_ITEMS) {
            labels.add(getString(sdkChannelLabelRes(channel)));
            values.add(channel.value());
        }
        sdkChannelPicker.setText(sdkChannelLabelRes(SdkChannelManager.getInstance().getChannel()));
        sdkChannelPicker.setOnClickListener(v -> SelectOptionDialog.show(getChildFragmentManager(), "sdkChannel",
                getString(R.string.settings_system_sdk_channel), labels, values,
                values.get(sdkChannelPosition(SdkChannelManager.getInstance().getChannel()))));
        SelectOptionDialog.listen(getChildFragmentManager(), this, "sdkChannel", (position, value) -> {
            cc.huoyan.sdk.client.SdkConfig.Channel selected = sdkChannelFromPosition(position);
            if (selected == null || selected == SdkChannelManager.getInstance().getChannel()) return;
            SdkChannelManager.getInstance().setChannel(selected);
            sdkChannelPicker.setText(sdkChannelLabelRes(selected));
            Logger.logDebug(LOG_TAG, "[Biz] sdk_channel=" + selected.value());
            ToastEx.showSuccessToast(mContext, getString(R.string.toast_sdk_channel_saved,
                    getString(sdkChannelLabelRes(selected))));
        });
    }

    /** 设置页 SDK 对接方式选择条目顺序：0 AIDL（默认）/ 1 WebSocket。 */
    private static final List<cc.huoyan.sdk.client.SdkConfig.Channel> SDK_CHANNEL_ITEMS = Arrays.asList(
            cc.huoyan.sdk.client.SdkConfig.Channel.AIDL, cc.huoyan.sdk.client.SdkConfig.Channel.WS);

    /**
     * SDK 对接通道 → 设置页选中位置。
     *
     * @param channel SDK 对接通道（null 按默认通道 AIDL 处理）
     * @return 对应条目下标（0 AIDL / 1 WebSocket）
     */
    static int sdkChannelPosition(cc.huoyan.sdk.client.SdkConfig.Channel channel) {
        int index = SDK_CHANNEL_ITEMS.indexOf(channel);
        return index >= 0 ? index : SDK_CHANNEL_ITEMS.indexOf(cc.huoyan.sdk.client.SdkConfig.Channel.AIDL);
    }

    /**
     * 设置页选中位置 → SDK 对接通道。
     *
     * @param position 弹窗选中条目下标
     * @return 对应通道；越界返回 null（调用方忽略）
     */
    static cc.huoyan.sdk.client.SdkConfig.Channel sdkChannelFromPosition(int position) {
        if (position < 0 || position >= SDK_CHANNEL_ITEMS.size()) {
            return null;
        }
        return SDK_CHANNEL_ITEMS.get(position);
    }

    /**
     * SDK 对接通道 → 设置页选择条目文案资源。
     *
     * @param channel SDK 对接通道
     * @return 对应文案资源 id
     */
    private static int sdkChannelLabelRes(cc.huoyan.sdk.client.SdkConfig.Channel channel) {
        if (channel == cc.huoyan.sdk.client.SdkConfig.Channel.WS) {
            return R.string.settings_system_sdk_channel_ws;
        }
        return R.string.settings_system_sdk_channel_aidl;
    }

    /**
     * 初始化打印店铺信息
     */
    private void initPrintShopData() {
        String spShopName = SPManager.getInstance().getString(SP_SHOP_NAME, getString(R.string.default_shop_name));
        if (shopNameEd != null) {
            shopNameEd.setText(spShopName);
        }
        String spShopAddress = SPManager.getInstance().getString(SP_SHOP_ADDRESS, getString(R.string.default_shop_address_value));
        if (shopAddressEd != null) {
            shopAddressEd.setText(spShopAddress);
        }
        String spShopPhone = SPManager.getInstance().getString(SP_SHOP_PHONE, "");
        if (shopPhoneEd != null) {
            shopPhoneEd.setText(spShopPhone);
        }

    }

    /** 为 SDK 和客户端维护入口复用首页的下载、权限申请和安装流程。 */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        if (view.findViewById(R.id.handleInstall) != null) {
            apkInstall = new ApkInstallController((AppCompatActivity) requireActivity(), state);
        }
    }

    /** 保存权限页返回后继续安装所需状态。 */
    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        if (apkInstall != null) apkInstall.saveState(state);
        super.onSaveInstanceState(state);
    }

    /** 释放维护页下载任务及弹窗。 */
    @Override
    public void onDestroyView() {
        androidx.fragment.app.Fragment restore = getChildFragmentManager().findFragmentByTag(RESTORE_TAG);
        if (restore instanceof cc.huoyan.retail.common.ui.AlertDialog)
            ((cc.huoyan.retail.common.ui.AlertDialog) restore).setConfirmListener(null);
        if (apkInstall != null) apkInstall.onDestroy();
        apkInstall = null;
        binding.detach();
        if (recognition != null) recognition.detach();
        recognition = null;
        deleteRecognitionResult = null;
        stableRecognition = null;
        recognitionSensitivity = null;
        recognitionSensitivityLabels = null;
        confidence = null;
        iou = null;
        dismissBindingProgress();
        super.onDestroyView();
    }

    @Override
    protected void loadData() {
        if (topSettings != null) changeView(0);
    }

    private void changeView(int position) {
        topSettings.setTypeface(null, Typeface.NORMAL);
        topShop.setTypeface(null, Typeface.NORMAL);
        topOther.setTypeface(null, Typeface.NORMAL);
        topSettings.setTextColor(Color.parseColor("#000000"));
        topShop.setTextColor(Color.parseColor("#000000"));
        topOther.setTextColor(Color.parseColor("#000000"));
        settingsLayout.setVisibility(View.GONE);
        shopSettingsLayout.setVisibility(View.GONE);
        otherLayout.setVisibility(View.GONE);
        switch (position) {
            case 0:
                topSettings.setTypeface(null, Typeface.BOLD);
                topSettings.setTextColor(Color.parseColor("#3F3EED"));
                settingsLayout.setVisibility(View.VISIBLE);
                break;
            case 1:
                topShop.setTypeface(null, Typeface.BOLD);
                topShop.setTextColor(Color.parseColor("#3F3EED"));
                shopSettingsLayout.setVisibility(View.VISIBLE);
                break;
            case 2:
                topOther.setTypeface(null, Typeface.BOLD);
                topOther.setTextColor(Color.parseColor("#3F3EED"));
                otherLayout.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public void onPause() {
        if (apkInstall != null) apkInstall.onPause();
        super.onPause();
    }

    /**
     * 弹出门店二维码扫描：复用既有扫码弹窗（激活扫码 TYPE_SERIAL
     * 分支不受影响）；扫描结果经 {@link #handleShopQrScan} 解析并发起 SDK 绑定。
     * <p>已知 UI 限制：扫码弹窗标题文案复用现有资源（「登录码」措辞与门店二维码
     * 语义有偏差），文案修正需设计确认后改字符串资源，本次不改布局/XML。</p>
     */
    private void showShopBindScanDialog() {
        ScanWechatQRCodeDialog scanDialog = ScanWechatQRCodeDialog.create(SCAN_WE_CHART_QRCODE_DIALOG_TYPE_LOGIN);
        scanDialog.setListener(new ScanWechatQRCodeDialog.ScanWechatQRCodeDialogListener() {
            @Override
            public void onScan(String result) {
                handleShopQrScan(result);
            }

            @Override
            public void onDismiss() {
            }
        });
        scanDialog.show(requireActivity().getSupportFragmentManager(), "scanShopQrCode");
    }

    /** 将扫码结果交给绑定协调器，不在页面决定成功归属。 */
    private void handleShopQrScan(String raw) {
        if (ShopBindingPresenter.parseShopQrShopId(raw) == null) Logger.logDebug(LOG_TAG, "[Biz] shop_qr=invalid raw=" + raw);
        binding.bind(raw);
    }
    /** 加载提示失败不会阻止已经受理的绑定操作。 */
    @Override public void bindingStarted() {
        try {
            DialogUtil.showLoadingDialog(requireActivity(), getString(R.string.dialog_binding_shop));
            bindingDialogShowing = true;
        }
        catch (RuntimeException error) { Logger.logError(LOG_TAG, "绑定加载提示显示失败，继续提交绑定：" + error.getClass().getSimpleName()); }
    }
    /** 仅释放本页创建的绑定等待提示。 */
    private void dismissBindingProgress() {
        if (bindingDialogShowing) DialogUtil.dismissLoadingDialog();
        bindingDialogShowing = false;
    }
    /** 无效输入或重复提交的即时提示。 */
    @Override public void bindingRejected(int text) { ToastEx.createToast(requireContext(), getString(text)); }
    /** 将绑定回执映射为原有成功或失败文案。 */
    @Override public void bindingFinished(boolean success, int text, String reason) {
        dismissBindingProgress();
        if (success) { initPrintShopData(); ToastEx.showSuccessToast(requireContext(), getString(text)); return; }
        if (text == R.string.bind_incomplete) { ToastEx.createToast(requireContext(), getString(text)); return; }
        String message = reason == null ? getString(text) : reason;
        Logger.logDebug(LOG_TAG, "[Biz] device_bind=fail reason=" + message);
        ToastEx.createToast(requireContext(), getString(R.string.bind_failed, message));
    }
}
