package cc.huoyan.retail.device.camera;

/** 已转正并应用镜像的独立 NV21 帧，发布后禁止修改像素。 */
public final class CameraFrame {
    /** 独占的只读像素。 */
    private final byte[] nv;
    /** 规范化宽度。 */
    private final int width;
    /** 规范化高度。 */
    private final int height;
    /** 相机入口代次，切换后旧帧失效。 */
    final long generation;
    /** 包装后端交付的独立缓冲。 */
    CameraFrame(byte[] nv, int width, int height, long generation) {
        this.nv = nv; this.width = width; this.height = height; this.generation = generation;
    }
    /** 只读访问像素，写入前应复制。 */
    public byte[] getNv() { return nv; }
    /** 返回图像宽度。 */
    public int getWidth() { return width; }
    /** 返回图像高度。 */
    public int getHeight() { return height; }
}
