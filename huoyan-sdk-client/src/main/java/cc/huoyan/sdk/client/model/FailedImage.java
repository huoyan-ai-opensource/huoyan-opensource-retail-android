package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 失败图片记录回执（）：{@code getFailedImages} 的单条失败记录——clazz +
 * 后端图片 ID + 失败原因 + 局域网 URL（有完整图片时非空，无完整图片为空）。
 */
public final class FailedImage {

    private final String clazz;
    private final String imageId;
    private final String failureReason;
    private final String imageUrl;

    /** 构造失败图片记录。 */
    public FailedImage(String clazz, String imageId, String failureReason, String imageUrl) {
        this.clazz = clazz;
        this.imageId = imageId;
        this.failureReason = failureReason;
        this.imageUrl = imageUrl;
    }

    /** 取得分类编码。 */
    public String getClazz() {
        return clazz;
    }

    /** 取得图片标识。 */
    public String getImageId() {
        return imageId;
    }

    /** 取得图片失败原因。 */
    public String getFailureReason() {
        return failureReason;
    }

    /** 取得图片地址。 */
    public String getImageUrl() {
        return imageUrl;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FailedImage)) {
            return false;
        }
        FailedImage that = (FailedImage) other;
        return Objects.equals(clazz, that.clazz)
                && Objects.equals(imageId, that.imageId)
                && Objects.equals(failureReason, that.failureReason)
                && Objects.equals(imageUrl, that.imageUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, imageId, failureReason, imageUrl);
    }

    @Override
    public String toString() {
        return "FailedImage(clazz=" + clazz + ", imageId=" + imageId
                + ", failureReason=" + failureReason + ", imageUrl=" + imageUrl + ")";
    }

}
