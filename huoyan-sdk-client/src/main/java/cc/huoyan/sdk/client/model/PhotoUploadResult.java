package cc.huoyan.sdk.client.model;

import java.util.Objects;

/**
 * 图片上传回执（两段式）：uploadPhoto 的字节上传结果——
 * code=0 成功时 photoFileUrl 为可访问 object URL（http(s)），供
 * submitFeedback 的 photo_file_url 使用；非 0 时 reason 携带错误说明。
 */
public final class PhotoUploadResult {

    private final int code;
    private final String reason;
    private final String photoFileUrl;

    /** 构造数据对象。 */
    public PhotoUploadResult(int code, String reason, String photoFileUrl) {
        this.code = code;
        this.reason = reason;
        this.photoFileUrl = photoFileUrl;
    }

    /** 结果码；0 表示请求成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 上传成功后返回的 object URL；code≠0 时为 null。 */
    public String getPhotoFileUrl() {
        return photoFileUrl;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PhotoUploadResult)) {
            return false;
        }
        PhotoUploadResult that = (PhotoUploadResult) other;
        return code == that.code
                && Objects.equals(reason, that.reason)
                && Objects.equals(photoFileUrl, that.photoFileUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason, photoFileUrl);
    }

    @Override
    public String toString() {
        return "PhotoUploadResult(code=" + code + ", reason=" + reason
                + ", photoFileUrl=" + photoFileUrl + ")";
    }

}
