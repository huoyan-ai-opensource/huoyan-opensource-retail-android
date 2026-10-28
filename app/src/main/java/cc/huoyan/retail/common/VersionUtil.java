package cc.huoyan.retail.common;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class VersionUtil {
    /**
     * 获取应用的版本名称
     *
     * @param context 上下文对象
     * @return 版本名称
     */
    public static String getVersionName(Context context) throws PackageManager.NameNotFoundException {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
        return packageInfo.versionName;

    }

    /**
     * 获取应用的版本代码
     *
     * @param context 上下文对象
     * @return 版本代码
     */
    public static int getVersionCode(Context context) throws PackageManager.NameNotFoundException {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
        // 在 Android 10 及以上版本中，使用 packageInfo.getLongVersionCode()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return (int) packageInfo.getLongVersionCode();
        } else {
            return packageInfo.versionCode;
        }
    }
}
