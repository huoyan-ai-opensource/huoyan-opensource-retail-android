package cc.huoyan.sdk.client.internal.ws;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WS v2 在途请求路由表：requestId → 等待响应的 future。
 * 发送线程注册、WS 读线程按响应 requestId 路由回 completing；超时/断线时
 * 主动出表作废（迟到的响应 complete 不上即静默丢弃）。
 *
 * <p>requestId 唯一性由发送方保证（会话纪元 + 单调序号，不复用）；本表只做
 * 路由与生命周期，{@link ConcurrentHashMap} 原子原语保证跨线程安全。纯 Java
 * 可在 JVM 单测直接覆盖。
 */
public final class WsPendingTable {

    private final ConcurrentHashMap<String, CompletableFuture<WsV2Codec.Response>> pending =
            new ConcurrentHashMap<>();

    /**
     * 登记一条在途请求。
     *
     * @param requestId 连接内唯一请求标识
     * @return 响应 future（由 WS 读线程 complete / 断线时 exceptionally）
     */
    public CompletableFuture<WsV2Codec.Response> register(String requestId) {
        CompletableFuture<WsV2Codec.Response> future = new CompletableFuture<>();
        pending.put(requestId, future);
        return future;
    }

    /**
     * 响应路由：按 requestId 出表并完成对应 future。
     *
     * @param requestId 响应归属的请求标识
     * @param response  服务端响应
     * @return true=命中在途请求并完成；false=未知/已超时/已断线作废
     */
    public boolean complete(String requestId, WsV2Codec.Response response) {
        CompletableFuture<WsV2Codec.Response> future = pending.remove(requestId);
        return future != null && future.complete(response);
    }

    /**
     * 出表作废（请求超时路径）：迟到的响应不再路由。
     *
     * @param requestId 请求标识
     * @return true=命中并作废；false=已被响应完成或断线清理
     */
    public boolean remove(String requestId) {
        return pending.remove(requestId) != null;
    }

    /**
     * 连接断开：全部在途请求以异常结算（等待线程立即感知，映射为本地未连接）。
     *
     * @param reason 断开原因（进异常消息）
     * @return 作废的在途请求数
     */
    public int failAll(String reason) {
        int failed = 0;
        for (Map.Entry<String, CompletableFuture<WsV2Codec.Response>> entry : pending.entrySet()) {
            if (pending.remove(entry.getKey()) != null) {
                entry.getValue().completeExceptionally(
                        new IllegalStateException(reason == null ? "连接断开" : reason));
                failed++;
            }
        }
        return failed;
    }

    /** 在途请求数（诊断/测试断言）。 */
    public int size() {
        return pending.size();
    }
}
