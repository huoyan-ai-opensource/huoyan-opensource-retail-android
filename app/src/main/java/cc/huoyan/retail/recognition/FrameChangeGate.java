package cc.huoyan.retail.recognition;

/** 以相邻帧变化启动一轮识别；启动后持续放行新帧，直至会话重置。 */
public final class FrameChangeGate {
    /** 上一张已比较的图像哈希，首次只建立基线。 */
    private String previous;

    /** 本轮是否已由画面变化触发，与基线一起由实例锁保护。 */
    private boolean triggered;

    /** 首次超过阈值启动本轮，之后静止的新帧也参与结果确认。 */
    public synchronized boolean accept(String hash, int threshold) {
        String before = previous;
        previous = hash;
        if (before == null || hash == null || before.length() != hash.length()) {
            triggered = false;
            return false;
        }
        int distance = 0;
        for (int i = 0; i < hash.length(); i++) {
            int left = Character.digit(before.charAt(i), 16);
            int right = Character.digit(hash.charAt(i), 16);
            if (left < 0 || right < 0) {
                triggered = false;
                return false;
            }
            distance += Integer.bitCount(left ^ right);
        }
        triggered |= distance > threshold;
        return triggered;
    }

    /** 开关只在称重模块关闭时参与自动识别。 */
    public static boolean enabled(boolean stableRecognition, boolean weighingEnabled) {
        return stableRecognition && !weighingEnabled;
    }

    /** 清除跨会话基线，下一帧重新建立比较起点。 */
    public synchronized void reset() {
        previous = null;
        triggered = false;
    }
}
