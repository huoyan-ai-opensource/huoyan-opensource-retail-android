package cc.huoyan.retail.device.camera;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import static org.junit.Assert.*;

/** 验证 RenderScript 资源复用不改变颜色、尺寸和输出位图所有权。 */
@RunWith(AndroidJUnit4.class)
public class Nv21ReuseDeviceTest {
    /** 连续转换及切换尺寸时与独立转换器像素一致，释放后拒绝继续调用。 */
    @Test public void matchesFreshConverterAcrossFramesAndResolutionChanges() {
        NV21ToBitmap reused = new NV21ToBitmap(InstrumentationRegistry.getInstrumentation().getTargetContext());
        Bitmap previous = null;
        int[] previousPixels = null;
        try {
            for (int size : new int[]{8, 8, 16, 4}) {
                byte[] nv21 = new byte[size * size * 3 / 2];
                for (int i = 0; i < size * size; i++) nv21[i] = (byte) (16 + i % 220);
                Arrays.fill(nv21, size * size, nv21.length, (byte) 128);
                Bitmap actual = reused.nv21ToBitmap(nv21, size, size);
                if (previous != null) {
                    assertArrayEquals(previousPixels, pixels(previous));
                    previous.recycle();
                }
                previous = actual;
                previousPixels = pixels(actual);
                NV21ToBitmap fresh = new NV21ToBitmap(InstrumentationRegistry.getInstrumentation().getTargetContext());
                try {
                    Bitmap expected = fresh.nv21ToBitmap(nv21, size, size);
                    try { assertArrayEquals(pixels(expected), previousPixels); }
                    finally { expected.recycle(); }
                } finally { fresh.release(); }
            }
        } finally {
            if (previous != null) previous.recycle();
            reused.release(); reused.release();
        }
        assertThrows(IllegalStateException.class, () -> reused.nv21ToBitmap(new byte[96], 8, 8));
    }

    private int[] pixels(Bitmap bitmap) {
        int[] result = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(result, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return result;
    }
}
