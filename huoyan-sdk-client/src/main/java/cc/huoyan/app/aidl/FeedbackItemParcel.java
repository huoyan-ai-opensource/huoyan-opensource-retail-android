package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 新反馈契约的 IPC 载体；参数校验由 Core DTO 统一执行。 */
public final class FeedbackItemParcel implements Parcelable {
    public static final int NORMAL = 0, MODIFIED = 1, DELETED = 2;
    private final int itemType;
    private final List<FeedbackDetectParcel> detect;
    private final FeedbackResultParcel result;
    private final Double x;
    private final Double y;
    private final Double width;
    private final Double height;
    public FeedbackItemParcel(int itemType, List<FeedbackDetectParcel> detect, FeedbackResultParcel result, Double x, Double y, Double width, Double height) {
        this.itemType = itemType;
        this.detect = detect == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(detect));
        this.result = result;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    public int getItemType() { return itemType; }
    public List<FeedbackDetectParcel> getDetect() { return detect; }
    public FeedbackResultParcel getResult() { return result; }
    public Double getX() { return x; }
    public Double getY() { return y; }
    public Double getWidth() { return width; }
    public Double getHeight() { return height; }
    private FeedbackItemParcel(Parcel parcel) {
        itemType = parcel.readInt();
        detect = parcel.createTypedArrayList(FeedbackDetectParcel.CREATOR);
        result = parcel.readByte() == 0 ? null : FeedbackResultParcel.CREATOR.createFromParcel(parcel);
        x = parcel.readByte() == 0 ? null : parcel.readDouble();
        y = parcel.readByte() == 0 ? null : parcel.readDouble();
        width = parcel.readByte() == 0 ? null : parcel.readDouble();
        height = parcel.readByte() == 0 ? null : parcel.readDouble();
    }
    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(itemType);
        parcel.writeTypedList(detect);
        parcel.writeByte((byte) (result == null ? 0 : 1));
        if (result != null) result.writeToParcel(parcel, flags);
        parcel.writeByte((byte) (x == null ? 0 : 1));
        if (x != null) parcel.writeDouble(x);
        parcel.writeByte((byte) (y == null ? 0 : 1));
        if (y != null) parcel.writeDouble(y);
        parcel.writeByte((byte) (width == null ? 0 : 1));
        if (width != null) parcel.writeDouble(width);
        parcel.writeByte((byte) (height == null ? 0 : 1));
        if (height != null) parcel.writeDouble(height);
    }
    @Override public int describeContents() { return 0; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof FeedbackItemParcel)) return false;
        FeedbackItemParcel that = (FeedbackItemParcel) other;
        return Objects.equals(itemType, that.itemType) && Objects.equals(detect, that.detect) && Objects.equals(result, that.result) && Objects.equals(x, that.x) && Objects.equals(y, that.y) && Objects.equals(width, that.width) && Objects.equals(height, that.height);
    }
    @Override public int hashCode() { return Objects.hash(itemType, detect, result, x, y, width, height); }
    public static final Creator<FeedbackItemParcel> CREATOR = new Creator<FeedbackItemParcel>() {
        @Override public FeedbackItemParcel createFromParcel(Parcel parcel) { return new FeedbackItemParcel(parcel); }
        @Override public FeedbackItemParcel[] newArray(int size) { return new FeedbackItemParcel[size]; }
    };
}
