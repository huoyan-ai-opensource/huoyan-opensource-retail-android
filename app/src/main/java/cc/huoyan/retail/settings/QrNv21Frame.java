package cc.huoyan.retail.settings;

import cc.huoyan.retail.device.camera.CameraFrame;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

/** 使用规范化 NV21 亮度后台扫码，避免截图绕过 TextureView 的方向变换。 */
final class QrNv21Frame implements QrScanPresenter.Frame {
    /** 当前任务独占的只读帧引用，结束后释放。 */
    private CameraFrame frame;
    /** 接管独立帧引用，不复制或修改像素。 */
    QrNv21Frame(CameraFrame frame) { this.frame = frame; }
    /** 后台直接解码亮度平面，无 Bitmap 转换及主线程拷贝。 */
    @Override public String decode() {
        int width = frame.getWidth(), height = frame.getHeight();
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(frame.getNv(), width, height, 0, 0, width, height, false);
        try { return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText(); }
        catch (NotFoundException | ChecksumException | FormatException noCode) { return null; }
    }
    /** 释放帧引用，由 GC 回收数组，不影响其他消费者。 */
    @Override public void close() { frame = null; }
}
