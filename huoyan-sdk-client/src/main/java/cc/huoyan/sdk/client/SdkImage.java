package cc.huoyan.sdk.client;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.util.Objects;

/** 调用方图像快照；创建时复制字节，无硬件、文件或共享内存句柄，可跨请求复用。 */
public final class SdkImage {
    public static final int FORMAT_RGB888 = 1;
    public static final int FORMAT_JPEG = 2;
    public static final int FORMAT_PNG = 3;
    /** SDK 的 SharedMemory/WS binary 有效图像字节上限。 */
    public static final int MAX_TRANSPORT_BYTES = 32 * 1024 * 1024;
    private final byte[] bytes;
    private final int width;
    private final int height;
    private final int format;

    private SdkImage(byte[] bytes, int width, int height, int format) {
        if (bytes.length > MAX_TRANSPORT_BYTES) throw new IllegalArgumentException("图像超过 32 MiB 传输上限");
        this.bytes = bytes.clone();
        this.width = width;
        this.height = height;
        this.format = format;
    }

    /** 构造紧凑 RGB888 帧，创建时复制调用方数据。 */
    public static SdkImage rgb888(byte[] bytes, int width, int height) {
        Objects.requireNonNull(bytes, "图像字节不能为空");
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || pixels > Integer.MAX_VALUE / 3
                || bytes.length != pixels * 3) {
            throw new IllegalArgumentException("RGB888 长度必须等于 width*height*3");
        }
        return new SdkImage(bytes, width, height, FORMAT_RGB888);
    }

    /** 构造 JPEG/PNG 字节快照；未知宽高传 0，由 SDK 解码。 */
    public static SdkImage encoded(byte[] bytes, int width, int height, int format) {
        Objects.requireNonNull(bytes, "图像字节不能为空");
        if (bytes.length == 0 || width < 0 || height < 0
                || (format != FORMAT_JPEG && format != FORMAT_PNG)) {
            throw new IllegalArgumentException("需要非空 JPEG/PNG 字节及非负尺寸");
        }
        return new SdkImage(bytes, width, height, format);
    }

    /** 从调用方位图提取 RGB888；不裁剪、旋转或回收位图。 */
    public static SdkImage rgb888(Bitmap bitmap) {
        requireBitmap(bitmap);
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        long count = (long) width * height;
        if (count > MAX_TRANSPORT_BYTES / 3) throw new IllegalArgumentException("位图过大");
        int[] pixels = new int[(int) count];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        byte[] rgb = new byte[pixels.length * 3];
        for (int i = 0; i < pixels.length; i++) {
            rgb[i * 3] = (byte) (pixels[i] >> 16);
            rgb[i * 3 + 1] = (byte) (pixels[i] >> 8);
            rgb[i * 3 + 2] = (byte) pixels[i];
        }
        return new SdkImage(rgb, width, height, FORMAT_RGB888);
    }

    /** 将调用方位图编码为 JPEG90；不裁剪、旋转或回收位图。 */
    public static SdkImage jpeg(Bitmap bitmap) {
        requireBitmap(bitmap);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
            throw new IllegalArgumentException("位图 JPEG 编码失败");
        }
        return new SdkImage(out.toByteArray(), bitmap.getWidth(), bitmap.getHeight(), FORMAT_JPEG);
    }

    private static void requireBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) throw new IllegalArgumentException("位图不能为空或已回收");
    }

    /** 获取独立字节副本，修改副本不影响图像快照。 */
    public byte[] getBytes() { return bytes.clone(); }
    /** 获取像素宽度，编码图片允许为 0。 */
    public int getWidth() { return width; }
    /** 获取像素高度，编码图片允许为 0。 */
    public int getHeight() { return height; }
    /** 获取图像格式。 */
    public int getFormat() { return format; }
    /** 获取实际字节数。 */
    public int getSize() { return bytes.length; }
}
