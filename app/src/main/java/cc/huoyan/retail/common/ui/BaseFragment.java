package cc.huoyan.retail.common.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;

/**
 * Fragment 基类
 * <p>
 * 功能特性：
 * 1. EventBus 自动管理，支持配置是否启用
 * 2. 懒加载支持，可配置是否每次可见都重新加载
 * 3. 生命周期安全检查，防止空指针和内存泄漏
 * 4. Handler 自动清理，防止内存泄漏
 * </p>
 */
public abstract class BaseFragment extends Fragment {

    private static final String TAG = "BaseFragment";

    // Handler 使用弱引用持有 Fragment，防止内存泄漏
    private final SafeHandler safeHandler = new SafeHandler(this);

    protected boolean isViewCreated = false;   // 视图是否已创建
    protected boolean isDataLoaded = false;     // 数据是否已加载（懒加载用）

    // 使用弱引用防止 Context 内存泄漏，但保留 protected 字段以便子类使用
    private WeakReference<Context> contextRef;
    private WeakReference<Activity> activityRef;

    /**
     * @deprecated 使用 getContextSafe() 代替
     * 保留此字段是为了兼容性，但可能返回 null
     */
    @Deprecated
    protected Context mContext;

    /**
     * @deprecated 使用 getActivitySafe() 代替
     * 保留此字段是为了兼容性，但可能返回 null
     */
    @Deprecated
    protected Activity mActivity;

