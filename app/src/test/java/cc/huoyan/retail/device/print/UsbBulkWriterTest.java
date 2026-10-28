package cc.huoyan.retail.device.print;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.junit.Assert.*;

/** 验证短写不会漏字节或重发，负回执不会触发整张重试。 */
public class UsbBulkWriterTest {
    /** 大数据分块，短写仅推进已确认偏移，保持原始顺序。 */
    @Test public void partialWritesPreserveEveryByte() throws Exception {
        byte[] expected = new byte[20000];
        for (int i = 0; i < expected.length; i++) expected[i] = (byte) i;
        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        UsbBulkWriter.write(expected, (bytes, offset, length, timeout) -> {
            assertTrue(length <= 4096);
            assertTrue(timeout > 0 && timeout <= 10000);
            int count = Math.min(137, length);
            actual.write(bytes, offset, count);
            return count;
        });
        assertArrayEquals(expected, actual.toByteArray());
    }

    /** 零、负数及非法超长回执均失败，绝不忙循环或重发。 */
    @Test public void uncertainTransfersFailImmediately() {
        for (int result : new int[]{0, -1, 100}) {
            int[] calls = {0};
            assertThrows(IOException.class, () -> UsbBulkWriter.write(new byte[10], (data, off, len, timeout) -> {
                calls[0]++;
                return result;
            }));
            assertEquals(1, calls[0]);
        }
    }
}
