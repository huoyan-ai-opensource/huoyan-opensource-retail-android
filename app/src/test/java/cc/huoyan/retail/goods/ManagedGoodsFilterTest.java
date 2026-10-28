package cc.huoyan.retail.goods;

import cc.huoyan.retail.data.GoodsEntity;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static cc.huoyan.retail.goods.ManagedGoodsFilter.Order.*;
import static cc.huoyan.retail.goods.ManagedGoodsFilter.Pricing.*;

public class ManagedGoodsFilterTest {
    private GoodsEntity goods(long id, String plu, String name, int price, int unit) {
        GoodsEntity goods = new GoodsEntity(); goods.setGoodsId(String.valueOf(id)); goods.setPlu(plu);
        goods.setName(name); goods.setPrice(price); goods.setUnit(unit); return goods;
    }

    @Test public void onlyUnitFiveIsCountAndFilteringDoesNotMutateSource() {
        GoodsEntity kg = goods(1, "01", "苹果", 900, 0), legacy = goods(2, "02", "香蕉", 800, 9),
                each = goods(3, "03", "面包", 1000, 5);
        List<GoodsEntity> source = Arrays.asList(kg, each, legacy);
        assertEquals(Arrays.asList(kg, legacy), ManagedGoodsFilter.apply(source, PLU, WEIGHT));
        assertEquals(Arrays.asList(each), ManagedGoodsFilter.apply(source, PLU, COUNT));
        assertEquals(Arrays.asList(kg, each, legacy), source);
        assertEquals(Arrays.asList(each, legacy, kg), ManagedGoodsFilter.apply(source, NEWEST, ALL));
    }

    @Test public void pricesUseIntegerCentsAndTiesStayStableAcrossPages() {
        GoodsEntity a = goods(1, "02", "B", 1234, 0), b = goods(2, "01", "A", 1234, 0),
                high = goods(3, "03", "C", Integer.MAX_VALUE, 0);
        assertEquals(Arrays.asList(b, a, high), ManagedGoodsFilter.apply(Arrays.asList(high, a, b), PRICE_ASC, ALL));
        assertEquals(Arrays.asList(high, b, a), ManagedGoodsFilter.apply(Arrays.asList(a, high, b), PRICE_DESC, ALL));
    }

    @Test public void searchAndPricingComposeBeforeSorting() {
        GoodsEntity apple = goods(1, "100", "Apple", 100, 0), bread = goods(2, "101", "Apple bread", 200, 5),
                pear = goods(3, "102", "Pear", 300, 5);
        List<GoodsEntity> matches = new GoodsSearchIndex(Arrays.asList(pear, bread, apple)).search("apple");
        assertEquals(Arrays.asList(apple, bread), ManagedGoodsFilter.apply(matches, NAME, ALL));
        assertEquals(Arrays.asList(bread), ManagedGoodsFilter.apply(matches, NAME, COUNT));
        assertTrue(ManagedGoodsFilter.apply(new GoodsSearchIndex(Arrays.asList(apple)).search("zzz"), NEWEST, ALL).isEmpty());
    }

    @Test public void absentNamesAndIdsAreSafe() {
        GoodsEntity value = goods(1, "01", null, 0, 0); value.setGoodsId(null);
        GoodsEntity other = goods(2, "02", "A", 0, 0);
        assertEquals(Arrays.asList(value, other), ManagedGoodsFilter.apply(Arrays.asList(other, value), NAME, ALL));
        assertEquals(Arrays.asList(other, value), ManagedGoodsFilter.apply(Arrays.asList(value, other), NEWEST, ALL));
    }
    @Test public void imageCountsSortNumericallyAndUnknownAlwaysLast() {
        GoodsEntity zero = goods(1, "01", "Zero", 100, 5), few = goods(2, "02", "Few", 100, 5),
                many = goods(3, "03", "Many", 100, 0), unknown = goods(4, "04", "Unknown", 100, 5);
        List<GoodsEntity> source = Arrays.asList(unknown, many, zero, few);
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        counts.put("01", 0L); counts.put("02", 2L); counts.put("03", 10L);
        assertEquals(Arrays.asList(zero, few, many, unknown), ManagedGoodsFilter.apply(source, IMAGES_ASC, ALL, counts));
        assertEquals(Arrays.asList(many, few, zero, unknown), ManagedGoodsFilter.apply(source, IMAGES_DESC, ALL, counts));
        assertEquals(Arrays.asList(few, zero, unknown), ManagedGoodsFilter.apply(source, IMAGES_DESC, COUNT, counts));
        counts.put("01", 2L);
        assertEquals(Arrays.asList(zero, few, unknown), ManagedGoodsFilter.apply(source, IMAGES_DESC, COUNT, counts));
    }
}
