package cc.huoyan.retail.device.print;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import cc.huoyan.retail.R;

/** 原生 USB Printer Class 传输；连接归单个后台任务所有，任务结束立即关闭。 */
final class UsbPrinterTransport implements AutoCloseable, EscPosReceiptWriter.Sink {
    /** 当前任务的 USB 连接，不跨任务缓存失效句柄。 */
    private final UsbDeviceConnection connection;
    /** 已独占的打印接口。 */
    private final UsbInterface printerInterface;
    /** 打印数据输出端点。 */
    private final UsbEndpoint output;

    /** 接管已成功 claim 的连接。 */
    private UsbPrinterTransport(UsbDeviceConnection connection, UsbInterface printerInterface, UsbEndpoint output) {
        this.connection = connection;
        this.printerInterface = printerInterface;
        this.output = output;
    }

    /** 枚举标准打印接口；不把摄像头、网卡或 USB 转串口当作直接打印设备。 */
    static List<String> devices(Context context) {
        return new ArrayList<>(discover(context).keySet());
    }

    /** 解析用户选择、申请授权并打开接口；任何失败均释放已打开的连接。 */
    static UsbPrinterTransport open(Context context, String selected) throws IOException {
        Map<String, UsbDevice> devices = discover(context);
        String resolved = UsbPrinterSelection.resolve(new ArrayList<>(devices.keySet()), selected);
        if (resolved == null) throw new IOException(context.getString(R.string.printer_select_device));
        UsbDevice device = devices.get(resolved);
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        UsbPrinterPermission.obtain(context, manager, device);
        UsbInterface printerInterface = findInterface(device);
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) throw new IOException(context.getString(R.string.printer_error_not_connected));
        boolean claimed = false;
        try {
            claimed = connection.claimInterface(printerInterface, true);
            if (!claimed) throw new IOException(context.getString(R.string.printer_usb_claim_failed));
            return new UsbPrinterTransport(connection, printerInterface, findOutput(printerInterface));
        } finally {
            if (!claimed) connection.close();
        }
    }

    /** 可选 USB 类状态预检；不支持查询时允许发送，已知缺纸或离线则阻止打印。 */
    void checkReady(Context context) throws IOException {
        byte[] status = new byte[1];
        int read = connection.controlTransfer(0xa1, 1, 0, printerInterface.getId(), status, 1, 500);
        if (read != 1) return;
        int value = status[0] & 0xff;
        if ((value & 0x20) != 0) throw new IOException(context.getString(R.string.printer_status_paper_empty));
        if ((value & 0x10) == 0) throw new IOException(context.getString(R.string.printer_status_offline));
        if ((value & 0x08) == 0) throw new IOException(context.getString(R.string.printer_status_error));
    }

    /** 完整写入本条数据；断线、短写失败或超时不触发重新发送。 */
    @Override public void write(byte[] data) throws IOException {
        UsbBulkWriter.write(data, (bytes, offset, length, timeout) ->
                connection.bulkTransfer(output, bytes, offset, length, timeout));
    }

    /** 任务结束释放接口及文件描述符，Activity 销毁不参与设备连接生命周期。 */
    @Override public void close() {
        try { connection.releaseInterface(printerInterface); }
        finally { connection.close(); }
    }

    /** 读取一次当前设备快照；系统枚举失败向上报告，避免沿用已拔出的设备。 */
    private static Map<String, UsbDevice> discover(Context context) {
        Map<String, UsbDevice> result = new TreeMap<>();
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) return result;
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (findInterface(device) == null) continue;
            String product = device.getProductName();
            if (product == null || product.trim().isEmpty()) product = "USB Printer";
            String label = String.format(Locale.ROOT, "%s [%04X:%04X] · %s", product,
                    device.getVendorId(), device.getProductId(), device.getDeviceName());
            result.put(label, device);
        }
        return result;
    }

    /** 查找单向或双向 USB 打印接口；跳过 IPP 和未知厂商私有协议。 */
    private static UsbInterface findInterface(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface candidate = device.getInterface(i);
            if (candidate.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER
                    && candidate.getAlternateSetting() == 0
                    && (candidate.getInterfaceProtocol() == 1 || candidate.getInterfaceProtocol() == 2)
                    && findOutput(candidate) != null) return candidate;
        }
        return null;
    }

    /** 使用端点描述符发现 Bulk OUT，不假定端点编号或排列顺序。 */
    private static UsbEndpoint findOutput(UsbInterface printerInterface) {
        for (int i = 0; i < printerInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = printerInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_OUT) return endpoint;
        }
        return null;
    }
}
