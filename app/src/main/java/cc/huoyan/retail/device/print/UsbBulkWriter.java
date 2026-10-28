package cc.huoyan.retail.device.print;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** USB 短写处理；单包限定总耗时，超时或负回执后绝不重发。 */
final class UsbBulkWriter {
    /** 小于旧 Android 16 KiB 限制，并给设备缓冲区保留余量。 */
    private static final int CHUNK_SIZE = 4096;
    /** 一条指令的传输截止时间；不使用无限超时。 */
    private static final int TIMEOUT_MS = 10000;

    /** 原生 USB 写入边界，供纯 Java 测试注入短写和断线。 */
    interface Transfer {
        /** 返回实际发送字节数，零或负数表示失败。 */
        int write(byte[] data, int offset, int length, int timeoutMs);
    }

    /** 工具类不创建实例。 */
    private UsbBulkWriter() { }

    /** 只推进已确认写入的偏移；任何不确定回执直接失败。 */
    static void write(byte[] data, Transfer transfer) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
        int offset = 0;
        while (offset < data.length) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("USB write timed out");
            int length = Math.min(CHUNK_SIZE, data.length - offset);
            int timeout = (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining));
            int written = transfer.write(data, offset, length, timeout);
            if (written <= 0 || written > length) throw new IOException("USB write failed: " + written);
            offset += written;
        }
    }
}
