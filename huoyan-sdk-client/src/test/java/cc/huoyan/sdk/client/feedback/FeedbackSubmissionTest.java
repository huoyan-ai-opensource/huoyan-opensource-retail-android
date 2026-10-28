package cc.huoyan.sdk.client.feedback;
import cc.huoyan.sdk.client.*;
import cc.huoyan.sdk.client.model.*;
import cc.huoyan.sdk.client.testing.SdkChannelFakes;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
/** 上传/反馈分阶段复用，执行结果未知不变成自动重试。 */
public class FeedbackSubmissionTest {
    private static final class Client extends SdkChannelFakes.FakeSdkChannelBridge {
        int uploads, submissions;
        String requestId;
        String url = "https://example.test/photo.jpg";
        BaseResult receipt;
        Client() { connected = true; }
        @Override public PhotoUploadResult uploadPhoto(SdkImage image) {
            uploads++; return new PhotoUploadResult(0,null,url);
        }
        @Override public BaseResult submitFeedback(Feedback feedback) {
            submissions++;
            if (requestId == null) requestId = feedback.getRequestId();
            else assertEquals(requestId, feedback.getRequestId());
            return receipt;
        }
    }
    private FeedbackSubmission submission(byte[] bytes) {
        return new FeedbackSubmission(bytes, Feedback.FEEDBACK_TYPE_NORMAL, Collections.singletonList(
                new FeedbackItem(0,java.util.Collections.emptyList(),new cc.huoyan.sdk.client.model.FeedbackResult("1","商品","SDK识别"),0D,0D,10D,10D)));
    }
    @Test public void explicitRetryReusesUploadedUrlAndRequestId() {
        Client client = new Client();
        FeedbackSubmission submission = submission(new byte[]{(byte)255,(byte)216,(byte)255});
        assertEquals(HuoyanSdkClient.RESULT_UNKNOWN,submission.submit(client).getCode());
        assertEquals(1,client.uploads); assertEquals(1,client.submissions);
        assertEquals(FeedbackSubmission.Stage.SUBMIT,submission.getStage());
        assertEquals(client.url,submission.getUploadedUrl());
        client.receipt = new BaseResult(0,null);
        assertEquals(0,submission.submit(client).getCode());
        assertEquals(0,submission.submit(client).getCode());
        assertEquals(1,client.uploads); assertEquals(2,client.submissions);
        assertEquals(FeedbackSubmission.Stage.COMPLETE,submission.getStage());
    }
    @Test public void formatSizeAndReturnedUrlAreValidatedBeforeFeedback() {
        Client client = new Client();
        assertEquals(-7,submission(new byte[]{1,2,3}).submit(client).getCode());
        assertEquals(-8,submission(new byte[FeedbackSubmission.MAX_PHOTO_BYTES + 1]).submit(client).getCode());
        assertEquals(0,client.uploads);
        client.url = "https://";
        assertEquals(-1,submission(new byte[]{(byte)137,80,78,71,13,10,26,10}).submit(client).getCode());
        assertEquals(0,client.submissions);
    }
}
