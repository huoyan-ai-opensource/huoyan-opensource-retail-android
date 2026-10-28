package cc.huoyan.retail.common;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import cc.huoyan.retail.checkout.GoodsVo;
import cc.huoyan.retail.device.camera.RecognizeAreaEntity;
import cc.huoyan.retail.checkout.RecognitionSelection;
import cc.huoyan.sdk.client.model.DetectBox;
import cc.huoyan.retail.data.SPManager;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class FileUtil {
    private static final String LOG_TAG = "FileUtil";

    public static String getFileType(String filename) {
        if ((filename != null) && (!filename.isEmpty())) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length() - 1))) {
                return filename.substring(dot + 1);
            }
        }
        return null;
    }

    public static void copyRawResourceToFile(Context context, int resourceId, File destinationFile) throws IOException {
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        FileOutputStream outputStream = new FileOutputStream(destinationFile);

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        outputStream.close();
    }

    /**
     * 将项目中的静态文件释放对应目录下
     *
     * @param context        上下文
     * @param assetsPath     静态文件路径
     * @param destinationDir 释放路径
     */
    public static void copyAssetsToDirectory(Context context, String assetsPath, File destinationDir, boolean is_replace) throws IOException {
        String[] files = context.getAssets().list(assetsPath);
        // 检查目标文件夹是否存在，如果不存在则创建
        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        // 目标文件夹下的文件数组
        File[] destinationDirFiles = destinationDir.listFiles();
        if ((files != null && destinationDirFiles.length == 0) || is_replace) {
            for (String file : files) {
                File outFile = new File(destinationDir, file);
                InputStream inputStream = context.getAssets().open(assetsPath + File.separator + file);
                OutputStream outputStream = new FileOutputStream(outFile);

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                inputStream.close();
                outputStream.close();
            }
        }
    }

    /**
     * 将assets文件放入应用内部存储
     */
    public static String copyAssetToInternalStorage(Context context, String assetFileName) {
        File internalFile = new File(context.getFilesDir(), assetFileName);

        try {
            // 检查是否已存在
            if (internalFile.exists()) {
                return internalFile.getAbsolutePath();
            }

            // 从 assets 读取并写入内部存储
            InputStream inputStream = context.getAssets().open(assetFileName);
            FileOutputStream outputStream = new FileOutputStream(internalFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();

            return internalFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 删除目录（文件夹）以及目录下的文件
     *
     * @param sPath 被删除目录的文件路径
     * @return 目录删除成功返回true，否则返回false
     */
    public static boolean deleteDirectory(String sPath) {
        // 如果sPath不以文件分隔符结尾，自动添加文件分隔符
        if (!sPath.endsWith(File.separator)) {
            sPath = sPath + File.separator;
        }
        File dirFile = new File(sPath);
        // 如果dir对应的文件不存在，或者不是一个目录，则退出
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            return false;
        }
        boolean flag = true;
        // 删除文件夹下的所有文件(包括子目录)
        File[] files = dirFile.listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            // 删除子文件
            if (files[i].isFile()) {
                flag = deleteFile(files[i].getAbsolutePath());
                if (!flag) break;
            } // 删除子目录
            else {
                flag = deleteDirectory(files[i].getAbsolutePath());
                if (!flag) break;
            }
        }
        if (!flag) return false;
        // 删除当前目录
        if (dirFile.delete()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 删除单个文件
     *
     * @param sPath 被删除文件的文件名
     * @return 单个文件删除成功返回true，否则返回false
     */
    public static boolean deleteFile(String sPath) {
        boolean flag = false;
        File file = new File(sPath);
        // 路径为文件且不为空则进行删除
        if (file.isFile() && file.exists()) {
            file.delete();
            flag = true;
        }
        return flag;
    }

    /*
     * 将文本写入文件中
     * */
    public static void writeContentToFile(Context context, String parentDir, String content, String fileName) {
        try {
            // 获取目标文件路径
            File directory = context.getFilesDir();
            File targetDirectory = new File(directory, parentDir);
            if (!doesDirectoryExist(targetDirectory)) {
                targetDirectory.mkdirs(); // 创建目录（如果不存在）
            }
            File targetFile = new File(targetDirectory, fileName);
            targetFile.setExecutable(true);
            // 使用 OutputStreamWriter 写入文件
            FileOutputStream fos = new FileOutputStream(targetFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos);

            osw.write(content);

            osw.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * 特定文件夹是否为空
     * */
    public static boolean isDirectoryEmpty(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            String[] files = directory.list();
            return files == null || files.length == 0;
        }
        return true;
    }

    /*
     * 特定文件夹是否为存在
     * */
    public static boolean doesDirectoryExist(File directory) {
        return directory.exists() && directory.isDirectory();
    }

    // 判断单个文件是否存在
    public static boolean isFileExists(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }

    public static boolean areFilesIdentical(String path1, String path2) {
        File file1 = new File(path1);
        File file2 = new File(path2);

        // 快速检查文件基础属性
        if (file1.length() != file2.length()) {
            return false;
        }

        try {
            byte[] bytes1 = null;
            byte[] bytes2 = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bytes1 = Files.readAllBytes(file1.toPath());
                bytes2 = Files.readAllBytes(file2.toPath());
            }
            return Arrays.equals(bytes1, bytes2); // 严格逐字节比较
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败: " + e.getMessage());
        }
    }

    /** */
    /**
     * 把字节数组转换成16进制字符串
     */
    public static String bytesToHexString(byte[] bArray) {
        StringBuilder sb = new StringBuilder(bArray.length);
        String sTemp;
        for (byte b : bArray) {
            sTemp = Integer.toHexString(0xFF & b);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }

    /**
     * 重命名文件夹
     *
     * @param oldPath 原路径
     * @param newPath 新路径
     */
    public static void renameFolder(String oldPath, String newPath) {
        File oldFolder = new File(oldPath);
        File newFolder = new File(newPath);

        if (oldFolder.exists()) {
            // 如果目标文件夹存在，先删除
            if (newFolder.exists()) {
                deleteDirectory(newPath);
            }
            // 重命名文件夹
            boolean success = oldFolder.renameTo(newFolder);
            if (!success) {
                throw new RuntimeException("重命名文件夹失败: " + oldPath);
            }
        }
    }

    /**
     * 对文件进行重命名
     */
    public static void renameFile(Context context, String oldFileName, String newFileName) {
        // 获取外部缓存目录的完整路径，并拼接原始文件名
        String oldFilePath = context.getExternalCacheDir().getAbsolutePath() + "/" + oldFileName;
        // 获取新文件的完整路径
        String newFilePath = context.getExternalCacheDir().getAbsolutePath() + "/" + newFileName;

        // 创建File对象
        File oldFile = new File(oldFilePath);
        File newFile = new File(newFilePath);

        // 检查原始文件是否存在
        if (oldFile.exists()) {
            // 执行重命名操作
            if (oldFile.renameTo(newFile)) {
                System.out.println("文件重命名成功: " + oldFileName + " -> " + newFileName);
            } else {
                System.out.println("文件重命名失败");
            }
        } else {
            System.out.println("原始文件不存在: " + oldFileName);
        }
    }

    /**
     * 复制单个文件
     *
     * @param sourcePath 源文件路径
     * @param destPath   目标文件路径
     */
    public static void copyFile(String sourcePath, String destPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(new File(sourcePath)); FileOutputStream fos = new FileOutputStream(new File(destPath))) {

            // 使用FileChannel进行文件复制
            FileChannel sourceChannel = fis.getChannel();
            FileChannel destChannel = fos.getChannel();
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    /**
     * 将 RGBA 数据转换为 PNG 或 JPG 格式的 byte[]
     *
     * @param rgbaData RGBA byte[]数据
     * @param width    原始宽度
     * @param height   原始高度
     * @param format   图片格式
     * @param quality  压缩率
     * @return PNG 或 JPG 格式的 byte[]
     */
    public static byte[] convertRGBAtoImage(byte[] rgbaData, int width, int height, Bitmap.CompressFormat format, int quality, boolean needCorp) {
        // 1. 创建一个Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // 2. 将 RGBA 数据包装为 ByteBuffer，并写入 Bitmap
        ByteBuffer buffer = ByteBuffer.wrap(rgbaData);
        bitmap.copyPixelsFromBuffer(buffer);
        if (needCorp) bitmap = corpRecognizeBitmap(bitmap);

        // 3. 使用 ByteArrayOutputStream 来压缩 Bitmap 到 PNG/JPG 格式
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(format, quality, outputStream);


        // 4. 返回压缩后的字节数组
        return outputStream.toByteArray();
    }

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

    public static List<File> getImagesFromExternalStorage(Context context, String folderPath) {
        // 检查外部存储是否可用
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return new ArrayList<>();
        }

        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 没有权限，可以在这里请求权限
            return new ArrayList<>();
        }
        // 获取外部存储目录
        File externalStorageDir = Environment.getExternalStorageDirectory();
        // 定位目标文件夹
        File targetFolder = new File(externalStorageDir, folderPath);

        if (targetFolder.exists() && targetFolder.isDirectory()) {
            // 获取目标文件夹下的所有文件
            File[] files = targetFolder.listFiles();
            if (files != null) {
                List<File> imageFiles = new ArrayList<>();
                // 筛选出图片文件
                for (File file : files) {
                    if (isImageFile(file.getName())) {
                        imageFiles.add(file);
                    }
                }
                return imageFiles;
            }
        }
        return new ArrayList<>();
    }


    private static boolean isImageFile(String fileName) {
        // 检查文件扩展名是否为常见的图片格式
        String lowerCaseFileName = fileName.toLowerCase();
        return lowerCaseFileName.endsWith(".jpg") || lowerCaseFileName.endsWith(".jpeg") || lowerCaseFileName.endsWith(".png");
    }

    /**
     * 将 Bitmap 转换为 byte 数组
     *
     * @param bitmap  要转换的 Bitmap
     * @param format  图片格式 (Bitmap.CompressFormat.PNG, JPEG 或 WEBP)
     * @param quality 压缩质量 (0-100)，仅对 JPEG 和 WEBP 有效
     * @return byte 数组
     */
    public static byte[] bitmapToByteArray(Bitmap bitmap, Bitmap.CompressFormat format, int quality, boolean needCorp) {
        if (needCorp) bitmap = corpRecognizeBitmap(bitmap);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(format, quality, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        try {
            byteArrayOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArray;
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


    // 保存识别图片
    public static void saveDetectPic(Context mContext, List<GoodsVo> goodsVos, int width, int height, byte[] byteArray) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        bitmap.copyPixelsFromBuffer(buffer);
        List<String> nameList = goodsVos.stream().map(vo -> vo.getGoods().getName()).collect(Collectors.toList());

        SimpleDateFormat yyMMdd = new SimpleDateFormat("yyMMdd", Locale.CHINA);
        Calendar calendar = Calendar.getInstance();
        saveBitmapToExternalStorage(mContext, bitmap, "retail/detect/" + yyMMdd.format(calendar.getTime()), String.join(",", nameList) + "_" +
                System.currentTimeMillis() % 1000000 + ".png");
        calendar.add(Calendar.DAY_OF_YEAR, -60);
        File directory = new File(Environment.getExternalStorageDirectory(),
                "retail/detect/" + yyMMdd.format(calendar.getTime()));
        if (directory.exists() && directory.isDirectory()) {
            deleteDirectory(directory.getPath());
        }
    }

    public static Bitmap convertFileToBitmap(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        // 使用 BitmapFactory.decodeFile 方法将文件解码为 Bitmap
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    public static Bitmap convertFileToBitmap(String path) {
        return convertFileToBitmap(new File(path));
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

    /**
     * 读取文件内容并转为字符串
     */
    public static String getFileContent(String filePath) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                InputStream inputStream = new FileInputStream(file);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
                bufferedReader.close();
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

}
