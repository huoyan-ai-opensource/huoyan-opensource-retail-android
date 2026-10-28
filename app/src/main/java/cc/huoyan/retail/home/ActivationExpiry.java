package cc.huoyan.retail.home;

/** 激活码临期规则；只使用秒级时间戳，独立于 Android 与 SDK。 */
final class ActivationExpiry {
    /** 一天的秒数，按剩余时长而非日历日期计算。 */
    private static final long DAY_SECONDS = 24 * 60 * 60;
    /** 授权实际到期时间，单位秒。 */
    final long expiresAt;
    /** 展示天数；0 专用于不足一天，其他值向上取整。 */
    final int days;

    /** 固定一次展示所需的时间与天数。 */
    private ActivationExpiry(long expiresAt, int days) {
        this.expiresAt = expiresAt;
        this.days = days;
    }

    /** 有效且未到期的授权仅在最后 72 小时提醒；0/负值代表未知或永久。 */
    static ActivationExpiry check(Boolean activated, long expiresAt, long nowSeconds) {
        if (!Boolean.TRUE.equals(activated) || expiresAt <= 0 || nowSeconds < 0
                || expiresAt <= nowSeconds) return null;
        long remaining = expiresAt - nowSeconds;
        if (remaining > 3 * DAY_SECONDS) return null;
        int days = remaining < DAY_SECONDS ? 0 : (int) ((remaining + DAY_SECONDS - 1) / DAY_SECONDS);
        return new ActivationExpiry(expiresAt, days);
    }
}
