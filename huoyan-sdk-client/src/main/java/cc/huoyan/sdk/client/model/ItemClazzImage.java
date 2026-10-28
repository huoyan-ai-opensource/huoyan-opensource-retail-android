package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 类别下样本图片记录回执（）：{@code getItemClazzImages} 的单个样本条目
 * ——uid + 图片可用性 + 局域网 URL + MIME。图片缺失/损坏时 imageAvailable=false、
 * imageUrl/mimeType 为空；有图片时 imageUrl 指向局域网图片服务逻辑 URL
 * （{@code /images/<uid>}）。</p>
 */
public final class ItemClazzImage {

    private final long uid;
    private final boolean imageAvailable;
    private final String imageUrl;
    private final String mimeType;

    /** 构造样本图片记录。 */
    public ItemClazzImage(long uid, boolean imageAvailable, String imageUrl,
            String mimeType) {
        this.uid = uid;
        this.imageAvailable = imageAvailable;
        this.imageUrl = imageUrl;
        this.mimeType = mimeType;
    }

    /** 取得样本 UID。 */
    public long getUid() {
        return uid;
    }

    /** 判断图片是否可用。 */
    public boolean isImageAvailable() {
        return imageAvailable;
    }

    /** 取得图片地址。 */
    public String getImageUrl() {
        return imageUrl;
    }

    /** 取得图片 MIME 类型。 */
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemClazzImage)) {
            return false;
        }
        ItemClazzImage that = (ItemClazzImage) other;
        return uid == that.uid
                && imageAvailable == that.imageAvailable
                && Objects.equals(imageUrl, that.imageUrl)
                && Objects.equals(mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, imageAvailable, imageUrl, mimeType);
    }

    @Override
    public String toString() {
        return "ItemClazzImage(uid=" + uid + ", imageAvailable=" + imageAvailable
                + ", imageUrl=" + imageUrl + ", mimeType=" + mimeType + ")";
    }

}
