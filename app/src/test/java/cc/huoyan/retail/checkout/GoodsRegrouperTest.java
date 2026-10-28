package cc.huoyan.retail.checkout;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class GoodsRegrouperTest {
    private static GoodsEntity goods(String plu) {
        GoodsEntity goods = new GoodsEntity();
        goods.setGoodsId("1"); goods.setPlu(plu); goods.setName(plu); goods.setPrice(100); goods.setUnit(5);
        return goods;
    }

    private static GoodsVo row(String plu, int count, int boxes) {
        java.util.ArrayList<RecognitionSelection> items = new java.util.ArrayList<>();
        for (int i = 0; i < boxes; i++) {
            RecognitionSelection item = new RecognitionSelection(new cc.huoyan.sdk.client.model.RecognitionItem(plu, 0, 1, 2, 3, 4));
            items.add(item);
        }
        return new GoodsVo(count, 100, goods(plu), items);
    }

    @Test public void preservesUnchangedManualCountAndUnmatchedRow() {
        GoodsVo a = row("A", 5, 1);
        GoodsVo b = row("B", 2, 1);
        GoodsVo unmatched = row("unknown", 1, 1);
        unmatched.setUnmatched(true);
        List<GoodsVo> result = GoodsRegrouper.replace(Arrays.asList(a, b, unmatched),
                Collections.singleton(b.getRecognitionSelection().get(0)), goods("C"));
        assertSame(a, result.get(0));
        assertEquals(5, result.get(0).getCount().intValue());
        assertSame(unmatched, result.get(1));
        assertTrue(result.get(1).isUnmatched());
        assertEquals("C", result.get(2).getGoods().getPlu());
        assertEquals(2, result.get(2).getCount().intValue());
    }

    @Test public void mergesWholeRowWithExistingManualQuantity() {
        GoodsVo a = row("A", 5, 1);
        GoodsVo b = row("B", 7, 1);
        List<GoodsVo> result = GoodsRegrouper.replace(Arrays.asList(a, b),
                Collections.singleton(a.getRecognitionSelection().get(0)), b.getGoods());
        assertEquals(1, result.size());
        assertEquals(12, result.get(0).getCount().intValue());
        assertEquals(2, result.get(0).getRecognitionSelection().size());
    }

    @Test public void partialCorrectionTransfersOnePiecePerBox() {
        GoodsVo a = row("A", 5, 3);
        RecognitionSelection moved = a.getRecognitionSelection().get(1);
        List<GoodsVo> result = GoodsRegrouper.replace(Collections.singletonList(a),
                Collections.singleton(moved), goods("B"));
        assertEquals(4, result.get(0).getCount().intValue());
        assertEquals(2, result.get(0).getRecognitionSelection().size());
        assertEquals(1, result.get(1).getCount().intValue());
        assertEquals("B", moved.getSelectedPlu());
    }

    @Test public void choosingSameProductPreservesManualCount() {
        GoodsVo a = row("A", 5, 1);
        List<GoodsVo> result = GoodsRegrouper.replace(Collections.singletonList(a),
                Collections.singleton(a.getRecognitionSelection().get(0)), a.getGoods());
        assertSame(a, result.get(0));
        assertEquals(5, result.get(0).getCount().intValue());
    }

    @Test public void impossibleSplitDoesNotChangeAnyDetection() {
        GoodsVo a = row("A", 1, 2);
        RecognitionSelection selected = a.getRecognitionSelection().get(0);
        assertThrows(IllegalArgumentException.class, () -> GoodsRegrouper.replace(
                Collections.singletonList(a), Collections.singleton(selected), goods("B")));
        assertEquals("A", selected.getSelectedPlu());
        assertEquals(1, a.getCount().intValue());
    }
}
