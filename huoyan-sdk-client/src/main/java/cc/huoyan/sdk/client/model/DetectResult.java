package cc.huoyan.sdk.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 纯检测回执（）：{@code detect()} 的载荷——code=0 时 boxes 为画框
 * 列表（无目标为空列表，属正常结果）；非 0 时 reason 携带错误说明、boxes 为空。
 */
public final class DetectResult {

    private final int code;
    private final String reason;
    private final List<DetectBox> boxes;

    /** 构造数据对象。 */
    public DetectResult(int code, String reason, List<DetectBox> boxes) {
        this.code = code;
        this.reason = reason;
        this.boxes = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(boxes, "boxes 不能为空")));
    }

    /** 结果码；0 表示请求成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 检测框列表（code=0 时有效；无目标为空列表）。 */
    public List<DetectBox> getBoxes() {
        return boxes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DetectResult)) {
            return false;
        }
        DetectResult that = (DetectResult) other;
        return code == that.code && Objects.equals(reason, that.reason) && boxes.equals(that.boxes);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        return 31 * result + boxes.hashCode();
    }

    @Override
    public String toString() {
        return "DetectResult(code=" + code + ", reason=" + reason + ", boxes=" + boxes + ")";
    }

}
