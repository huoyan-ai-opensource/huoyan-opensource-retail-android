package cc.huoyan.sdk.client.feedback;
import cc.huoyan.sdk.client.model.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class FeedBackUploadHelperTest {
    @org.junit.Test public void rejectsRetiredStudyType() {
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new Feedback("r", "https://x/p", 2, Collections.emptyList()));
    }

    /** 正常、改品及删除均保留原始命中，随后按 SDK 顺序上报同框落选候选。 */
    @Test public void correctionsAndDeletionKeepWinnerCandidateOrderAndFinalChoice() {
        List<FeedbackDetect> candidates = Arrays.asList(new FeedbackDetect("001","梨",.234),
                new FeedbackDetect("C3","香蕉",.345));
        List<FeedbackDetect> expected = Arrays.asList(new FeedbackDetect("A001","苹果",.123456),
                candidates.get(0), candidates.get(1));
        FeedbackProduct original = new FeedbackProduct(null,"苹果","A001");
        FeedbackProduct selected = new FeedbackProduct(null,"桃","B2");
        List<FeedbackItem> items = FeedBackUploadHelper.prepareDetectFeedback(Arrays.asList(
                new FeedbackDetection(original,original,false,.123456,new float[]{1,2,31,42},candidates),
                new FeedbackDetection(original,selected,false,.123456,new float[]{1,2,31,42},candidates),
                new FeedbackDetection(original,selected,true,.123456,new float[]{1,2,31,42},candidates)),100,80);
        assertEquals(3,items.size());
        for(int i=0;i<3;i++) { assertEquals(i,items.get(i).getItemType()); assertEquals(expected,items.get(i).getDetect()); }
        assertEquals("B2",items.get(1).getResult().getClazz());
        assertEquals("manual",items.get(1).getResult().getSource());
        assertNull(items.get(2).getResult());
        assertEquals(.123,items.get(0).getDetect().get(0).getDistance(),0);
        assertEquals(1,new Feedback("r","https://x/p",0,items).getFeedbackType());
        assertEquals(0,new Feedback("r","https://x/p",1,Collections.singletonList(items.get(0))).getFeedbackType());
    }

    /** SDK 没有落选候选时，detect 仍包含该框命中，不能上报空识别列表。 */
    @Test public void noCandidatesStillReportsOriginalWinner() {
        FeedbackProduct original = new FeedbackProduct(null, "苹果", "A001");
        List<FeedbackItem> items = FeedBackUploadHelper.prepareDetectFeedback(Collections.singletonList(
                new FeedbackDetection(original, null, false, .123456,
                        new float[]{1, 2, 31, 42}, Collections.emptyList())), 100, 80);
        assertEquals(Collections.singletonList(new FeedbackDetect("A001", "苹果", .123)),
                items.get(0).getDetect());
    }

    /** 回放实际两个框：卤蛋的多个距离属于同一商品，每框仅上报卤蛋与米饭两条。 */
    @Test public void reportedBoxesContainTwoDistinctProductsEach() {
        FeedbackProduct egg = new FeedbackProduct(null, "外购卤蛋", "81001379");
        FeedbackProduct rice = new FeedbackProduct(null, "米饭", "81001413");
        List<FeedbackDetect> first = Arrays.asList(
                new FeedbackDetect("81001379", "外购卤蛋", 2.547),
                new FeedbackDetect("81001413", "米饭", 2.934),
                new FeedbackDetect("81001379", "外购卤蛋", 7.003));
        List<FeedbackDetect> second = Arrays.asList(
                new FeedbackDetect("81001379", "外购卤蛋", 2.347),
                new FeedbackDetect("81001413", "米饭", 7.192),
                new FeedbackDetect("81001379", "外购卤蛋", 7.327));
        List<FeedbackItem> items = FeedBackUploadHelper.prepareDetectFeedback(Arrays.asList(
                new FeedbackDetection(egg, rice, false, 2.547,
                        new float[]{1122, 914, 1391, 1064}, first),
                new FeedbackDetection(egg, egg, false, 2.347,
                        new float[]{733, 677, 1053, 995}, second)), 1920, 1080);
        assertEquals(first.subList(0, 2), items.get(0).getDetect());
        assertEquals(second.subList(0, 2), items.get(1).getDetect());
        assertEquals(FeedbackItem.MODIFIED, items.get(0).getItemType());
        assertEquals("81001413", items.get(0).getResult().getClazz());
        assertEquals(FeedbackItem.NORMAL, items.get(1).getItemType());
    }

    /** 商品去重不受距离及名称影响；命中保留原始距离，其他商品保留首次记录及顺序。 */
    @Test public void deduplicatesByClassKeepingWinnerAndFirstCandidateForOtherProducts() {
        FeedbackProduct original = new FeedbackProduct(null, "外购卤蛋", "81001379");
        FeedbackDetect other = new FeedbackDetect("81001413", "米饭", 2.934);
        FeedbackDetect sameClass = new FeedbackDetect("81001379", null, 7.003);
        FeedbackDetect third = new FeedbackDetect("001", "第三个商品", .789);
        List<FeedbackDetect> candidates = Arrays.asList(other,
                new FeedbackDetect("81001379", null, 2.547123), sameClass, third,
                new FeedbackDetect("81001413", "米饭", 1.234));
        List<FeedbackItem> items = FeedBackUploadHelper.prepareDetectFeedback(Collections.singletonList(
                new FeedbackDetection(original, null, true, 2.547123,
                        new float[]{1, 2, 31, 42}, candidates)), 100, 80);
        assertEquals(Arrays.asList(new FeedbackDetect("81001379", "外购卤蛋", 2.547), other, third),
                items.get(0).getDetect());
        assertEquals(5, candidates.size());
        assertEquals(FeedbackItem.DELETED, items.get(0).getItemType());
    }

    /** 商品编码按原值区分，前导零和大小写不同的商品不能合并。 */
    @Test public void classDeduplicationPreservesLeadingZerosAndCase() {
        FeedbackProduct original = new FeedbackProduct(null, "原商品", "A001");
        List<FeedbackDetect> candidates = Arrays.asList(
                new FeedbackDetect("a001", null, .2), new FeedbackDetect("001", null, .3),
                new FeedbackDetect("1", null, .4), new FeedbackDetect("a001", null, .5));
        List<FeedbackItem> items = FeedBackUploadHelper.prepareDetectFeedback(Collections.singletonList(
                new FeedbackDetection(original, null, false, .1,
                        new float[]{1, 2, 31, 42}, candidates)), 100, 80);
        assertEquals(Arrays.asList(new FeedbackDetect("A001", "原商品", .1),
                candidates.get(0), candidates.get(1), candidates.get(2)), items.get(0).getDetect());
    }
}
