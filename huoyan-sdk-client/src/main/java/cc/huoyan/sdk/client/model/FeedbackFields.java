package cc.huoyan.sdk.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 反馈字段的公共校验与防御性复制。 */
final class FeedbackFields {
    /** 禁止实例化字段工具。 */
    private FeedbackFields() { }
    /** 校验可选文本长度。 */
    static String text(String value, int max, String field) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) throw new IllegalArgumentException(field + " 超长");
        return text;
    }
    /** 校验非负有限坐标。 */
    static Double geometry(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0))
            throw new IllegalArgumentException(field + " 必须为非负有限数");
        return value;
    }
    /** 限制列表数量并复制为不可变快照。 */
    static <T> List<T> list(List<T> values, String field) {
        if (values == null) return Collections.emptyList();
        if (values.size() > 50) throw new IllegalArgumentException(field + " 最多 50 项");
        List<T> copy = new ArrayList<>(values);
        if (copy.contains(null)) throw new IllegalArgumentException(field + " 不能含空项");
        return Collections.unmodifiableList(copy);
    }
}
