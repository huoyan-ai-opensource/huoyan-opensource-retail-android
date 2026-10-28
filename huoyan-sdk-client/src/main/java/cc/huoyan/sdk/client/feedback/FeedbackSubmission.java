package cc.huoyan.sdk.client.feedback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import cc.huoyan.sdk.client.SdkImage;
import cc.huoyan.sdk.client.model.PhotoUploadResult;
import cc.huoyan.sdk.client.HuoyanSdkClient;


import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.FeedbackItem;
import cc.huoyan.sdk.client.model.Feedback;

/** 上传后提交反馈的 SDK 会话；支持字节或文件输入，写操作仅在显式调用时执行。 */
public final class FeedbackSubmission {
    /** 图片上传契约上限，与通用传输的 32 MiB 上限独立。 */
    public static final int MAX_PHOTO_BYTES = 8 * 1024 * 1024;
    /** 同一反馈会话的稳定请求标识。 */
    private final String requestId = UUID.randomUUID().toString();
    /** 压缩图片快照，文件输入在首次提交时读取，实例锁保护。 */
    private byte[] photo;
    /** 可选源文件；不使用任何宿主目录命名约定。 */
    private final File sourceFile;
    /** 显式文件所有权策略。 */
    private final PhotoRetention retention;
    /** 首次读取的规范路径，用于避免清理后来改指向的链接。 */
    private File loadedFile;
    /** 首次读取的修改时间，用于避免清理后来改写的文件。 */
    private long loadedModified;

    /** 文件来源默认保留，专用反馈临时文件可显式交给 JAR 清理。 */
    public enum PhotoRetention {
        /** 始终保留源文件。 */ KEEP,
        /** 仅在反馈明确成功且源文件未变化时删除。 */ DELETE_ON_SUCCESS
    }
    /** SDK 反馈类型。 */
    private final int feedbackType;
    /** 创建时固定的反馈条目。 */
    private final List<FeedbackItem> items;
    /** 上传成功后的地址，显式重试时复用，锁保护。 */
    private String photoUrl;

    /** 当前提交阶段，不推断后端尚未回执的执行结果。 */
    public enum Stage {
        /** 等待上传。 */ UPLOAD,
        /** 已有 URL，等待提交反馈。 */ SUBMIT,
        /** 收到成功反馈回执。 */ COMPLETE
    }
    /** 阶段与上传 URL 一起受实例锁保护。 */
    private Stage stage = Stage.UPLOAD;
    /** 最近的明确成功回执，完成后不再次提交。 */
    private BaseResult completed;
    /** 查询阶段。 */
    public synchronized Stage getStage() { return stage; }
    /** 查询稳定的请求标识。 */
    public String getRequestId() { return requestId; }
    /** 查询已确认的上传地址，上传完成前为 null。 */
    public synchronized String getUploadedUrl() { return photoUrl; }

    /** 复制图像和反馈条目，后续提交复用请求标识和上传 URL。 */
    public FeedbackSubmission(byte[] bytes, int feedbackType, List<FeedbackItem> items) {
        this.sourceFile = null;
        this.retention = PhotoRetention.KEEP;
        this.photo = bytes == null ? null : bytes.clone();
        if (feedbackType != Feedback.FEEDBACK_TYPE_NORMAL && feedbackType != Feedback.FEEDBACK_TYPE_MODIFIED)
            throw new IllegalArgumentException("feedback_type 只接受 0、1");
        this.feedbackType = feedbackType;
        this.items = items == null ? Collections.emptyList() : new ArrayList<>(items);
    }

    /** 从文件创建会话，后台首次提交时限量读取；失败/未知结果始终保留文件。 */
    public static FeedbackSubmission fromFile(File photo, int feedbackType,
            List<FeedbackItem> items, PhotoRetention retention) {
        return new FeedbackSubmission(photo, feedbackType, items, retention);
    }

    /** 固定文件引用、清理策略与反馈参数；不在调用线程读取图片。 */
    private FeedbackSubmission(File file, int feedbackType, List<FeedbackItem> items,
            PhotoRetention retention) {
        this.sourceFile = file == null ? null : file.getAbsoluteFile();
        this.retention = retention == null ? PhotoRetention.KEEP : retention;
        if (feedbackType != Feedback.FEEDBACK_TYPE_NORMAL && feedbackType != Feedback.FEEDBACK_TYPE_MODIFIED)
            throw new IllegalArgumentException("feedback_type 只接受 0、1");
        this.feedbackType = feedbackType;
        this.items = items == null ? Collections.emptyList() : new ArrayList<>(items);
    }

