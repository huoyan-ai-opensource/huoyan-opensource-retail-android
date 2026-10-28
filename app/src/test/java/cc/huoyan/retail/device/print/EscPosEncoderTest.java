package cc.huoyan.retail.device.print;

import org.junit.Test;
import static org.junit.Assert.*;

/** 验证 ESC/POS 线上字节布局，纯 Java 测试不依赖 Android Bitmap。 */
public class EscPosEncoderTest {
    /** 两种纸宽均在位图中加入真实白边，黑色正文的左右边界不得被裁剪。 */
    @Test public void paperWidthsCenterContentWithoutClipping() {
        int[] pixels = new int[320 * 2];
        java.util.Arrays.fill(pixels, 0xff000000);
        for (ReceiptPaper paper : ReceiptPaper.values()) {
            byte[] data = EscPosEncoder.centeredRaster(paper, 320, 2, pixels);
            int bytesPerRow = paper.dots() / 8;
            int marginBytes = (paper.dots() - 320) / 16;
            assertEquals(8 + bytesPerRow * 2, data.length);
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < bytesPerRow; column++) {
                    byte expected = (byte) (column >= marginBytes && column < marginBytes + 40 ? 0xff : 0);
                    assertEquals(expected, data[8 + row * bytesPerRow + column]);
                }
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> EscPosEncoder.centeredRaster(ReceiptPaper.MM_58, 400, 1, new int[400]));
    }

    /** 窄纸仍相对固件默认区域靠右，不能重设 GS L/GS W 把整幅小票移出实际纸面。 */
    @Test public void initializationPreservesNativeAreaAndRightAlignment() {
        assertArrayEquals(new byte[]{0x1b, 0x40, 0x1b, 0x53,
                0x1d, 0x50, (byte) 203, (byte) 203, 0x1b, 0x61, 2},
                EscPosEncoder.initialize());
        assertArrayEquals(new byte[]{0x1d, 0x56, 66, 0}, EscPosEncoder.cut());
    }

    /** 非整字节宽度逐行补白，不能把下一行像素挤进上一行尾部。 */
    @Test public void rasterUsesMsbFirstAndPadsEachRow() {
        int[] pixels = {
                0xff000000, -1, -1, -1, -1, -1, -1, 0xff000000, 0xff000000,
                -1, 0xff000000, -1, -1, -1, -1, -1, -1, -1
        };
        assertArrayEquals(new byte[]{0x1d, 0x76, 0x30, 0, 2, 0, 2, 0,
                (byte) 0x81, (byte) 0x80, 0x40, 0}, EscPosEncoder.raster(9, 2, pixels));
    }

    /** 透明色按白底合成，中文抗锯齿灰度按固定亮度阈值保留。 */
    @Test public void rasterCompositesAlphaAndThresholdsLuminance() {
        assertEquals((byte) 0x50, EscPosEncoder.raster(4, 1,
                new int[]{0x00000000, 0xff000000, 0xffeeeeee, 0xff808080})[8]);
    }

    /** 栅格尺寸以小端字节编码；宽度单位为字节，高度单位为点。 */
    @Test public void rasterDimensionsUseLittleEndian() {
        byte[] data = EscPosEncoder.raster(2049, 257, new int[2049 * 257]);
        assertArrayEquals(new byte[]{0x1d, 0x76, 0x30, 0, 1, 1, 1, 1},
                java.util.Arrays.copyOf(data, 8));
        assertEquals(8 + 257 * 257, data.length);
    }

    /** 像素长度不匹配时不能生成会吞掉后续命令的错误数据包。 */
    @Test public void malformedRasterIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EscPosEncoder.raster(320, 2, new int[320]));
        assertThrows(IllegalArgumentException.class, () -> EscPosEncoder.raster(0, 1, new int[0]));
    }
}
