package cc.huoyan.retail.goods;

import cc.huoyan.retail.data.GoodsEntity;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoodsSearchIndexTest {
    private GoodsEntity goods(String plu, String name) {
        GoodsEntity value = new GoodsEntity(); value.setPlu(plu); value.setName(name); return value;
    }

    @Test public void searchesChineseInitialsEnglishAndPartialPlu() {
        GoodsEntity bread = goods("1001", "全麦吐司面包"), apple = goods("2100", "Apple 苹果");
        GoodsSearchIndex index = new GoodsSearchIndex(Arrays.asList(bread, apple));
        assertEquals(Arrays.asList(bread), index.search(" qm "));
        assertEquals(Arrays.asList(apple), index.search("APPLE"));
        assertEquals(Arrays.asList(bread), index.search("吐司"));
        assertEquals(Arrays.asList(bread, apple), index.search("100"));
        assertTrue(index.search("zzz").isEmpty());
        assertEquals(Arrays.asList(bread, apple), index.search(null));
    }

    @Test public void exactPluPrecedesPartialMatchesAndMissingNamesAreSafe() {
        GoodsEntity partial = goods("31001", ""), exact = goods("1001", null);
        GoodsSearchIndex index = new GoodsSearchIndex(Arrays.asList(partial, exact));
        assertEquals(Arrays.asList(exact, partial), index.search("1001"));
    }
}
