package cc.huoyan.retail.device.camera;

import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

/** 验证真实驱动常见的填充、交错色度和直角旋转。 */
public class YuvFramesTest {
    /** 平面存在行填充，缓冲位置不为零时仍按有效区域读取。 */
    @Test public void paddedPlanesAndPositions() {
        ByteBuffer y = ByteBuffer.wrap(new byte[]{99,1,2,3,4,0,0,5,6,7,8}); y.position(1);
        ByteBuffer u = ByteBuffer.wrap(new byte[]{9,0,10});
        ByteBuffer v = ByteBuffer.wrap(new byte[]{11,0,12});
        assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,11,9,12,10},
                YuvFrames.nv21(new ByteBuffer[]{y,u,v},new int[]{6,4,4},new int[]{1,2,2},0,0,4,2));
        assertEquals(1,y.position());
    }
    /** 非零裁剪偏移同时作用于亮度和色度。 */
    @Test public void cropWithSharedChroma() {
        ByteBuffer y=ByteBuffer.wrap(new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15});
        ByteBuffer vu=ByteBuffer.wrap(new byte[]{20,21,22,23,24,25,26,27});
        ByteBuffer u=vu.duplicate();u.position(1);
        assertArrayEquals(new byte[]{10,11,14,15,26,27},YuvFrames.nv21(
                new ByteBuffer[]{y,u,vu},new int[]{4,4,4},new int[]{1,2,2},2,2,2,2));
    }
    /** 旋转亮度和色度对，四次直角旋转恢复原图。 */
    @Test public void rightAngleRotation() {
        byte[] input={1,2,3,4,5,6,7,8,11,9,12,10};
        byte[] rotated=YuvFrames.rotate(input,4,2,90);
        assertArrayEquals(new byte[]{5,1,6,2,7,3,8,4,11,9,12,10},rotated);
        assertArrayEquals(input,YuvFrames.rotate(rotated,2,4,270));
        assertArrayEquals(input,YuvFrames.rotate(YuvFrames.rotate(input,4,2,180),4,2,180));
    }
    /** 前后置方向均输出未镜像的正向图像。 */
    @Test public void sensorAndDisplayOrientation() {
        assertEquals(90,YuvFrames.rotation(90,0,false));
        assertEquals(0,YuvFrames.rotation(90,90,false));
        assertEquals(180,YuvFrames.rotation(90,90,true));
        assertEquals(0,YuvFrames.rotation(270,90,true));
    }
}
