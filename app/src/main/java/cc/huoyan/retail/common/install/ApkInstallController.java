package cc.huoyan.retail.common.install;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;

import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.AlertConfirmDialogListener;
import cc.huoyan.retail.common.ui.AlertDialog;
import cc.huoyan.retail.common.ui.LoadingDialog;
import cc.huoyan.retail.common.ui.ToastEx;
import cc.huoyan.sdk.client.deployment.SdkApkDownloader;
import cc.huoyan.sdk.client.deployment.SdkShell;

import java.io.File;
import java.io.IOException;

/** 客户端与 SDK 共用的下载、授权及安装流程；所有状态在主线程读写。 */
public final class ApkInstallController {
    /** 当前页面的下载弹窗标识；同一控制器只允许一个下载。 */
    private static final String TAG = "apkInstallProgress";
    /** 项目样式的安装确认弹窗标识。 */
    private static final String PROMPT_TAG = "apkInstallPrompt";
    /** 保存整个安装会话的独立状态键。 */
    private static final String STATE = "apkInstall";
    /** 安装页面宿主；销毁时取消请求并关闭弹窗。 */
    private final AppCompatActivity activity;
    /** 仅在前台显示的安装确认框。 */
    private AlertDialog prompt;
    /** 仅通过项目 LoadingDialog 展示下载进度。 */
    private LoadingDialog progress;
    /** 当前后台下载；使用对象身份丢弃过期回调。 */
    private SdkApkDownloader download;
    /** 已下载的临时安装包；授权返回后继续使用。 */
    private File pendingApk;
    /** 当前发行地址；默认用于首页 SDK 安装引导。 */
    private String sourceUrl = SdkShell.DEFAULT_APK_URL;
    /** 当前安装对象的名称资源，避免客户端流程显示 SDK 文案。 */
    @StringRes private int packageLabel = R.string.apk_name_sdk;
    /** 正在等待系统未知来源授权页返回。 */
    private boolean awaitingPermission;
    /** 宿主是否在前台，后台完成不直接启动安装器。 */
    private boolean resumed;
    /** 页面销毁后禁止回调操作 UI。 */
    private boolean destroyed;
    /** 下载在后台结束，回到前台后继续安装。 */
    private boolean installWhenResumed;
    /** 用户主动下载时不因已安装 SDK 而终止流程。 */
    private boolean manualRequest;
    /** 系统安装器已经打开；返回后不自动重复安装。 */
    private boolean installerOpened;
    /** 后台失败时暂存的一次性提示，恢复前台后消费。 */
    @StringRes private int failureMessage;

    /** 创建页面级共用流程，恢复授权页或安装器返回时所需状态。 */
    public ApkInstallController(AppCompatActivity activity, Bundle state) {
        this.activity = activity;
        Bundle saved = state == null ? null : state.getBundle(STATE);
        if (saved != null) {
            sourceUrl = saved.getString("url", SdkShell.DEFAULT_APK_URL);
            packageLabel = saved.getInt("label", R.string.apk_name_sdk);
            String path = saved.getString("apk");
            if (path != null && new File(path).isFile()) pendingApk = new File(path);
            awaitingPermission = saved.getBoolean("permission");
            manualRequest = saved.getBoolean("manual");
            installerOpened = saved.getBoolean("installer");
            installWhenResumed = saved.getBoolean("installWhenResumed");
            failureMessage = saved.getInt("failure");
        }
        dismissRestoredDialog(TAG);
        dismissRestoredDialog(PROMPT_TAG);
    }

    /** 恢复安装流程；仅首页按需提示缺少 SDK，维护页只处理主动下载。 */
    public boolean onResume(boolean promptIfMissing) {
        if (destroyed) return false;
        resumed = true;
        boolean installed = SdkShell.installedVersion(activity.getPackageManager()) != null;
        if (BuildConfig.DEBUG) Log.d(TAG, "[Biz] sdk_installed=" + installed);
        showFailure();
        if (installerOpened || (installed && !manualRequest)) {
            cancelDownload();
            dismissPrompt();
            dismissProgress();
            clearApk();
            awaitingPermission = false;
            manualRequest = false;
            installerOpened = false;
            installWhenResumed = false;
            return installed;
        }
        if (!promptIfMissing && !manualRequest) return installed;
        if (awaitingPermission) {
            awaitingPermission = false;
            if (canInstallPackages()) installApk();
            else fail(R.string.apk_install_permission_required);
        } else if (installWhenResumed) {
            installWhenResumed = false;
            installApk();
        } else if (download == null) {
            showPrompt();
        }
        return false;
    }

