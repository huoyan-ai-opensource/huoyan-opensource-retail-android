package cc.huoyan.retail.device.print;

/** ESC/POS 指令编码；只处理像素与字节，不依赖 Android 或厂商 SDK。 */
final class EscPosEncoder {
    /** 工具类不创建实例。 */
    private EscPosEncoder() { }

    /** 保留设备默认打印区域并右对齐；窄纸画布不能强制锚定打印头最左端。 */
    static byte[] initialize() {
        return new byte[]{0x1b, 0x40,
                0x1b, 0x53,
                0x1d, 0x50, (byte) 203, (byte) 203,
                0x1b, 0x61, 2};
    }

    /** 完整尾部输出后进纸半切，仅使用固件切刀定位，不再额外走纸 5mm。 */
    static byte[] cut() {
        return new byte[]{0x1d, 0x56, 66, 0};
    }

    /** 逐行补入对称白边，正文不缩放；每个条带均使用同一纸宽和横向位置。 */
    static byte[] centeredRaster(ReceiptPaper paper, int width, int height, int[] pixels) {
        if (width <= 0 || width > paper.dots() || height <= 0 || height > 65535
                || (long) width * height != pixels.length) {
            throw new IllegalArgumentException("Raster exceeds printable paper width");
        }
        int[] padded = new int[paper.dots() * height];
        java.util.Arrays.fill(padded, 0xffffffff);
        int left = (paper.dots() - width) / 2;
        for (int y = 0; y < height; y++) {
            System.arraycopy(pixels, y * width, padded, y * paper.dots() + left, width);
        }
        return raster(paper.dots(), height, padded);
    }

    /** 将一条像素带编码为 GS v 0 单倍栅格图；高位在左，行尾补白。 */
    static byte[] raster(int width, int height, int[] pixels) {
        if (width <= 0 || width > 65535 || height <= 0 || height > 65535
                || (long) width * height != pixels.length) {
            throw new IllegalArgumentException("Invalid raster dimensions");
        }
        int bytesPerRow = (width + 7) / 8;
        byte[] data = new byte[8 + bytesPerRow * height];
        data[0] = 0x1d; data[1] = 0x76; data[2] = 0x30; data[3] = 0;
        data[4] = (byte) bytesPerRow; data[5] = (byte) (bytesPerRow >> 8);
        data[6] = (byte) height; data[7] = (byte) (height >> 8);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isBlack(pixels[y * width + x])) {
                    data[8 + y * bytesPerRow + x / 8] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        return data;
    }

    /** 将透明像素合成到白底后按亮度二值化，保留文字和条码的清晰边缘。 */
    private static boolean isBlack(int argb) {
        int alpha = argb >>> 24;
        int luminance = (299 * ((argb >> 16) & 255) + 587 * ((argb >> 8) & 255)
                + 114 * (argb & 255)) / 1000;
        return (luminance * alpha + 255 * (255 - alpha)) / 255 < 160;
    }
}
