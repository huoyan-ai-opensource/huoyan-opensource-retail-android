package cc.huoyan.retail.device.camera;

import java.nio.ByteBuffer;

/** 纯 Java YUV 转换与旋转，兼容平面填充和交错色度。 */
public final class YuvFrames {
    /** 工具类不创建实例。 */
    private YuvFrames() { }
    /** 按各平面步长和裁剪区域复制为紧凑 NV21，不修改源缓冲位置。 */
    public static byte[] nv21(ByteBuffer[] planes, int[] rows, int[] pixels,
                              int left, int top, int width, int height) {
        if (width <= 0 || height <= 0 || ((left | top | width | height) & 1) != 0)
            throw new IllegalArgumentException("YUV 裁剪必须为偶数边界");
        byte[] result = new byte[width * height * 3 / 2];
        for (int plane = 0; plane < 3; plane++) {
            int divisor = plane == 0 ? 1 : 2;
            ByteBuffer source = planes[plane].duplicate();
            int base = source.position() + (top / divisor) * rows[plane] + (left / divisor) * pixels[plane];
            for (int y = 0; y < height / divisor; y++) {
                for (int x = 0; x < width / divisor; x++) {
                    int target = plane == 0 ? y * width + x
                            : width * height + y * width + 2 * x + (plane == 1 ? 1 : 0);
                    result[target] = source.get(base + y * rows[plane] + x * pixels[plane]);
                }
            }
        }
        return result;
    }
    /** 顺时针旋转 NV21；输入与输出均为独立、紧凑的偶数尺寸图像。 */
    public static byte[] rotate(byte[] source, int width, int height, int degrees) {
        if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270)
            throw new IllegalArgumentException("只支持直角旋转");
        if (degrees == 0) return source;
        byte[] output = new byte[source.length];
        rotatePlane(source, output, 0, width, height, 1, degrees);
        rotatePlane(source, output, width * height, width / 2, height / 2, 2, degrees);
        return output;
    }
    /** 旋转亮度像素或 VU 色度对，保持色度通道顺序。 */
    private static void rotatePlane(byte[] source, byte[] target, int offset, int width, int height, int step, int angle) {
        int outputWidth = angle == 180 ? width : height;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int dx = angle == 90 ? height - 1 - y : angle == 180 ? width - 1 - x : y;
            int dy = angle == 90 ? x : angle == 180 ? height - 1 - y : width - 1 - x;
            for (int c = 0; c < step; c++)
                target[offset + (dy * outputWidth + dx) * step + c] = source[offset + (y * width + x) * step + c];
        }
    }
    /** 根据传感器与屏幕角度得到不含自拍镜像的图像旋转角。 */
    public static int rotation(int sensor, int displayDegrees, boolean front) {
        return (sensor + (front ? displayDegrees : -displayDegrees) + 360) % 360;
    }
}
