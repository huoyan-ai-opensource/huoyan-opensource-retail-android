package cc.huoyan.sdk.client.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * 已学类别列表回执（扩展分页元数据 + 缩略图）：{@code
 * listClasses(page, size)} 的载荷——code=0 时 clazz 与 uidCount 同长度、
 * 下标一一对应（对齐桥接层 {@code cc.huoyan.bridge.ClazzPage} 的 C 侧
 * DISTINCT+COUNT 聚合语义）；空库为 0 条空列表（正常结果非错误）。非 0 时
 * reason 携带错误说明、数组为空。
 *
 * <p>分页元数据：{@code total}（类别总数 class_count）、{@code current}
 * （当前页 1-based）、{@code size}（页大小）、{@code pages}（总页数）；{@code
 * thumbnails} 与 clazz 下标一一对应（每类一个缩略图 URL，阶段三图片服务填充，
 * 暂无 null）。旧四参构造器保留向后兼容（分页元数据 0、缩略图 null）。</p>
 */
public final class ItemClazzList {

    private final int code;
    private final String reason;
    private final String[] clazz;
    private final long[] uidCount;
    private final long total;
    private final int current;
    private final int size;
    private final int pages;
    private final String[] thumbnails;

    /** 旧四参构造器（形态，向后兼容）：无分页元数据（total/current/
     * size/pages 均 0）、缩略图 null。 */
    public ItemClazzList(int code, String reason, String[] clazz, long[] uidCount) {
        this(code, reason, clazz, uidCount, 0L, 0, 0, 0, null);
    }

    /** 全参构造器（）：分页元数据 + 缩略图。 */
    public ItemClazzList(int code, String reason, String[] clazz, long[] uidCount,
            long total, int current, int size, int pages, String[] thumbnails) {
        this.code = code;
        this.reason = reason;
        this.clazz = (clazz == null ? new String[0] : clazz.clone());
        this.uidCount = (uidCount == null ? new long[0] : uidCount.clone());
        if (this.clazz.length != this.uidCount.length) {
            throw new IllegalArgumentException(
                    "clazz 与 uidCount 必须同长度（下标一一对应，当前："
                            + this.clazz.length + " vs " + this.uidCount.length + "）");
        }
        if (thumbnails != null && thumbnails.length != this.clazz.length) {
            throw new IllegalArgumentException(
                    "thumbnails 非空时必须与 clazz 同长度（当前："
                            + thumbnails.length + " vs " + this.clazz.length + "）");
        }
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = pages;
        this.thumbnails = thumbnails == null ? null : thumbnails.clone();
    }

    /** 结果码；0 表示请求成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 类别编码数组（防御性拷贝；code=0 时与 {@link #getUidCount()} 下标对齐）。 */
    public String[] getClazz() {
        return clazz.clone();
    }

    /** 各类别下向量条数数组（防御性拷贝）。 */
    public long[] getUidCount() {
        return uidCount.clone();
    }

    /** 类别总数（class_count；0=空库/未接线分页）。 */
    public long getTotal() {
        return total;
    }

    /** 当前页（1-based；0=未指定分页）。 */
    public int getCurrent() {
        return current;
    }

    /** 页大小（0=未指定分页）。 */
    public int getSize() {
        return size;
    }

    /** 总页数（0=未指定分页/空库）。 */
    public int getPages() {
        return pages;
    }

    /** 各类别缩略图 URL 数组（防御性拷贝；无缩略图为空数组）。 */
    public String[] getThumbnails() {
        return thumbnails == null ? new String[0] : thumbnails.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemClazzList)) {
            return false;
        }
        ItemClazzList that = (ItemClazzList) other;
        return code == that.code
                && Objects.equals(reason, that.reason)
                && Arrays.equals(clazz, that.clazz)
                && Arrays.equals(uidCount, that.uidCount);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        result = 31 * result + Arrays.hashCode(clazz);
        return 31 * result + Arrays.hashCode(uidCount);
    }

    @Override
    public String toString() {
        return "ItemClazzList(code=" + code + ", reason=" + reason
                + ", clazz=" + Arrays.toString(clazz) + ", uidCount=" + Arrays.toString(uidCount)
                + ", total=" + total + ", current=" + current
                + ", size=" + size + ", pages=" + pages + ")";
    }

}
