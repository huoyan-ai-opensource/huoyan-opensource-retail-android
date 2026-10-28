package cc.huoyan.retail.settings;

import android.graphics.Bitmap;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

/** 扫码截图的 ZXing 解码适配；截图所有权由构造调用方转交。 */
final class QrBitmapFrame implements QrScanPresenter.Frame {
    /** 独立预览截图，仅由当前后台任务读取和回收。 */
    private final Bitmap bitmap;
    /** 接管非空截图，不持有相机或页面。 */
    QrBitmapFrame(Bitmap bitmap) { this.bitmap = bitmap; }
    /** 后台按原 RGB 像素及 HybridBinarizer 规则解码二维码。 */
    @Override public String decode() {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        BinaryBitmap source = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        try { return new QRCodeReader().decode(source).getText(); }
        catch (NotFoundException | ChecksumException | FormatException noCode) { return null; }
    }
    /** 无论识别成功、失败或页面已关闭，都释放这张独立截图。 */
    @Override public void close() { bitmap.recycle(); }
}
