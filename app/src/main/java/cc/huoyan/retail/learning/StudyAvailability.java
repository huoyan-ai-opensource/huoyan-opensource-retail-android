package cc.huoyan.retail.learning;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.status.SdkStatus;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 学习保存资格观察；后台只持有模型，页面通过生命周期观察当前有效回执。 */
public final class StudyAvailability extends ViewModel {
    /** 未取得有效授权和模型回执时，保存按钮默认禁用。 */
    final MutableLiveData<Boolean> ready = new MutableLiveData<>(false);
    /** 独立状态读取队列，避免被相机检测占满。 */
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    /** 当前前台观察任务，暂停后取消。 */
    private ScheduledFuture<?> task;
    /** 前后台切换代次，旧回执不能重新启用按钮。 */
    private volatile int generation;
    /** 主线程发布时再次核对代次。 */
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    /** 前台开始周期查询；不以历史激活记录推断当前能力。 */
    void start() {
        stop();
        int token = generation;
        task = worker.scheduleWithFixedDelay(() -> {
            boolean available = false;
            try {
                HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
                available = client.isConnected() && SdkStatus.modelReady(client.getStatus());
            } catch (RuntimeException ignored) { /* 查询失败按不可用处理。 */ }
            boolean current = available;
            ui.post(() -> { if (token == generation) ready.setValue(current); });
        }, 0, 1, TimeUnit.SECONDS);
    }

    /** 暂停时立即撤销保存资格，并丢弃在途查询回执。 */
    void stop() {
        generation++;
        if (task != null) { task.cancel(false); task = null; }
        ready.setValue(false);
    }

    /** 页面最终销毁后释放状态队列。 */
    @Override protected void onCleared() { stop(); worker.shutdown(); }
}
