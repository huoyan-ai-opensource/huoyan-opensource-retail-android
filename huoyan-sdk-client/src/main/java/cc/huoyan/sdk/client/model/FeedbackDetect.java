package cc.huoyan.sdk.client.model;

import java.util.Objects;

/** 单个识别候选的不可变快照。 */
public final class FeedbackDetect {
    /** 商品 PLU 原值。 */
    private final String clazz;
    /** 商品名称快照。 */
    private final String name;
    /** 识别距离，保留三位小数。 */
    private final Double distance;

    /** 校验并固定反馈数据，提交线程可安全读取快照。 */
    public FeedbackDetect(String clazz, String name, Double distance) {
        this.clazz = FeedbackFields.text(clazz, 64, "clazz");
        this.name = FeedbackFields.text(name, 100, "name");
        this.distance = normalizeDistance(distance);
    }
    /** 返回商品 PLU 原值。 */
    public String getClazz() { return clazz; }
    /** 返回商品名称快照。 */
    public String getName() { return name; }
    /** 返回保留三位小数的距离。 */
    public Double getDistance() { return distance; }
    /** 限制服务端距离精度与取值范围。 */
    private static double normalizeDistance(Double value) {
        double distance = value == null ? 0 : value;
        if (!Double.isFinite(distance) || distance < 0 || distance > 9999.999)
            throw new IllegalArgumentException("distance 必须在 0..9999.999 之间");
        return Math.round(distance * 1000D) / 1000D;
    }
    /** 按反馈字段比较快照。 */
    @Override public boolean equals(Object other) {
        if (!(other instanceof FeedbackDetect)) return false;
        FeedbackDetect that = (FeedbackDetect) other;
        return Objects.equals(clazz, that.clazz) && Objects.equals(name, that.name) && Objects.equals(distance, that.distance);
    }
    /** 返回与字段相等规则一致的哈希。 */
    @Override public int hashCode() { return Objects.hash(clazz, name, distance); }
}
