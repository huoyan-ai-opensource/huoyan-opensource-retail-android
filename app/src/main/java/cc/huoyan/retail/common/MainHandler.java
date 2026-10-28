package cc.huoyan.retail.common;

import android.os.Handler;
import android.os.Looper;

public class MainHandler extends Handler {

    private static MainHandler instance = new MainHandler(Looper.getMainLooper());

    protected MainHandler(Looper looper) {
        super(looper);
    }

    public static MainHandler get() {
        return instance;
    }

    public static void runOnUiThread(Runnable runnable) {
        if(Looper.getMainLooper().equals(Looper.myLooper())) {
            runnable.run();
        } else {
            instance.post(runnable);
        }
    }

    public static void postDelay(Runnable runnable, long delay) {
        instance.postDelayed(runnable, delay);
    }

    public static void removeCallback(Runnable runnable) {
        instance.removeCallbacks(runnable);
    }
}