    /** 主动下载指定发行包；同页已有下载或授权流程时忽略重复提交。 */
    public void downloadAndInstall(String url, @StringRes int label) {
        if (!canShowUi() || download != null || awaitingPermission || installerOpened) return;
        sourceUrl = url;
        packageLabel = label;
        manualRequest = true;
        installWhenResumed = false;
        dismissPrompt();
        clearApk();
        startDownload();
    }

    /** 离开前台后下载可继续，但不弹出系统页面或结果提示。 */
    public void onPause() {
        resumed = false;
    }

    /** 保存待安装文件和发行对象，避免授权返回后重复下载或串用 SDK 文案。 */
    public void saveState(Bundle state) {
        Bundle saved = new Bundle();
        saved.putString("url", sourceUrl);
        saved.putInt("label", packageLabel);
        if (pendingApk != null) saved.putString("apk", pendingApk.getAbsolutePath());
        saved.putBoolean("permission", awaitingPermission);
        saved.putBoolean("manual", manualRequest);
        saved.putBoolean("installer", installerOpened);
        saved.putBoolean("installWhenResumed", installWhenResumed);
        saved.putInt("failure", failureMessage);
        state.putBundle(STATE, saved);
    }

    /** 页面销毁时取消下载，关闭弹窗并阻止旧任务回调操作新页面。 */
    public void onDestroy() {
        destroyed = true;
        resumed = false;
        cancelDownload();
        dismissPrompt();
        dismissProgress();
    }

    /** 页面及 FragmentManager 均可交互时才允许启动 UI 流程。 */
    private boolean canShowUi() {
        return resumed && !destroyed && !activity.isFinishing()
                && !activity.getSupportFragmentManager().isStateSaved();
    }

    /** 默认 SDK 缺失提示与已下载安装包的继续安装确认共用项目 AlertDialog。 */
    private void showPrompt() {
        if (!canShowUi() || prompt != null) return;
        String message = pendingApk != null
                ? activity.getString(R.string.apk_install_pending, activity.getString(packageLabel))
                : manualRequest ? activity.getString(R.string.apk_install_confirm, activity.getString(packageLabel))
                : activity.getString(R.string.sdk_install_missing);
        prompt = new AlertDialog(activity.getString(R.string.apk_install_title, activity.getString(packageLabel)),
                message, activity.getString(pendingApk == null
                        ? R.string.sdk_install_download : R.string.sdk_install_continue),
                activity.getString(R.string.sdk_install_later), new AlertConfirmDialogListener() {
                    /** 确认后只启动一个下载或使用已有完整安装包。 */
                    @Override public void onConfirm() {
                        dismissPrompt();
                        if (pendingApk == null) startDownload();
                        else installApk();
                    }

                    /** 取消后保留已下载文件，下次主动操作可重新获取发行包。 */
                    @Override public void onCancel() {
                        dismissPrompt();
                        manualRequest = false;
                    }
                });
        prompt.setCancelable(false);
        prompt.show(activity.getSupportFragmentManager(), PROMPT_TAG);
    }

