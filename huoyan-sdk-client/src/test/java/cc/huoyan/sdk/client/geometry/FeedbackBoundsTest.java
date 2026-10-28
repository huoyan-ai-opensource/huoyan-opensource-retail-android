package cc.huoyan.sdk.client.geometry;
import cc.huoyan.sdk.client.geometry.FeedbackBounds;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/** 反馈坐标转换覆盖 AABB、OBB、越界及无效坐标。 */
public class FeedbackBoundsTest {
    @Test
    public void aabbKeepsOriginalImageCoordinates() {
        assertArrayEquals(new int[]{10, 20, 30, 40},
                FeedbackBounds.fromPoints(new float[]{10, 20, 40, 60}, 1920, 1080));
    }

    @Test
    public void obbUsesAllFourVerticesAndClipsToImage() {
        assertArrayEquals(new int[]{0, 0, 100, 80},
                FeedbackBounds.fromPoints(new float[]{20, -10, 120, 30, 90, 100, -5, 60}, 100, 80));
    }

    @Test
    public void negativeAndOutsideBoxesDoNotProduceNegativeDimensions() {
        assertArrayEquals(new int[]{0, 0, 0, 0},
                FeedbackBounds.fromPoints(new float[]{-20, -30, -10, -5}, 100, 100));
        assertArrayEquals(new int[]{100, 100, 0, 0},
                FeedbackBounds.fromPoints(new float[]{120, 130, 140, 160}, 100, 100));
    }

    @Test
    public void malformedCoordinatesProduceEmptyBounds() {
        for (float[] points : new float[][]{null, {}, {1, 2, 3, 4, 5}, {Float.NaN, 0, 1, 2},
                {0, Float.POSITIVE_INFINITY, 1, 2}}) {
            assertArrayEquals(new int[4], FeedbackBounds.fromPoints(points, 100, 100));
        }
    }
}
