package cc.huoyan.retail.device.camera;

import cc.huoyan.retail.recognition.FrameChangeGate;
import org.junit.Test;
import static org.junit.Assert.*;

/** 直接验证真实 NV21 亮度采样和门控，纯 Java 不分配 Bitmap。 */
public class CompareImageTest {
    /** 相同画面保持相同哈希，亮暗结构翻转产生足够位变化。 */
    @Test public void consecutiveCameraFramesDriveHammingGate() {
        byte[] first = new byte[96], changed = new byte[96];
        for (int i = 0; i < 64; i++) {
            first[i] = (byte) (i < 32 ? 32 : 220);
            changed[i] = (byte) (i < 32 ? 220 : 32);
        }
        String before = CompareImage.frameHash(new CameraFrame(first, 8, 8, 1));
        String after = CompareImage.frameHash(new CameraFrame(changed, 8, 8, 1));
        FrameChangeGate gate = new FrameChangeGate();
        assertFalse(gate.accept(before, 4));
        assertFalse(gate.accept(before, 4));
        assertTrue(gate.accept(after, 4));
        assertTrue("本轮启动后静止帧继续放行", gate.accept(after, 4));
    }
    /** 仅色度变化不改变亮度哈希，避免色度噪声触发识别。 */
    @Test public void chromaDoesNotChangeLuminanceHash() {
        byte[] first = new byte[96], changed = new byte[96];
        for (int i = 64; i < changed.length; i++) changed[i] = (byte) 255;
        assertEquals(CompareImage.frameHash(new CameraFrame(first, 8, 8, 1)),
                CompareImage.frameHash(new CameraFrame(changed, 8, 8, 1)));
    }
    /** 无效帧不越界读取，交由门控等待下一张有效帧。 */
    @Test public void invalidFrameHasNoHash() {
        assertNull(CompareImage.frameHash(new CameraFrame(new byte[10], 8, 8, 1)));
        assertNull(CompareImage.frameHash(new CameraFrame(null, 8, 8, 1)));
        assertNull(CompareImage.frameHash(new CameraFrame(new byte[96], 0, 8, 1)));
    }
}
