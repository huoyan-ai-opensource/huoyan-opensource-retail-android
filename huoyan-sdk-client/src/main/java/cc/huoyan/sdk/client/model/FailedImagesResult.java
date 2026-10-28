package cc.huoyan.sdk.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 失败图片查询回执：只有 code=0 的空列表才表示没有失败记录。 */
public final class FailedImagesResult {
    private final int code;
    private final String reason;
    private final List<FailedImage> items;

    /** 构造失败图片查询回执。 */
    public FailedImagesResult(int code, String reason, List<FailedImage> items) {
        this.code = code;
        this.reason = reason;
        this.items = Collections.unmodifiableList(new ArrayList<>(
                items == null ? Collections.emptyList() : items));
    }

    /** 取得回执码，0 表示成功。 */
    public int getCode() { return code; }
    /** 取得错误说明。 */
    public String getReason() { return reason; }
    /** 取得失败图片记录。 */
    public List<FailedImage> getItems() { return items; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FailedImagesResult)) return false;
        FailedImagesResult that = (FailedImagesResult) other;
        return code == that.code && Objects.equals(reason, that.reason)
                && Objects.equals(items, that.items);
    }

    @Override public int hashCode() { return Objects.hash(code, reason, items); }

}
