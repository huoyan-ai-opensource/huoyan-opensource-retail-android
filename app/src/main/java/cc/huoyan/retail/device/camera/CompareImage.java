package cc.huoyan.retail.device.camera;

/** 从规范化相机帧生成亮度感知哈希，供逐帧汉明距离比较使用。 */
public final class CompareImage {
    /** 仅提供无状态哈希计算，不创建工具实例。 */
    private CompareImage() { }

    /** 从每个相机帧的亮度平面采样 8×8 哈希，避免逐帧分配整幅 Bitmap。 */
    public static String frameHash(CameraFrame frame) {
        byte[] pixels = frame.getNv();
        int width = frame.getWidth(), height = frame.getHeight();
        if (width <= 0 || height <= 0 || pixels == null || pixels.length < (long) width * height) return null;
        int[] samples = new int[64];
        int sum = 0;
        for (int y = 0; y < 8; y++) {
            int row = Math.min(height - 1, (2 * y + 1) * height / 16) * width;
            for (int x = 0; x < 8; x++) {
                int value = pixels[row + Math.min(width - 1, (2 * x + 1) * width / 16)] & 255;
                samples[y * 8 + x] = value;
                sum += value;
            }
        }
        long hash = 0;
        for (int value : samples) hash = (hash << 1) | (value * 64 >= sum ? 1 : 0);
        return String.format(java.util.Locale.ROOT, "%016x", hash);
    }

}
