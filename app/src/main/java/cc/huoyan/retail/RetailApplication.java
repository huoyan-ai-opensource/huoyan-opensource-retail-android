package cc.huoyan.retail;

import static cc.huoyan.retail.ThreadAffinity.pidToCores;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import cc.huoyan.retail.common.LocalePreferenceUtil;

import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;



import xcrash.ICrashCallback;
import xcrash.XCrash;

public class RetailApplication extends Application {
    private String LOG_TAG = "RetailApplication";
    private static RetailApplication mInstance;

    public static RetailApplication getInstance() {
        return mInstance;
    }


    /** 应用级资源同样跟随系统映射，供 Toast、后台任务及 SDK 宿主界面读取。 */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocalePreferenceUtil.wrap(base));
    }

    /** 系统配置变化时更新应用资源；Activity 由系统重建并重新包装资源。 */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Configuration localized = new Configuration(newConfig);
        localized.setLocale(LocalePreferenceUtil.getCurrentLocale(this));
        getResources().updateConfiguration(localized, getResources().getDisplayMetrics());
    }

    // callback for java crash, native crash and ANR
    ICrashCallback callback = (logPath, emergency) -> {
        Log.d(LOG_TAG, "log path: " + (logPath != null ? logPath : "(null)") + ", emergency: " + (emergency != null ? emergency : "(null)"));
    };


    public void onCreate() {
        super.onCreate();
        cc.huoyan.retail.common.ui.ToastEx.initialize(this);
        // 捕获崩溃日志
        xcrash.XCrash.init(this, new XCrash.InitParameters()
                .setAppVersion(BuildConfig.VERSION_NAME)
                .setJavaRethrow(true)
                .setJavaLogCountMax(10)
                .setJavaDumpAllThreadsCountMax(10)
                .setJavaCallback(callback)
                .setNativeRethrow(true)
                .setNativeLogCountMax(10)
                .setNativeDumpAllThreadsCountMax(10)
                .setNativeCallback(callback)
                .setAnrCheckProcessState(false)
                .setAnrRethrow(true)
                .setAnrLogCountMax(10)
                .setAnrCallback(callback)
                .setLogDir(Environment.getExternalStorageDirectory() + "/retail/Huoyan-Retail-Log"));
//        设备CPU大小核分配，有需要可自行启用绑定
//        int pid = android.os.Process.myPid();
//        int result = pidToCores(pid, new int[]{4, 5});
//        Log.d("RetailApplication", pid + " 绑定cpu结果: " + result);
        mInstance = this;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

}
