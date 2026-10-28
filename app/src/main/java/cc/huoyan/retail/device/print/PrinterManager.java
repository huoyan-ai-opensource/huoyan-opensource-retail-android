package cc.huoyan.retail.device.print;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import cc.huoyan.retail.R;
import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.RetailApplication;
import cc.huoyan.retail.checkout.domain.WeightedSaleResult;
import cc.huoyan.retail.common.LocalizedText;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.diagnostics.Logger;

/** 通用小票入口；后台串行执行打印任务，仅持有应用上下文，页面退出不取消打印。 */
public final class PrinterManager {
    /** 应用级唯一打印协调器。 */
    private static final PrinterManager INSTANCE = new PrinterManager();
    /** 连接测试与打印共用的互斥锁；主线程不得执行持锁操作。 */
    private final Object deviceLock = new Object();

    /** 单例无原生 SDK 初始化副作用。 */
    private PrinterManager() { }

    /** 返回通用打印协调器。 */
    public static PrinterManager getInstance() { return INSTANCE; }

    /** 返回标准 USB 打印设备，供设置页后台发现。 */
    public List<String> devices() {
        return UsbPrinterTransport.devices(RetailApplication.getInstance());
    }

    /** 从完整设备标识提取展示名称；界面隐藏 USB 标识，保存及连接仍使用完整值。 */
    public static String displayName(String device) {
        return device.replaceFirst(" \\[[0-9A-F]{4}:[0-9A-F]{4}\\] · /dev/.*$", "");
    }

    /** 从发现结果解析已保存的打印设备标识，不自动选择未配置的设备。 */
    public String resolveSelection(List<String> devices) {
        return UsbPrinterSelection.resolve(devices, SPManager.getInstance().getPrinterDevice());
    }

    /** 在后台验证授权、接口占用及可用的类状态，不发送出纸命令。 */
    public void testConnection(String selected) throws IOException {
        synchronized (deviceLock) {
            Context context = RetailApplication.getInstance();
            try (UsbPrinterTransport transport = UsbPrinterTransport.open(context, selected)) {
                transport.checkReady(context);
                if (BuildConfig.DEBUG) Logger.logDebug("PrinterManager", "[Biz] printer_connection=ok");
            }
        }
    }

    /** 使用真实销售小票模板打印示例数据，不提交销售反馈或改变购物车。 */
    public void printTest(String selected, ReceiptPaper paper) throws IOException {
        WeightedSaleResult.Item item = new WeightedSaleResult.Item("00001",
                LocalizedText.get(R.string.printer_test_item), 2000, 1, false, "00001", cc.huoyan.retail.pricing.PricingMode.WEIGHT);
        printWeightedSale(new WeightedSaleResult("print-test", new BigDecimal("0.500"),
                2000, 1000, 1, 1, Collections.singletonList(item)), selected, paper);
    }

    /** 发送固定销售快照；正常返回只代表字节已发送，不能保证机械出纸已完成。 */
    public void printWeightedSale(WeightedSaleResult result, String selected, ReceiptPaper paper) throws IOException {
        if (result == null || result.getItems().isEmpty()) {
            throw new IllegalArgumentException(LocalizedText.get(R.string.printer_error_no_sale_result));
        }
        synchronized (deviceLock) {
            Context context = RetailApplication.getInstance();
            ReceiptRenderer renderer = new ReceiptRenderer();
            Bitmap header = renderer.header(result);
            Bitmap footer = null;
            try {
                footer = renderer.footer(result);
                try (UsbPrinterTransport transport = UsbPrinterTransport.open(context, selected)) {
                    transport.checkReady(context);
                    try {
                        EscPosReceiptWriter.print(raster(header), raster(footer), paper, transport);
                    } catch (IOException | RuntimeException error) {
                        // 设备可能已收到部分数据，不能通过自动重连推断是否需要补打。
                        throw new IOException(LocalizedText.get(R.string.printer_send_uncertain), error);
                    }
                    if (BuildConfig.DEBUG) Logger.logDebug("PrinterManager", "[Biz] printer_result=sent");
                }
            } finally {
                if (header != null) header.recycle();
                if (footer != null) footer.recycle();
            }
        }
    }

    /** 将 Android 位图适配为可分带读取的像素源；仅在位图回收前同步使用。 */
    private static EscPosReceiptWriter.Raster raster(Bitmap bitmap) {
        return new EscPosReceiptWriter.Raster() {
            /** 返回位图原始宽度。 */
            @Override public int width() { return bitmap.getWidth(); }
            /** 返回位图原始高度。 */
            @Override public int height() { return bitmap.getHeight(); }
            /** 仅读取当前条带，限制临时像素数组大小。 */
            @Override public int[] rows(int top, int height) {
                int[] pixels = new int[width() * height];
                bitmap.getPixels(pixels, 0, width(), 0, top, width(), height);
                return pixels;
            }
        };
    }
}
