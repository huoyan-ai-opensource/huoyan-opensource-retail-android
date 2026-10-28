package cc.huoyan.retail.checkout.integration;

import android.content.Context;
import android.graphics.Bitmap;
import cc.huoyan.retail.checkout.GoodsVo;
import cc.huoyan.retail.checkout.domain.SalePrintJob;
import cc.huoyan.retail.common.FileUtil;
import cc.huoyan.retail.common.IOThreadHelper;
import cc.huoyan.retail.common.MainHandler;
import cc.huoyan.retail.device.print.PrinterManager;
import cc.huoyan.retail.device.print.ReceiptPaper;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.diagnostics.Logger;
import cc.huoyan.retail.feedback.FeedbackInputs;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.feedback.FeedBackUploadHelper;
import cc.huoyan.sdk.client.feedback.FeedbackSubmission;
import cc.huoyan.sdk.client.model.Feedback;
import java.io.File;
import cc.huoyan.retail.checkout.CheckoutRecognition;
import cc.huoyan.sdk.client.model.FeedbackItem;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 收银输出接入：固定反馈内容、发送小票、保存照片与提交 SDK 反馈。 */
public final class CheckoutOutput {
    private static final String LOG_TAG = "CheckoutOutput";
    private final Context appContext;

    /** 仅持有应用上下文，输出任务可在页面退出后完成。 */
    public CheckoutOutput(Context context) { appContext = context.getApplicationContext(); }

    /** 接管截图所有权；同步固定反馈，后台按开关打印并提交反馈，主线程报告确认结果；反馈回执独立记录。 */
    public void submit(SalePrintJob job, boolean printEnabled, CheckoutRecognition response,
            List<GoodsVo> rows, List<GoodsVo> deletedRows, Bitmap photo, Consumer<Exception> completion) {
        final String printerDevice = SPManager.getInstance().getPrinterDevice();
        final ReceiptPaper printerPaper = ReceiptPaper.fromMillimeters(SPManager.getInstance().getPrinterPaperWidthMm());
        final List<FeedbackItem> feedback;
        try {
            int width = photo == null ? 0 : photo.getWidth();
            int height = photo == null ? 0 : photo.getHeight();
            feedback = FeedBackUploadHelper.prepareDetectFeedback(
                    FeedbackInputs.detections(response, rows, deletedRows, width, height), width, height);
        } catch (RuntimeException error) {
            recycle(photo);
            completion.accept(error);
            return;
        }
        IOThreadHelper.getInstance().post(() -> {
            Exception failure = null;
            try {
                job.execute(receipt -> {
                    if (printEnabled) PrinterManager.getInstance().printWeightedSale(receipt, printerDevice, printerPaper);
                }, () -> {
                    if (feedback.isEmpty()) return;
                    FeedbackSubmission submission = FeedbackSubmission.fromFile(new File(savePhoto(photo)),
                            Feedback.FEEDBACK_TYPE_NORMAL, feedback, FeedbackSubmission.PhotoRetention.DELETE_ON_SUCCESS);
                    FeedBackUploadHelper.submit(SdkChannelManager.getInstance().getClient(), submission,
                            IOThreadHelper.getInstance()::post,
                            result -> Logger.logDebug(LOG_TAG, "[Biz] feedback_request=" + submission.getRequestId()
                                    + " feedback_stage=" + submission.getStage() + " feedback="
                                    + (result.getCode() == 0 ? "ok" : "fail:" + result.getCode())));
                },
                        error -> Logger.logError(LOG_TAG, "confirmed sale feedback failed", error));
            } catch (Exception error) {
                failure = error;
                Logger.logError(LOG_TAG, "confirm sale failed", error);
            } finally {
                recycle(photo);
            }
            Exception result = failure;
            MainHandler.runOnUiThread(() -> completion.accept(result));
        });
    }

    /** 全部删除时同步固定纠错快照；后台上传，不打印，不依赖页面后续生命周期。 */
    public void submitDeletion(CheckoutRecognition response, List<GoodsVo> deletedRows, Bitmap photo) {
        final List<FeedbackItem> feedback;
        try {
            int width = photo == null ? 0 : photo.getWidth();
            int height = photo == null ? 0 : photo.getHeight();
            feedback = FeedBackUploadHelper.prepareDetectFeedback(
                    FeedbackInputs.detections(response, Collections.emptyList(), deletedRows, width, height), width, height);
        } catch (RuntimeException error) {
            recycle(photo);
            Logger.logError(LOG_TAG, "prepare deleted feedback failed", error);
            return;
        }
        IOThreadHelper.getInstance().post(() -> {
            try {
                if (feedback.isEmpty()) return;
                FeedbackSubmission submission = FeedbackSubmission.fromFile(new File(savePhoto(photo)),
                        Feedback.FEEDBACK_TYPE_NORMAL, feedback, FeedbackSubmission.PhotoRetention.DELETE_ON_SUCCESS);
                FeedBackUploadHelper.submit(SdkChannelManager.getInstance().getClient(), submission,
                        Runnable::run, result -> Logger.logDebug(LOG_TAG, "[Biz] feedback_request=" + submission.getRequestId()
                                + " feedback_stage=" + submission.getStage() + " deleted_feedback="
                                + (result.getCode() == 0 ? "ok" : "fail:" + result.getCode())));
            } catch (RuntimeException error) {
                Logger.logError(LOG_TAG, "deleted feedback failed", error);
            } finally {
                recycle(photo);
            }
        });
    }

    private String savePhoto(Bitmap photo) {
        if (photo == null || photo.isRecycled()) return "";
        return FileUtil.saveBitmapToExternalStorage(appContext, photo, "retail/feedback/detect",
                "confirm_" + System.currentTimeMillis() + ".jpg");
    }

    private static void recycle(Bitmap photo) {
        if (photo != null && !photo.isRecycled()) photo.recycle();
    }
}