    /** 显式执行一次上传/反馈；绝不自动重试写操作。 */
    public synchronized BaseResult submit(HuoyanSdkClient bridge) {
        if (completed != null) { cleanPhoto(); return completed; }
        if (items.isEmpty() || (photo == null && sourceFile == null)) {
            return new BaseResult(-1, "反馈缺少图片或商品");
        }
        if (bridge == null || !bridge.isConnected()) {
            return new BaseResult(HuoyanSdkClient.LOCAL_DISCONNECTED, "SDK 通道未连接");
        }
        if (photoUrl == null) {
            if (photo == null) {
                BaseResult failure = loadPhoto();
                if (failure != null) return failure;
            }
            if (photo.length == 0) return new BaseResult(-1, "反馈图片为空");
            if (photo.length > MAX_PHOTO_BYTES) return new BaseResult(-8, "图片超过 8MB");
            byte[] bytes = photo;
            int format = imageFormat(bytes);
            if (format == 0) return new BaseResult(-7, "反馈图片必须为 JPEG/PNG");
            SdkImage image = bridge.photoImage(bytes, format);
            if (image == null) return new BaseResult(-8, "当前通道无法承载图片");
            PhotoUploadResult uploaded = bridge.uploadPhoto(image);
            if (uploaded == null) return new BaseResult(HuoyanSdkClient.RESULT_UNKNOWN, "图片上传结果未知");
            if (uploaded.getCode() != 0) {
                return new BaseResult(uploaded.getCode(), uploaded.getReason());
            }
            if (!validUrl(uploaded.getPhotoFileUrl())) {
                return new BaseResult(-1, "图片上传回执缺少有效 HTTP(S) URL");
            }
            photoUrl = uploaded.getPhotoFileUrl();
            stage = Stage.SUBMIT;
        }
        // 同一提交对象复用 URL 和 requestId，不会因反馈失败而重复上传图片。
        BaseResult result = bridge.submitFeedback(new Feedback(requestId, photoUrl, feedbackType, items));
        if (result != null && result.getCode() == 0) {
            completed = result;
            stage = Stage.COMPLETE;
            cleanPhoto();
        }
        return result == null ? new BaseResult(HuoyanSdkClient.RESULT_UNKNOWN, "反馈结果未知") : result;
    }

    /** 限量读取源文件并固定清理标识；读取失败不触发 SDK 写调用。 */
    private BaseResult loadPhoto() {
        try {
            File file = sourceFile.getCanonicalFile();
            if (!file.isFile() || !file.canRead() || file.length() == 0)
                return new BaseResult(-1, "反馈图片不存在、为空或不可读");
            if (file.length() > MAX_PHOTO_BYTES) return new BaseResult(-8, "图片超过 8MB");
            long modified = file.lastModified();
            try (FileInputStream input = new FileInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_PHOTO_BYTES) return new BaseResult(-8, "图片超过 8MB");
                    output.write(buffer, 0, count);
                }
                photo = output.toByteArray();
            }
            loadedFile = file;
            loadedModified = modified;
            return null;
        } catch (IOException | SecurityException error) {
            return new BaseResult(-1, "反馈图片读取失败：" + error.getMessage());
        }
    }

    /** 成功后仅清理由宿主授权且未被替换的源文件；清理失败不改变成功回执。 */
    private void cleanPhoto() {
        if (retention != PhotoRetention.DELETE_ON_SUCCESS || loadedFile == null) return;
        try {
            if (loadedFile.equals(sourceFile.getCanonicalFile())
                    && loadedFile.length() == photo.length && loadedFile.lastModified() == loadedModified) {
                loadedFile.delete();
            }
        } catch (IOException | SecurityException ignored) {
            // 提交已经成功，后续可显式再次调用以尝试清理，不重发反馈。
        }
    }

    /** 检测 JPEG/PNG 文件签名，不接受其他编码。 */
    private static int imageFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255) {
            return SdkImage.FORMAT_JPEG;
        }
        byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length >= png.length && Arrays.equals(png, Arrays.copyOf(bytes, png.length))) {
            return SdkImage.FORMAT_PNG;
        }
        return 0;
    }

    /** 验证上传回执提供的 HTTP(S) 地址。 */
    private static boolean validUrl(String url) {
        try {
            URI uri = new URI(url == null ? "" : url);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** 判断反馈必填文本。 */
    private static boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
