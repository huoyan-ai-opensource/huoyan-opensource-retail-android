package cc.huoyan.sdk.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单个识别结果：只携带 clazz（分类编码，无业务属性）、
 * 距离与画布绝对坐标检测框；PLU/商品业务属性只能存在于业务 App。
 *
 * <p>尺寸加权比对字段接入——
 * square 为 winner 样本面积、rank 为 winner 加权分（可负：C 侧软加权公式
 * 产物）、candidates 为同框候选（可包含 winner，也可为空）。</p>
 *
 */
public final class RecognitionItem {

    /**
     * 当前线格式版本头。取负值，为后续格式演进保留清晰的判版空间。
     */

    private final String clazz;
    private final double distance;
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;
    private final double square;
    private final double rank;
    private final List<RecognitionCandidate> candidates;

    /** 构造数据对象。 */
    public RecognitionItem(String clazz, double distance, int left, int top, int right, int bottom) {
        this(clazz, distance, left, top, right, bottom, 0.0, 0.0,
                Collections.<RecognitionCandidate>emptyList());
    }

    /** 构造数据对象。 */
    public RecognitionItem(String clazz, double distance, int left, int top, int right,
            int bottom, double square, double rank, List<RecognitionCandidate> candidates) {
        this.clazz = clazz;
        this.distance = distance;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.square = square;
        this.rank = rank;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(candidates, "candidates 不能为空")));
    }

    /** 分类编码；由业务应用映射商品。 */
    public String getClazz() {
        return clazz;
    }

    /** 识别距离，数值语义由 SDK 定义，不是置信度。 */
    public double getDistance() {
        return distance;
    }

    /** 框左侧像素坐标。 */
    public int getLeft() {
        return left;
    }

    /** 框顶部像素坐标。 */
    public int getTop() {
        return top;
    }

    /** 框右侧像素坐标。 */
    public int getRight() {
        return right;
    }

    /** 框底部像素坐标。 */
    public int getBottom() {
        return bottom;
    }

    /** winner 样本面积（尺寸加权重放字段，缺省 0）。 */
    public double getSquare() {
        return square;
    }

    /** winner 加权分（尺寸加权重放字段，缺省 0；可负——C 侧软加权公式产物）。 */
    public double getRank() {
        return rank;
    }

    /** 不可变的同框候选列表，可包含命中项；空库/无候选路径可为空。 */
    public List<RecognitionCandidate> getCandidates() {
        return candidates;
    }

    /** 返回有效轴对齐框坐标副本；无面积时返回 null。 */
    public float[] getPoints() {
        float[] points = new float[]{left, top, right, bottom};
        float left = Math.min(points[0], points[2]), right = Math.max(points[0], points[2]);
        float top = Math.min(points[1], points[3]), bottom = Math.max(points[1], points[3]);
        return right <= left || bottom <= top ? null : new float[]{left, top, right, bottom};
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecognitionItem)) {
            return false;
        }
        RecognitionItem that = (RecognitionItem) other;
        return Double.compare(distance, that.distance) == 0
                && Double.compare(square, that.square) == 0
                && Double.compare(rank, that.rank) == 0
                && clazz.equals(that.clazz)
                && left == that.left
                && top == that.top
                && right == that.right
                && bottom == that.bottom
                && candidates.equals(that.candidates);
    }

    @Override
    public int hashCode() {
        int result = clazz.hashCode();
        result = 31 * result + Double.hashCode(distance);
        result = 31 * result + left;
        result = 31 * result + top;
        result = 31 * result + right;
        result = 31 * result + bottom;
        result = 31 * result + Double.hashCode(square);
        result = 31 * result + Double.hashCode(rank);
        return 31 * result + candidates.hashCode();
    }

    @Override
    public String toString() {
        return "RecognitionItem(clazz=" + clazz + ", distance=" + distance
                + ", left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom
                + ", square=" + square + ", rank=" + rank + ", candidates=" + candidates + ")";
    }

}
