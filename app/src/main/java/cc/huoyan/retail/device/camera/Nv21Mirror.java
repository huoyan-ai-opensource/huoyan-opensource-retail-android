package cc.huoyan.retail.device.camera;

/** NV21 水平镜像，亮度逐像素翻转，色度按 VU 对翻转。 */
public final class Nv21Mirror {
    private Nv21Mirror() { }

    /** 原位水平镜像完整 NV21 图像；拒绝不合法尺寸以免损坏相机帧。 */
    public static void mirror(byte[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0
                || (long) width * height * 3 / 2 > pixels.length) {
            throw new IllegalArgumentException("NV21 图像尺寸或数据不合法");
        }
        for (int y = 0; y < height; y++) {
            int start = y * width;
            for (int x = 0; x < width / 2; x++) swap(pixels, start + x, start + width - 1 - x);
        }
        for (int y = 0; y < height / 2; y++) {
            int start = width * height + y * width;
            for (int x = 0; x < width / 2; x += 2) {
                swap(pixels, start + x, start + width - 2 - x);
                swap(pixels, start + x + 1, start + width - 1 - x);
            }
        }
    }

    private static void swap(byte[] pixels, int first, int second) {
        byte value = pixels[first];
        pixels[first] = pixels[second];
        pixels[second] = value;
    }
}
