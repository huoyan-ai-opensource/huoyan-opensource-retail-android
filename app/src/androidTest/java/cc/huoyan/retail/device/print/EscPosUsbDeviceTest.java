package cc.huoyan.retail.device.print;

import android.graphics.Bitmap;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.checkout.domain.WeightedSaleResult;
import cc.huoyan.retail.R;
import cc.huoyan.retail.settings.SettingsPageActivity;
import cc.huoyan.retail.common.ui.ToastTestProbe;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** 实机验证系统 USB 连接及现有小票位图；默认不出纸，显式参数才允许试打。 */
@RunWith(AndroidJUnit4.class)
public class EscPosUsbDeviceTest {
    /** 真实设置页使用新协议，连接回执只提示一次，重建不残留加载框。 */
    @Test public void settingsConnectsAndRecreatesWithoutInlineStatus() throws Exception {
        String selected = PrinterManager.getInstance().resolveSelection(PrinterManager.getInstance().devices());
        assertNotNull("先在设置中选择打印设备", selected);
        String name = PrinterManager.displayName(selected);
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                SettingsPageActivity.class).putExtra("page", "print");
        try (ActivityScenario<SettingsPageActivity> scenario = ActivityScenario.launch(intent)) {
            long deadline = SystemClock.elapsedRealtime() + 10000;
            boolean[] ready = {false};
            while (!ready[0] && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity(activity -> ready[0] = ((TextView) activity.findViewById(R.id.printerModel))
                        .getText().toString().equals(name));
                SystemClock.sleep(50);
            }
            assertTrue("设备枚举应完成", ready[0]);
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.printer_escpos),
                        ((TextView) activity.findViewById(R.id.printerTypeName)).getText().toString());
                assertEquals(View.GONE, activity.findViewById(R.id.printerStatus).getVisibility());
                ToastTestProbe.clear();
                activity.findViewById(R.id.communicationTest).performClick();
            });
            boolean[] completed = {false};
            deadline = SystemClock.elapsedRealtime() + 55000;
            while (!completed[0] && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity(activity -> {
                    completed[0] = ToastTestProbe.text()
                            .equals(activity.getString(R.string.toast_printer_connected));
                });
                SystemClock.sleep(50);
            }
            assertTrue("原生 USB 连接应成功", completed[0]);
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.communicationTest).isEnabled());
                assertTrue(activity.findViewById(R.id.printTest).isEnabled());
                assertEquals(View.GONE, activity.findViewById(R.id.printerStatus).getVisibility());
            });
        }
    }

    /** 厂商 SDK 已移除；原生 USB 能枚举、申请权限、占用接口并执行无出纸预检。 */
    @Test public void connectsWithoutVendorSdk() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("POSSDKAPI.SDK"));
        PrinterManager manager = PrinterManager.getInstance();
        List<String> devices = manager.devices();
        assertFalse("需要连接标准 USB 小票打印机", devices.isEmpty());
        String selected = manager.resolveSelection(devices);
        assertNotNull("先在设置中选择打印设备", selected);
        manager.testConnection(selected);
        if ("true".equals(InstrumentationRegistry.getArguments().getString("printReceipt"))) {
            manager.printTest(selected, ReceiptPaper.fromMillimeters(
                    cc.huoyan.retail.data.SPManager.getInstance().getPrinterPaperWidthMm()));
        }
    }

    /** 真实 Android 字体与条码渲染保留 320 点宽，并可转换为 ESC/POS 完整指令。 */
    @Test public void rendersExistingReceiptIntoEscPos() throws Exception {
        WeightedSaleResult result = new WeightedSaleResult("render-test", new BigDecimal("0.500"),
                2000, 1000, 1, 1, Collections.singletonList(
                new WeightedSaleResult.Item("00001", "测试商品", 2000, 1, false, "00001", cc.huoyan.retail.pricing.PricingMode.WEIGHT)));
        ReceiptRenderer renderer = new ReceiptRenderer();
        Bitmap header = renderer.header(result);
        Bitmap footer = renderer.footer(result);
        try {
            assertEquals(320, header.getWidth());
            assertEquals(320, footer.getWidth());
            assertTrue(header.getHeight() > 200);
            assertTrue(footer.getHeight() > 100);
            int[] footerPixels = new int[footer.getWidth() * footer.getHeight()];
            footer.getPixels(footerPixels, 0, footer.getWidth(), 0, 0, footer.getWidth(), footer.getHeight());
            boolean bottomHasInk = false;
            for (int x = footerPixels.length - footer.getWidth(); x < footerPixels.length; x++) {
                int pixel = footerPixels[x];
                int luminance = (299 * ((pixel >> 16) & 255) + 587 * ((pixel >> 8) & 255)
                        + 114 * (pixel & 255)) / 1000;
                if (luminance < 160) bottomHasInk = true;
            }
            assertTrue("模板尾部不叠加白行，最后一行正文必须保留", bottomHasInk);
            java.io.File preview = new java.io.File(InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getExternalFilesDir(null), "receipt-footer-preview.png");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(preview)) {
                assertTrue(footer.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            com.google.zxing.BinaryBitmap code = new com.google.zxing.BinaryBitmap(
                    new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(
                            footer.getWidth(), footer.getHeight(), footerPixels)));
            assertEquals(ReceiptBarcode.build("00001", 1000),
                    new com.google.zxing.oned.EAN13Reader().decode(code).getText());
            for (Bitmap bitmap : new Bitmap[]{header, footer}) {
                int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                byte[] raster = EscPosEncoder.raster(bitmap.getWidth(), bitmap.getHeight(), pixels);
                assertEquals(8 + 40 * bitmap.getHeight(), raster.length);
                boolean ink = false;
                for (int i = 8; i < raster.length; i++) if (raster[i] != 0) ink = true;
                assertTrue("位图应包含可打印的文字或条码", ink);
            }
        } finally {
            header.recycle(); footer.recycle();
        }
    }
}
