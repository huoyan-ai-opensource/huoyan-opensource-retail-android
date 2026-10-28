package cc.huoyan.sdk.client.model;

import java.util.Objects;

/** 目标最终选择的不可变快照。 */
public final class FeedbackResult {
    /** 商品 PLU 原值。 */
    private final String clazz;
    /** 商品名称快照。 */
    private final String name;
    /** 最终选择来源。 */
    private final String source;

    /** 校验并固定反馈数据，提交线程可安全读取快照。 */
    public FeedbackResult(String clazz, String name, String source) {
        this.clazz = FeedbackFields.text(clazz, 64, "clazz");
        this.name = FeedbackFields.text(name, 100, "name");
        this.source = FeedbackFields.text(source, 32, "source");
    }
    /** 返回商品 PLU 原值。 */
    public String getClazz() { return clazz; }
    /** 返回商品名称快照。 */
    public String getName() { return name; }
    /** 返回最终选择来源。 */
    public String getSource() { return source; }
    /** 按反馈字段比较快照。 */
    @Override public boolean equals(Object other) {
        if (!(other instanceof FeedbackResult)) return false;
        FeedbackResult that = (FeedbackResult) other;
        return Objects.equals(clazz, that.clazz) && Objects.equals(name, that.name) && Objects.equals(source, that.source);
    }
    /** 返回与字段相等规则一致的哈希。 */
    @Override public int hashCode() { return Objects.hash(clazz, name, source); }
}
