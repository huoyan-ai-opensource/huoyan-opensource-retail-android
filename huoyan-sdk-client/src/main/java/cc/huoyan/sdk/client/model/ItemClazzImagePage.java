package cc.huoyan.sdk.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 类别下样本图片分页回执（）：{@code getItemClazzImages} 的载荷——code/
 * reason 信封（0 = 成功）+ 分页元数据（total/current/size/pages）+ 当前页样本图片
 * 记录（items，按 uid 升序）。空 clazz/翻页过头为空页（正常结果非错误）。
 */
public final class ItemClazzImagePage {

    private final int code;
    private final String reason;
    private final long total;
    private final int current;
    private final int size;
    private final int pages;
    private final List<ItemClazzImage> items;

    /** 构造图片分页回执。 */
    public ItemClazzImagePage(int code, String reason, long total, int current,
            int size, int pages, List<ItemClazzImage> items) {
        this.code = code;
        this.reason = reason;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = pages;
        this.items = items == null
                ? Collections.<ItemClazzImage>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
    }

    /** 取得回执码，0 表示成功。 */
    public int getCode() {
        return code;
    }

    /** 取得错误说明。 */
    public String getReason() {
        return reason;
    }

    /** 取得记录总数。 */
    public long getTotal() {
        return total;
    }

    /** 取得当前页码。 */
    public int getCurrent() {
        return current;
    }

    /** 取得页大小。 */
    public int getSize() {
        return size;
    }

    /** 取得总页数。 */
    public int getPages() {
        return pages;
    }

    /** 取得当前页图片记录。 */
    public List<ItemClazzImage> getItems() {
        return items;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemClazzImagePage)) {
            return false;
        }
        ItemClazzImagePage that = (ItemClazzImagePage) other;
        return code == that.code
                && total == that.total
                && current == that.current
                && size == that.size
                && pages == that.pages
                && Objects.equals(reason, that.reason)
                && Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, total, current, size, pages, items);
    }

    @Override
    public String toString() {
        return "ItemClazzImagePage(code=" + code + ", reason=" + reason
                + ", total=" + total + ", current=" + current
                + ", size=" + size + ", pages=" + pages + ", items=" + items + ")";
    }

}
