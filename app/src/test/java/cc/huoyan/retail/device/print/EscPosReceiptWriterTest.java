package cc.huoyan.retail.device.print;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** 验证长小票分带输出及失败后的命令边界。 */
public class EscPosReceiptWriterTest {
    /** 输出原高连续条带，条带间不插入空行，最后仅执行一次切纸。 */
    @Test public void streamsWholeReceiptWithoutGapsOrScaling() throws Exception {
        List<byte[]> packets = new ArrayList<>();
        EscPosReceiptWriter.print(image(320, 257), image(320, 1), ReceiptPaper.MM_58, packets::add, milliseconds -> { });
        assertEquals(7, packets.size());
        assertArrayEquals(new byte[]{0x1b, 0x40, 0x1b, 0x53,
                0x1d, 0x50, (byte) 203, (byte) 203, 0x1b, 0x61, 2}, packets.get(0));
        for (int i = 1; i <= 3; i++) {
            byte[] packet = packets.get(i);
            assertEquals(48, packet[4]);
            assertEquals(i == 3 ? 1 : 128, packet[6] & 255);
            for (int j = 8; j < packet.length; j++) {
                int column = (j - 8) % 48;
                assertEquals((byte) (column < 4 || column >= 44 ? 0 : 0xff), packet[j]);
            }
        }
        assertArrayEquals(new byte[]{0x0a}, packets.get(4));
        assertArrayEquals(new byte[]{0x1d, 0x56, 66, 0}, packets.get(6));
    }

    /** 尾部跨带且只剩一行时仍完整发送；最后一行必须先于切纸且不得追加 5mm 走纸。 */
    @Test public void finalFooterRowIsSentBeforeCutWithoutExtraFeed() throws Exception {
        List<byte[]> packets = new ArrayList<>();
        EscPosReceiptWriter.print(image(320, 1), image(320, 257), ReceiptPaper.MM_58, packets::add, milliseconds -> { });
        assertEquals(7, packets.size());
        for (int i = 3; i <= 5; i++) {
            assertEquals(i == 5 ? 1 : 128, packets.get(i)[6] & 255);
        }
        byte[] lastRow = packets.get(5);
        assertEquals(56, lastRow.length);
        for (int column = 4; column < 44; column++) assertEquals((byte) 0xff, lastRow[8 + column]);
        assertArrayEquals(new byte[]{0x1d, 0x56, 66, 0}, packets.get(6));
    }

    /** 任一写入失败后立即停止，不补发尾部、重置或切纸。 */
    @Test public void failedStripeStopsWithoutRetryOrCut() {
        List<byte[]> attempted = new ArrayList<>();
        IOException failure = new IOException("detached");
        IOException thrown = assertThrows(IOException.class, () -> EscPosReceiptWriter.print(
                image(320, 300), image(320, 100), ReceiptPaper.MM_58, data -> {
                    attempted.add(data);
                    if (attempted.size() == 3) throw failure;
                }, milliseconds -> { }));
        assertSame(failure, thrown);
        assertEquals(3, attempted.size());
    }

    /** 长票发送完成后也只固定等待 300ms，再切纸并保留 100ms 收尾等待。 */
    @Test public void longReceiptUsesFixedWaitBeforeCut() throws Exception {
        List<String> events = new ArrayList<>();
        EscPosReceiptWriter.print(image(320, 2000), image(320, 257), ReceiptPaper.MM_58, data -> {
            if (Arrays.equals(data, EscPosEncoder.cut())) events.add("cut");
        }, milliseconds -> {
            if (events.isEmpty()) {
                assertEquals(300, milliseconds);
                events.add("output-waited");
            } else {
                assertEquals(Arrays.asList("output-waited", "cut"), events);
                assertEquals(100, milliseconds);
                events.add("cut-waited");
            }
        });
        assertEquals(Arrays.asList("output-waited", "cut", "cut-waited"), events);
    }

    /** 短票与长票采用相同固定等待，避免残留票长或最低耗时估算。 */
    @Test public void shortReceiptUsesSameFixedWait() throws Exception {
        List<Long> waits = new ArrayList<>();
        EscPosReceiptWriter.print(image(320, 1), image(320, 1), ReceiptPaper.MM_58,
                data -> { }, waits::add);
        assertEquals(Arrays.asList(300L, 100L), waits);
    }

    /** 输出等待被打断后不发送切纸，也不补发整票或额外进纸。 */
    @Test public void interruptedWaitDoesNotCut() {
        List<byte[]> attempted = new ArrayList<>();
        IOException interrupted = new IOException("interrupted");
        assertSame(interrupted, assertThrows(IOException.class, () -> EscPosReceiptWriter.print(
                image(320, 1), image(320, 1), ReceiptPaper.MM_58, attempted::add,
                milliseconds -> { throw interrupted; })));
        assertEquals(4, attempted.size());
        assertEquals(0x76, attempted.get(3)[1]);
    }

    /** 创建全黑测试图，检查条带的行边界与像素顺序。 */
    private static EscPosReceiptWriter.Raster image(int width, int height) {
        return new EscPosReceiptWriter.Raster() {
            /** 测试点宽。 */
            public int width() { return width; }
            /** 测试点高。 */
            public int height() { return height; }
            /** 越界读取立即失败。 */
            public int[] rows(int top, int count) {
                assertTrue(top >= 0 && top + count <= height);
                int[] pixels = new int[width * count];
                Arrays.fill(pixels, 0xff000000);
                return pixels;
            }
        };
    }
}
