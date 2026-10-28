package cc.huoyan.sdk.client.internal;

/** RGB 像素转换；不访问摄像头。 */
final class RgbImages {
    private RgbImages() {}
    public static byte[] rgb888FromArgbPixels(int[] argbPixels, int width, int height) {
        if (width <= 0 || height <= 0 || argbPixels == null || argbPixels.length != width * height) {
            throw new IllegalArgumentException(
                    "argbPixels 长度必须等于 width*height（" + width + "*" + height + "）：length="
                            + (argbPixels == null ? "null" : argbPixels.length));
        }
        byte[] rgb = new byte[width * height * 3];
        writeRgb888(argbPixels, rgb);
        return rgb;
    }

    static void writeRgb888(int[] argbPixels, byte[] rgb) {
        if (argbPixels == null || rgb == null || (long) argbPixels.length * 3 != rgb.length) {
            throw new IllegalArgumentException("RGB 缓冲区长度必须等于像素数乘 3");
        }
        for (int i = 0; i < argbPixels.length; i++) {
            int pixel = argbPixels[i];
            int offset = i * 3;
            rgb[offset] = (byte) ((pixel >> 16) & 0xFF);
            rgb[offset + 1] = (byte) ((pixel >> 8) & 0xFF);
            rgb[offset + 2] = (byte) (pixel & 0xFF);
        }
    }

}
