package cc.huoyan.sdk.client.internal;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import cc.huoyan.app.aidl.ImageRequestParcel;

/** 识别和检测共用的 JPEG 编码，通道仅负责选择传输载体。 */
final class JpegImages {
    private JpegImages() { }

    static ImageRequestParcel encode(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("图像 Bitmap 不能为空或已回收");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(128 * 1024);
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
            throw new IllegalStateException("Bitmap 转 JPEG 失败");
        }
        byte[] jpeg = out.toByteArray();
        return new ImageRequestParcel(null, jpeg, jpeg.length, bitmap.getWidth(), bitmap.getHeight(),
                ImageRequestParcel.FORMAT_JPEG);
    }
}
