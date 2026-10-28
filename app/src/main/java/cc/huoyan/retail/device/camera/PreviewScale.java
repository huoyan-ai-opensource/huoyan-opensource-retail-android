package cc.huoyan.retail.device.camera;

/** 预览画面、实时检测框和静态识别结果共同使用的缩放规则。 */
public final class PreviewScale {
    private PreviewScale() { }

    /** 竖屏按两边约束完整显示画面；横屏保留原有按高度适配的行为。 */
    public static float compute(int imageWidth, int imageHeight, int viewWidth, int viewHeight,
                                boolean fitInside) {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return 0f;
        float heightScale = (float) viewHeight / imageHeight;
        return fitInside ? Math.min((float) viewWidth / imageWidth, heightScale) : heightScale;
    }
}
