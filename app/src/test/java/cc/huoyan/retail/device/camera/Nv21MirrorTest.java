package cc.huoyan.retail.device.camera;

import org.junit.Test;
import static org.junit.Assert.*;

public class Nv21MirrorTest {
    @Test public void mirrorsLuminanceAndKeepsVuPairOrder() {
        byte[] pixels = {1,2,3,4,5,6,7,8,11,12,21,22};
        Nv21Mirror.mirror(pixels,4,2);
        assertArrayEquals(new byte[]{4,3,2,1,8,7,6,5,21,22,11,12},pixels);
        Nv21Mirror.mirror(pixels,4,2);
        assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,11,12,21,22},pixels);
    }
    @Test public void handlesOddNumberOfChromaPairs() {
        byte[] pixels = {1,2,3,4,5,6,7,8,9,10,11,12,21,22,31,32,41,42};
        Nv21Mirror.mirror(pixels,6,2);
        assertArrayEquals(new byte[]{6,5,4,3,2,1,12,11,10,9,8,7,41,42,31,32,21,22},pixels);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsTruncatedFrame() {
        Nv21Mirror.mirror(new byte[10],4,2);
    }
}
