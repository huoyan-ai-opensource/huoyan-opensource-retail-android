package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 识别候选：同框尺寸加权比对中
 * 候选条目，列表可包含 winner；随 {@link RecognitionItem} 追加字段透传（候选
 * clazz/distance/square/rank）。与 RecognitionItem 同规：只携带 clazz
 * （无业务属性分类编码）；rank 可负（C 侧软加权公式产物）。
 */
public final class RecognitionCandidate {

    private final String clazz;
    private final double distance;
    private final double square;
    private final double rank;

    /** 构造数据对象。 */
    public RecognitionCandidate(String clazz, double distance, double square, double rank) {
        this.clazz = clazz;
        this.distance = distance;
        this.square = square;
        this.rank = rank;
    }

    /** 分类编码；由业务应用映射商品。 */
    public String getClazz() {
        return clazz;
    }

    /** 识别距离，数值语义由 SDK 定义，不是置信度。 */
    public double getDistance() {
        return distance;
    }

    /** 候选样本面积（尺寸加权重放字段，缺省 0）。 */
    public double getSquare() {
        return square;
    }

    /** 候选加权分（尺寸加权重放字段，缺省 0；可负——C 侧软加权公式产物）。 */
    public double getRank() {
        return rank;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecognitionCandidate)) {
            return false;
        }
        RecognitionCandidate that = (RecognitionCandidate) other;
        return Double.compare(distance, that.distance) == 0
                && Double.compare(square, that.square) == 0
                && Double.compare(rank, that.rank) == 0
                && clazz.equals(that.clazz);
    }

    @Override
    public int hashCode() {
        int result = clazz.hashCode();
        result = 31 * result + Double.hashCode(distance);
        result = 31 * result + Double.hashCode(square);
        return 31 * result + Double.hashCode(rank);
    }

    @Override
    public String toString() {
        return "RecognitionCandidate(clazz=" + clazz + ", distance=" + distance
                + ", square=" + square + ", rank=" + rank + ")";
    }

}
