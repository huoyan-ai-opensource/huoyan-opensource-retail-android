package cc.huoyan.sdk.client.internal;

import android.content.Context;
import android.content.pm.PackageManager;

/** 读取宿主客户端版本，不使用客户端库或 SDK 壳的版本号。 */
final class ClientAppVersion {
    /** 工具类不创建实例。 */
    private ClientAppVersion() {
    }

    /** 读取宿主 APK 的 versionName；包信息不可查时不伪造版本。 */
    static String read(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException unavailable) {
            return null;
        }
    }
}
