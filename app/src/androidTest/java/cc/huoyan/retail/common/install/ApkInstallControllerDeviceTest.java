package cc.huoyan.retail.common.install;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.fragment.app.DialogFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import cc.huoyan.retail.R;
import cc.huoyan.retail.RetailConstants;
import cc.huoyan.retail.common.ui.LoadingDialog;
import cc.huoyan.retail.common.ui.ToastTestProbe;
import cc.huoyan.retail.settings.SettingsPageActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

/** 实机验证维护入口、下载互斥、后台完成和失败；不确认安装，不改业务配置。 */
@RunWith(AndroidJUnit4.class)
public class ApkInstallControllerDeviceTest {
    /** 通过主线程执行页面和弹窗操作。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 每例独立打开的系统维护页面。 */
    private SettingsPageActivity page;
    /** 当前例持有的安装控制器，结束时取消所有请求。 */
    private ApkInstallController controller;
    /** 本例已经完整下载的临时文件，验证后删除。 */
    private File downloaded;

    /** 打开维护页并取得真实页面使用的下载会话。 */
    @Before public void openMaintenance() {
        page = (SettingsPageActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                SettingsPageActivity.class).putExtra("page", "maintenance")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.waitForIdleSync();
        ins.runOnMainSync(() -> controller = (ApkInstallController) field(
                page.getSupportFragmentManager().findFragmentById(R.id.pageContent), "apkInstall"));
    }

    /** 关闭本例页面、任务与临时文件，不修改用户商品或偏好。 */
    @After public void closeMaintenance() {
        ins.runOnMainSync(() -> {
            if (controller != null) controller.onDestroy();
            if (page != null) page.finish();
        });
        ins.waitForIdleSync();
        if (downloaded != null) downloaded.delete();
    }

    /** 两入口共享一个任务；取消加载后不能残留遮罩或接收过期下载结果。 */
    @Test public void maintenanceClientEntryUsesFixedUrlAndBlocksDuplicateSdkRequest() throws Exception {
        assertNotNull(page.findViewById(R.id.handleClientInstall));
        capture("maintenance");
        ins.runOnMainSync(() -> {
            assertTrue(page.findViewById(R.id.handleClientInstall).performClick());
            assertEquals(RetailConstants.CLIENT_LATEST_APK_URL, field(controller, "sourceUrl"));
            assertEquals(R.string.apk_name_client, field(controller, "packageLabel"));
            Object task = field(controller, "download");
            assertNotNull(task);
            page.findViewById(R.id.handleInstall).performClick();
            assertSame(task, field(controller, "download"));
            assertEquals(RetailConstants.CLIENT_LATEST_APK_URL, field(controller, "sourceUrl"));
            DialogFragment progress = (DialogFragment) page.getSupportFragmentManager()
                    .findFragmentByTag("apkInstallProgress");
            assertTrue(progress instanceof LoadingDialog);
            assertTrue(((TextView) progress.requireView().findViewById(R.id.textView))
                    .getText().toString().contains(page.getString(R.string.apk_name_client)));
            progress.requireDialog().cancel();
        });
        ins.waitForIdleSync();
        ins.runOnMainSync(() -> {
            assertNull(field(controller, "download"));
            assertNull(field(controller, "pendingApk"));
            assertNull(page.getSupportFragmentManager().findFragmentByTag("apkInstallProgress"));
        });
    }

    /** 完整后台下载保存真实文件及客户端身份，重建会话不串用 SDK 地址或名称。 */
    @Test public void backgroundDownloadRestoresClientPackageWithoutOpeningInstaller() throws Exception {
        byte[] payload = "isolated-install-test".getBytes(StandardCharsets.UTF_8);
        try (ReplyServer server = new ReplyServer(200, payload)) {
            ins.runOnMainSync(() -> {
                controller.downloadAndInstall(server.url(), R.string.apk_name_client);
            });
            capture("client-download");
            ins.runOnMainSync(() -> controller.onPause());
            server.reply.countDown();
            await(() -> {
                boolean[] ready = {false};
                ins.runOnMainSync(() -> ready[0] = field(controller, "pendingApk") != null);
                return ready[0];
            });
            Bundle saved = new Bundle();
            ins.runOnMainSync(() -> {
                downloaded = (File) field(controller, "pendingApk");
                assertEquals(false, field(controller, "installerOpened"));
                assertEquals(true, field(controller, "installWhenResumed"));
                assertNull(page.getSupportFragmentManager().findFragmentByTag("apkInstallProgress"));
                controller.saveState(saved);
                controller.onDestroy();
                controller = new ApkInstallController(page, saved);
                assertEquals(server.url(), field(controller, "sourceUrl"));
                assertEquals(R.string.apk_name_client, field(controller, "packageLabel"));
                assertEquals(downloaded, field(controller, "pendingApk"));
                assertEquals(true, field(controller, "installWhenResumed"));
            });
            assertArrayEquals(payload, Files.readAllBytes(downloaded.toPath()));
        }
    }

