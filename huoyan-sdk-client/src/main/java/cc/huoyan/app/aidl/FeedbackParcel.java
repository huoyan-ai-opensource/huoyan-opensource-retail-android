package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 新反馈契约的 IPC 载体；参数校验由 Core DTO 统一执行。 */
public final class FeedbackParcel implements Parcelable {
    public static final int FEEDBACK_TYPE_NORMAL = 0;
    public static final int FEEDBACK_TYPE_MODIFIED = 1;
    private final String requestId;
    private final String photoFileUrl;
    private final int feedbackType;
    private final List<FeedbackItemParcel> items;
    public FeedbackParcel(String requestId, String photoFileUrl, int feedbackType, List<FeedbackItemParcel> items) {
        this.requestId = requestId;
        this.photoFileUrl = photoFileUrl;
        this.feedbackType = feedbackType;
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(items));
    }
    public String getRequestId() { return requestId; }
    public String getPhotoFileUrl() { return photoFileUrl; }
    public int getFeedbackType() { return feedbackType; }
    public List<FeedbackItemParcel> getItems() { return items; }
    private FeedbackParcel(Parcel parcel) {
        requestId = parcel.readString();
        photoFileUrl = parcel.readString();
        feedbackType = parcel.readInt();
        items = parcel.createTypedArrayList(FeedbackItemParcel.CREATOR);
    }
    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(requestId);
        parcel.writeString(photoFileUrl);
        parcel.writeInt(feedbackType);
        parcel.writeTypedList(items);
    }
    @Override public int describeContents() { return 0; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof FeedbackParcel)) return false;
        FeedbackParcel that = (FeedbackParcel) other;
        return Objects.equals(requestId, that.requestId) && Objects.equals(photoFileUrl, that.photoFileUrl) && Objects.equals(feedbackType, that.feedbackType) && Objects.equals(items, that.items);
    }
    @Override public int hashCode() { return Objects.hash(requestId, photoFileUrl, feedbackType, items); }
    public static final Creator<FeedbackParcel> CREATOR = new Creator<FeedbackParcel>() {
        @Override public FeedbackParcel createFromParcel(Parcel parcel) { return new FeedbackParcel(parcel); }
        @Override public FeedbackParcel[] newArray(int size) { return new FeedbackParcel[size]; }
    };
}
