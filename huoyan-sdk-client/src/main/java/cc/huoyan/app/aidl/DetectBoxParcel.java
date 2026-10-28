package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 单个检测框：{@link DetectResultParcel} 的元素——画布绝对坐标、
 * 左上为原点，x/y 为框左上角，w/h 为框宽高（与 native 归一化 x,y,w,h 乘帧宽高
 * 取整的口径一致），score 为框置信度（后端无置信度时为 0）。不可变值对象。
 */
public final class DetectBoxParcel implements Parcelable {

    private final int x;
    private final int y;
    private final int w;
    private final int h;
    private final double score;

    public DetectBoxParcel(int x, int y, int w, int h, double score) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.score = score;
    }

    /** 框左上角 x（画布绝对坐标）。 */
    public int getX() {
        return x;
    }

    /** 框左上角 y（画布绝对坐标）。 */
    public int getY() {
        return y;
    }

    /** 框宽（像素）。 */
    public int getW() {
        return w;
    }

    /** 框高（像素）。 */
    public int getH() {
        return h;
    }

    /** 框置信度；后端无置信度输出时为 0。 */
    public double getScore() {
        return score;
    }

    private DetectBoxParcel(Parcel parcel) {
        x = parcel.readInt();
        y = parcel.readInt();
        w = parcel.readInt();
        h = parcel.readInt();
        score = parcel.readDouble();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(x);
        parcel.writeInt(y);
        parcel.writeInt(w);
        parcel.writeInt(h);
        parcel.writeDouble(score);
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
        if (!(other instanceof DetectBoxParcel)) {
            return false;
        }
        DetectBoxParcel that = (DetectBoxParcel) other;
        return x == that.x && y == that.y && w == that.w && h == that.h
                && Double.compare(score, that.score) == 0;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + w;
        result = 31 * result + h;
        return 31 * result + Double.hashCode(score);
    }

    @Override
    public String toString() {
        return "DetectBoxParcel(x=" + x + ", y=" + y + ", w=" + w + ", h=" + h + ", score=" + score + ")";
    }

    public static final Creator<DetectBoxParcel> CREATOR = new Creator<DetectBoxParcel>() {
        @Override
        public DetectBoxParcel createFromParcel(Parcel parcel) {
            return new DetectBoxParcel(parcel);
        }

        @Override
        public DetectBoxParcel[] newArray(int size) {
            return new DetectBoxParcel[size];
        }
    };
}
