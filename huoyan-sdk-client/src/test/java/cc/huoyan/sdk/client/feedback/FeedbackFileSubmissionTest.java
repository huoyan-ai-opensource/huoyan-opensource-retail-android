package cc.huoyan.sdk.client.feedback;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.lang.reflect.Proxy;
import java.util.*;
import cc.huoyan.sdk.client.model.*;
import cc.huoyan.sdk.client.SdkImage;
import static org.junit.Assert.*;

/** 两段式反馈：上传成功才提交 URL，失败重用 URL，不依赖本地门店或用户令牌。 */
public class FeedbackFileSubmissionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final List<String> calls = new ArrayList<>();
    private PhotoUploadResult upload = new PhotoUploadResult(0, null, "https://files/photo.jpg");
    private BaseResult receipt = new BaseResult(0, null);
    private boolean connected = true;
    private Feedback sent;

    @Test public void jpegAndPngUploadBeforeFeedbackAndCleanAfterSuccess() throws Exception {
        for (int type : new int[]{0, 1}) {
            File photo = photo(type == 1 ? new byte[]{(byte)255, (byte)216, (byte)255} : new byte[]{(byte)137,80,78,71,13,10,26,10});
            calls.clear();
            assertEquals(0, submission(photo, type).submit(bridge()).getCode());
            assertEquals(Arrays.asList("photoImage", "uploadPhoto", "submitFeedback"), calls);
            assertEquals("https://files/photo.jpg", sent.getPhotoFileUrl());
            assertEquals(0, sent.getFeedbackType());
            assertEquals(30D, sent.getItems().get(0).getWidth(), 0);
            assertEquals(10D, sent.getItems().get(0).getX(), 0);
            assertFalse(photo.exists());
        }
    }

    @Test public void uploadFailuresAndInvalidUrlsNeverSubmitFeedback() throws Exception {
        for (PhotoUploadResult result : Arrays.asList(null,
                new PhotoUploadResult(404, "未绑定门店", null),
                new PhotoUploadResult(0, null, "/sdcard/photo.jpg"),
                new PhotoUploadResult(0, null, null))) {
            upload = result;
            File photo = photo(new byte[]{(byte)255,(byte)216,(byte)255});
            calls.clear();
            BaseResult actual = submission(photo, 1).submit(bridge());
            assertTrue(actual == null || actual.getCode() != 0);
            if (result != null && result.getCode() == 404) assertEquals(404, actual.getCode());
            assertFalse(calls.contains("submitFeedback"));
            assertTrue(photo.exists());
        }
    }

    @Test public void feedbackRetryReusesUrlAndRequestId() throws Exception {
        File photo = photo(new byte[]{(byte)255,(byte)216,(byte)255});
        FeedbackSubmission submission = submission(photo, 1);
        receipt = new BaseResult(-6, "网络失败");
        assertEquals(-6, submission.submit(bridge()).getCode());
        String id = sent.getRequestId();
        assertTrue(photo.exists());
        receipt = new BaseResult(0, null);
        assertEquals(0, submission.submit(bridge()).getCode());
        assertEquals(id, sent.getRequestId());
        assertEquals(1, Collections.frequency(calls, "uploadPhoto"));
        assertFalse(photo.exists());
    }

    @Test public void disconnectInvalidFormatAndOversizeDoNotUpload() throws Exception {
        File photo = photo(new byte[]{1,2,3});
        connected = false;
        assertEquals(HuoyanSdkClient.LOCAL_DISCONNECTED, submission(photo, 1).submit(bridge()).getCode());
        connected = true;
        assertEquals(-7, submission(photo, 1).submit(bridge()).getCode());
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(photo, "rw")) { f.setLength(8 * 1024 * 1024 + 1); }
        assertEquals(-8, submission(photo, 1).submit(bridge()).getCode());
        assertTrue(calls.isEmpty());
        assertTrue(photo.exists());
    }

    @Test public void missingReceiptKeepsPhotoAndUploadedUrl() throws Exception {
        File photo = photo(new byte[]{(byte)255,(byte)216,(byte)255});
        receipt = null;
        FeedbackSubmission submission = submission(photo, 0);
        assertEquals(HuoyanSdkClient.RESULT_UNKNOWN, submission.submit(bridge()).getCode());
        assertEquals(HuoyanSdkClient.RESULT_UNKNOWN, submission.submit(bridge()).getCode());
        assertEquals(1, Collections.frequency(calls, "uploadPhoto"));
        assertTrue(photo.exists());
    }

    @Test public void callerOwnedFileIsKeptUnlessDeletionIsExplicit() throws Exception {
        File photo = photo(new byte[]{(byte)255,(byte)216,(byte)255});
        FeedbackSubmission submission = FeedbackSubmission.fromFile(photo, 1, Collections.singletonList(
                new FeedbackItem(0,java.util.Collections.emptyList(),new cc.huoyan.sdk.client.model.FeedbackResult( "1", "商品", "SDK识别"),null,null,null,null)), null);
        assertEquals(0, submission.submit(bridge()).getCode());
        assertEquals(0, submission.submit(bridge()).getCode());
        assertTrue(photo.exists());
        assertEquals(1, Collections.frequency(calls, "submitFeedback"));
    }

    @Test public void missingOrEmptyFileDoesNotDispatch() throws Exception {
        File photo = new File(temporary.getRoot(), "missing.jpg");
        assertEquals(-1, submission(photo, 1).submit(bridge()).getCode());
        assertTrue(photo.createNewFile());
        assertEquals(-1, submission(photo, 1).submit(bridge()).getCode());
        assertTrue(calls.isEmpty());
    }

    @Test public void changedSourceIsNotDeletedAfterRetry() throws Exception {
        File photo = photo(new byte[]{(byte)255,(byte)216,(byte)255});
        FeedbackSubmission submission = submission(photo, 1);
        receipt = new BaseResult(-6, "网络失败");
        assertEquals(-6, submission.submit(bridge()).getCode());
        Files.write(photo.toPath(), new byte[]{1,2,3,4});
        receipt = new BaseResult(0, null);
        assertEquals(0, submission.submit(bridge()).getCode());
        assertTrue(photo.exists());
        assertEquals(1, Collections.frequency(calls, "uploadPhoto"));
    }

    private FeedbackSubmission submission(File photo, int type) {
        return FeedbackSubmission.fromFile(photo, type, Collections.singletonList(
                new FeedbackItem(0,java.util.Collections.emptyList(),new cc.huoyan.sdk.client.model.FeedbackResult( "1001", "面包", "SDK识别"), 10D, 20D, 30D, 40D)), FeedbackSubmission.PhotoRetention.DELETE_ON_SUCCESS);
    }
    private File photo(byte[] bytes) throws Exception {
        File directory = new File(temporary.getRoot(), "host-feedback/recognition/A001");
        directory.mkdirs();
        File photo = new File(directory, UUID.randomUUID() + ".jpg");
        Files.write(photo.toPath(), bytes);
        return photo;
    }
    private HuoyanSdkClient bridge() {
        return (HuoyanSdkClient) Proxy.newProxyInstance(HuoyanSdkClient.class.getClassLoader(), new Class[]{HuoyanSdkClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isConnected")) return connected;
                    calls.add(method.getName());
                    switch (method.getName()) {
                        case "photoImage": return SdkImage.encoded((byte[]) args[0], 0, 0, (int) args[1]);
                        case "uploadPhoto": return upload;
                        case "submitFeedback": sent = (Feedback)args[0]; return receipt;
                        default: throw new AssertionError(method.getName());
                    }
                });
    }
}
