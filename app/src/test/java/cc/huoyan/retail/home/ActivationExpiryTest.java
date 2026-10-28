package cc.huoyan.retail.home;

import org.junit.Test;
import static org.junit.Assert.*;

/** 验证按秒计算的临期边界，防止过早提醒或到期后出现负数。 */
public class ActivationExpiryTest {
    /** 固定当前时间，避免测试依赖设备日期。 */
    private static final long NOW = 1_800_000_000L;

    /** 临界点含 72 小时，天数向上取整，不足一天单独显示。 */
    @Test public void countdownBoundaries() {
        assertNull(ActivationExpiry.check(true, NOW + 259201, NOW));
        long[] remaining = {259200, 172801, 172800, 86401, 86400, 86399, 1};
        int[] expected = {3, 3, 2, 2, 1, 0, 0};
        for (int i = 0; i < remaining.length; i++) {
            ActivationExpiry expiry = ActivationExpiry.check(true, NOW + remaining[i], NOW);
            assertNotNull(expiry);
            assertEquals(expected[i], expiry.days);
            assertEquals(NOW + remaining[i], expiry.expiresAt);
        }
    }

    /** 未知、永久、失效、已过期与异常时间不提醒。 */
    @Test public void ignoresUnavailableAndExpiredLicenses() {
        assertNull(ActivationExpiry.check(null, NOW + 1, NOW));
        assertNull(ActivationExpiry.check(false, NOW + 1, NOW));
        for (long expires : new long[]{0, -1, NOW - 1, NOW, Long.MAX_VALUE})
            assertNull(ActivationExpiry.check(true, expires, NOW));
        assertNull(ActivationExpiry.check(true, NOW, -1));
    }
}
