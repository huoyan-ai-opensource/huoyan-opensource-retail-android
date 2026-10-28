package cc.huoyan.retail.common.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import java.lang.ref.WeakReference;

import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.R;

/** 应用内一次性结果提示；独立窗口覆盖业务弹窗，不抢焦点、不拦截触摸。 */
public final class ToastEx {
    /** 平台自动化依赖的提示日志标签。 */
    private static final String LOG_TAG = "ToastEx";
    /** 所有窗口和生命周期状态只在主线程读写。 */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** 成功提示的显示时长，与原短 Toast 一致。 */
    private static final long SHORT_DURATION_MS = 2000;
    /** 错误提示的显示时长，与原长 Toast 一致。 */
    private static final long LONG_DURATION_MS = 3500;
    /** 应用上下文调用提示时使用当前前台页面，不延长页面生命周期。 */
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    /** 防止重复注册应用生命周期监听。 */
    private static boolean initialized;
    /** 当前提示的页面；提示关闭或页面暂停时释放。 */
    private static Activity owner;
    /** 当前提示窗口的管理器；只使用所属 Activity 的应用窗口权限。 */
    private static WindowManager windowManager;
    /** 当前提示视图；包内设备测试可核验实际渲染内容，隐藏后立即清空。 */
    static View currentView;
    /** 当前窗口参数；重新置顶不改变显示时长与交互属性。 */
    private static WindowManager.LayoutParams windowParams;
    /** 递归监听当前页面及子 Fragment 中的新弹窗，提示关闭时解除。 */
    private static FragmentManager fragments;
    /** 单次自动关闭任务；替换提示时先移除旧任务。 */
    private static final Runnable HIDE = ToastEx::hideOnMain;
    /** 等待新弹窗完成添加后重新置顶；不会续期提示。 */
    private static final Runnable RAISE = ToastEx::raiseOnMain;
    /** 在提示可见期间跟随新打开的业务弹窗更新窗口顺序。 */
    private static final FragmentManager.FragmentLifecycleCallbacks DIALOGS =
            new FragmentManager.FragmentLifecycleCallbacks() {
                /** DialogFragment 的 onStart 已添加窗口，下一主线程消息再置顶提示。 */
                @Override public void onFragmentStarted(FragmentManager manager, Fragment fragment) {
                    if (fragment instanceof DialogFragment && currentView != null) {
                        MAIN.removeCallbacks(RAISE);
                        MAIN.post(RAISE);
                    }
                }
            };

    /** 工具类不允许实例化。 */
    private ToastEx() { }

