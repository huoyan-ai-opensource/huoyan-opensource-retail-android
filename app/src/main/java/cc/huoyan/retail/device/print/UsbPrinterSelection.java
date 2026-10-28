package cc.huoyan.retail.device.print;

import java.util.List;

/** USB 设备选择规则；完整标识匹配或唯一设备拔插后的地址恢复。 */
final class UsbPrinterSelection {
    /** 设备名称和易变 USB 地址的分隔符。 */
    static final String ADDRESS_SEPARATOR = " · /dev/";

    /** 工具类不创建实例。 */
    private UsbPrinterSelection() { }

    /** 优先精确匹配；多台同型号或已选设备缺失时不退回任意第一台设备。 */
    static String resolve(List<String> devices, String selected) {
        if (selected == null || selected.isEmpty()) return null;
        int address = selected.indexOf(ADDRESS_SEPARATOR);
        if (address <= 0) return null;
        if (devices.contains(selected)) return selected;
        String identity = selected.substring(0, address);
        String found = null;
        for (String candidate : devices) {
            if (candidate.startsWith(identity + ADDRESS_SEPARATOR)) {
                if (found != null) return null;
                found = candidate;
            }
        }
        return found;
    }
}
