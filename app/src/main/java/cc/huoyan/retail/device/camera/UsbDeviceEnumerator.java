package cc.huoyan.retail.device.camera;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** UVC 设备枚举的 Android Parcel 兼容入口，由构建期替换 USBMonitor 的调用。 */
public final class UsbDeviceEnumerator {
    private static final Map<UsbManager, Snapshot> SNAPSHOTS = new WeakHashMap<>();

    private UsbDeviceEnumerator() {
    }

    /**
     * 读取 USB 设备；系统 Parcel 已回收时沿用上次成功快照，下轮仍重新枚举。
     * 返回独立副本，避免库侧修改快照；正常空列表仍表示设备已全部拔出。
     */
    public static HashMap<String, UsbDevice> getDeviceList(UsbManager manager) {
        Snapshot snapshot;
        synchronized (SNAPSHOTS) {
            snapshot = SNAPSHOTS.computeIfAbsent(manager, ignored -> new Snapshot());
        }
        return snapshot.read(manager::getDeviceList,
                error -> Log.w("USBMonitor", "USB enumeration Parcel unavailable; retaining last snapshot", error));
    }

    static final class Snapshot {
        private HashMap<String, UsbDevice> devices = new HashMap<>();
        private boolean failed;

        synchronized HashMap<String, UsbDevice> read(Supplier<HashMap<String, UsbDevice>> source,
                                                     Consumer<IllegalStateException> reportFailure) {
            try {
                HashMap<String, UsbDevice> result = source.get();
                devices = result == null ? new HashMap<>() : new HashMap<>(result);
                failed = false;
            } catch (IllegalStateException error) {
                if (!"Parcel recycled earlier than expected".equals(error.getMessage())) {
                    throw error;
                }
                // 连续失败只记一次，避免 150ms 轮询刷屏；成功读取后允许再次报告。
                if (!failed) {
                    failed = true;
                    reportFailure.accept(error);
                }
            }
            return new HashMap<>(devices);
        }
    }
}
