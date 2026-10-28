package cc.huoyan.retail.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 默认识别区域计算测试（中心基准、宽高各占 50%）。
 * 仅测纯计算 {@link SPManager#computeDefaultRecognizeAreaPoints(int, int)}，
 * SP 读写（getRecognizeArea）依赖 SharedPreferences/RetailApplication，不做 JVM 单测。
 */
public class SPManagerTest {

    private static final float EXPECTED_MARGIN = 0.25f; // 宽高各占 50%，四边边距为 25%

    @Test
    public void defaultArea_margin25Percent() {
        float[] pts = SPManager.computeDefaultRecognizeAreaPoints(1000, 1000);
        assertEquals(EXPECTED_MARGIN, pts[0] / 1000f, 1e-4f);
        assertEquals(EXPECTED_MARGIN, pts[1] / 1000f, 1e-4f);
        assertEquals(1f - EXPECTED_MARGIN, pts[2] / 1000f, 1e-4f);
        assertEquals(1f - EXPECTED_MARGIN, pts[5] / 1000f, 1e-4f);
    }

    @Test
    public void defaultArea_centerAnchored_symmetricAroundMidpoint() {
        int[][] sizes = {{772, 468}, {1920, 1008}, {1280, 720}};
        for (int[] size : sizes) {
            float[] pts = SPManager.computeDefaultRecognizeAreaPoints(size[0], size[1]);
            assertEquals("左右边距对称（中心基准），分辨率：" + size[0] + "*" + size[1],
                    size[0], pts[0] + pts[2], 0.01f);
            assertEquals("上下边距对称（中心基准），分辨率：" + size[0] + "*" + size[1],
                    size[1], pts[1] + pts[5], 0.01f);
        }
    }

    @Test
    public void defaultArea_covers25PercentArea() {
        int[][] sizes = {{772, 468}, {1920, 1008}, {1280, 720}};
        for (int[] size : sizes) {
            float[] pts = SPManager.computeDefaultRecognizeAreaPoints(size[0], size[1]);
            float areaRatio = (pts[2] - pts[0]) * (pts[5] - pts[1]) / (size[0] * (float) size[1]);
            assertEquals("面积占比 25%，分辨率：" + size[0] + "*" + size[1], 0.25f, areaRatio, 1e-4f);
        }
    }

    @Test
    public void defaultArea_keepsPreviewAspectRatio() {
        int[][] sizes = {{772, 468}, {1920, 1008}, {1280, 720}};
        for (int[] size : sizes) {
            float[] pts = SPManager.computeDefaultRecognizeAreaPoints(size[0], size[1]);
            float previewRatio = size[0] / (float) size[1];
            float areaRatio = (pts[2] - pts[0]) / (pts[5] - pts[1]);
            assertEquals("区域宽高比与预览一致（等比例），分辨率：" + size[0] + "*" + size[1],
                    previewRatio, areaRatio, 1e-4f);
        }
    }

    @Test
    public void defaultArea_pointOrder_topLeftTopRightBottomRightBottomLeft() {
        float[] pts = SPManager.computeDefaultRecognizeAreaPoints(772, 468);
        // 左上(x0,y1) 右上(x2,y3) 右下(x4,y5) 左下(x6,y7)：上边 y 相等、右边 x 相等、下边 y 相等、左边 x 相等
        assertEquals(pts[1], pts[3], 0f);
        assertEquals(pts[2], pts[4], 0f);
        assertEquals(pts[5], pts[7], 0f);
        assertEquals(pts[0], pts[6], 0f);
        assertEquals(8, pts.length);
    }

    @Test
    public void defaultArea_fallbackResolution772x468_expectedPixels() {
        float[] pts = SPManager.computeDefaultRecognizeAreaPoints(772, 468);
        float margin = 0.25f;
        assertArrayEquals(new float[]{
                772 * margin, 468 * margin,
                772 * (1f - margin), 468 * margin,
                772 * (1f - margin), 468 * (1f - margin),
                772 * margin, 468 * (1f - margin)
        }, pts, 0.001f);
    }

    @Test
    public void studyUidMap_putThenFind_roundtripAcrossSerializations() {
        String json = SPManager.putStudyUidEntry(null, "1001", 1, 42L);
        assertEquals(42L, SPManager.findStudyUid(json, "1001", 1));
        json = SPManager.putStudyUidEntry(json, "1001", 2, 43L);
        json = SPManager.putStudyUidEntry(json, "1002", 1, 44L);
        assertEquals(42L, SPManager.findStudyUid(json, "1001", 1));
        assertEquals(43L, SPManager.findStudyUid(json, "1001", 2));
        assertEquals(44L, SPManager.findStudyUid(json, "1002", 1));
    }

    @Test
    public void studyUidMap_findMissing_returnsMinusOne() {
        assertEquals(-1L, SPManager.findStudyUid(null, "1001", 1));
        assertEquals(-1L, SPManager.findStudyUid("", "1001", 1));
        String json = SPManager.putStudyUidEntry("{}", "1001", 1, 42L);
        assertEquals(-1L, SPManager.findStudyUid(json, "1001", 2));
        assertEquals(-1L, SPManager.findStudyUid(json, "1002", 1));
    }

    @Test
    public void studyUidMap_putSamePicId_replacesUid() {
        String json = SPManager.putStudyUidEntry(null, "1001", 1, 42L);
        json = SPManager.putStudyUidEntry(json, "1001", 1, 99L);
        assertEquals(99L, SPManager.findStudyUid(json, "1001", 1));
        assertEquals(1, SPManager.parseStudyUidMap(json).get("1001").size());
    }

    @Test
    public void studyUidMap_invalidArgs_notWritten() {
        String json = SPManager.putStudyUidEntry(null, "", 1, 42L);
        assertEquals(-1L, SPManager.findStudyUid(json, "", 1));
        json = SPManager.putStudyUidEntry(json, "1001", 0, 42L);
        assertEquals(-1L, SPManager.findStudyUid(json, "1001", 0));
        json = SPManager.putStudyUidEntry(json, "1001", 1, 0L);
        assertEquals(-1L, SPManager.findStudyUid(json, "1001", 1));
    }

    @Test
    public void studyUidMap_removeEntry_keepsOthersAndDropsEmptyPlu() {
        String json = SPManager.putStudyUidEntry(null, "1001", 1, 42L);
        json = SPManager.putStudyUidEntry(json, "1001", 2, 43L);
        json = SPManager.putStudyUidEntry(json, "1002", 1, 44L);
        json = SPManager.removeStudyUidEntry(json, "1001", 1);
        assertEquals(-1L, SPManager.findStudyUid(json, "1001", 1));
        assertEquals(43L, SPManager.findStudyUid(json, "1001", 2));
        assertEquals(44L, SPManager.findStudyUid(json, "1002", 1));
        json = SPManager.removeStudyUidEntry(json, "1001", 2);
        assertFalse(SPManager.parseStudyUidMap(json).containsKey("1001"));
        assertEquals(44L, SPManager.findStudyUid(json, "1002", 1));
    }

    @Test
    public void studyUidMap_removePlu_onlyDropsTargetPlu() {
        String json = SPManager.putStudyUidEntry(null, "1001", 1, 42L);
        json = SPManager.putStudyUidEntry(json, "1002", 1, 44L);
        json = SPManager.removeStudyUidPlu(json, "1001");
        assertEquals(-1L, SPManager.findStudyUid(json, "1001", 1));
        assertEquals(44L, SPManager.findStudyUid(json, "1002", 1));
    }

    @Test
    public void studyUidMap_malformedJson_tolerated() {
        assertEquals(-1L, SPManager.findStudyUid("not-json{", "1001", 1));
        String json = SPManager.removeStudyUidEntry("not-json{", "1001", 1);
        assertEquals(-1L, SPManager.findStudyUid(json, "1001", 1));
        json = SPManager.putStudyUidEntry("not-json{", "1001", 1, 42L);
        assertEquals(42L, SPManager.findStudyUid(json, "1001", 1));
    }

}
