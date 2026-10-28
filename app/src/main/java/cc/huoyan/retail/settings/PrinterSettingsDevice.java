package cc.huoyan.retail.settings;

import cc.huoyan.retail.device.print.PrinterManager;
import cc.huoyan.retail.device.print.ReceiptPaper;
import java.util.List;

/** 打印设置硬件边界；调用统一打印入口，不持有页面或厂商 SDK。 */
final class PrinterSettingsDevice implements PrintSettingsPresenter.Device {
    /** 设备协调器只管理后台操作互斥。 */
    private final PrinterManager manager = PrinterManager.getInstance();

    /** 创建无页面引用的适配器。 */
    PrinterSettingsDevice() { }

    /** 枚举标准 USB 打印设备。 */
    @Override public List<String> list() { return manager.devices(); }

    /** 解析已选择的打印设备，不在查询线程改写用户配置。 */
    @Override public String selected(List<String> devices) { return manager.resolveSelection(devices); }

    /** 连接测试的真实错误回执。 */
    @Override public String connect(String selected) {
        try { manager.testConnection(selected); return null; }
        catch (Exception error) { return message(error); }
    }

    /** 通过与收银一致的排版和 ESC/POS 链路试打。 */
    @Override public String print(String selected, int paperWidthMm) {
        try { manager.printTest(selected, ReceiptPaper.fromMillimeters(paperWidthMm)); return null; }
        catch (Exception error) { return message(error); }
    }

    /** 异常没有文案时仍返回失败，不能把 null 当作成功。 */
    private static String message(Exception error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
