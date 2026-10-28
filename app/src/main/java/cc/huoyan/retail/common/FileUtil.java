package cc.huoyan.retail.common;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.MediaScannerConnection;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.device.camera.RecognizeAreaEntity;
import cc.huoyan.sdk.client.model.DetectBox;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** 识别与学习共用的图片裁剪、存储和坐标换算。 */
public class FileUtil {

    /**
     * 裁剪图片
     *
     * @param originalBitmap
     * @param x
     * @param y
     * @param width
     * @param height
     * @return
     */
    public static Bitmap cropBitmap(Bitmap originalBitmap, int x, int y, int width, int height, float rorate) {
        // 检查参数是否有效
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > originalBitmap.getWidth() || y + height > originalBitmap.getHeight()) {
            throw new IllegalArgumentException("Invalid crop parameters");
        }
        Matrix matrix = new Matrix();
        // 设置旋转角度为 180°，以裁剪后的 Bitmap 中心为旋转点
        matrix.postRotate(rorate, width / 2f, height / 2f);
        return Bitmap.createBitmap(originalBitmap, x, y, width, height, matrix, true);
    }

    /**
     * 保存图片到外部存储
     *
     * @param context
     * @param bitmap
     * @param folderName
     * @param fileName
     * @return
     */
    public static String saveBitmapToExternalStorage(Context context, Bitmap bitmap, String folderName, String fileName) {
        // 检查外部存储是否可用
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return "";
        }

        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 没有权限，可以在这里请求权限
            return "";
        }

        try {
            // 创建目录
            File dir = new File(Environment.getExternalStorageDirectory(), folderName);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 创建文件
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);

            // 保存图片 (可选择JPEG或PNG格式)
            boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);

            fos.flush();
            fos.close();

            // 通知系统更新媒体库
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, new String[]{"image/jpeg"}, null);

            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static Bitmap corpRecognizeBitmap(Bitmap originalBitmap) {
        // 获取 RecognizeArea
        RecognizeAreaEntity recognizeArea = SPManager.getInstance().getRecognizeArea();
        if (recognizeArea == null) {
            return originalBitmap;
        }

        List<PointF> points = recognizeArea.getPoints();
        if (points.size() != 4) {
            return originalBitmap;
        }

        int imageWidth = originalBitmap.getWidth();
        int imageHeight = originalBitmap.getHeight();
        // 计算宽度和高度的缩放比例
        float widthScale = (float) imageWidth / recognizeArea.getWidth();
        float heightScale = (float) imageHeight / recognizeArea.getHeight();


        // 确定裁剪区域的边界
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = Float.MIN_VALUE;
        float bottom = Float.MIN_VALUE;
        // 映射四个点到图片坐标
        for (PointF point : points) {
            float mappedX = point.x * widthScale;
            float mappedY = point.y * heightScale;
            left = Math.min(left, mappedX);
            top = Math.min(top, mappedY);
            right = Math.max(right, mappedX);
            bottom = Math.max(bottom, mappedY);
        }

        // 确保裁剪区域不超出图片边界
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(imageWidth, right);
        bottom = Math.min(imageHeight, bottom);
        // 裁剪图片
        return Bitmap.createBitmap(originalBitmap, (int) left, (int) top, (int) (right - left), (int) (bottom - top));
    }

    public static float[] invertPoints(RecognitionSelection detectItemEntity, int picWidth, int picHeight) {
        return invertPoints(detectItemEntity.getPoints(), picWidth, picHeight);
    }

    public static float[] invertPoints(DetectBox detectItemEntity, int picWidth, int picHeight) {
        return invertPoints(detectItemEntity.getPoints(), picWidth, picHeight);
    }

    public static float[] invertPoints(float[] originalPoints, int picWidth, int picHeight) {
        if (originalPoints == null || originalPoints.length < 4) {
            // 防御：异常/无匹配条目可能携带 null 或过短 points（真机 NPE：MainDrawView/RecognizeResultPreviewView 绘制路径，2026-08-24）
            return null;
        }
        RecognizeAreaEntity recognizeArea = SPManager.getInstance().getRecognizeArea();
        // 计算宽度和高度的缩放比例
        float widthScale = (float) picWidth / recognizeArea.getWidth();
        float heightScale = (float) picHeight / recognizeArea.getHeight();
        // 映射四个点到图片坐标
        // 确定裁剪区域的边界
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        for (PointF point : recognizeArea.getPoints()) {
            float mappedX = point.x * widthScale;
            float mappedY = point.y * heightScale;
            left = Math.min(left, mappedX);
            top = Math.min(top, mappedY);
        }

        // 确保裁剪区域不超出图片边界
        left = Math.max(0, left);
        top = Math.max(0, top);

        float[] reversedPoints = new float[originalPoints.length];
        for (int i = 0; i < originalPoints.length; i += 2) {
            float croppedX = originalPoints[i] * widthScale;
            float croppedY = originalPoints[i + 1] * heightScale;
            float originalX = (croppedX / widthScale) + left;
            float originalY = (croppedY / heightScale) + top;
            reversedPoints[i] = originalX;
            reversedPoints[i + 1] = originalY;
        }
        return reversedPoints;
    }

    public static float[] convertDetectPointsToOriginal(float[] detectPoints, int picWidth, int picHeight) {
        RecognizeAreaEntity recognizeArea = SPManager.getInstance().getRecognizeArea();
        if (recognizeArea == null || detectPoints == null || detectPoints.length < 4) {
            return new float[0];
        }
        float widthScale = (float) picWidth / recognizeArea.getWidth();
        float heightScale = (float) picHeight / recognizeArea.getHeight();
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = Float.MIN_VALUE;
        float bottom = Float.MIN_VALUE;
        for (PointF point : recognizeArea.getPoints()) {
            float mappedX = point.x * widthScale;
            float mappedY = point.y * heightScale;
            left = Math.min(left, mappedX);
            top = Math.min(top, mappedY);
            right = Math.max(right, mappedX);
            bottom = Math.max(bottom, mappedY);
        }
        left = Math.max(0, left);
        top = Math.max(0, top);
        float[] originalPoints = new float[detectPoints.length];
        for (int i = 0; i < detectPoints.length; i += 2) {
            originalPoints[i] = detectPoints[i] + left;
            originalPoints[i + 1] = detectPoints[i + 1] + top;
        }
        return originalPoints;
    }

    public static float[] convertDetectPointsToOriginal(RecognitionSelection detectItemEntity, int picWidth, int picHeight) {
        if (detectItemEntity == null) {
            return new float[0];
        }
        return convertDetectPointsToOriginal(detectItemEntity.getPoints(), picWidth, picHeight);
    }
}
