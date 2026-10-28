package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 新反馈契约的 IPC 载体；参数校验由 Core DTO 统一执行。 */
public final class FeedbackResultParcel implements Parcelable {
    private final String clazz;
    private final String name;
    private final String source;
    public FeedbackResultParcel(String clazz, String name, String source) {
        this.clazz = clazz;
        this.name = name;
        this.source = source;
    }
    public String getClazz() { return clazz; }
    public String getName() { return name; }
    public String getSource() { return source; }
    private FeedbackResultParcel(Parcel parcel) {
        clazz = parcel.readString();
        name = parcel.readString();
        source = parcel.readString();
    }
    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(clazz);
        parcel.writeString(name);
        parcel.writeString(source);
    }
    @Override public int describeContents() { return 0; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof FeedbackResultParcel)) return false;
        FeedbackResultParcel that = (FeedbackResultParcel) other;
        return Objects.equals(clazz, that.clazz) && Objects.equals(name, that.name) && Objects.equals(source, that.source);
    }
    @Override public int hashCode() { return Objects.hash(clazz, name, source); }
    public static final Creator<FeedbackResultParcel> CREATOR = new Creator<FeedbackResultParcel>() {
        @Override public FeedbackResultParcel createFromParcel(Parcel parcel) { return new FeedbackResultParcel(parcel); }
        @Override public FeedbackResultParcel[] newArray(int size) { return new FeedbackResultParcel[size]; }
    };
}
