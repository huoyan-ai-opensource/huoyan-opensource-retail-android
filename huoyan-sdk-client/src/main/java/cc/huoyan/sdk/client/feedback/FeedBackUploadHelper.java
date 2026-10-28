package cc.huoyan.sdk.client.feedback;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.geometry.FeedbackBounds;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.FeedbackDetection;
import cc.huoyan.sdk.client.model.FeedbackDetect;
import cc.huoyan.sdk.client.model.FeedbackItem;
import cc.huoyan.sdk.client.model.FeedbackResult;
import cc.huoyan.sdk.client.model.FeedbackProduct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** 识别结果反馈组装及异步提交入口；不访问宿主数据库、偏好、控件或业务实体。 */
public final class FeedBackUploadHelper {
    /** 静态入口。 */
    private FeedBackUploadHelper() {}

    /** 每框对应一个 item；detect 按商品编码去重，原始命中在前，删除仍保留识别与整图位置。 */
    public static List<FeedbackItem> prepareDetectFeedback(List<FeedbackDetection> detections,
            int imageWidth, int imageHeight) {
        List<FeedbackItem> items = new ArrayList<>();
        if (detections != null) for (FeedbackDetection item : detections) {
            if (item == null || item.getOriginal() == null) continue;
            int[] box = bounds(item.getPoints(), imageWidth, imageHeight);
            FeedbackProduct selected = item.getSelected() == null ? item.getOriginal() : item.getSelected();
            boolean modified = !Objects.equals(item.getOriginal().getPlu(), selected.getPlu());
            int type = item.isDeleted() ? FeedbackItem.DELETED
                    : modified ? FeedbackItem.MODIFIED : FeedbackItem.NORMAL;
            List<FeedbackDetect> detect = new ArrayList<>();
            FeedbackDetect winner = new FeedbackDetect(item.getOriginal().getPlu(),
                    item.getOriginal().getName(), item.getDistance());
            detect.add(winner);
            // 每框按商品编码合并样本候选；命中保留原始距离，其余商品保留首次出现的候选。
            Set<String> classes = new HashSet<>();
            classes.add(winner.getClazz());
            for (FeedbackDetect candidate : item.getCandidates()) {
                if (classes.add(candidate.getClazz())) detect.add(candidate);
            }
            items.add(new FeedbackItem(type, detect,
                    type == FeedbackItem.DELETED ? null
                            : new FeedbackResult(selected.getPlu(), selected.getName(), modified ? "manual" : "sdk"),
                    (double) box[0], (double) box[1], (double) box[2], (double) box[3]));
        }
        return Collections.unmodifiableList(items);
    }

    /** 在宿主提供的后台执行器提交一次；回调同线程执行，不自动重试，不持有页面。 */
    public static void submit(HuoyanSdkClient client, FeedbackSubmission submission,
            Executor executor, Consumer<BaseResult> completion) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(executor, "executor");
        executor.execute(() -> {
            BaseResult result;
            try {
                result = submission.submit(client);
            } catch (RuntimeException error) {
                // 通道实现可能已发送请求，不能将异常解释为未执行或自动重试。
                result = new BaseResult(HuoyanSdkClient.RESULT_UNKNOWN, "反馈执行异常：" + error.getMessage());
            }
            if (completion != null) completion.accept(result);
        });
    }

    /** 无有效图片尺寸时沿用零框，否则裁剪到上传图片边界。 */
    private static int[] bounds(float[] points, int width, int height) {
        return width > 0 && height > 0 ? FeedbackBounds.fromPoints(points, width, height) : new int[4];
    }

}
