package cc.huoyan.retail.learning;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class LearningPictureUpdatesTest {
    /** 历史查询已返回本次 UID 时，重复批量回执只更新该 UID 并保持新图置前。 */
    @Test public void captureOverlappingHistoryAndRepeatedUidAppearsOnce() {
        StudyPicEntity capture = picture(12, 100);
        List<StudyPicEntity> result = LearningPictureUpdates.prepend(
                Arrays.asList(picture(13, 0), picture(12, 0), picture(5, 0)),
                Arrays.asList(capture, picture(13, 100), picture(12, 100)), new HashSet<>(), new HashSet<>());
        assertEquals(Arrays.asList(12L, 13L, 5L), uids(result));
        assertSame(capture, result.get(0));
    }

    /** 不同 UID 即使图片地址相同仍是不同学习样本，不能按图片内容擅自合并。 */
    @Test public void differentUidsWithSameImageRemainSeparate() {
        StudyPicEntity first = picture(1, 100), second = picture(2, 100);
        first.setFilePath("http://sdk/shared.jpg"); second.setFilePath(first.getFilePath());
        assertEquals(Arrays.asList(1L, 2L), uids(LearningPictureUpdates.prepend(
                Collections.emptyList(), Arrays.asList(first, second), new HashSet<>(), new HashSet<>())));
    }

    /** 删除全部图片后复用 UID，立即展示新回执，后续保存前的空查询也不能丢图。 */
    @Test public void captureAfterDeletingAllPicturesRevivesReusedUid() {
        Set<Long> saved = new HashSet<>(Arrays.asList(11L, 12L));
        Set<Long> removed = new HashSet<>(Arrays.asList(11L, 12L));
        StudyPicEntity capture = picture(12, 200);
        List<StudyPicEntity> result = LearningPictureUpdates.prepend(
                Collections.emptyList(), Collections.singletonList(capture), saved, removed);
        assertEquals(Collections.singletonList(12L), uids(result));
        assertSame(capture, result.get(0));
        assertFalse(saved.contains(12L));
        assertFalse(removed.contains(12L));
        assertTrue(removed.contains(11L));
        assertSame(capture, LearningPictureUpdates.mergeHistory(result,
                Collections.emptyList(), saved, removed).get(0));
    }

    /** 删除最新 UID 再学习时，旧历史不能替换新回执，也不能恢复其他已删样本。 */
    @Test public void recapturePreservesFeedbackAndOtherDeletedUidsStayHidden() {
        Set<Long> saved = new HashSet<>(Collections.singletonList(12L));
        Set<Long> removed = new HashSet<>(Arrays.asList(11L, 12L));
        StudyPicEntity capture = picture(12, 300);
        List<StudyPicEntity> result = LearningPictureUpdates.prepend(
                Collections.singletonList(picture(10, 0)), Collections.singletonList(capture), saved, removed);
        result = LearningPictureUpdates.mergeHistory(result,
                Arrays.asList(picture(10, 0), picture(11, 0), picture(12, 0)), saved, removed);
        assertEquals(Arrays.asList(12L, 10L), uids(result));
        assertSame(capture, result.get(0));
        saved.add(12L);
        result = LearningPictureUpdates.mergeHistory(result,
                Arrays.asList(picture(10, 0), picture(12, 0)), saved, removed);
        assertEquals(0, result.get(0).getCaptureTimestamp());
    }

    /** 历史回写保留已显示顺序，删除中的中间项不被旧查询重新插入。 */
    @Test public void staleHistoryDoesNotRestoreDeletedPictureOrReorderOthers() {
        List<StudyPicEntity> result = LearningPictureUpdates.mergeHistory(
                Arrays.asList(picture(8, 0), picture(10, 0), picture(7, 0)),
                Arrays.asList(picture(10, 0), picture(9, 0), picture(8, 0), picture(7, 0)),
                Collections.emptySet(), Collections.singleton(9L));
        assertEquals(Arrays.asList(8L, 10L, 7L), uids(result));
    }

    /** 保存回写只完成保存时的照片，查询期间新拍的照片及其反馈标识保留。 */
    @Test public void saveRefreshRetainsNewCaptureAndCompletesSavedPictures() {
        StudyPicEntity justCaptured = picture(20, 200);
        List<StudyPicEntity> result = LearningPictureUpdates.mergeHistory(
                Arrays.asList(justCaptured, picture(8, 100), picture(10, 100)),
                Arrays.asList(picture(10, 0), picture(8, 0)),
                new java.util.HashSet<>(Arrays.asList(8L, 10L)), Collections.emptySet());
        assertEquals(Arrays.asList(20L, 8L, 10L), uids(result));
        assertSame(justCaptured, result.get(0));
        assertEquals(0, result.get(1).getCaptureTimestamp());
        assertEquals(0, result.get(2).getCaptureTimestamp());
    }

    /** 首次加载按最新 UID 展示，分页重叠记录去重。 */
    @Test public void initialHistoryIsUniqueAndNewestFirst() {
        assertEquals(Arrays.asList(10L, 9L, 8L), uids(LearningPictureUpdates.mergeHistory(
                Collections.emptyList(), Arrays.asList(picture(8, 0), picture(10, 0), picture(9, 0), picture(10, 0)),
                Collections.emptySet(), Collections.emptySet())));
    }

    /** 整组遗忘成功后，已保存回执不能使已遗忘图片重新出现。 */
    @Test public void authoritativeEmptyHistoryRemovesSavedPictures() {
        assertTrue(LearningPictureUpdates.mergeHistory(Collections.singletonList(picture(8, 100)),
                Collections.emptyList(), Collections.singleton(8L), Collections.emptySet()).isEmpty());
    }

    @Test public void olderPageKeepsVisibleHistoryAndDeduplicatesOverlap() {
        List<StudyPicEntity> result = LearningPictureUpdates.mergePage(
                Arrays.asList(picture(30, 0), picture(29, 0)),
                Arrays.asList(picture(29, 0), picture(28, 0), picture(27, 0)),
                Collections.emptySet(), Collections.emptySet());
        assertEquals(Arrays.asList(30L, 29L, 28L, 27L), uids(result));
    }

    @Test public void pageCannotResurrectDeletedImageOrReplaceUnsavedCapture() {
        StudyPicEntity capture = picture(40, 100);
        List<StudyPicEntity> result = LearningPictureUpdates.mergePage(
                Arrays.asList(capture, picture(30, 0)),
                Arrays.asList(picture(40, 0), picture(30, 0), picture(29, 0)),
                Collections.emptySet(), Collections.singleton(29L));
        assertEquals(Arrays.asList(40L, 30L), uids(result));
        assertSame(capture, result.get(0));
    }

    @Test public void savingMoreThanOnePageKeepsAllPicturesAndRemovesNewBadges() {
        List<StudyPicEntity> result = LearningPictureUpdates.mergePage(
                Arrays.asList(picture(40, 100), picture(30, 100)),
                Collections.singletonList(picture(40, 0)),
                new HashSet<>(Arrays.asList(40L, 30L)), Collections.emptySet());
        assertEquals(Arrays.asList(40L, 30L), uids(result));
        assertEquals(0, result.get(0).getCaptureTimestamp());
        assertEquals(0, result.get(1).getCaptureTimestamp());
    }

    private static StudyPicEntity picture(long uid, long timestamp) {
        StudyPicEntity picture = new StudyPicEntity();
        picture.setUid(uid); picture.setCaptureTimestamp(timestamp);
        return picture;
    }

    private static List<Long> uids(List<StudyPicEntity> pictures) {
        return pictures.stream().map(StudyPicEntity::getUid).collect(Collectors.toList());
    }
}
