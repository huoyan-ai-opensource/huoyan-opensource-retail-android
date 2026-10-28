package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 批量学习单框结果：{@link ItemLearnBatchParcel} 的元素——框几何
 * （x/y/w/h/score，detect 原始像素坐标口径）与该框学习回执（uid/code/reason）
 * 的扁平合体记录。code=0 时 uid 为该框新学习条目句柄（0=裁片无检测条目的正常
 * 结果，对齐单条 itemLearn 的 uid=0 语义）；code≠0 时该框学习失败（reason 携带
 * 原因——裁切无效/引擎错误等），独立于其他框。不可变值对象。
 */
public final class ItemLearnBoxParcel implements Parcelable {

    private final int x;
    private final int y;
    private final int w;
    private final int h;
    private final double score;
    private final long uid;
    private final int code;
    private final String reason;

    public ItemLearnBoxParcel(int x, int y, int w, int h, double score, long uid, int code, String reason) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.score = score;
        this.uid = uid;
        this.code = code;
        this.reason = reason;
    }

    /** 框左上角 x（画布绝对坐标，detect 原始口径）。 */
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

    /** 框置信度（detect 透传；无置信度输出的路径为 0）。 */
    public double getScore() {
        return score;
    }

    /** 该框学习入库的新 UID（code=0 时有效；0=裁片无检测条目）。 */
    public long getUid() {
        return uid;
    }

    /** 该框学习结果码（0=成功；非 0=该框失败，独立于其他框）。 */
    public int getCode() {
        return code;
    }

    /** 该框失败说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    private ItemLearnBoxParcel(Parcel parcel) {
        x = parcel.readInt();
        y = parcel.readInt();
        w = parcel.readInt();
        h = parcel.readInt();
        score = parcel.readDouble();
        uid = parcel.readLong();
        code = parcel.readInt();
        reason = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(x);
        parcel.writeInt(y);
        parcel.writeInt(w);
        parcel.writeInt(h);
        parcel.writeDouble(score);
        parcel.writeLong(uid);
        parcel.writeInt(code);
        parcel.writeString(reason);
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
        if (!(other instanceof ItemLearnBoxParcel)) {
            return false;
        }
        ItemLearnBoxParcel that = (ItemLearnBoxParcel) other;
        return x == that.x && y == that.y && w == that.w && h == that.h
                && Double.compare(score, that.score) == 0 && uid == that.uid
                && code == that.code && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + w;
        result = 31 * result + h;
        result = 31 * result + Double.hashCode(score);
        result = 31 * result + (int) (uid ^ (uid >>> 32));
        result = 31 * result + code;
        return 31 * result + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return "ItemLearnBoxParcel(x=" + x + ", y=" + y + ", w=" + w + ", h=" + h
                + ", score=" + score + ", uid=" + uid + ", code=" + code + ", reason=" + reason + ")";
    }

    public static final Creator<ItemLearnBoxParcel> CREATOR = new Creator<ItemLearnBoxParcel>() {
        @Override
        public ItemLearnBoxParcel createFromParcel(Parcel parcel) {
            return new ItemLearnBoxParcel(parcel);
        }

        @Override
        public ItemLearnBoxParcel[] newArray(int size) {
            return new ItemLearnBoxParcel[size];
        }
    };
}
