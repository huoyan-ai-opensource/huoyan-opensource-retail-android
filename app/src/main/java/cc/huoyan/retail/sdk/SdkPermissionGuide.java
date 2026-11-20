package cc.huoyan.retail.sdk;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;

import cc.huoyan.retail.R;
import cc.huoyan.retail.common.IOThreadHelper;
import cc.huoyan.retail.common.install.ApkInstallIntents;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.deployment.SdkShell;
import cc.huoyan.sdk.client.model.CertInfo;

/**
 * 从前台引导客户为识物 SDK 壳授权，手动入口与业务权限回执共用。
 */
public final class SdkPermissionGuide extends DialogFragment {
    /**
     * 同一页面只显示一个引导。
     */
    private static final String TAG = "sdkPermissionGuide";
    /**
     * 系统设置往返时保留重新检查的机会。
     */
    private static final String SETTINGS_OPENED = "settingsOpened";
    /**
     * SDK 通用未就绪码，只有指定缺权原因才需要引导。
     */
    private static final int NOT_READY = -3;
    /**
     * 已发布 SDK 业务操作缺权时的回执。
     */
    private static final String OPERATION_DENIED =
            "SDK 壳必需权限未齐备，请先调用 init 并完成悬浮窗和安装应用授权";
    /**
     * 已发布 SDK 初始化缺权时的回执。
     */
    private static final String INIT_DENIED =
            "请先授予 SDK 壳悬浮窗和安装应用权限；全部授权后将继续本次初始化";
    /**
     * 用户从设置返回后可重新初始化当前 SDK。
     */
    private boolean settingsOpened;
    /**
     * 往返设置期间换了连接，就由新连接自己的启动流程处理。
     */
    private HuoyanSdkClient settingsClient;
    /**
     * 系统设置返回时复查，返回键和关闭按钮都不影响授权续接。
     */
    private final ActivityResultLauncher<Intent> settings = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> onSettingsReturned());

    /**
     * 打开独立引导，仅由当前前台页面发起。
     */
    public static boolean show(FragmentActivity activity) {
        FragmentManager fragments = activity.getSupportFragmentManager();
        if (fragments.findFragmentByTag(TAG) != null) return true;
        if (activity.isFinishing() || activity.isDestroyed() || fragments.isStateSaved()
                || !activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            return false;
        }
        new SdkPermissionGuide().showNow(fragments, TAG);
        return true;
    }

    /**
     * 根据原始 SDK 回执打开引导。
     */
    public static boolean showIfRequired(FragmentActivity activity, int code, String reason) {
        return isPermissionDenial(code, reason) && show(activity);
    }

    /**
     * 完整回执须同时匹配未就绪码和壳权限门禁原因。
     */
    public static boolean isPermissionDenial(int code, String reason) {
        return code == NOT_READY && isPermissionDenial(reason);
    }

    /**
     * 部分业务层只保留原因，学习失败还可能在原文前拼接错误码。
     */
    public static boolean showIfRequired(FragmentActivity activity, String reason) {
        return isPermissionDenial(reason) && show(activity);
    }

    /**
     * 区分缺权与其他未就绪回执，业务错误格式化前保留此原因。
     */
    public static boolean isPermissionDenial(String reason) {
        if (reason == null) return false;
        if (reason.startsWith("-3 ")) reason = reason.substring(3);
        return OPERATION_DENIED.equals(reason) || INIT_DENIED.equals(reason);
    }

    /**
     * 远端连接只显示操作说明，避免误授权本机安装的另一个 SDK。
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.dialog_sdk_permission_guide, container, false);
        Configuration config = getResources().getConfiguration();
        int paddingRes = R.dimen.dp_12;
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            paddingRes = R.dimen.dp_10;
        } else if (config.orientation == Configuration.ORIENTATION_LANDSCAPE && config.screenWidthDp < 1100) {
            paddingRes = R.dimen.dp_8;
        }
        int padding = getResources().getDimensionPixelSize(paddingRes);
        view.findViewById(R.id.sdkPermissionContent).setPadding(padding, padding, padding, padding);
        settingsOpened = state != null && state.getBoolean(SETTINGS_OPENED);
        boolean local = SdkChannelManager.getInstance().requiresLocalShell();
        View overlay = view.findViewById(R.id.sdkPermissionOverlay);
        View install = view.findViewById(R.id.sdkPermissionInstall);
        View finish = view.findViewById(R.id.sdkPermissionFinish);
        TextView instructions = view.findViewById(R.id.sdkPermissionInstructions);
        if (!local) {
            instructions.setText(R.string.sdk_permission_remote);
        } else if (SdkShell.installedVersion(requireContext().getPackageManager()) == null) {
            instructions.setText(R.string.sdk_permission_not_installed);
            local = false;
        } else {
            instructions.setText(Build.VERSION.SDK_INT >= 26
                    ? R.string.sdk_permission_steps : R.string.sdk_permission_overlay_only);
        }
        overlay.setVisibility(local ? View.VISIBLE : View.GONE);
        install.setVisibility(local && Build.VERSION.SDK_INT >= 26 ? View.VISIBLE : View.GONE);
        overlay.setOnClickListener(v -> openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        install.setOnClickListener(v -> openSettings(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
        finish.setOnClickListener(v -> dismiss());
        return view;
    }

    /**
     * 使用项目弹窗样式，宽度不超过当前可用窗口。
     */
    @Override
    public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int margin = getResources().getDimensionPixelSize(R.dimen.dp_8);
        int maxWidth = getResources().getDimensionPixelSize(R.dimen.dp_260);
        int available = requireActivity().getWindow().getDecorView().getWidth();
        if (available <= 0) available = getResources().getDisplayMetrics().widthPixels;
        window.setLayout(Math.min(maxWidth, Math.max(1, available - 2 * margin)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 按厂商支持情况尝试应用专页、权限列表及系统设置。
     */
    private void openSettings(String action) {
        SdkChannelManager channel = SdkChannelManager.getInstance();
        if (settingsOpened || !channel.requiresLocalShell()) return;
        Uri sdk = Uri.fromParts("package", SdkShell.PACKAGE_NAME, null);
        Intent[] intents = {
                new Intent(action, sdk),
                new Intent(action),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, sdk),
                new Intent(Settings.ACTION_SETTINGS)
        };
        for (Intent intent : intents) {
            try {
                Intent systemPage = ApkInstallIntents.explicitSystemActivity(requireContext().getPackageManager(), intent);
                settingsOpened = true;
                settingsClient = channel.getClient();
                settings.launch(systemPage);
                return;
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                // 部分固件没有专用页面，继续尝试下一个入口。
                settingsOpened = false;
                settingsClient = null;
            }
        }
        ToastEx.showFailureToast(requireContext(), getString(R.string.sdk_permission_open_failed));
    }

    /**
     * 先用只读回执确认权限，避免用户只完成一项时 SDK 再弹一次。
     */
    private void onSettingsReturned() {
        if (!settingsOpened || !isAdded()) return;
        settingsOpened = false;
        SdkChannelManager channel = SdkChannelManager.getInstance();
        HuoyanSdkClient client = channel.getClient();
        if (!channel.requiresLocalShell() || (settingsClient != null && settingsClient != client))
            return;
        settingsClient = null;
        IOThreadHelper.getInstance().post(() -> {
            if (!client.isConnected()) return;
            try {
                CertInfo reply = client.getCertInfo();
                if (reply != null && !isPermissionDenial(reply.getReason())
                        && reply.getCode() != HuoyanSdkClient.RESULT_UNKNOWN
                        && reply.getCode() != HuoyanSdkClient.NOT_EXECUTED
                        && reply.getCode() != HuoyanSdkClient.LOCAL_DISCONNECTED
                        && client == channel.getClient()) {
                    client.initialize();
                }
            } catch (RuntimeException unavailable) {
                // 通信中断时由现有连接流程处理。
            }
        });
    }

    /**
     * 重建后保留系统设置往返状态。
     */
    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(SETTINGS_OPENED, settingsOpened);
    }
}
