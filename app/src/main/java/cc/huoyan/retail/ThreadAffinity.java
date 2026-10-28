package cc.huoyan.retail;

public class ThreadAffinity {

    // 静态初始化块加载库
    static {
        System.loadLibrary("socaffinity");
    }

    // Native 方法声明
    private static native int getCores();

    private static native int bindThreadToCore(int core);

    private static native int bindThreadToCores(int[] cores);

    private static native int bindPidToCore(int pid, int core);

    private static native int bindPidToCores(int pid, int[] core);

    // 获取核心数
    public static int getCoresCount() {
        return getCores();
    }

    // 将线程绑定到指定核心并执行代码块
    public static void threadToCore(int core, Runnable block) {
        bindThreadToCore(core);
        block.run();
    }

    // 将线程绑定到多个指定核心并执行代码块
    public static void threadToCore(int[] cores, Runnable block) {
        bindThreadToCores(cores);
        block.run();
    }

    // 将进程绑定到指定核心
    public static void pidToCore(int pid, int core) {
        bindPidToCore(pid, core);
    }

    // 将进程绑定到指定核心组
    public static int pidToCores(int pid, int[] cores) {
        return bindPidToCores(pid, cores);
    }
}
