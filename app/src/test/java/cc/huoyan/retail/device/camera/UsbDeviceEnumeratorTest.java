package cc.huoyan.retail.device.camera;

import android.hardware.usb.UsbDevice;

import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class UsbDeviceEnumeratorTest {
    private final UsbDeviceEnumerator.Snapshot snapshot = new UsbDeviceEnumerator.Snapshot();
    private final AtomicInteger failures = new AtomicInteger();
    private final Supplier<HashMap<String, UsbDevice>> parcelFailure = () -> {
        throw new IllegalStateException("Parcel recycled earlier than expected");
    };

    /** 首次失败可安全返回空列表，后续成功枚举恢复设备。 */
    @Test
    public void firstEnumerationFailureRetriesUntilDevicesBecomeAvailable() {
        assertTrue(read(parcelFailure).isEmpty());
        assertTrue(read(parcelFailure).isEmpty());
        assertEquals(1, failures.get());
        assertTrue(read(() -> devices("camera")).containsKey("camera"));
        assertTrue(read(parcelFailure).containsKey("camera"));
        assertEquals(2, failures.get());
    }

    /** 连续失败保留已发现设备，且调用方不能修改内部快照。 */
    @Test
    public void repeatedFailuresPreserveLastSuccessfulSnapshotWithoutSharingMaps() {
        HashMap<String, UsbDevice> source = devices("camera");
        HashMap<String, UsbDevice> first = read(() -> source);
        source.clear();
        first.clear();
        for (int i = 0; i < 100; i++) {
            HashMap<String, UsbDevice> retained = read(parcelFailure);
            assertTrue(retained.containsKey("camera"));
            retained.clear();
        }
        assertEquals(1, failures.get());
    }

    /** 正常枚举仍更新接入设备，并允许空列表清除已拔出设备。 */
    @Test
    public void recoveryUsesNewDevicesAndSuccessfulEmptyListClearsSnapshot() {
        read(() -> devices("camera1"));
        read(parcelFailure);
        HashMap<String, UsbDevice> changed = read(() -> devices("camera2"));
        assertFalse(changed.containsKey("camera1"));
        assertTrue(changed.containsKey("camera2"));
        assertTrue(read(HashMap::new).isEmpty());
        assertTrue(read(parcelFailure).isEmpty());
    }

    /** 无关状态异常和权限错误仍交给原调用方处理。 */
    @Test
    public void unrelatedIllegalStateAndSecurityErrorsAreNotSuppressed() {
        IllegalStateException state = new IllegalStateException("other failure");
        assertSame(state, assertThrows(IllegalStateException.class, () -> read(() -> { throw state; })));
        SecurityException security = new SecurityException("permission denied");
        assertSame(security, assertThrows(SecurityException.class, () -> read(() -> { throw security; })));
        assertEquals(0, failures.get());
    }

    private HashMap<String, UsbDevice> read(Supplier<HashMap<String, UsbDevice>> source) {
        return snapshot.read(source, error -> failures.incrementAndGet());
    }

    private static HashMap<String, UsbDevice> devices(String name) {
        HashMap<String, UsbDevice> result = new HashMap<>();
        // 仅验证枚举快照，不构造 mockable Android jar 中的 UsbDevice。
        result.put(name, null);
        return result;
    }
}
