package cc.huoyan.retail.device.print;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** 验证 USB 设备标识、拔插地址恢复及多设备选择边界。 */
public class UsbPrinterSelectionTest {
    /** 已选设备唯一时可恢复拔插后改变的 USB 地址。 */
    @Test public void uniqueDeviceRestoresChangedAddress() {
        String selected = "BTP-N90LF [154F:1300] · /dev/bus/usb/001/008";
        String current = "BTP-N90LF [154F:1300] · /dev/bus/usb/001/009";
        assertEquals(current, UsbPrinterSelection.resolve(Collections.singletonList(current), selected));
    }

    /** 只有型号或未带 USB 地址的值不是设备选择，不允许自动绑定。 */
    @Test public void incompleteSelectionDoesNotChooseEvenOnePrinter() {
        String current = "BTP-N90LF [154F:1300] · /dev/bus/usb/001/009";
        for (String selected : new String[]{null, "", "BTP-N90LF", "BTP-N90LF [154F:1300]"}) {
            assertNull(UsbPrinterSelection.resolve(Collections.singletonList(current), selected));
        }
    }

    /** 多台同型号时保留精确选择；地址变化后有歧义或设备缺失时要求重新选择。 */
    @Test public void ambiguousOrMissingPrinterIsNotAutoSelected() {
        String first = "BTP-N90LF [154F:1300] · /dev/bus/usb/001/008";
        String second = "BTP-N90LF [154F:1300] · /dev/bus/usb/001/009";
        String unplugged = "BTP-N90LF [154F:1300] · /dev/bus/usb/001/007";
        assertNull(UsbPrinterSelection.resolve(Arrays.asList(first, second), unplugged));
        assertEquals(second, UsbPrinterSelection.resolve(Arrays.asList(first, second), second));
        assertNull(UsbPrinterSelection.resolve(Collections.singletonList(first),
                "Different printer [1234:5678] · /dev/bus/usb/001/008"));
        assertNull(UsbPrinterSelection.resolve(Collections.emptyList(), first));
    }
}
