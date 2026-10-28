package cc.huoyan.retail.settings;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.ViewModel;
import cc.huoyan.retail.common.IOThreadHelper;

/** 配置变化期间保留激活会话，不持有 Activity、Fragment 或弹窗。 */
public final class ModelActivationViewModel extends ViewModel {
    /** 当前宿主独占的会话，旋转不重发查询和激活。 */
    final ModelActivationPresenter session = new ModelActivationPresenter(
            IOThreadHelper.getInstance()::post, new Handler(Looper.getMainLooper())::post, new ModelActivationSource());
    /** 为 ViewModelProvider 提供无参构造。 */
    public ModelActivationViewModel() { }
    /** 宿主彻底销毁后作废回调，已提交写请求仍可完成。 */
    @Override protected void onCleared() { session.destroy(); }
}
