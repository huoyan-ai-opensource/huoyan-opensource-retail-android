package cc.huoyan.retail.device.camera;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.learning.ProductLearningActivity;
import cc.huoyan.retail.learning.StudyActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

/** 在实际打包的 UVC 库中注入系统枚举异常，验证替换调用及后台轮询恢复。 */
@RunWith(AndroidJUnit4.class)
public class UsbMonitorRecoveryDeviceTest {
    /** 学习页停留 90 秒，确认摄像头持续打开且每秒仍有新帧。 */
    @Test
    public void learningPreviewContinuesWhileIdle() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        MainActivity main = (MainActivity) instrumentation.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ProductLearningActivity learning = null;
        try {
            learning = (ProductLearningActivity) instrumentation.startActivitySync(
                    new Intent(context, ProductLearningActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ProductLearningActivity page = learning;
            Field frame = StudyActivity.class.getDeclaredField("latestFrame");
            frame.setAccessible(true);
            long deadline = SystemClock.elapsedRealtime() + 15000;
            while (frame.get(page) == null && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100);
            }
            assertNotNull("学习页应收到摄像头帧", frame.get(page));
            for (int second = 0; second < 90; second++) {
                Object previous = frame.get(page);
                SystemClock.sleep(1000);
                assertTrue("摄像头应持续打开", CameraUsbManager.getInstance().isCameraOpen());
                assertNotSame("学习页应持续收到新帧", previous, frame.get(page));
            }
        } finally {
            ProductLearningActivity page = learning;
            instrumentation.runOnMainSync(() -> {
                if (page != null) page.finish();
                main.finish();
            });
        }
    }

    /** 枚举失败不杀死轮询、不重复上报已有设备，恢复后仍能发现重新接入的设备。 */
    @Test
    public void parcelFailurePreservesDevicesAndPollingRecovers() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UsbManager realManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> devices = UsbDeviceEnumerator.getDeviceList(realManager);
        assertFalse("真机须接有 USB 设备", devices.isEmpty());
        AtomicBoolean fail = new AtomicBoolean();
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger attaches = new AtomicInteger();
        AtomicReference<HashMap<String, UsbDevice>> current = new AtomicReference<>(devices);

        // 仅构造独立 UsbManager；不替换系统服务、不请求设备权限、不打开物理设备。
        Class<?> serviceType = Class.forName("android.hardware.usb.IUsbManager");
        Object service = Proxy.newProxyInstance(serviceType.getClassLoader(), new Class<?>[]{serviceType},
                (proxy, method, args) -> {
                    if (method.getName().equals("getDeviceList")) {
                        reads.incrementAndGet();
                        if (fail.get()) {
                            throw new IllegalStateException("Parcel recycled earlier than expected");
                        }
                        Bundle bundle = (Bundle) args[0];
                        current.get().forEach(bundle::putParcelable);
                        return null;
                    }
                    if (method.getName().equals("hasDevicePermission")) return false;
                    throw new AssertionError("Unexpected USB service call: " + method.getName());
                });
        UsbManager fakeManager = (UsbManager) UsbManager.class
                .getConstructor(Context.class, serviceType).newInstance(context, service);
        Context fakeContext = new ContextWrapper(context) {
            @Override public Object getSystemService(String name) {
                return USB_SERVICE.equals(name) ? fakeManager : super.getSystemService(name);
            }
        };
        USBMonitor monitor = new USBMonitor(fakeContext, new USBMonitor.OnDeviceConnectListener() {
            @Override public void onAttach(UsbDevice device) { attaches.incrementAndGet(); }
            @Override public void onDetach(UsbDevice device) { }
            @Override public void onDeviceOpen(UsbDevice device, USBMonitor.UsbControlBlock block, boolean first) { }
            @Override public void onDeviceClose(UsbDevice device, USBMonitor.UsbControlBlock block) { }
            @Override public void onCancel(UsbDevice device) { }
        });
        try {
            monitor.register();
            await(() -> attaches.get() == devices.size());
            fail.set(true);
            int beforeFailure = reads.get();
            await(() -> reads.get() >= beforeFailure + 5);
            assertEquals(devices.size(), attaches.get());
            assertEquals(devices.size(), monitor.getDeviceList((List<DeviceFilter>) null).size());
            assertEquals(devices.size(), monitor.getDeviceList((DeviceFilter) null).size());
            assertTrue(monitor.getDevices().hasNext());
            monitor.dumpDevices();

            current.set(new HashMap<>());
            fail.set(false);
            int beforeEmpty = reads.get();
            await(() -> reads.get() >= beforeEmpty + 3);
            assertTrue(monitor.getDeviceList().isEmpty());
            current.set(devices);
            await(() -> attaches.get() == devices.size() * 2);
        } finally {
            monitor.destroy();
        }
    }

    private static void await(BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50);
        }
        assertTrue("USBMonitor 轮询应持续运行并完成回调", condition.getAsBoolean());
    }
}
