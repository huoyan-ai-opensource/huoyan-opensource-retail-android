package cc.huoyan.sdk.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 批量反馈载体，字段与 POST /api/v2/feedback/ 对齐。
 * 两段式：删 shopId（三端归属取验签设备）；photo_file_url 必填
 * http(s)（图片经 uploadPhoto 上传后返回 object URL 再提交）。
 */
public final class Feedback {

    public static final int FEEDBACK_TYPE_NORMAL = 0;
    public static final int FEEDBACK_TYPE_MODIFIED = 1;

    private final String requestId;
    private final String photoFileUrl;
    private final int feedbackType;
    private final List<FeedbackItem> items;

    /** 构造数据对象。 */
    public Feedback(String requestId, String photoFileUrl,
            int feedbackType, List<FeedbackItem> items) {
        if (feedbackType != FEEDBACK_TYPE_NORMAL && feedbackType != FEEDBACK_TYPE_MODIFIED)
            throw new IllegalArgumentException("feedback_type 只接受 0、1");
        this.requestId = requestId;
        this.photoFileUrl = photoFileUrl;
        this.items = FeedbackFields.list(items, "items");
        this.feedbackType = this.items.stream().anyMatch(item -> item.getItemType() != FeedbackItem.NORMAL)
                ? FEEDBACK_TYPE_MODIFIED : FEEDBACK_TYPE_NORMAL;

    }

    /** 调用方请求标识。 */
    public String getRequestId() { return requestId; }
    /** 已上传图片的 URL。 */
    public String getPhotoFileUrl() { return photoFileUrl; }
    /** 反馈类型。 */
    public int getFeedbackType() { return feedbackType; }
    /** 反馈商品列表。 */
    public List<FeedbackItem> getItems() { return items; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Feedback)) return false;
        Feedback that = (Feedback) other;
        return feedbackType == that.feedbackType
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(photoFileUrl, that.photoFileUrl)
                && items.equals(that.items);
    }

    @Override public int hashCode() {
        return Objects.hash(requestId, photoFileUrl, feedbackType, items);
    }

}