    /** HTTP 失败只产生错误 Toast，关闭等待弹窗，随后允许用户再次发起下载。 */
    @Test public void failedDownloadClosesLoadingAndCanRetry() throws Exception {
        try (ReplyServer server = new ReplyServer(404, new byte[0]);
             ReplyServer retry = new ReplyServer(200, new byte[]{1})) {
            ins.runOnMainSync(() -> controller.downloadAndInstall(server.url(), R.string.apk_name_client));
            server.reply.countDown();
            String expected = page.getString(R.string.apk_install_download_failed, page.getString(R.string.apk_name_client));
            await(() -> {
                String[] message = {""};
                ins.runOnMainSync(() -> message[0] = ToastTestProbe.text());
                return expected.equals(message[0]);
            });
            ins.runOnMainSync(() -> {
                assertNull(field(controller, "download"));
                assertNull(page.getSupportFragmentManager().findFragmentByTag("apkInstallProgress"));
                assertNull(page.getSupportFragmentManager().findFragmentByTag("apkInstallPrompt"));
                controller.downloadAndInstall(retry.url(), R.string.apk_name_client);
                assertNotNull(field(controller, "download"));
                controller.onDestroy();
            });
            ins.waitForIdleSync();
            ins.runOnMainSync(() -> assertNull(page.getSupportFragmentManager().findFragmentByTag("apkInstallProgress")));
        }
    }

    /** SDK 与客户端都按网络字节推进同一个进度条，关闭按钮取消各自当前任务。 */
    @Test public void sdkAndClientShareRealProgressBarAndCloseAction() throws Exception {
        for (int label : new int[]{R.string.apk_name_sdk, R.string.apk_name_client}) {
            try (ReplyServer server = new ReplyServer(200, new byte[]{1, 2}, true)) {
                ins.runOnMainSync(() -> {
                    controller.downloadAndInstall(server.url(), label);
                    LoadingDialog dialog = (LoadingDialog) field(controller, "progress");
                    ProgressBar bar = dialog.requireView().findViewById(R.id.progressBar);
                    assertTrue(bar.isIndeterminate());
                });
                server.reply.countDown();
                await(() -> {
                    boolean[] halfway = {false};
                    ins.runOnMainSync(() -> {
                        LoadingDialog dialog = (LoadingDialog) field(controller, "progress");
                        ProgressBar bar = dialog.requireView().findViewById(R.id.progressBar);
                        halfway[0] = !bar.isIndeterminate() && bar.getProgress() == 50;
                    });
                    return halfway[0];
                });
                capture(label == R.string.apk_name_sdk ? "sdk-progress" : "client-progress");
                ins.runOnMainSync(() -> {
                    LoadingDialog dialog = (LoadingDialog) field(controller, "progress");
                    ProgressBar bar = dialog.requireView().findViewById(R.id.progressBar);
                    assertTrue(bar instanceof cc.huoyan.retail.common.ui.TextProgressBar);
                    assertEquals(50, bar.getProgress());
                    assertTrue(((TextView) dialog.requireView().findViewById(R.id.textView))
                            .getText().toString().contains(page.getString(label)));
                    dialog.requireView().findViewById(R.id.close).performClick();
                });
                ins.waitForIdleSync();
                ins.runOnMainSync(() -> {
                    assertNull(field(controller, "download"));
                    assertNull(page.getSupportFragmentManager().findFragmentByTag("apkInstallProgress"));
                });
            }
        }
    }

