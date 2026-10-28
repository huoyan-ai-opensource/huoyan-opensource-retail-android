package cc.huoyan.retail.recognition;

/** 主识别结果判定：无稳定称重时三次一致或三秒超时，有稳定称重时沿用两次确认。 */
public final class RecognitionResultGate {
    static final long TIMEOUT_MS = 3000L;
    private String lastKey;
    private int count;
    private long startedAt = -1L;

    /** 接纳本帧识别集合，判断是否达到锁定条件。 */
    public synchronized boolean accept(String key, long now, boolean weightStable) {
        if (key == null) {
            reset();
            return false;
        }
        if (startedAt < 0) startedAt = now;
        count = key.equals(lastKey) ? count + 1 : 1;
        lastKey = key;
        return count >= (weightStable ? 2 : 3) || (!weightStable && timedOut(now));
    }

    /** 判断本轮识别是否已达到等待时限。 */
    public synchronized boolean timedOut(long now) {
        return startedAt >= 0 && now - startedAt >= TIMEOUT_MS;
    }

    /** 返回本轮识别剩余的等待毫秒数。 */
    public synchronized long remaining(long now) {
        return startedAt < 0 ? TIMEOUT_MS : Math.max(0, TIMEOUT_MS - (now - startedAt));
    }

    /** 清空本轮识别的连续结果及计时。 */
    public synchronized void reset() {
        lastKey = null;
        count = 0;
        startedAt = -1L;
    }
}
