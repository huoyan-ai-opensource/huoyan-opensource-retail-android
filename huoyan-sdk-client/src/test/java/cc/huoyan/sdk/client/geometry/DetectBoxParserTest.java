package cc.huoyan.sdk.client.geometry;
import cc.huoyan.sdk.client.geometry.DetectGeometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import cc.huoyan.sdk.client.geometry.DetectBoxParser.DetectRect;

import org.junit.Test;

public class DetectBoxParserTest {

    private static void assertRect(DetectRect rect, float left, float top, float right, float bottom) {
        assertNotNull(rect);
        assertEquals(left, rect.left, 0f);
        assertEquals(top, rect.top, 0f);
        assertEquals(right, rect.right, 0f);
        assertEquals(bottom, rect.bottom, 0f);
    }

    @Test
    public void parse_rotatedObb_returnsBoundingRectByMinMax() {
        float[] obb = {10, 10, 90, 20, 80, 80, 0, 70};

        assertRect(DetectBoxParser.parse(obb, DetectGeometry.OBB), 0f, 10f, 90f, 80f);
    }

    @Test
    public void parse_axisAlignedObb_returnsSameRect() {
        float[] obb = {10, 20, 100, 20, 100, 120, 10, 120};

        assertRect(DetectBoxParser.parse(obb, DetectGeometry.OBB), 10f, 20f, 100f, 120f);
    }

    @Test
    public void parse_obbWithExtraValues_usesFirstEightOnly() {
        float[] obb = {0, 0, 10, 0, 10, 10, 0, 10, 999, 999};

        assertRect(DetectBoxParser.parse(obb, DetectGeometry.OBB), 0f, 0f, 10f, 10f);
    }

    @Test
    public void parse_bbox_returnsDirectly() {
        float[] bbox = {10, 20, 100, 120};

        assertRect(DetectBoxParser.parse(bbox, DetectGeometry.BBOX), 10f, 20f, 100f, 120f);
    }

    @Test
    public void parse_bboxReversedOrder_clampsToNormalized() {
        float[] bbox = {100, 120, 10, 20};

        assertRect(DetectBoxParser.parse(bbox, DetectGeometry.BBOX), 10f, 20f, 100f, 120f);
    }

    @Test
    public void parse_bboxPartiallyReversed_clampsPerAxis() {
        float[] bbox = {100, 20, 10, 120};

        assertRect(DetectBoxParser.parse(bbox, DetectGeometry.BBOX), 10f, 20f, 100f, 120f);
    }

    @Test
    public void parse_negativeValues_keptAsIs() {
        float[] bbox = {-10, -20, 30, 40};
        assertRect(DetectBoxParser.parse(bbox, DetectGeometry.BBOX), -10f, -20f, 30f, 40f);

        float[] obb = {-10, -10, -90, -20, -80, -80, 0, -70};
        assertRect(DetectBoxParser.parse(obb, DetectGeometry.OBB), -90f, -80f, 0f, -10f);
    }

    @Test
    public void parse_obbTooShort_returnsNull() {
        assertNull(DetectBoxParser.parse(new float[]{10, 10, 90, 20}, DetectGeometry.OBB));
    }

    @Test
    public void parse_bboxTooShort_returnsNull() {
        assertNull(DetectBoxParser.parse(new float[]{10, 20}, DetectGeometry.BBOX));
    }

    @Test
    public void parse_nullPoints_returnsNull() {
        assertNull(DetectBoxParser.parse(null, DetectGeometry.OBB));
        assertNull(DetectBoxParser.parse(null, DetectGeometry.BBOX));
    }

    @Test
    public void parse_nullGeometry_returnsNull() {
        assertNull(DetectBoxParser.parse(new float[]{10, 10, 90, 20, 80, 80, 0, 70}, null));
    }
}
