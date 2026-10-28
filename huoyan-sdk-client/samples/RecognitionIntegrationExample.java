package example;

import android.content.Context;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkConfig;
import cc.huoyan.sdk.client.SdkImage;
import cc.huoyan.sdk.client.session.SdkConnection;
import cc.huoyan.sdk.client.status.SdkStatus;
import cc.huoyan.sdk.client.model.*;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 识别与学习对接示例；远程调用均须在工作线程执行，可用于第三方 Android 应用。 */
public final class RecognitionIntegrationExample {
    private RecognitionIntegrationExample() { }

    // region open
    /** 连接并尝试初始化一次；未就绪时返回错误，由页面引导激活或等待模型。 */
    public static HuoyanSdkClient openReady(Context context, SdkConfig config)
            throws IOException, InterruptedException {
        HuoyanSdkClient client = HuoyanSdkClient.create(context, config);
        try {
            if (!SdkConnection.await(client, 15000, 50, () -> true)) throw new IOException("连接 SDK 超时");
            requireSuccess(client.initialize(), "初始化");
            Capability capability = client.getCapabilities();
            if (capability == null || capability.getCode() != 0
                    || !capability.isRecognize()) {
                throw new IOException("未取得识别能力，请检查配套火眼 SDK 版本");
            }
            requireReady(client);
            return client;
        } catch (IOException | RuntimeException e) {
            client.close();
            throw e;
        }
    }

    /** 依据状态字段检查模型就绪；不通过错误文案猜测状态。 */
    public static void requireReady(HuoyanSdkClient client) throws IOException {
        StatusSnapshot status = client.getStatus();
        if (status == null) throw new IOException("未取得 SDK 状态");
        requireCode(status.getCode(), status.getReason(), "查询状态");
        if (!SdkStatus.modelReady(status)) {
            throw new IOException("SDK 未就绪：" + status.getSdkState()
                    + "，最近错误：" + status.getRecentError());
        }
    }
    // endregion open

    // region binding
    /** 绑定外部门店标识；调用方仅在成功后保存回执中的火眼 SDK 门店及账号。 */
    public static BindingResult bindShop(HuoyanSdkClient client, String externalShopId)
            throws IOException {
        if (externalShopId == null || externalShopId.trim().isEmpty()) {
            throw new IllegalArgumentException("外部门店标识不能为空");
        }
        BindingResult result = client.bindDevice(externalShopId);
        if (result == null) throw new IOException("绑定结果未知，请核对后再操作");
        requireCode(result.getCode(), result.getReason(), "绑定门店");
        return result;
    }
    // endregion binding

    // region learn
    /** 将同一画面中的所有目标学为同一分类；保留每框结果供页面展示部分成功。 */
    public static ItemLearnBatch learnCategory(HuoyanSdkClient client,
            SdkImage frame, String clazz) throws IOException {
        cc.huoyan.sdk.client.operation.SdkInputs.clazz(clazz);
        ItemLearnBatch result = client.batchLearn(frame, clazz);
        if (result == null) throw new IOException("学习结果未知，请查询样本，勿直接重拍重发");
        requireCode(result.getCode(), result.getReason(), "批量学习");
        return result;
    }

    /** 提取学习成功的 UID；空列表可能是没有检测框，也可能是所有框均失败。 */
    public static List<Long> successfulUids(ItemLearnBatch result) {
        List<Long> uids = new ArrayList<>();
        for (ItemLearnBox box : cc.huoyan.sdk.client.operation.BatchLearning.learnedBoxesOf(result)) uids.add(box.getUid());
        return uids;
    }
    // endregion learn

    // region images
    /** 分页读取完整样本列表；保留缺图 UID，任一页失败不伪装成完整图集。 */
    public static List<ItemClazzImage> loadAllImages(HuoyanSdkClient client, String clazz)
            throws IOException, InterruptedException {
        return cc.huoyan.sdk.client.samples.LearnedImagePages.loadAll(client, clazz);
    }
    // endregion images

