package cc.huoyan.retail.home;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.ViewModel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 首页存活期间保留云端操作，弹窗关闭重开或旋转不会重复提交。 */
public final class CloudServiceModel extends ViewModel {
    /** 云操作专用串行队列，避免长同步阻塞其他设备任务。 */
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** 无 Activity 引用的主线程投递器。 */
    private final Handler ui = new Handler(Looper.getMainLooper());
    /** 弹窗共享的状态协调器。 */
    final CloudServicePresenter presenter = new CloudServicePresenter(worker, ui::post, new CloudServiceSource());
    /** 供 ViewModelProvider 创建，无外部副作用。 */
    public CloudServiceModel() { }
    /** 页面退出时解绑视图，已提交的后台操作完成后释放线程。 */
    @Override protected void onCleared() { presenter.close(); worker.shutdown(); }
}
