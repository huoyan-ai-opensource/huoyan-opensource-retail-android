package cc.huoyan.retail.feedback;

import cc.huoyan.retail.checkout.CheckoutRecognition;
import cc.huoyan.retail.checkout.GoodsVo;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.sdk.client.feedback.FeedBackUploadHelper;
import cc.huoyan.sdk.client.model.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** 宿主到 SDK 的逐框识别与删除反馈回归，不依赖数据库或 Android 图形字段。 */
public class FeedbackInputsTest {
    /** 构造含原始类别、距离与坐标的独立识别框。 */
    private RecognitionSelection item(String plu) {
        return new RecognitionSelection(new RecognitionItem(plu, 0.123456, 1, 2, 11, 12));
    }

    /** 构造同商品下包含多个原始框的行。 */
    private GoodsVo row(String plu, RecognitionSelection... items) {
        GoodsEntity goods = new GoodsEntity();
        goods.setPlu(plu); goods.setName(plu);
        return new GoodsVo(items.length, 100, goods, Arrays.asList(items));
    }

    /** 用确定的商品映射测试实际宿主组装，再通过 app 当前接入 JAR 转换协议。 */
    private List<FeedbackItem> feedback(List<RecognitionSelection> items, List<GoodsVo> kept, List<GoodsVo> deleted) {
        for (RecognitionSelection item : items) {
            Map<String, String> names = new HashMap<>();
            for (RecognitionCandidate candidate : item.getCandidates())
                names.put(candidate.getClazz(), candidate.getClazz());
            item.retainCandidateNames(names);
        }
        return FeedBackUploadHelper.prepareDetectFeedback(FeedbackInputs.detections(
                new CheckoutRecognition(items), kept, deleted, plu -> row(plu).getGoods(),
                (goods, plu) -> new FeedbackProduct("sdk-" + plu, plu, plu), RecognitionSelection::getPoints), 100, 80);
    }

    /** 同坐标独立框不能互相污染；整行删除为每个原始框输出一条删除反馈。 */
    @Test public void sameCoordinatesDoNotDeleteKeptDetection() {
        RecognitionSelection a1 = item("A"), a2 = item("A"), b = item("B");
        List<FeedbackItem> result = feedback(Arrays.asList(a1, a2, b),
                Collections.singletonList(row("B", b)), Collections.singletonList(row("A", a1, a2)));
        assertEquals(3, result.size());
        for (int i = 0; i < 2; i++) {
            assertNull(result.get(i).getResult());
            assertEquals(FeedbackItem.DELETED, result.get(i).getItemType());
            assertEquals("A", result.get(i).getDetect().get(0).getClazz());
            assertEquals(10, result.get(i).getWidth(), 0);
        }
        assertEquals("sdk", result.get(2).getResult().getSource());
        assertEquals(FeedbackItem.NORMAL, result.get(2).getItemType());
        assertEquals("B", result.get(2).getResult().getClazz());
    }

    /** 改品后删除清空最终选择，保留原始识别，后续编辑不改变快照。 */
    @Test public void deletionAfterCorrectionKeepsOriginalAndSnapshot() {
        RecognitionSelection a = item("A"); a.select("B");
        List<GoodsVo> deleted = new ArrayList<>(Collections.singletonList(row("B", a)));
        List<FeedbackItem> result = feedback(Collections.singletonList(a), Collections.emptyList(), deleted);
        deleted.clear(); a.select("C");
        assertEquals(1, result.size());
        assertNull(result.get(0).getResult());
        assertEquals(FeedbackItem.DELETED, result.get(0).getItemType());
        assertEquals(Collections.singletonList(new FeedbackDetect("A", "A", .123)),
                result.get(0).getDetect());
    }

    /** 多框同坐标且候选含命中时仍各自上报；命中不重复，改品不混入 detect 或其他框记录。 */
    @Test public void eachBoxReportsOnlyItsWinnerAndCandidates() {
        RecognitionSelection a = new RecognitionSelection(new RecognitionItem("A", .111,
                1, 2, 11, 12, 0, 0, Arrays.asList(
                        new RecognitionCandidate("A", .111, 0, 0),
                        new RecognitionCandidate("C", .333, 0, 0),
                        new RecognitionCandidate("D", .444, 0, 0),
                        new RecognitionCandidate("A", .777, 0, 0),
                        new RecognitionCandidate("C", .888, 0, 0))));
        RecognitionSelection b = new RecognitionSelection(new RecognitionItem("B", .222,
                1, 2, 11, 12, 0, 0,
                Arrays.asList(new RecognitionCandidate("B", .222, 0, 0),
                        new RecognitionCandidate("E", .555, 0, 0),
                        new RecognitionCandidate("B", .999, 0, 0))));
        a.select("Z");
        List<FeedbackItem> result = feedback(Arrays.asList(a, b),
                Arrays.asList(row("Z", a), row("B", b)), Collections.emptyList());
        assertEquals(2, result.size());
        assertEquals(Arrays.asList(new FeedbackDetect("A", "A", .111),
                new FeedbackDetect("C", "C", .333), new FeedbackDetect("D", "D", .444)),
                result.get(0).getDetect());
        assertEquals(Arrays.asList(new FeedbackDetect("B", "B", .222),
                new FeedbackDetect("E", "E", .555)), result.get(1).getDetect());
        assertEquals("Z", result.get(0).getResult().getClazz());
        assertEquals(FeedbackItem.MODIFIED, result.get(0).getItemType());
        assertEquals(FeedbackItem.NORMAL, result.get(1).getItemType());
    }
    /** 公开确认入口仅用内存快照，改品后仍保留原始 SDK ID，无须读取数据库或 SDK。 */
    @Test public void confirmationUsesSdkSnapshotsWithoutLookup() {
        RecognitionSelection original = item("A");
        original.retainOriginalProduct(new FeedbackProduct("100", "苹果", "A"));
        original.select("B");
        GoodsVo selected = row("B", original); selected.getGoods().setGoodsId("200");
        List<FeedbackDetection> result = FeedbackInputs.detections(new CheckoutRecognition(Collections.singletonList(original)),
                Collections.singletonList(selected), Collections.emptyList(), 0, 0);
        selected.getGoods().setGoodsId("300");
        assertEquals("100", result.get(0).getOriginal().getGoodsId());
        assertEquals("200", result.get(0).getSelected().getGoodsId());
        FeedbackItem feedback = FeedBackUploadHelper.prepareDetectFeedback(result, 0, 0).get(0);
        assertEquals(Collections.singletonList(new FeedbackDetect("A", "苹果", .123)), feedback.getDetect());
    }
}
