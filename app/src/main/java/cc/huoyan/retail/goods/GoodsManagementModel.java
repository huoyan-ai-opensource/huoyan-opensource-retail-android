package cc.huoyan.retail.goods;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 在页面重建期间保留 SDK 写入状态，防止旋转屏幕后重复提交。 */
public final class GoodsManagementModel extends AndroidViewModel {
    /** 串行后台队列；最终离页后仍执行已提交操作。 */
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** 不持有销毁视图的商品交互状态。 */
    final GoodsManagementPresenter presenter;
    /** 装配无上下文依赖的仓库，View 由当前 Activity 单独绑定。 */
    public GoodsManagementModel(Application application) {
        super(application);
        presenter = new GoodsManagementPresenter(null, worker, new Handler(Looper.getMainLooper())::post,
                new GoodsManagementData(new GoodsRepository()));
    }
    /** 永久离页后不再接受任务，允许已有 SDK 操作收尾。 */
    @Override protected void onCleared() { presenter.detach(); worker.shutdown(); }
}