    // EventBus 注册状态
    private boolean isEventBusRegistered = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // 保存 Context 引用（使用弱引用）
        contextRef = new WeakReference<>(context);
        mContext = context; // 兼容性
        if (context instanceof Activity) {
            activityRef = new WeakReference<>((Activity) context);
            mActivity = (Activity) context; // 兼容性
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResId(), container, false);
        isViewCreated = true;
        initView(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 在视图创建完成后注册 EventBus
        if (useEventBus()) {
            registerEventBus();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 检查是否需要加载数据（懒加载）
        if (isLazyLoad()) {
            checkAndLoadData();
            // 独立宿主首次提交 Fragment 时，onResume 可能早于视图挂接窗口。
            // 布局队列后重试可见性检查，避免首屏永远停留在静态占位值。
            View content = getView();
            if (content != null) content.post(this::checkAndLoadData);
        }
        // 确保 EventBus 已注册
        if (useEventBus() && !isEventBusRegistered) {
            registerEventBus();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // 清理 Handler 中待执行的回调
        safeHandler.removeAllCallbacks();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            // Fragment 隐藏时的处理
            onFragmentHidden();
            // 根据配置决定是否注销 EventBus
            if (useEventBus() && unregisterEventBusWhenHidden()) {
                unregisterEventBus();
            }
        } else {
            // Fragment 显示时的处理
            onFragmentVisible();
            // 重新注册 EventBus
            if (useEventBus() && unregisterEventBusWhenHidden()) {
                registerEventBus();
            }
            // Fragment 显示时检查是否需要加载数据
            if (isLazyLoad()) {
                if (reloadWhenVisible()) {
                    // 每次可见都重新加载
                    isDataLoaded = false;
                }
                checkAndLoadData();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 注销 EventBus
        if (useEventBus()) {
            unregisterEventBus();
        }
        // 清理 Handler 回调
        safeHandler.removeAllCallbacks();
        // 重置视图状态
        isViewCreated = false;
        // 不重置 isDataLoaded，因为 Fragment 可能只是视图重建而不是真正销毁
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 再次确保 EventBus 已注销
        if (useEventBus()) {
            unregisterEventBus();
        }
        // 清理 Handler
        safeHandler.release();
        // 清理引用
        if (contextRef != null) {
            contextRef.clear();
            contextRef = null;
        }
        if (activityRef != null) {
            activityRef.clear();
            activityRef = null;
        }
        // 清理兼容性字段
        mContext = null;
        mActivity = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    // ==================== 子类可重写的方法 ====================

    /**
     * 是否使用 EventBus（默认不使用）
     * 子类重写此方法返回 true 来启用 EventBus
     */
    protected boolean useEventBus() {
        return false;
    }

    /**
     * Fragment 隐藏时是否注销 EventBus（默认不注销）
     * 如果返回 true，则在 onHiddenChanged(hidden=true) 时注销 EventBus
     */
    protected boolean unregisterEventBusWhenHidden() {
        return false;
    }

    /**
     * 是否启用懒加载（默认启用）
     */
    protected boolean isLazyLoad() {
        return true;
    }

    /**
     * 每次 Fragment 可见时是否重新加载数据（默认不重新加载）
     * 如果返回 true，则在 onHiddenChanged(hidden=false) 时会重新加载数据
     */
    protected boolean reloadWhenVisible() {
        return false;
    }

    /**
     * Fragment 显示时的回调（子类可重写）
     */
    protected void onFragmentVisible() {
        // 默认空实现
    }

    /**
     * Fragment 隐藏时的回调（子类可重写）
     */
    protected void onFragmentHidden() {
        // 默认空实现
    }

    // ==================== 工具方法 ====================

    /**
     * 获取 Context（安全）
     */
    protected Context getContextSafe() {
        if (contextRef != null) {
            Context context = contextRef.get();
            if (context != null) {
                return context;
            }
        }
        return super.getContext();
    }

    /**
     * 获取 Activity（安全）
     */
    protected Activity getActivitySafe() {
        if (activityRef != null) {
            Activity activity = activityRef.get();
            if (activity != null && !activity.isDestroyed()) {
                return activity;
            }
        }
        Activity activity = getActivity();
        if (activity != null && !activity.isDestroyed()) {
            return activity;
        }
        return null;
    }

    /**
     * 检查 Fragment 是否安全可用
     */
    protected boolean isFragmentSafe() {
        return isAdded() && getActivity() != null && !isRemoving() && !isDetached();
    }

    /**
     * 检查并加载数据（懒加载逻辑）
     */
    private void checkAndLoadData() {
        if (isViewCreated && isVisible() && isResumed() && !isDataLoaded && isFragmentSafe()) {
            loadData();
            isDataLoaded = true;
        }
    }

    /**
     * 在主线程执行任务
     */
    public void postMainHandler(Runnable runnable) {
        if (isFragmentSafe()) {
            safeHandler.post(runnable);
        }
    }

    /**
     * 延迟在主线程执行任务
     */
    public void postMainHandler(Runnable runnable, long delayMillis) {
        if (isFragmentSafe()) {
            safeHandler.postDelayed(runnable, delayMillis);
        }
    }

    /**
     * 移除所有待执行的 Handler 回调
     */
    public void removeAllCallbacks() {
        safeHandler.removeAllCallbacks();
    }

    /**
     * 显示 Toast（安全）
     */
    protected void showToast(Context context, String msg) {
        if (context != null && isFragmentSafe()) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }
    }

    // ==================== EventBus 管理 ====================

    /**
     * 注册 EventBus（带异常保护）
     */
    protected void registerEventBus() {
        if (isEventBusRegistered) {
            return;
        }

        try {
            if (!EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().register(this);
                isEventBusRegistered = true;
//                Log.d(TAG, getClass().getSimpleName() + " - EventBus 注册成功");
            }
        } catch (Exception e) {
            Log.e(TAG, getClass().getSimpleName() + " - EventBus 注册失败", e);
            isEventBusRegistered = false;
        }
    }

    /**
     * 注销 EventBus（带异常保护）
     */
    protected void unregisterEventBus() {
        if (!isEventBusRegistered) {
            return;
        }

        try {
            if (EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().unregister(this);
//                Log.d(TAG, getClass().getSimpleName() + " - EventBus 注销成功");
            }
        } catch (Exception e) {
            Log.e(TAG, getClass().getSimpleName() + " - EventBus 注销失败", e);
        } finally {
            isEventBusRegistered = false;
        }
    }

    // ==================== 子类必须实现的方法 ====================

    /**
     * 获取布局资源 ID
     */
    protected abstract int getLayoutResId();

    /**
     * 初始化视图
     */
    protected abstract void initView(View view);

    /**
     * 加载数据（懒加载时调用）
     */
    protected abstract void loadData();

    // ==================== 内部类 ====================

    /**
     * 安全的 Handler，使用弱引用持有 Fragment，防止内存泄漏
     */
    private static class SafeHandler extends Handler {
        private WeakReference<BaseFragment> fragmentRef;

        SafeHandler(BaseFragment fragment) {
            super(Looper.getMainLooper());
            this.fragmentRef = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(@NonNull android.os.Message msg) {
            BaseFragment fragment = fragmentRef.get();
            if (fragment != null && fragment.isFragmentSafe()) {
                // 如果 Fragment 仍然安全，处理消息
                super.handleMessage(msg);
            }
        }

        void release() {
            if (fragmentRef != null) {
                fragmentRef.clear();
                fragmentRef = null;
            }
            removeCallbacksAndMessages(null);
        }

        void removeAllCallbacks() {
            removeCallbacksAndMessages(null);
        }
    }
}
