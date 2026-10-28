package cc.huoyan.retail.device.print;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Looper;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cc.huoyan.retail.R;

/** 单次 USB 授权等待；只持有应用上下文，接收器在成功、拒绝、超时后释放。 */
final class UsbPrinterPermission {
    /** 工具类不创建实例。 */
    private UsbPrinterPermission() { }

    /** 后台等待系统授权；页面重建不取消已发起的打印操作。 */
    static void obtain(Context context, UsbManager manager, UsbDevice device) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("USB permission must run on a worker thread");
        }
        if (manager.hasPermission(device)) return;
        String action = context.getPackageName() + ".USB_PRINT_PERMISSION." + UUID.randomUUID();
        CountDownLatch completed = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            /** 系统回调只解除等待，最终授权状态重新向 UsbManager 核验。 */
            @Override public void onReceive(Context ignored, Intent intent) {
                if (action.equals(intent.getAction())) completed.countDown();
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, new IntentFilter(action));
        }
        PendingIntent permission = null;
        try {
            // 系统需要填充 USB 授权回执；限定本应用包与一次性随机 action。
            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            permission = PendingIntent.getBroadcast(context, 0,
                    new Intent(action).setPackage(context.getPackageName()), flags);
            manager.requestPermission(device, permission);
            if (!completed.await(45, TimeUnit.SECONDS)) {
                throw new IOException(context.getString(R.string.printer_usb_permission_timeout));
            }
            if (!manager.hasPermission(device)) {
                throw new IOException(context.getString(R.string.printer_usb_permission_denied));
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException(context.getString(R.string.printer_usb_permission_denied), error);
        } finally {
            if (permission != null) permission.cancel();
            context.unregisterReceiver(receiver);
        }
    }
}
