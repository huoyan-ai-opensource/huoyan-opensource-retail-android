package cc.huoyan.retail.diagnostics;

import cc.huoyan.retail.common.GsonUtil;
import cc.huoyan.retail.common.IOThreadHelper;
import cc.huoyan.retail.common.QRCodeUtil;

import android.util.Log;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Logger {
    public static void logError(String logTag, Exception e) {
        Log.e(logTag, e.getMessage());
    }

    public static void logError(String logTag, String message, Exception e) {
        Log.e(logTag, message + e.getMessage());
    }

    public static void logError(String logTag, String message) {
        Log.e(logTag, message);
        IOThreadHelper.getInstance().post(Logger::createAppLogs);
    }

    public static void logWarn(String logTag, String message) {
        Log.w(logTag, message);
        IOThreadHelper.getInstance().post(Logger::createAppLogs);
    }

    /**
     * 保存SDK响应日志信息
     *
     * @param cost      耗时
     * @param ret       状态码
     * @param results   响应内容
     * @param isConfirm 是否最终输出（是否打印）
     * @param path      存储图片路径
     */
    public static void logSdk(long cost, int ret, cc.huoyan.sdk.client.model.RecognizeResult results, boolean isConfirm, String path) {
        SdkLogEntity entity = new SdkLogEntity();
        entity.ret = ret;
        entity.cost = cost;
        entity.sdk_result = results;
        entity.is_print = isConfirm;
        entity.path = path;
        String json = GsonUtil.toJson(entity);
        IOThreadHelper.getInstance().post(() -> saveSdkLog(json));
        Log.i("SDK-LOG", json);
    }

    public static void logInfo(String logTag, String message) {
        Log.i(logTag, message);
    }

    public static void logDebug(String logTag, String message) {
        Log.d(logTag, message);
    }

    public static void logVerbose(String logTag, String message) {
        Log.v(logTag, message);
    }

    public static void createAppLogs() {
        StringBuilder logOutput = new StringBuilder();
        try {
            // 构建Logcat命令
            String command = "/system/bin/logcat -b main -d -v threadtime -t 200 --pid " + android.os.Process.myPid() + " *:D";
            Process process = Runtime.getRuntime().exec(command);

            // 读取命令输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logOutput.append(line).append("\n");
            }
            reader.close();

            // 等待命令执行完成并获取返回值
            int exitValue = process.waitFor();
            if (exitValue != 0) {
                // 命令执行失败，读取错误输出
                BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()));
                String errorLine;
                StringBuilder errorOutput = new StringBuilder();
                while ((errorLine = errorReader.readLine()) != null) {
                    errorOutput.append(errorLine).append("\n");
                }
                errorReader.close();
            }
        } catch (IOException | InterruptedException e) {
            Log.e("Logger", "error", e);
        }
        FileWriterUtil.createAndWriteToTxtLegacy("retail/log/app/" + QRCodeUtil.getProductionDay("yyMMdd"),
                QRCodeUtil.getProductionDay("HHmmss"), logOutput.toString());
    }

    public static void saveSdkLog(String msg) {
        FileWriterUtil.createAndAppendToTxtLegacy("retail/log/app/" + QRCodeUtil.getProductionDay("yyMMdd"),
                "SDK_RESULTS", msg);
    }

}