    // region detect
    /** 检测图片中的目标框；空列表是正常结果，不包含分类判断。 */
    public static List<DetectBox> detect(HuoyanSdkClient client, SdkImage frame)
            throws IOException {
        DetectResult result = client.detect(frame);
        if (result == null) throw new IOException("检测未回执");
        requireCode(result.getCode(), result.getReason(), "检测");
        return result.getBoxes();
    }
    // endregion detect

    // region recognize
    /** 识别已裁剪、转正的图像；空结果是正常结果，不代表通信失败。 */
    public static List<RecognitionItem> recognize(HuoyanSdkClient client, SdkImage frame)
            throws IOException {
        RecognizeResult result = client.recognize(frame);
        if (result == null) throw new IOException("识别未回执");
        requireCode(result.getCode(), result.getReason(), "识别");
        return result.getItems();
    }
    // endregion recognize

    // region feedback
    /** 上传反馈原图并返回 URL；与当前应用一致，示例限制反馈图片不超过 8 MiB。 */
    public static String uploadFeedbackPhoto(HuoyanSdkClient client, byte[] jpeg)
            throws IOException {
        if (jpeg == null || jpeg.length == 0 || jpeg.length > 8 * 1024 * 1024) {
            throw new IllegalArgumentException("反馈 JPEG 不能为空或超过 8 MiB");
        }
        PhotoUploadResult result = client.uploadPhoto(
                SdkImage.encoded(jpeg, 0, 0, SdkImage.FORMAT_JPEG));
        if (result == null) throw new IOException("上传结果未知，保留本地图片");
        requireCode(result.getCode(), result.getReason(), "上传反馈图片");
        String url = result.getPhotoFileUrl();
        try {
            URI uri = new URI(url == null ? "" : url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) throw new IOException("上传回执不是有效图片 URL");
        } catch (java.net.URISyntaxException e) {
            throw new IOException("上传回执不是有效图片 URL", e);
        }
        return url; // 调用方保存 URL 和 requestId 后再提交反馈。
    }

    /** 提交单商品确认反馈；框坐标须已还原到上传原图，火眼 SDK 商品 ID 无映射时可传 null。 */
    public static void submitConfirmedFeedback(HuoyanSdkClient client, String requestId,
            String photoUrl, String sdkGoodsId, String name, String plu,
            double distance, int left, int top, int right, int bottom) throws IOException {
        if (left < 0 || top < 0 || right <= left || bottom <= top) {
            throw new IllegalArgumentException("反馈检测框无效");
        }
        FeedbackItem item = new FeedbackItem(FeedbackItem.NORMAL,
                Collections.singletonList(new FeedbackDetect(plu, name, distance)),
                new FeedbackResult(plu, name, "sdk"), (double) left, (double) top,
                (double) (right - left), (double) (bottom - top));
        Feedback feedback = new Feedback(requestId, photoUrl,
                Feedback.FEEDBACK_TYPE_NORMAL, Collections.singletonList(item));
        requireSuccess(client.submitFeedback(feedback), "提交识别反馈");
        // 到这里才可按业务文件管理规则清理专用反馈图片。
    }
    // endregion feedback

    // region imageflow
    /** 判断图片下载学习流程是否全部成功；跳过项仍需单独展示。 */
    public static boolean imageFlowSucceeded(ImageFlowProgress result) {
        return result != null && result.outcome() == ImageFlowProgress.Outcome.SUCCESS;
    }
    // endregion imageflow

    // region errors
    /** 检查基础回执；缺失回执时保留结果未知语义。 */
    public static void requireSuccess(BaseResult result, String operation) throws IOException {
        if (result == null) throw new IOException(operation + "未回执，写操作结果可能未知");
        requireCode(result.getCode(), result.getReason(), operation);
    }

    private static void requireCode(int code, String reason, String operation) throws IOException {
        if (code == 0) return;
        if (code == HuoyanSdkClient.RESULT_UNKNOWN) {
            throw new IOException(operation + "结果未知，先查询核对，禁止自动重放：" + reason);
        }
        if (code == HuoyanSdkClient.NOT_EXECUTED) {
            throw new IOException(operation + "未执行，可由业务层退避后重试：" + reason);
        }
        throw new IOException(operation + "失败，code=" + code + "，reason=" + reason);
    }
    // endregion errors
}
