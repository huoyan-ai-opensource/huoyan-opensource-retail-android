package cc.huoyan.retail.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IOThreadHelper {
    private static IOThreadHelper instance;
    private final ExecutorService executor;

    // 单例模式
    private IOThreadHelper() {
        // 创建一个核心线程数为3的线程池
        executor = Executors.newFixedThreadPool(2);
    }

    public static synchronized IOThreadHelper getInstance() {
        if (instance == null) {
            instance = new IOThreadHelper();
        }
        return instance;
    }


    public void post(Runnable runnable) {
        executor.execute(runnable);
    }

    public void shutdown() {
        executor.shutdown();
    }

}
