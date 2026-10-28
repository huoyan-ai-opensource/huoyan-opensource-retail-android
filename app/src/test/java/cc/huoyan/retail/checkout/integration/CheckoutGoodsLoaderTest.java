package cc.huoyan.retail.checkout.integration;

import cc.huoyan.retail.checkout.GoodsVo;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.checkout.CheckoutRecognition;
import cc.huoyan.retail.feedback.FeedbackInputs;
import cc.huoyan.sdk.client.feedback.FeedBackUploadHelper;
import cc.huoyan.sdk.client.model.FeedbackDetect;
import cc.huoyan.sdk.client.model.FeedbackItem;
import cc.huoyan.sdk.client.model.RecognitionCandidate;
import cc.huoyan.sdk.client.model.RecognitionItem;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class CheckoutGoodsLoaderTest {
    @Test public void missingProductsRemainVisibleAndMatchedDetectionsKeepIdentity() {
        RecognitionSelection first = new RecognitionSelection(new cc.huoyan.sdk.client.model.RecognitionItem("1", 0, 1, 2, 3, 4));
        RecognitionSelection second = new RecognitionSelection(new cc.huoyan.sdk.client.model.RecognitionItem("1", 0, 1, 2, 3, 4));
        RecognitionSelection unknown = new RecognitionSelection(new cc.huoyan.sdk.client.model.RecognitionItem("1", 0, 1, 2, 3, 4));
        Map<String, List<RecognitionSelection>> groups = new LinkedHashMap<>();
        groups.put("A", Arrays.asList(first, second));
        groups.put("UNKNOWN", Collections.singletonList(unknown));
        GoodsEntity goods = new GoodsEntity();
        goods.setPlu("A"); goods.setPrice(123);
        List<GoodsVo> result = CheckoutGoodsLoader.assemble(groups, Collections.singletonList(goods));
        assertEquals(2, result.size());
        assertEquals(Integer.valueOf(2), result.get(0).getCount());
        assertSame(second, result.get(0).getRecognitionSelection().get(1));
        assertFalse(result.get(0).isUnmatched());
        assertTrue(result.get(1).isUnmatched());
        assertEquals("UNKNOWN", result.get(1).getGoods().getName());
        assertNull(result.get(0).getRecognizeBitmap());
    }

    @Test public void emptySdkCatalogDoesNotDiscardRecognitionRows() {
        Map<String, List<RecognitionSelection>> groups = new LinkedHashMap<>();
        groups.put("MISSING", Collections.singletonList(new RecognitionSelection(new cc.huoyan.sdk.client.model.RecognitionItem("1", 0, 1, 2, 3, 4))));
        List<GoodsVo> result = CheckoutGoodsLoader.assemble(groups, Collections.emptyList());
        assertEquals(1, result.size());
        assertTrue(result.get(0).isUnmatched());
    }
    /** 原始商品身份在查询阶段固定，之后改品和刷新不能覆盖反馈原商品。 */
    @Test public void sdkIdentityIsRetainedForFeedbackAfterCorrection() {
        RecognitionSelection item = new RecognitionSelection(new cc.huoyan.sdk.client.model.RecognitionItem("123", 0, 1, 2, 3, 4));
        GoodsEntity goods = new GoodsEntity();
        goods.setGoodsId("987654321"); goods.setPlu("123"); goods.setName("苹果"); goods.setPrice(4_000_000_000L);
        Map<String, List<RecognitionSelection>> groups = Collections.singletonMap("123", Collections.singletonList(item));
        List<GoodsVo> rows = CheckoutGoodsLoader.assemble(groups, Collections.singletonList(goods));
        item.select("456"); goods.setGoodsId("changed");
        assertEquals("987654321", item.getOriginalProduct().getGoodsId());
        assertEquals("123", item.getOriginalProduct().getPlu());
        assertEquals(4_000_000_000L, rows.get(0).getAmount());
    }

    /** 候选未入车也从批量查询保存名称；后续目录变化、改品与删除不改变上报快照。 */
    @Test public void feedbackIncludesNamesForCandidatesNotInCheckoutRows() {
        RecognitionSelection item = new RecognitionSelection(new RecognitionItem("tz", 2.990,
                672, 429, 932, 735, 0, 0, Arrays.asList(
                        new RecognitionCandidate("tz", 2.990, 0, 0),
                        new RecognitionCandidate("xj", 7.941, 0, 0),
                        new RecognitionCandidate("tz", 8.123, 0, 0))));
        Map<String, List<RecognitionSelection>> groups = Collections.singletonMap("tz", Collections.singletonList(item));
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("tz", "xj")), CheckoutGoodsLoader.productPlus(groups));
        GoodsEntity peach = new GoodsEntity(); peach.setPlu("tz"); peach.setName("桃子");
        GoodsEntity banana = new GoodsEntity(); banana.setPlu("xj"); banana.setName("香蕉");
        List<GoodsVo> rows = CheckoutGoodsLoader.assemble(groups, Arrays.asList(peach, banana));
        assertEquals(1, rows.size());
        assertEquals("tz", rows.get(0).getGoods().getPlu());
        banana.setName("已改名");
        item.retainCandidateNames(Collections.singletonMap("xj", "后续查询名称"));
        CheckoutRecognition response = new CheckoutRecognition(Collections.singletonList(item));
        List<FeedbackDetect> expected = Arrays.asList(new FeedbackDetect("tz", "桃子", 2.990),
                new FeedbackDetect("xj", "香蕉", 7.941));
        FeedbackItem normal = FeedBackUploadHelper.prepareDetectFeedback(FeedbackInputs.detections(
                response, rows, Collections.emptyList(), 0, 0), 0, 0).get(0);
        assertEquals(expected, normal.getDetect());
        GoodsEntity apple = new GoodsEntity(); apple.setPlu("pg"); apple.setName("苹果");
        item.select("pg");
        List<GoodsVo> corrected = Collections.singletonList(new GoodsVo(1, 100, apple, Collections.singletonList(item)));
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("pg", "tz", "xj")),
                CheckoutGoodsLoader.productPlus(Collections.singletonMap("pg", Collections.singletonList(item))));
        FeedbackItem modified = FeedBackUploadHelper.prepareDetectFeedback(FeedbackInputs.detections(
                response, corrected, Collections.emptyList(), 0, 0), 0, 0).get(0);
        FeedbackItem deleted = FeedBackUploadHelper.prepareDetectFeedback(FeedbackInputs.detections(
                response, Collections.emptyList(), corrected, 0, 0), 0, 0).get(0);
        assertEquals(expected, modified.getDetect());
        assertEquals(expected, deleted.getDetect());
        assertEquals("苹果", modified.getResult().getName());
        assertEquals(FeedbackItem.MODIFIED, modified.getItemType());
        assertEquals(FeedbackItem.DELETED, deleted.getItemType());
        assertNull(deleted.getResult());
    }

    /** SDK 目录确实缺少候选商品时保留类别和距离，不用编码伪造商品名。 */
    @Test public void missingCandidateProductKeepsEmptyName() {
        RecognitionSelection item = new RecognitionSelection(new RecognitionItem("tz", 2.990,
                1, 2, 11, 12, 0, 0,
                Collections.singletonList(new RecognitionCandidate("unknown", 7.941, 0, 0))));
        GoodsEntity peach = new GoodsEntity(); peach.setPlu("tz"); peach.setName("桃子");
        List<GoodsVo> rows = CheckoutGoodsLoader.assemble(
                Collections.singletonMap("tz", Collections.singletonList(item)), Collections.singletonList(peach));
        FeedbackItem feedback = FeedBackUploadHelper.prepareDetectFeedback(FeedbackInputs.detections(
                new CheckoutRecognition(Collections.singletonList(item)), rows, Collections.emptyList(), 0, 0), 0, 0).get(0);
        assertEquals(new FeedbackDetect("unknown", "", 7.941), feedback.getDetect().get(1));
    }
}
