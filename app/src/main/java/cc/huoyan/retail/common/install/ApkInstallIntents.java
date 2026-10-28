package cc.huoyan.retail.common.install;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** 安装流程的系统页面解析；显式启动组件，避开部分 Android 14 固件的隐式启动异常。 */
final class ApkInstallIntents {
    /** 静态工具，不持有页面或待安装文件。 */
    private ApkInstallIntents() { }

    /** 查询系统导出页面并复制为显式 Intent，保留 APK 地址和临时读取授权。 */
    static Intent explicitSystemActivity(PackageManager packages, Intent request) {
        for (ResolveInfo match : packages.queryIntentActivities(request,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_SYSTEM_ONLY)) {
            ActivityInfo activity = match.activityInfo;
            if (activity != null && activity.exported && activity.enabled) {
                return new Intent(request).setComponent(new ComponentName(activity.packageName, activity.name));
            }
        }
        throw new ActivityNotFoundException("No system activity for " + request.getAction());
    }
}