    /** Application 启动时注册一次，支持应用上下文提示和页面退出清理；仅主线程调用。 */
    public static void initialize(Application application) {
        if (initialized) return;
        initialized = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            /** 记录真实前台页面，供相机等应用级回调显示提示。 */
            @Override public void onActivityResumed(Activity activity) {
                resumedActivity = new WeakReference<>(activity);
            }
            /** 离开页面时移除提示，不把上一页结果覆盖到下一页。 */
            @Override public void onActivityPaused(Activity activity) {
                if (resumedActivity.get() == activity) resumedActivity.clear();
                if (owner == activity) hideOnMain();
            }
            /** 销毁时再次保证窗口、延迟任务与页面引用均已清理。 */
            @Override public void onActivityDestroyed(Activity activity) {
                if (resumedActivity.get() == activity) resumedActivity.clear();
                if (owner == activity) hideOnMain();
            }
            /** 创建阶段不显示操作结果。 */
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            /** 前台页面在 resumed 阶段确定。 */
            @Override public void onActivityStarted(Activity activity) { }
            /** 停止前已通过 paused 清理提示。 */
            @Override public void onActivityStopped(Activity activity) { }
            /** 一次性提示不参与页面状态恢复。 */
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
        });
    }

    /** 显示带成功图标的短提示，用于保存、连接等操作成功。 */
    public static void showSuccessToast(Context context, String msg) {
        showSuccessToast(context, msg, R.drawable.ic_toast_success);
    }

    /** 读取调用页面的当前语言资源并显示成功提示。 */
    public static void showSuccessToast(Context context, int stringId) {
        if (context != null) showSuccessToast(context, context.getString(stringId));
    }

    /** 默认显示错误图标；成功场景应调用 showSuccessToast。 */
    public static void createToast(Context context, String msg) {
        showFailureToast(context, msg);
    }

    /** 使用调用方指定的图标显示成功短提示。 */
    public static void showSuccessToast(Context context, String msg, int drawableId) {
        show(context, msg, drawableId, SHORT_DURATION_MS);
    }

    /** 使用错误图标显示长提示，支持从后台回调调用。 */
    public static void showFailureToast(Context context, String msg) {
        show(context, msg, R.drawable.ic_toast_warning, LONG_DURATION_MS);
    }

    /** 解开页面或弹窗的主题包装；应用上下文没有 Activity，由主线程选择前台页面。 */
    private static Activity activityFrom(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return null;
    }

    /** 延后到主线程显示，让当前弹窗关闭事务先执行；排队期间不强引用页面。 */
    private static void show(Context context, String msg, int drawableId, long duration) {
        if (context == null) return;
        Activity activity = activityFrom(context);
        WeakReference<Activity> requested = new WeakReference<>(activity);
        boolean applicationContext = activity == null;
        MAIN.post(() -> {
            Activity target = applicationContext ? resumedActivity.get() : requested.get();
            if (target == null || target != resumedActivity.get()
                    || target.isFinishing() || target.isDestroyed()) return;
            showOnMain(target, msg, drawableId, duration);
        });
    }

    /** 以独立应用窗口显示结果，避免系统 Toast 在弹窗退场时出现不可见的合成层。 */
    private static void showOnMain(Activity activity, String msg, int drawableId, long duration) {
        hideOnMain();
        owner = activity;
        windowManager = activity.getWindowManager();
        currentView = LayoutInflater.from(activity).inflate(R.layout.view_toast, null);
        ((TextView) currentView.findViewById(R.id.toast_text)).setText(msg);
        ((ImageView) currentView.findViewById(R.id.toast_image)).setImageResource(drawableId);
        windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.CENTER;
        windowParams.setTitle("ToastEx");
        // token 由 Activity 的 WindowManager 填入；不能使用 View 的子窗口 token。
        try {
            windowManager.addView(currentView, windowParams);
        } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException error) {
            hideOnMain();
            if (BuildConfig.DEBUG) Log.w(LOG_TAG, "提示所属窗口已失效", error);
            return;
        }
        if (activity instanceof FragmentActivity) {
            fragments = ((FragmentActivity) activity).getSupportFragmentManager();
            fragments.registerFragmentLifecycleCallbacks(DIALOGS, true);
        }
        MAIN.postDelayed(HIDE, duration);
        currentView.announceForAccessibility(msg);
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "[Toast] " + msg);
    }

    /** 新弹窗显示后把同一提示重新加入窗口末尾，不抢焦点、不延长剩余时长。 */
    private static void raiseOnMain() {
        if (currentView == null) return;
        if (owner.isFinishing() || owner.isDestroyed()) {
            hideOnMain();
            return;
        }
        try {
            windowManager.removeViewImmediate(currentView);
            windowManager.addView(currentView, windowParams);
        } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException
                 | IllegalArgumentException error) {
            hideOnMain();
            if (BuildConfig.DEBUG) Log.w(LOG_TAG, "提示窗口置顶时页面已退出", error);
        }
    }

    /** 主线程移除窗口、监听和自动关闭任务；包内设备测试也使用此入口清理。 */
    static void hideOnMain() {
        MAIN.removeCallbacks(HIDE);
        MAIN.removeCallbacks(RAISE);
        if (fragments != null) fragments.unregisterFragmentLifecycleCallbacks(DIALOGS);
        if (currentView != null && currentView.getParent() != null) {
            try {
                windowManager.removeViewImmediate(currentView);
            } catch (IllegalArgumentException error) {
                if (BuildConfig.DEBUG) Log.w(LOG_TAG, "提示窗口已被页面销毁移除", error);
            }
        }
        currentView = null;
        windowParams = null;
        windowManager = null;
        fragments = null;
        owner = null;
    }
}
