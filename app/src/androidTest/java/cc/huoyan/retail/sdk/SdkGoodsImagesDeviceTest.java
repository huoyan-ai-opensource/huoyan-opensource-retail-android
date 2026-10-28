package cc.huoyan.retail.sdk;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.widget.ImageView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.bumptech.glide.Glide;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 用可覆盖的本地 HTTP 图片验证 SDK 复用 UID 地址时的真实 Glide 行为。 */
@RunWith(AndroidJUnit4.class)
public class SdkGoodsImagesDeviceTest {
    /** 同一视图和同一 URL 在失效后必须显示新图片，新建视图也不能命中旧缓存。 */
    @Test public void reusedUidReloadsPixelsInSameAndNewViews() throws Exception {
        Instrumentation ins = InstrumentationRegistry.getInstrumentation();
        AtomicReference<byte[]> pixels = new AtomicReference<>(png(Color.RED));
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread serving = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket client = server.accept()) {
                        client.setSoTimeout(5000);
                        BufferedReader request = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        String line;
                        while ((line = request.readLine()) != null && !line.isEmpty()) { }
                        byte[] body = pixels.get();
                        client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\n"
                                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                        client.getOutputStream().write(body);
                        client.getOutputStream().flush();
                    } catch (Exception e) {
                        if (!server.isClosed()) serverFailure.set(e);
                    }
                }
            }, "sdk-test-images");
            serving.setDaemon(true);
            serving.start();
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/images/12.jpg";
            ImageView[] views = new ImageView[2];
            try {
                ins.runOnMainSync(() -> {
                    for (int i = 0; i < views.length; i++) {
                        views[i] = new ImageView(ins.getTargetContext());
                        views[i].layout(0, 0, 32, 32);
                    }
                    SdkGoodsImages.load(views[0], url);
                });
                awaitColor(ins, views[0], Color.RED);
                pixels.set(png(Color.BLUE));
                ins.runOnMainSync(() -> {
                    SdkGoodsImages.invalidate();
                    SdkGoodsImages.load(views[0], url);
                    SdkGoodsImages.load(views[1], url);
                });
                awaitColor(ins, views[0], Color.BLUE);
                awaitColor(ins, views[1], Color.BLUE);
                assertNull(serverFailure.get());
            } finally {
                ins.runOnMainSync(() -> {
                    for (ImageView view : views) if (view != null) Glide.with(view).clear(view);
                });
                server.close();
                serving.join(5000);
            }
        }
    }

    private static byte[] png(int color) {
        Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        bitmap.recycle();
        return output.toByteArray();
    }

    private static void awaitColor(Instrumentation ins, ImageView view, int color) {
        AtomicBoolean matches = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (!matches.get() && SystemClock.elapsedRealtime() < deadline) {
            ins.runOnMainSync(() -> {
                Drawable drawable = view.getDrawable();
                if (drawable instanceof BitmapDrawable) {
                    Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                    Bitmap readable = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                    matches.set(readable.getPixel(0, 0) == color);
                    readable.recycle();
                }
            });
            if (!matches.get()) SystemClock.sleep(50);
        }
        assertTrue("同一 UID 地址应显示颜色 " + color, matches.get());
    }
}
