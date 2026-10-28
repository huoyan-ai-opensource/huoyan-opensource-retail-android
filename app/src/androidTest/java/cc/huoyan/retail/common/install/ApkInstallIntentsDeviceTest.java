package cc.huoyan.retail.common.install;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.*;

/** 真机验证安装页面解析；可选使用已下载文件打开确认页，不执行安装或修改权限。 */
@RunWith(AndroidJUnit4.class)
public class ApkInstallIntentsDeviceTest {
    /** 安装器显式解析后仍保留文件地址、类型及临时读权限。 */
    @Test public void installerKeepsApkUriAndReadGrant() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent request = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("content://" + context.getPackageName()
                        + ".fileprovider/internal_cache/test.apk"), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent resolved = ApkInstallIntents.explicitSystemActivity(context.getPackageManager(), request);
        assertNotNull(resolved.getComponent());
        assertNull(request.getComponent());
        assertEquals(request.getData(), resolved.getData());
        assertEquals(request.getType(), resolved.getType());
        assertEquals(request.getFlags(), resolved.getFlags());
    }

    /** 授权页按系统组件解析，保留当前应用的包名参数。 */
    @Test public void permissionPageKeepsPackageUri() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent request = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
        Intent resolved = ApkInstallIntents.explicitSystemActivity(context.getPackageManager(), request);
        assertNotNull(resolved.getComponent());
        assertEquals(request.getData(), resolved.getData());
    }

    /** 没有系统处理页面时明确报错，交给安装引导展示重试提示。 */
    @Test public void missingSystemPageReportsFailure() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThrows(ActivityNotFoundException.class, () -> ApkInstallIntents.explicitSystemActivity(
                context.getPackageManager(), new Intent("cc.huoyan.retail.test.NO_INSTALL_HANDLER")));
    }

    /** 指定缓存 APK 时从应用进程打开安装或授权页，后续确认交给用户。 */
    @Test public void openDownloadedApkWhenRequested() {
        String path = InstrumentationRegistry.getArguments().getString("sdkApkPath");
        Assume.assumeNotNull(path);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File apk = new File(path);
        assertTrue(apk.isFile());
        Intent request;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            request = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
        } else {
            request = new Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", apk),
                    "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        Intent launch = ApkInstallIntents.explicitSystemActivity(context.getPackageManager(), request)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> context.startActivity(launch));
        // 安装器在后台复制 APK，等待其读取完成再结束测试进程及临时授权。
        android.os.SystemClock.sleep(3000);
    }
}
