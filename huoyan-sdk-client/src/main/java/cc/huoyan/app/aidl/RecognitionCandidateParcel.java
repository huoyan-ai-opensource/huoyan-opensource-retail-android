package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 识别候选：同框尺寸加权比对中
 * 候选条目，列表可包含 winner；随 {@link RecognitionItemParcel} 追加字段透传（候选
 * clazz/distance/square/rank）。与 RecognitionItemParcel 同规：只携带 clazz
 * （无业务属性分类编码）；rank 可负（C 侧软加权公式产物）。
 */
public final class RecognitionCandidateParcel implements Parcelable {

    private final String clazz;
    private final double distance;
    private final double square;
    private final double rank;

    public RecognitionCandidateParcel(String clazz, double distance, double square, double rank) {
        this.clazz = clazz;
        this.distance = distance;
        this.square = square;
        this.rank = rank;
    }

    public String getClazz() {
        return clazz;
    }

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

    private RecognitionCandidateParcel(Parcel parcel) {
        // 对齐 RecognitionItemParcel：readString 单次消费 + null 回空串占位
        String readClazz = parcel.readString();
        clazz = readClazz == null ? "" : readClazz;
        distance = parcel.readDouble();
        square = parcel.readDouble();
        rank = parcel.readDouble();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(clazz);
        parcel.writeDouble(distance);
        parcel.writeDouble(square);
        parcel.writeDouble(rank);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecognitionCandidateParcel)) {
            return false;
        }
        RecognitionCandidateParcel that = (RecognitionCandidateParcel) other;
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
        return "RecognitionCandidateParcel(clazz=" + clazz + ", distance=" + distance
                + ", square=" + square + ", rank=" + rank + ")";
    }

    public static final Creator<RecognitionCandidateParcel> CREATOR = new Creator<RecognitionCandidateParcel>() {
        @Override
        public RecognitionCandidateParcel createFromParcel(Parcel parcel) {
            return new RecognitionCandidateParcel(parcel);
        }

        @Override
        public RecognitionCandidateParcel[] newArray(int size) {
            return new RecognitionCandidateParcel[size];
        }
    };
}
