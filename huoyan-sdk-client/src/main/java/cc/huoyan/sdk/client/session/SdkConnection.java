package cc.huoyan.sdk.client.session;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
/** 可取消的连接等待，不提交或重放业务请求。 */
public final class SdkConnection {
    /** 静态连接工具。 */
    private SdkConnection() {}
    /** 后台等待连接；超时、取消或中断返回 false，保留线程中断标记。 */
    public static boolean await(HuoyanSdkClient client, long timeoutMillis, long pollMillis, BooleanSupplier active) {
        if (timeoutMillis < 0 || pollMillis <= 0) throw new IllegalArgumentException("等待参数无效");
        if (!active.getAsBoolean()) return false;
        client.connect();
        long start = System.nanoTime();
        while (active.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
            if (client.isConnected()) return true;
            long remaining = timeoutMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (remaining <= 0) return false;
            try { Thread.sleep(Math.min(remaining, pollMillis)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }
}
