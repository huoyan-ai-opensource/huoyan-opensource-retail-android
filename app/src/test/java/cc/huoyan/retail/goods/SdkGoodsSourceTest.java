package cc.huoyan.retail.goods;

import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.*;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import static org.junit.Assert.*;

/** SDK 商品来源回归：分页完整性、远端写入结果及换绑隔离均在纯 JVM 验证。 */
public class SdkGoodsSourceTest {
    /** 创建只处理指定方法的 SDK 替身，意外调用立即失败。 */
    private static HuoyanSdkClient client(BiFunction<String, Object[], Object> action) {
        return (HuoyanSdkClient) Proxy.newProxyInstance(HuoyanSdkClient.class.getClassLoader(),
                new Class<?>[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isConnected")) return true;
                    return action.apply(method.getName(), args);
                });
    }
    /** 注入纯 Java 文案，单测不依赖 Android 资源。 */
    private static SdkGoodsSource source(HuoyanSdkClient client) {
        return new SdkGoodsSource(() -> client, () -> 0, String::valueOf);
    }
    /** 构造真实 SDK 结构的商品，保留业务标识与元数据。 */
    private static Goods goods(String id, String plu, long price) {
        return new Goods(id, "shop", "苹果", plu, null, "image", 1, "水果", "kg", 1,
                price, 50, "配料", "3天", "PG", 1, 2, 0, "updated", "门店", "account", "客户", "created");
    }
    /** 构造 SDK 分页回执。 */
    private static GoodsQueryResult page(int page, long total, long pages, Goods... goods) {
        return new GoodsQueryResult(0, null, total, pages, page, 100, Arrays.asList(goods));
    }
    @Test public void readsEveryPageAndKeepsLongCentsAndSdkIdentity() {
        List<Integer> pages = new ArrayList<>();
        HuoyanSdkClient client = client((method, args) -> {
            assertEquals("queryGoods", method); assertEquals(0, args[3]);
            int current = (Integer) args[4]; pages.add(current);
            return page(current, 2, 2, goods("" + current, "00" + current, 4_000_000_000L));
        });
        List<GoodsEntity> result = source(client).getAllGoods();
        assertEquals(Arrays.asList(1, 2), pages);
        assertEquals(4_000_000_000L, result.get(0).getPrice());
        assertEquals("1", result.get(0).getGoodsId());
        assertEquals("account", result.get(0).getAccount_id());
        assertEquals("shop", result.get(0).getShop_id());
        assertEquals(0, result.get(0).getUnit());
    }
    @Test public void laterPageFailureDoesNotReturnPartialCatalog() {
        SdkGoodsSource source = source(client((method, args) -> (Integer) args[4] == 1
                ? page(1, 2, 2, goods("1", "001", 100))
                : new GoodsQueryResult(500, "second page failed", 0, 0, 2, 100, null)));
        assertEquals("second page failed", assertThrows(IllegalStateException.class, source::getAllGoods).getMessage());
    }
    @Test public void rejectsIncompleteOrDuplicateCatalog() {
        assertThrows(IllegalStateException.class, () -> source(client((method, args) ->
                page(1, 2, 1, goods("1", "001", 100)))).getAllGoods());
        assertThrows(IllegalStateException.class, () -> source(client((method, args) ->
                page((Integer) args[4], 2, 2, goods("1", "001", 100)))).getAllGoods());
    }
    @Test public void lookupPreservesLeadingZerosAndRequiresExactPlu() {
        SdkGoodsSource source = source(client((method, args) -> {
            assertEquals("001", args[2]);
            return page(1, 2, 1, goods("1", "001", 100), goods("2", "1001", 200));
        }));
        assertEquals("1", source.getGoodsByPlu("001").getGoodsId());
    }
    @Test public void emptySuccessIsMissingButFailureAndDisconnectAreNot() {
        assertNull(source(client((method, args) -> page(1, 0, 0))).getGoodsByPlu("001"));
        assertThrows(IllegalStateException.class, () -> source(client((method, args) -> null)).getGoodsByPlu("001"));
        HuoyanSdkClient disconnected = (HuoyanSdkClient) Proxy.newProxyInstance(HuoyanSdkClient.class.getClassLoader(),
                new Class<?>[]{HuoyanSdkClient.class}, (proxy, method, args) -> false);
        assertThrows(IllegalStateException.class, source(disconnected)::getAllGoods);
    }
    @Test public void changedBindingDiscardsQueryBeforePublishing() {
        AtomicLong generation = new AtomicLong();
        HuoyanSdkClient client = client((method, args) -> { generation.incrementAndGet(); return page(1, 0, 0); });
        SdkGoodsSource source = new SdkGoodsSource(() -> client, generation::get, String::valueOf);
        assertThrows(IllegalStateException.class, source::getAllGoods);
    }
    @Test public void replacedClientDiscardsQuery() {
        HuoyanSdkClient[] current = new HuoyanSdkClient[1];
        current[0] = client((method, args) -> { current[0] = null; return page(1, 0, 0); });
        SdkGoodsSource source = new SdkGoodsSource(() -> current[0], () -> 0, String::valueOf);
        assertThrows(IllegalStateException.class, source::getAllGoods);
    }
    @Test public void createFailureAndUnknownReceiptNeverSucceedOrRetry() {
        for (GoodsResult result : Arrays.asList(null, new GoodsResult(500, "failed", null), new GoodsResult(0, null, null))) {
            List<String> calls = new ArrayList<>();
            SdkGoodsSource source = source(client((method, args) -> {
                calls.add(method);
                if (method.equals("queryGoods")) return page(1, 0, 0);
                assertEquals("createGoods", method); return result;
            }));
            assertThrows(IllegalStateException.class, () -> source.save(SdkGoodsSource.fromSdk(goods("1", "001", 100)), false));
            assertEquals(Arrays.asList("queryGoods", "createGoods"), calls);
        }
    }
    @Test public void createAndUpdateUseSdkIdAndOnlyEditableFields() {
        Goods record = goods("987654321", "001", 4_000_000_000L);
        for (boolean editing : new boolean[]{false, true}) {
            SdkGoodsSource source = source(client((method, args) -> {
                if (method.equals("queryGoods")) return editing ? page(1, 1, 1, record) : page(1, 0, 0);
                assertEquals(editing ? "updateGoods" : "createGoods", method);
                if (editing) assertEquals("987654321", args[0]);
                GoodsSave save = (GoodsSave) args[editing ? 1 : 0];
                assertEquals(4_000_000_000L, save.getPrice()); assertEquals("001", save.getPlu());
                assertNull(save.getImage()); assertTrue(save.hasTareWeight()); assertEquals(record.getTareWeight(), save.getTareWeight());
                assertFalse(save.hasCategory()); assertFalse(save.hasStatus()); assertFalse(save.hasSort());
                return new GoodsResult(0, null, record);
            }));
            source.save(SdkGoodsSource.fromSdk(record), editing);
        }
    }
    @Test public void staleIdentityCannotUpdateOrDeleteCurrentShopGoods() {
        GoodsEntity old = SdkGoodsSource.fromSdk(goods("1", "001", 100));
        SdkGoodsSource source = source(client((method, args) -> {
            assertEquals("queryGoods", method); return page(1, 1, 1, goods("2", "001", 100));
        }));
        assertThrows(IllegalStateException.class, () -> source.save(old, true));
        assertThrows(IllegalStateException.class, () -> source.delete(old));
    }
    @Test public void deleteFailureIsReportedAndNeverRetried() {
        Goods record = goods("1", "001", 100);
        List<String> calls = new ArrayList<>();
        SdkGoodsSource source = source(client((method, args) -> {
            calls.add(method);
            if (method.equals("queryGoods")) return page(1, 1, 1, record);
            assertEquals("deleteGoods", method); assertEquals("1", args[0]); return new BaseResult(500, "delete failed");
        }));
        assertThrows(IllegalStateException.class, () -> source.delete(SdkGoodsSource.fromSdk(record)));
        assertEquals(Arrays.asList("queryGoods", "deleteGoods"), calls);
    }
    @Test public void countUsesSdkEveryTime() {
        AtomicLong value = new AtomicLong(12);
        SdkGoodsSource source = source(client((method, args) -> {
            assertEquals("getGoodsCount", method); assertEquals(0, args[3]);
            return new GoodsCountResult(0, null, value.getAndIncrement());
        }));
        assertEquals(12, source.count()); assertEquals(13, source.count());
    }
}
