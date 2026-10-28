package cc.huoyan.sdk.client.deployment;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
/** 本机 SDK 部署元信息；安装权限和交互由宿主负责。 */
public final class SdkShell {
    /** SDK 应用包名，服务标识保持现有契约。 */
    public static final String PACKAGE_NAME = "cc.huoyan";
    /** 默认发行源，下载器允许宿主覆盖 URL。 */
    public static final String DEFAULT_APK_URL = "http://f.huoyan.cc/apk/object_sdk_lastest.apk";
    /** 静态部署工具。 */
    private SdkShell() {}
    /** 返回已安装版本；宿主 Manifest 必须声明包可见性，未找到时返回 null。 */
    public static PackageInfo installedVersion(PackageManager packages) {
        try { return packages.getPackageInfo(PACKAGE_NAME, 0); }
        catch (PackageManager.NameNotFoundException ignored) { return null; }
    }
}
