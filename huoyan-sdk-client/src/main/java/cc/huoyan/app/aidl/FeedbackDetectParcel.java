package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 新反馈契约的 IPC 载体；参数校验由 Core DTO 统一执行。 */
public final class FeedbackDetectParcel implements Parcelable {
    private final String clazz;
    private final String name;
    private final Double distance;
    public FeedbackDetectParcel(String clazz, String name, Double distance) {
        this.clazz = clazz;
        this.name = name;
        this.distance = distance;
    }
    public String getClazz() { return clazz; }
    public String getName() { return name; }
    public Double getDistance() { return distance; }
    private FeedbackDetectParcel(Parcel parcel) {
        clazz = parcel.readString();
        name = parcel.readString();
        distance = parcel.readByte() == 0 ? null : parcel.readDouble();
    }
    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(clazz);
        parcel.writeString(name);
        parcel.writeByte((byte) (distance == null ? 0 : 1));
        if (distance != null) parcel.writeDouble(distance);
    }
    @Override public int describeContents() { return 0; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof FeedbackDetectParcel)) return false;
        FeedbackDetectParcel that = (FeedbackDetectParcel) other;
        return Objects.equals(clazz, that.clazz) && Objects.equals(name, that.name) && Objects.equals(distance, that.distance);
    }
    @Override public int hashCode() { return Objects.hash(clazz, name, distance); }
    public static final Creator<FeedbackDetectParcel> CREATOR = new Creator<FeedbackDetectParcel>() {
        @Override public FeedbackDetectParcel createFromParcel(Parcel parcel) { return new FeedbackDetectParcel(parcel); }
        @Override public FeedbackDetectParcel[] newArray(int size) { return new FeedbackDetectParcel[size]; }
    };
}
