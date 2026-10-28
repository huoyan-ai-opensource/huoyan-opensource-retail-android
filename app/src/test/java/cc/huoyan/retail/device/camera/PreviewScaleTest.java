package cc.huoyan.retail.device.camera;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** 验证宽幅相机在竖屏完整显示，以及横屏兼容原有坐标比例。 */
public class PreviewScaleTest {
    /** 宽画面受竖屏宽度约束，留白由画面及检测层共同居中。 */
    @Test public void wideImageFitsPortraitWidth() {
        assertEquals(0.28125f, PreviewScale.compute(1920,1080,540,336,true), 0.00001f);
    }
    /** 竖幅图像受高度约束，不会越过预览区。 */
    @Test public void tallImageFitsHeight() {
        assertEquals(0.175f, PreviewScale.compute(1080,1920,540,336,true), 0.00001f);
    }
    /** 横屏仍然按高度缩放，即使宽度超过容器也不改变原有显示。 */
    @Test public void landscapeKeepsHeightScaling() {
        assertEquals(336f/1080, PreviewScale.compute(1920,1080,540,336,false), 0.00001f);
    }
    /** 相机尚未准备好时不产生无效坐标。 */
    @Test public void missingImageHasZeroScale() {
        assertEquals(0f, PreviewScale.compute(0,0,540,336,true), 0f);
    }
}
