package cc.huoyan.sdk.client.deployment;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

import static org.junit.Assert.*;

/** 验证下载完整性、未知大小、HTTP 错误及取消时的半包清理。 */
public class SdkApkDownloaderTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void completeAndUnknownLengthDownloadsDeliverExactBytes() throws Exception {
        byte[] bytes = new byte[150_000];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        for (long total : new long[]{bytes.length, -1}) {
            Result result = run(200, bytes, total, false);
            assertNull(result.error);
            assertNotNull(result.apk);
            assertArrayEquals(bytes, Files.readAllBytes(result.apk.toPath()));
            assertEquals(bytes.length, result.downloaded);
            assertEquals(bytes.length, result.total);
            assertEquals(total, result.initialTotal);
            assertTrue(result.apk.delete());
        }
    }

    @Test public void httpErrorEmptyAndTruncatedBodiesNeverDeliverAnApk() throws Exception {
        for (Result result : new Result[]{run(403, new byte[]{1}, 1, false),
                run(200, new byte[0], 0, false), run(200, new byte[]{1, 2}, 10, false)}) {
            assertNotNull(result.error);
            assertNull(result.apk);
        }
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void cancellationDuringTransferSuppressesResultAndRemovesPartialFile() throws Exception {
        Result result = run(200, new byte[150_000], 150_000, true);
        assertNull(result.apk);
        assertNull(result.error);
        assertEquals(0, temporary.getRoot().list().length);
    }

    private Result run(int code, byte[] bytes, long length, boolean cancel) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
                new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(code).message("test").body(new ResponseBody() {
                            private final Buffer source = new Buffer().write(bytes);
                            @Override public MediaType contentType() { return null; }
                            @Override public long contentLength() { return length; }
                            @Override public BufferedSource source() { return source; }
                        }).build()).build();
        SdkApkDownloader task = new SdkApkDownloader("https://example.test/sdk.apk", temporary.getRoot(), client);
        Result result = new Result();
        CountDownLatch callback = new CountDownLatch(1);
        task.start(new SdkApkDownloader.Listener() {
            @Override public void onProgress(long downloaded, long total) {
                if (downloaded == 0) result.initialTotal = total;
                result.downloaded = downloaded;
                result.total = total;
                if (cancel && downloaded > 0) {
                    task.cancel();
                    callback.countDown();
                }
            }
            @Override public void onComplete(File apk) {
                result.apk = apk;
                callback.countDown();
            }
            @Override public void onFailure(IOException error) {
                result.error = error;
                callback.countDown();
            }
        });
        try {
            assertTrue("下载应结束或被取消", callback.await(5, TimeUnit.SECONDS));
        } finally {
            client.dispatcher().executorService().shutdown();
            assertTrue("等待后台清理文件", client.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS));
            client.connectionPool().evictAll();
        }
        return result;
    }

    private static final class Result {
        File apk;
        IOException error;
        long initialTotal;
        long downloaded;
        long total;
    }
}
