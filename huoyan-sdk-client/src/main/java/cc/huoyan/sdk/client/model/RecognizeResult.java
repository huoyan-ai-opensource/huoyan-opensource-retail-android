package cc.huoyan.sdk.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 识别请求回执：code=0 时 items 有效；非 0 时 reason 携带错误说明、items 为空
 * ——与 WS 通道 recognize 响应同语义（C4 两通道语义一致）。
 */
public final class RecognizeResult {

    private final int code;
    private final String reason;
    private final List<RecognitionItem> items;

    /** 构造数据对象。 */
    public RecognizeResult(int code, String reason, List<RecognitionItem> items) {
        this.code = code;
        this.reason = reason;
        this.items = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(items, "items 不能为空")));
    }

    /** 结果码；0 表示请求成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 结果条目列表。 */
    public List<RecognitionItem> getItems() {
        return items;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecognizeResult)) {
            return false;
        }
        RecognizeResult that = (RecognizeResult) other;
        return code == that.code && Objects.equals(reason, that.reason) && items.equals(that.items);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        return 31 * result + items.hashCode();
    }

    @Override
    public String toString() {
        return "RecognizeResult(code=" + code + ", reason=" + reason + ", items=" + items + ")";
    }

}
