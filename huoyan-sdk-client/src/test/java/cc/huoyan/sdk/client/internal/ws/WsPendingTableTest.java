package cc.huoyan.sdk.client.internal.ws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * {@link WsPendingTable} requestId 路由表单测：登记/路由/
 * 超时出表/断线批量作废的完整生命周期。
 */
public class WsPendingTableTest {

    @Test
    public void 登记后按requestId路由响应() throws Exception {
        WsPendingTable table = new WsPendingTable();
        CompletableFuture<WsV2Codec.Response> future = table.register("r-1");

        WsV2Codec.Response response =
                new WsV2Codec.Response("r-1", 0, null, null);
        assertTrue(table.complete("r-1", response));
        assertTrue(future.isDone());
        assertSame(response, future.get(1, TimeUnit.SECONDS));
    }

    @Test
    public void 不同requestId互不串扰() throws Exception {
        WsPendingTable table = new WsPendingTable();
        CompletableFuture<WsV2Codec.Response> first = table.register("r-1");
        CompletableFuture<WsV2Codec.Response> second = table.register("r-2");

        WsV2Codec.Response forSecond =
                new WsV2Codec.Response("r-2", 0, null, null);
        assertTrue(table.complete("r-2", forSecond));

        assertTrue(second.isDone());
        assertFalse(first.isDone());
        assertEquals(1, table.size());
    }

    @Test
    public void 未知requestId路由返回false() {
        WsPendingTable table = new WsPendingTable();
        assertFalse(table.complete("unknown",
                new WsV2Codec.Response("unknown", 0, null, null)));
    }

    @Test
    public void 已完成请求再收迟到响应不再路由() throws Exception {
        WsPendingTable table = new WsPendingTable();
        CompletableFuture<WsV2Codec.Response> future = table.register("r-1");
        table.complete("r-1", new WsV2Codec.Response("r-1", 0, null, null));

        // 迟到响应：表已出键，complete 不上（静默丢弃）
        assertFalse(table.complete("r-1",
                new WsV2Codec.Response("r-1", -1, "迟到", null)));
        assertEquals(0, table.size());
    }

    @Test
    public void 超时出表后响应不再送达() {
        WsPendingTable table = new WsPendingTable();
        table.register("r-1");

        assertTrue(table.remove("r-1"));
        assertFalse(table.complete("r-1",
                new WsV2Codec.Response("r-1", 0, null, null)));
    }

    @Test
    public void 断线批量作废_全部以异常结算() {
        WsPendingTable table = new WsPendingTable();
        CompletableFuture<WsV2Codec.Response> first = table.register("r-1");
        CompletableFuture<WsV2Codec.Response> second = table.register("r-2");
        CompletableFuture<WsV2Codec.Response> third = table.register("r-3");

        int failed = table.failAll("连接关闭 code=1006");

        assertEquals(3, failed);
        assertEquals(0, table.size());
        assertTrue(first.isCompletedExceptionally());
        assertTrue(second.isCompletedExceptionally());
        assertTrue(third.isCompletedExceptionally());
        try {
            first.get(1, TimeUnit.SECONDS);
            throw new AssertionError("应已异常结算");
        } catch (ExecutionException e) {
            assertEquals("连接关闭 code=1006", e.getCause().getMessage());
        } catch (Exception e) {
            throw new AssertionError("unexpected: " + e);
        }
    }

    @Test
    public void 空表failAll返回0() {
        assertEquals(0, new WsPendingTable().failAll(null));
    }

    private static void assertSame(Object expected, Object actual) {
        assertTrue("应路由回同一响应对象", expected == actual);
    }
}
