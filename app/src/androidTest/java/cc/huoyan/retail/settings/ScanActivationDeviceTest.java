package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.BaseResult;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 真机验证截图解码、恢复构造及激活 UI 接线；激活 SDK 调用使用隔离回执。 */
@RunWith(AndroidJUnit4.class)
public class ScanActivationDeviceTest {
    /** 当前测试运行器，所有控件操作通过主线程执行。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 授权测试页面，结束时关闭。 */
    private SettingsPageActivity page;

    /** 使用真实 Bitmap 和 ZXing 解码生成二维码，并验证资源释放。 */
    @Test public void generatedQrDecodesAndBitmapIsRecycled() throws Exception {
        String payload = "{\"serial\":\"ABCD1234EFGH5678\"}";
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 320, 320);
        Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[320 * 320];
        for (int y = 0; y < 320; y++) for (int x = 0; x < 320; x++) pixels[y * 320 + x] = matrix.get(x, y) ? 0xff000000 : 0xffffffff;
        bitmap.setPixels(pixels, 0, 320, 0, 0, 320, 320);
        try (QrBitmapFrame frame = new QrBitmapFrame(bitmap)) { assertEquals(payload, frame.decode()); }
        assertTrue(bitmap.isRecycled());
    }

    /** 模拟 FragmentManager 无参恢复，扫码类型和预览页面保持可用。 */
    @Test public void scannerCanBeRestoredWithItsArguments() {
        open();
        try {
            ins.runOnMainSync(() -> {
                ScanWechatQRCodeDialog original = ScanWechatQRCodeDialog.create(
                        cc.huoyan.retail.RetailConstants.SCAN_WE_CHART_QRCODE_DIALOG_TYPE_SERIAL);
                ScanWechatQRCodeDialog restored = (ScanWechatQRCodeDialog) page.getSupportFragmentManager()
                        .getFragmentFactory().instantiate(getClass().getClassLoader(), ScanWechatQRCodeDialog.class.getName());
                restored.setArguments(original.getArguments());
                restored.showNow(page.getSupportFragmentManager(), "restoredScan");
                assertEquals(page.getString(R.string.scan_qrcode_activate_hint),
                        ((TextView) restored.requireView().findViewById(R.id.text)).getText().toString());
                restored.dismiss();
            });
            ins.waitForIdleSync();
        } finally { close(); }
    }

    /** 窗口关闭后即使 View 尚未销毁，已经排队的解码结果也不能交付。 */
    @Test public void dismissRejectsQueuedResultBeforeViewDestruction() throws Exception {
        open();
        try {
            Field presenterField = ScanWechatQRCodeDialog.class.getDeclaredField("presenter");
            presenterField.setAccessible(true);
            ins.runOnMainSync(() -> {
                ScanWechatQRCodeDialog dialog = ScanWechatQRCodeDialog.create(1);
                dialog.showNow(page.getSupportFragmentManager(), "dismissScan");
                try {
                    ((QrScanPresenter) presenterField.get(dialog)).detach();
                    java.util.ArrayDeque<Runnable> worker = new java.util.ArrayDeque<>(), ui = new java.util.ArrayDeque<>();
                    int[] delivered = {0}, released = {0};
                    QrScanPresenter presenter = new QrScanPresenter(new QrScanPresenter.View() {
                        /** 用确定性帧固定解码完成和关闭窗口之间的时序。 */
                        public QrScanPresenter.Frame capture(int width, int height) {
                            return new QrScanPresenter.Frame() {
                                /** 模拟已解码的业务码，禁止实际提交授权。 */
                                public String decode() { return "late-code"; }
                                /** 记录帧确实完成回收。 */
                                public void close() { released[0]++; }
                            };
                        }
                        /** 若关闭后仍交付，测试必须失败。 */
                        public void scanned(String value) { delivered[0]++; }
                    }, worker::add, ui::add, () -> 0L);
                    presenterField.set(dialog, presenter);
                    presenter.frameAvailable(640, 480); ui.removeFirst().run(); worker.removeFirst().run();
                    dialog.onDismiss(dialog.requireDialog());
                    assertNotNull("Fragment 移除事务尚未执行", dialog.getView());
                    ui.removeFirst().run();
                    assertEquals(0, delivered[0]); assertEquals(1, released[0]);
                } catch (IllegalAccessException error) { throw new AssertionError(error); }
            });
        } finally { close(); }
    }

    /** 通过实际输入和扫码回调验证归一化及保存，不调用真实 SDK 激活。 */
    @Test public void manualAndScanReachSamePresenterWithoutChangingRealLicense() throws Exception {
        SdkChannelManager manager = SdkChannelManager.getInstance();
        HuoyanSdkClient real = manager.getClient();
        Field sessionField = SdkChannelManager.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        Object session = sessionField.get(manager);
        Field clientField = session.getClass().getDeclaredField("client");
        clientField.setAccessible(true);
        SharedPreferences preferences = ins.getTargetContext().getSharedPreferences("HuoYanApplication", 0);
        Object previousCode = preferences.getAll().get(SPManager.SP_ACTIVATE_CODE);
        List<String> submitted = new CopyOnWriteArrayList<>();
        HuoyanSdkClient fake = (HuoyanSdkClient) Proxy.newProxyInstance(real.getClass().getClassLoader(),
                new Class[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isConnected")) return true;
                    if (method.getName().equals("queryLicenseModels")) return new cc.huoyan.sdk.client.model.LicenseModels(
                            0, null, java.util.Arrays.asList(new cc.huoyan.sdk.client.model.LicenseModel("dynamic-code", "动态测试模型", "chip", false)));
                    if (method.getName().equals("activate")) {
                        assertEquals("dynamic-code", args[1]); submitted.add((String) args[0]); return new BaseResult(-987, "隔离测试回执");
                    }
                    try { return method.invoke(real, args); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
        try {
            synchronized (session) { clientField.set(session, fake); }
            open(); openInput();
            ins.runOnMainSync(() -> {
                ActivateCodeDialog input = input();
                ((EditText) input.requireView().findViewById(R.id.editText)).setText("bad");
                input.requireView().findViewById(R.id.activate).performClick();
                assertTrue(input.isAdded()); assertTrue(submitted.isEmpty());
                ((EditText) input.requireView().findViewById(R.id.editText)).setText("ABCD1234EFGH5678");
                input.requireView().findViewById(R.id.activate).performClick();
            });
            chooseModel();
            await(() -> submitted.size() == 1 && dialog("modelLoading") == null && input() == null);
            assertEquals("ABCD-1234-EFGH-5678", submitted.get(0));
            assertEquals(submitted.get(0), preferences.getString(SPManager.SP_ACTIVATE_CODE, ""));

            openInput();
            ins.runOnMainSync(() -> input().requireView().findViewById(R.id.scan).performClick());
            await(() -> dialog("scanWechatQRCode") != null && dialog("scanWechatQRCode").getView() != null);
            ins.runOnMainSync(() -> ((ScanWechatQRCodeDialog) dialog("scanWechatQRCode"))
                    .scanned("{\"serial\":\"HY2-87IA-VY86-QZXY\"}"));
            chooseModel();
            await(() -> submitted.size() == 2 && dialog("modelLoading") == null && dialog("scanWechatQRCode") == null);
            assertEquals("HY2-87IA-VY86-QZXY", submitted.get(1));
            assertEquals(submitted.get(1), preferences.getString(SPManager.SP_ACTIVATE_CODE, ""));
        } finally {
            close();
            synchronized (session) { clientField.set(session, real); }
            SharedPreferences.Editor editor = preferences.edit();
            if (previousCode == null) editor.remove(SPManager.SP_ACTIVATE_CODE); else editor.putString(SPManager.SP_ACTIVATE_CODE, (String) previousCode);
            editor.commit();
        }
    }

    /** 点选前没有激活请求，选项提交稳定模型代码。 */
    private void chooseModel() {
        await(() -> dialog("selectOption") != null && dialog("selectOption").getView() != null);
        ins.runOnMainSync(() -> {
            android.widget.ListView list = dialog("selectOption").requireView().findViewById(R.id.selectOptions);
            list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0));
        });
    }

    /** 打开授权页，保持现有账号和识别库。 */
    private void open() {
        page = (SettingsPageActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), SettingsPageActivity.class)
                .putExtra("page", "authorization").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.waitForIdleSync();
    }
    /** 点击真实激活入口并等待输入控件创建。 */
    private void openInput() {
        ins.runOnMainSync(() -> page.findViewById(R.id.activate).performClick());
        await(() -> input() != null && input().getView() != null);
    }
    /** 返回当前输入弹窗，不创建替代页面。 */
    private ActivateCodeDialog input() { return (ActivateCodeDialog) dialog("activateCode"); }
    /** 按页面的实际 tag 查询当前弹窗。 */
    private DialogFragment dialog(String tag) { androidx.fragment.app.Fragment flow = page.getSupportFragmentManager().findFragmentByTag("modelActivation");
        return flow == null ? null : (DialogFragment) flow.getChildFragmentManager().findFragmentByTag(tag); }
    /** 结束页面并等待生命周期清理。 */
    private void close() { if (page != null) { ins.runOnMainSync(page::finish); ins.waitForIdleSync(); page = null; } }
    /** 有界等待主线程状态，以断言验证超时结果。 */
    private void await(BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (SystemClock.elapsedRealtime() < deadline) {
            boolean[] passed = {false}; ins.runOnMainSync(() -> passed[0] = condition.getAsBoolean());
            if (passed[0]) return; SystemClock.sleep(100);
        }
        ins.runOnMainSync(() -> assertTrue("等待扫码或激活状态超时", condition.getAsBoolean()));
    }
}
