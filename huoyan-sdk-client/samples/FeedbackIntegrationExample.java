package example;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.feedback.FeedBackUploadHelper;
import cc.huoyan.sdk.client.feedback.FeedbackSubmission;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.Feedback;
import cc.huoyan.sdk.client.model.FeedbackDetection;
import cc.huoyan.sdk.client.model.FeedbackItem;
import cc.huoyan.sdk.client.model.FeedbackProduct;
import java.io.File;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** 独立反馈接入：客户端只提供参数、后台执行器和回执处理。 */
public final class FeedbackIntegrationExample {
    /** 静态示例。 */
    private FeedbackIntegrationExample() {}

    /** 提交识别反馈；坐标属于上传原图，专用临时文件明确成功后交由 JAR 清理。 */
    public static FeedbackSubmission detect(HuoyanSdkClient client, File photo,
            List<FeedbackDetection> detections, int width, int height,
            Executor worker, Consumer<BaseResult> completion) {
        List<FeedbackItem> goods = FeedBackUploadHelper.prepareDetectFeedback(detections, width, height);
        FeedbackSubmission submission = FeedbackSubmission.fromFile(photo,
                Feedback.FEEDBACK_TYPE_NORMAL, goods, FeedbackSubmission.PhotoRetention.DELETE_ON_SUCCESS);
        FeedBackUploadHelper.submit(client, submission, worker, completion);
        return submission;
    }

}
