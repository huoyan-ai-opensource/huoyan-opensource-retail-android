package cc.huoyan.retail.device.camera;

/** 驱动原始输出尺寸，不包含屏幕旋转。 */
public final class CameraSize {
    /** 原始像素宽度。 */
    public final int width;
    /** 原始像素高度。 */
    public final int height;
    /** 创建尺寸值。 */
    public CameraSize(int width, int height) { this.width = width; this.height = height; }
    /** 返回持久化尺寸字符串。 */
    public String key() { return width + "*" + height; }
}