    /** 复用 SDK 流式下载器；等待及进度只显示在 LoadingDialog 中。 */
    private void startDownload() {
        if (!canShowUi() || download != null) return;
        failureMessage = 0;
        progress = new LoadingDialog(activity.getString(R.string.apk_install_downloading,
                activity.getString(packageLabel)));
        progress.setDownloadProgress(0, -1);
        progress.setCancelable(true);
        progress.setCancelAction(() -> {
            cancelDownload();
            manualRequest = false;
            progress = null;
        });
        progress.showNow(activity.getSupportFragmentManager(), TAG);
        progress.requireDialog().setCanceledOnTouchOutside(false);
        SdkApkDownloader task = new SdkApkDownloader(sourceUrl, activity.getCacheDir());
        download = task;
        task.start(new SdkApkDownloader.Listener() {
            /** 下载线程只投递进度；主线程核对任务和视图存活状态。 */
            @Override public void onProgress(long downloaded, long total) {
                activity.runOnUiThread(() -> {
                    if (download != task || progress == null || destroyed) return;
                    progress.setDownloadProgress(downloaded, total);
                });
            }

            /** 完整下载后关闭等待；后台完成则推迟到恢复前台再启动安装器。 */
            @Override public void onComplete(File apk) {
                if (BuildConfig.DEBUG) Log.d(TAG, "[Biz] apk_downloaded=" + apk.length());
                activity.runOnUiThread(() -> {
                    if (download != task || destroyed) {
                        apk.delete();
                        return;
                    }
                    download = null;
                    dismissProgress();
                    pendingApk = apk;
                    if (canShowUi()) installApk();
                    else installWhenResumed = true;
                });
            }

            /** 真实失败关闭遮罩，通过 ToastEx 一次性提示。 */
            @Override public void onFailure(IOException error) {
                activity.runOnUiThread(() -> {
                    if (download != task || destroyed) return;
                    download = null;
                    dismissProgress();
                    fail(R.string.apk_install_download_failed);
                });
            }
        });
    }

    /** 校验缓存文件及未知来源权限后，显式打开设备系统安装器。 */
    private void installApk() {
        if (!canShowUi()) {
            installWhenResumed = true;
            return;
        }
        if (pendingApk == null || !pendingApk.isFile()) {
            pendingApk = null;
            fail(R.string.apk_install_download_failed);
            return;
        }
        if (!canInstallPackages()) {
            try {
                awaitingPermission = true;
                activity.startActivity(ApkInstallIntents.explicitSystemActivity(activity.getPackageManager(),
                        new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()))));
            } catch (RuntimeException error) {
                Log.e(TAG, "Cannot open install permission page", error);
                awaitingPermission = false;
                fail(R.string.sdk_install_open_failed);
            }
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", pendingApk);
            activity.startActivity(ApkInstallIntents.explicitSystemActivity(activity.getPackageManager(),
                    new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)));
            installerOpened = true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Cannot open package installer", error);
            fail(R.string.sdk_install_open_failed);
        }
    }

    /** Android 8 起读取本应用的未知来源安装授权。 */
    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls();
    }

    /** 暂存真实失败结果；后台不展示 Toast，回到前台后消费一次。 */
    private void fail(@StringRes int message) {
        failureMessage = message;
        manualRequest = false;
        installWhenResumed = false;
        showFailure();
    }

    /** 消费失败结果，不把安装器打开误报为安装成功。 */
    private void showFailure() {
        if (!resumed || destroyed || failureMessage == 0) return;
        ToastEx.showFailureToast(activity, activity.getString(failureMessage, activity.getString(packageLabel)));
        failureMessage = 0;
    }

    /** 取消当前请求；下载器负责清理未交付的临时文件。 */
    private void cancelDownload() {
        if (download != null) download.cancel();
        download = null;
    }

    /** 丢弃不再使用的完整安装包。 */
    private void clearApk() {
        if (pendingApk != null) pendingApk.delete();
        pendingApk = null;
    }

    /** 关闭当前确认框，允许宿主在保存状态后销毁。 */
    private void dismissPrompt() {
        if (prompt != null) prompt.dismissAllowingStateLoss();
        prompt = null;
    }

    /** 关闭等待遮罩，不向页面写入任何进度或结果。 */
    private void dismissProgress() {
        if (progress != null) progress.dismissAllowingStateLoss();
        progress = null;
    }

    /** 旧页面下载已取消，重建时移除没有对应任务的恢复弹窗。 */
    private void dismissRestoredDialog(String tag) {
        androidx.fragment.app.Fragment stale = activity.getSupportFragmentManager().findFragmentByTag(tag);
        if (stale instanceof DialogFragment) ((DialogFragment) stale).dismissAllowingStateLoss();
    }
}
