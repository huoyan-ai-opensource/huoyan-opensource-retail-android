package cc.huoyan.retail.settings;

import cc.huoyan.retail.home.HomeActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.BaseActivity;

/** 横屏功能页宿主；仅创建当前功能，避免后台页抢占相机或串口。 */
public class SettingsPageActivity extends BaseActivity {
    /** 从首页或其他功能页打开指定设置。 */
    public static void open(Context context, String page) {
        context.startActivity(new Intent(context, SettingsPageActivity.class).putExtra("page", page));
    }

    /** 退出提示的固定标识，防止重复返回叠加弹窗。 */
    private static final String EXIT_TAG = "unsavedSettings";

    @Override protected int initContentView() { return R.layout.activity_settings_page; }

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        String page = getIntent().getStringExtra("page");
        if (page == null) page = "maintenance";
        Fragment fragment;
        int title;
        switch (page) {
            case "camera": fragment = new CameraFragment(); title = R.string.land_title_camera; break;
            case "scale": fragment = new AclasScaleSettingsFragment(); title = R.string.land_title_scale; break;
            case "print": fragment = new PrintSettingsFragment(); title = R.string.land_title_print; break;
            case "goods": fragment = new GoodsFragment(); title = R.string.land_title_goods; break;
            case "authorization": fragment = new PanelFragment(); title = R.string.land_title_authorization; break;
            case "sdk": fragment = new SdkConnectionFragment(); title = R.string.land_title_sdk; break;
            case "shop": fragment = new SystemFragment(); title = R.string.land_title_shop; break;
            case "recognition": fragment = new SystemFragment(); title = R.string.land_title_recognition; break;
            default: fragment = new SystemFragment(); title = R.string.land_title_maintenance; page = "maintenance";
        }
        ((TextView) findViewById(R.id.navTitle)).setText(title);
        ((TextView) findViewById(R.id.navBack)).setText(R.string.home_back);
        findViewById(R.id.navBack).setOnClickListener(v -> requestExit());
        if (state == null) {
            Bundle arguments = new Bundle();
            arguments.putBoolean("standalone", true);
            arguments.putString("page", page);
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction().replace(R.id.pageContent, fragment).commit();
        }
    }

    /** 系统返回与页面返回按钮共用未保存检查。 */
    @Override public void onBackPressed() { requestExit(); }

    /** 仅有未保存配置时显示项目主题确认框。 */
    private void requestExit() {
        Fragment page = getSupportFragmentManager().findFragmentById(R.id.pageContent);
        if (!(page instanceof PendingSettings) || !((PendingSettings) page).hasUnsavedSettings()) {
            leaveSettings();
            return;
        }
        if (getSupportFragmentManager().isStateSaved()
                || getSupportFragmentManager().findFragmentByTag(EXIT_TAG) != null) return;
        cc.huoyan.retail.common.ui.AlertDialog dialog = new cc.huoyan.retail.common.ui.AlertDialog(
                getString(R.string.settings_unsaved_title), getString(R.string.settings_unsaved_message),
                getString(R.string.button_save_config), getString(R.string.settings_discard_exit), exitListener());
        dialog.show(getSupportFragmentManager(), EXIT_TAG);
    }

    /** 重建后恢复保存与放弃操作，遮罩或系统返回只关闭弹窗。 */
    @Override protected void onPostResume() {
        super.onPostResume();
        Fragment dialog = getSupportFragmentManager().findFragmentByTag(EXIT_TAG);
        if (dialog instanceof cc.huoyan.retail.common.ui.AlertDialog)
            ((cc.huoyan.retail.common.ui.AlertDialog) dialog).setConfirmListener(exitListener());
    }

    /** 创建仅在当前 Activity 有效的退出回调。 */
    private cc.huoyan.retail.common.ui.AlertConfirmDialogListener exitListener() {
        return new cc.huoyan.retail.common.ui.AlertConfirmDialogListener() {
            /** 保存通过校验并完成后返回首页；失败保留页面与草稿。 */
            @Override public void onConfirm() {
                Fragment page = getSupportFragmentManager().findFragmentById(R.id.pageContent);
                if (page instanceof PendingSettings && ((PendingSettings) page).savePendingSettings()) leaveSettings();
            }
            /** 仅左侧按钮明确放弃；点击遮罩或系统返回不退出页面。 */
            @Override public void onCancel() { leaveSettings(); }
        };
    }

    /** 完成返回首页并销毁当前设置页。 */
    private void leaveSettings() { HomeActivity.open(this); finish(); }
}
