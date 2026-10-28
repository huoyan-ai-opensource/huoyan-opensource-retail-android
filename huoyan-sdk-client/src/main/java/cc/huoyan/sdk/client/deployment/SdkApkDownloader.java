package cc.huoyan.sdk.client.deployment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** SDK 流式下载；每次请求使用独立文件，失败或取消不留下半包。 */
public final class SdkApkDownloader {
    /** 共享网络连接池；使用系统 TLS 信任。 */
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build();
    /** 单次可取消的下载请求。 */
    private final Call call;
    /** 宿主提供的临时文件目录。 */
    private final File directory;

    /** 下载结果在后台线程回调，由宿主切回主线程更新界面。 */
    public interface Listener {
        /** 返回字节进度；总量为负数表示未知。 */
        void onProgress(long downloaded, long total);
        /** 返回完整文件，调用方负责校验 APK 并发起安装。 */
        void onComplete(File apk);
        /** 返回下载失败原因，取消请求不触发此回调。 */
        void onFailure(IOException error);
    }

    /** 创建单次下载，沿用系统 TLS 证书及主机名校验。 */
    public SdkApkDownloader(String url, File directory) {
        this(url, directory, HTTP);
    }

    /** 注入请求工厂用于独立网络测试。 */
    SdkApkDownloader(String url, File directory, Call.Factory calls) {
        this.directory = directory;
        call = calls.newCall(new Request.Builder().url(url).build());
    }

    /** 开始后台下载，同一个实例仅允许调用一次。 */
    public void start(Listener listener) {
        call.enqueue(new Callback() {
            /** 网络失败后通知宿主，主动取消不回调。 */
            @Override public void onFailure(Call failed, IOException error) {
                if (!call.isCanceled()) listener.onFailure(error);
            }

            /** 流式写入，只有完整下载才交付临时文件。 */
            @Override public void onResponse(Call completed, Response response) {
                File apk = null;
                boolean delivered = false;
                try (Response ignored = response) {
                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        throw new IOException("HTTP " + response.code());
                    }
                    apk = File.createTempFile("sdk-shell-", ".apk", directory);
                    long total = body.contentLength();
                    long downloaded = 0;
                    long lastUpdate = 0;
                    listener.onProgress(0, total);
                    try (InputStream input = body.byteStream();
                         FileOutputStream output = new FileOutputStream(apk)) {
                        byte[] buffer = new byte[64 * 1024];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (call.isCanceled()) throw new IOException("Cancelled");
                            output.write(buffer, 0, count);
                            downloaded += count;
                            long now = System.nanoTime();
                            if (now - lastUpdate >= TimeUnit.MILLISECONDS.toNanos(200)) {
                                listener.onProgress(downloaded, total);
                                lastUpdate = now;
                            }
                        }
                    }
                    if (downloaded == 0 || (total >= 0 && downloaded != total)) {
                        throw new IOException("Incomplete APK");
                    }
                    if (call.isCanceled()) return;
                    listener.onProgress(downloaded, downloaded);
                    listener.onComplete(apk);
                    delivered = true;
                } catch (IOException error) {
                    if (!call.isCanceled()) listener.onFailure(error);
                } finally {
                    if (!delivered && apk != null) apk.delete();
                }
            }
        });
    }

    /** 取消连接和读取，后台任务会删除未交付文件。 */
    public void cancel() {
        call.cancel();
    }
}
