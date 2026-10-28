package cc.huoyan.retail.device.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;

import java.io.ByteArrayOutputStream;

public class NV21ToBitmap {

    private RenderScript rs;
    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;

    public NV21ToBitmap(Context context) {
        rs = RenderScript.create(context);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    }

    private Allocation inputAllocation;
    private Allocation outputAllocation;
    private Type inputType;
    private Type outputType;
    private int inputLength;
    private int frameWidth;
    private int frameHeight;

    /** 转换 NV21，复用同尺寸的 RenderScript 资源，返回独立且由调用方回收的位图。 */
    public synchronized Bitmap nv21ToBitmap(byte[] nv21, int width, int height) {
        if (rs == null) throw new IllegalStateException("转换器已释放");
        if (inputAllocation == null || inputLength != nv21.length
                || frameWidth != width || frameHeight != height) {
            releaseAllocations();
            try {
                inputType = new Type.Builder(rs, Element.U8(rs)).setX(nv21.length).create();
                inputAllocation = Allocation.createTyped(rs, inputType, Allocation.USAGE_SCRIPT);
                outputType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height).create();
                outputAllocation = Allocation.createTyped(rs, outputType, Allocation.USAGE_SCRIPT);
                yuvToRgbIntrinsic.setInput(inputAllocation);
                inputLength = nv21.length;
                frameWidth = width;
                frameHeight = height;
            } catch (RuntimeException e) {
                releaseAllocations();
                throw e;
            }
        }
        inputAllocation.copyFrom(nv21);
        yuvToRgbIntrinsic.forEach(outputAllocation);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            outputAllocation.copyTo(bitmap);
            return bitmap;
        } catch (RuntimeException e) {
            bitmap.recycle();
            throw e;
        }
    }

    private void releaseAllocations() {
        if (outputAllocation != null) outputAllocation.destroy();
        if (inputAllocation != null) inputAllocation.destroy();
        if (outputType != null) outputType.destroy();
        if (inputType != null) inputType.destroy();
        outputAllocation = null;
        inputAllocation = null;
        outputType = null;
        inputType = null;
    }

    public byte[] bitmapToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }

    /** 释放转换资源；与转换互斥，可重复调用。 */
    public synchronized void release() {
        releaseAllocations();
        if (yuvToRgbIntrinsic != null) {
            yuvToRgbIntrinsic.destroy();  // 销毁 YUV 转换器
            yuvToRgbIntrinsic = null;
        }
        if (rs != null) {
            rs.destroy();                // 销毁 RenderScript 实例
            rs = null;
        }
    }

}
