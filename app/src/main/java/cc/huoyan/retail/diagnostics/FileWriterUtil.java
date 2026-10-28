package cc.huoyan.retail.diagnostics;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;

public class FileWriterUtil {
    private static final String LOG_TAG = "FileWriterUtil";

    /**
     * 在外部存储创建TXT文件并写入内容（Android 10以下）
     */
    public static boolean createAndWriteToTxtLegacy(String folderName, String fileName, String content) {
        if (!Objects.equals(Environment.getExternalStorageState(), Environment.MEDIA_MOUNTED)) {
            Log.e(LOG_TAG, "外部存储不可用");
            return false;
        }

        try {
            // 创建文件夹
            File folder = new File(Environment.getExternalStorageDirectory(), folderName);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            // 创建文件
            File file = new File(folder, fileName + ".txt");
            if (!file.exists()) {
                file.createNewFile();
            }

            // 写入内容
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(content);
            writer.close();
            return true;
        } catch (IOException e) {
            Log.e(LOG_TAG, "文件操作失败", e);
            return false;
        }
    }

    public static boolean createAndAppendToTxtLegacy(String folderName, String fileName, String content) {
        // 1. 检查外部存储状态
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.e(LOG_TAG, "外部存储不可用");
            return false;
        }

        BufferedWriter writer = null;
        try {
            // 2. 创建目标文件夹（如果不存在）
            File folder = new File(Environment.getExternalStorageDirectory(), folderName);
            if (!folder.exists() && !folder.mkdirs()) {
                Log.e(LOG_TAG, "文件夹创建失败: " + folder.getAbsolutePath());
                return false;
            }

            // 3. 创建或追加文件
            File file = new File(folder, fileName + ".txt");
            if (!file.exists() && !file.createNewFile()) {
                Log.e(LOG_TAG, "文件创建失败: " + file.getAbsolutePath());
                return false;
            }

            // 4. 追加写入内容（关键修改点）
            writer = new BufferedWriter(new FileWriter(file, true)); // true表示追加模式
            writer.append(content);
            writer.append("\n"); // 可选：追加换行符分隔每次写入
            return true;

        } catch (IOException e) {
            Log.e(LOG_TAG, "写入文件失败", e);
            return false;
        } finally {
            // 5. 确保关闭流
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "关闭流失败", e);
                }
            }
        }
    }

}