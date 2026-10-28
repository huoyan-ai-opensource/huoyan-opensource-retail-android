package cc.huoyan.retail.settings;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.ViewModel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 跨配置变化保留打印任务与等待状态，页面最终退出后有序释放后台队列。 */
public final class PrintSettingsModel extends ViewModel {
    /** 后台队列不捕获 Activity；shutdown 不打断已经提交的打印。 */
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** 只在主线程访问的页面协调器。 */
    final PrintSettingsPresenter presenter = new PrintSettingsPresenter(null, worker,
            new Handler(Looper.getMainLooper())::post, new PrinterSettingsDevice());

    /** 供 ViewModelProvider 创建无页面引用的任务宿主。 */
    public PrintSettingsModel() { }

    /** 页面最终销毁时解除视图并等待已提交任务自然结束。 */
    @Override protected void onCleared() {
        presenter.detach();
        worker.shutdown();
    }
}