    /** 显式启用时下载线上客户端并验证完整 APK，不打开安装器或覆盖当前应用。 */
    @Test public void publishedClientDownloadsWhenRequested() throws Exception {
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments()
                .getString("downloadPublishedClient")));
        ins.runOnMainSync(() -> {
            page.findViewById(R.id.handleClientInstall).performClick();
            controller.onPause();
        });
        long deadline = SystemClock.elapsedRealtime() + 60000;
        while (SystemClock.elapsedRealtime() < deadline && downloaded == null) {
            ins.runOnMainSync(() -> downloaded = (File) field(controller, "pendingApk"));
            SystemClock.sleep(100);
        }
        assertNotNull("固定地址应返回完整安装包", downloaded);
        android.content.pm.PackageInfo info = page.getPackageManager()
                .getPackageArchiveInfo(downloaded.getAbsolutePath(), 0);
        assertNotNull("下载内容必须为可解析 APK", info);
        assertEquals(page.getPackageName(), info.packageName);
        ins.runOnMainSync(() -> {
            assertEquals(false, field(controller, "installerOpened"));
            assertNull(page.getSupportFragmentManager().findFragmentByTag("apkInstallProgress"));
        });
    }

    /** 在 UI 线程读取内部会话状态，验证生命周期而不替换实际下载器。 */
    private static Object field(Object object, String name) {
        try {
            java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    /** 有界等待异步回调，失败时保留明确断言。 */
    private void await(BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        assertTrue("下载回调应在 10 秒内结束", condition.getAsBoolean());
    }

    /** 保存当前真实窗口截图，文件名区分本轮横竖屏。 */
    private void capture(String name) throws Exception {
        File dir = new File(page.getExternalFilesDir(null), "client-install-qa");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        SystemClock.sleep(300);
        Bitmap screenshot = ins.getUiAutomation().takeScreenshot();
        String orientation = page.getResources().getConfiguration().orientation == 1 ? "portrait" : "landscape";
        try (FileOutputStream output = new FileOutputStream(new File(dir, name + "-" + orientation + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            screenshot.recycle();
        }
    }

    /** 仅监听设备回环地址的单次 HTTP 回执，避免测试依赖公网或真实安装包。 */
    private static final class ReplyServer implements AutoCloseable {
        /** 临时端口，仅供当前测试的下载器访问。 */
        private final ServerSocket socket;
        /** 测试明确放行后才返回，保证可覆盖暂停和重复点击。 */
        private final CountDownLatch reply = new CountDownLatch(1);
        /** 分段测试在半包时等待，便于断言真实中间进度。 */
        private final CountDownLatch finishReply = new CountDownLatch(1);
        /** 后台 HTTP 响应线程，关闭时回收。 */
        private final Thread worker;

        /** 创建单次服务，不解析或修改任何应用配置。 */
        ReplyServer(int code, byte[] bytes) throws Exception {
            this(code, bytes, false);
        }

        /** 可在半包处暂停，完整下载与普通错误用例保持一次返回。 */
        ReplyServer(int code, byte[] bytes, boolean pauseHalfway) throws Exception {
            socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
            worker = new Thread(() -> {
                try (Socket client = socket.accept()) {
                    if (!reply.await(10, TimeUnit.SECONDS)) return;
                    String headers = "HTTP/1.1 " + code + " Test\r\nContent-Length: " + bytes.length
                            + "\r\nConnection: close\r\n\r\n";
                    client.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                    if (pauseHalfway) {
                        client.getOutputStream().write(bytes, 0, bytes.length / 2);
                        client.getOutputStream().flush();
                        if (!finishReply.await(10, TimeUnit.SECONDS)) return;
                        client.getOutputStream().write(bytes, bytes.length / 2, bytes.length - bytes.length / 2);
                    } else client.getOutputStream().write(bytes);
                    client.getOutputStream().flush();
                } catch (Exception ignored) {
                    // 用户取消或关闭测试时，连接中断属于预期；成功与失败由业务断言核验。
                }
            }, "apk-test-response");
            worker.start();
        }

        /** 返回当前测试唯一的本机 URL。 */
        String url() {
            return "http://127.0.0.1:" + socket.getLocalPort() + "/client.apk";
        }

        /** 释放等待及端口，并确保测试线程退出。 */
        @Override public void close() throws Exception {
            reply.countDown();
            finishReply.countDown();
            socket.close();
            worker.join(2000);
        }
    }
}
