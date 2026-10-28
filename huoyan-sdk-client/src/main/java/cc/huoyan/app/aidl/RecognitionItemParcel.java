package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

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
 * <p>线格式以 {@link #PARCEL_VERSION} 版本头起始，随后写入全部字段。
 * 后续格式演进通过版本头明确分发，避免多条目结果发生字段错位。</p>
 */
public final class RecognitionItemParcel implements Parcelable {

    /**
     * 当前线格式版本头。取负值，为后续格式演进保留清晰的判版空间。
     */
    public static final int PARCEL_VERSION = -2;

    private final String clazz;
    private final double distance;
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;
    private final double square;
    private final double rank;
    private final List<RecognitionCandidateParcel> candidates;

    public RecognitionItemParcel(String clazz, double distance, int left, int top, int right, int bottom) {
        this(clazz, distance, left, top, right, bottom, 0.0, 0.0,
                Collections.<RecognitionCandidateParcel>emptyList());
    }

    public RecognitionItemParcel(String clazz, double distance, int left, int top, int right,
            int bottom, double square, double rank, List<RecognitionCandidateParcel> candidates) {
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

    public String getClazz() {
        return clazz;
    }

    public double getDistance() {
        return distance;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

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
    public List<RecognitionCandidateParcel> getCandidates() {
        return candidates;
    }

    private RecognitionItemParcel(Parcel parcel) {
        int version = parcel.readInt();
        if (version != PARCEL_VERSION) {
            throw new IllegalArgumentException("不支持的 RecognitionItemParcel 版本：" + version);
        }
        String readClazz = parcel.readString();
        clazz = readClazz == null ? "" : readClazz;
        distance = parcel.readDouble();
        left = parcel.readInt();
        top = parcel.readInt();
        right = parcel.readInt();
        bottom = parcel.readInt();
        square = parcel.readDouble();
        rank = parcel.readDouble();
        List<RecognitionCandidateParcel> read =
                parcel.createTypedArrayList(RecognitionCandidateParcel.CREATOR);
        // 反序列化结果包不可变视图，保证 IPC 前后行为一致。
        candidates = read == null ? Collections.<RecognitionCandidateParcel>emptyList()
                : Collections.unmodifiableList(read);
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(PARCEL_VERSION);
        parcel.writeString(clazz);
        parcel.writeDouble(distance);
        parcel.writeInt(left);
        parcel.writeInt(top);
        parcel.writeInt(right);
        parcel.writeInt(bottom);
        parcel.writeDouble(square);
        parcel.writeDouble(rank);
        parcel.writeTypedList(candidates);
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
        if (!(other instanceof RecognitionItemParcel)) {
            return false;
        }
        RecognitionItemParcel that = (RecognitionItemParcel) other;
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
        return "RecognitionItemParcel(clazz=" + clazz + ", distance=" + distance
                + ", left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom
                + ", square=" + square + ", rank=" + rank + ", candidates=" + candidates + ")";
    }

    public static final Creator<RecognitionItemParcel> CREATOR = new Creator<RecognitionItemParcel>() {
        @Override
        public RecognitionItemParcel createFromParcel(Parcel parcel) {
            return new RecognitionItemParcel(parcel);
        }

        @Override
        public RecognitionItemParcel[] newArray(int size) {
            return new RecognitionItemParcel[size];
        }
    };
}
