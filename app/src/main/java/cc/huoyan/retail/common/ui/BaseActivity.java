package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import cc.huoyan.retail.common.EventBusModel;
import cc.huoyan.retail.common.LocalePreferenceUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseActivity extends AppCompatActivity {
    /**
     * 与 EventBus 3.1.1 SubscriberMethodFinder 的 MODIFIERS_IGNORE 一致（bridge=0x40、
     * synthetic=0x1000 非 public API，java.lang.reflect.Modifier 未暴露）。
     */
    private static final int MODIFIERS_IGNORE =
            Modifier.STATIC | Modifier.ABSTRACT | 0x00000040 | 0x00001000;
    private static final Map<Class<?>, Boolean> SUBSCRIBER_CACHE = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    protected Context mContext;
    /** 当前页面创建时使用的语言，用于返回前台时检测系统语言变化。 */
    private Locale renderedLocale;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocalePreferenceUtil.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderedLocale = LocalePreferenceUtil.getCurrentLocale(this);
        overridePendingTransition(0, 0);
        setContentView(initContentView());
        mContext = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!LocalePreferenceUtil.getCurrentLocale(this).equals(renderedLocale)) {
            recreate();
            return;
        }
        if (hasEventSubscriber() && !EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    /** 包装过的资源可能屏蔽语言配置差异，收到变化时按页面创建语言决定是否重建。 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!LocalePreferenceUtil.getCurrentLocale(this).equals(renderedLocale)) recreate();
    }

    /**
     * 判定实例类层次上是否存在 EventBus 可识别的订阅方法，规则与 EventBus 3.1.1
     * 反射查找一致（public + @Subscribe、排除 static/bridge/synthetic、遍历父类
     * 跳过 java./javax./android. 系统包）。基类的 {@link #onMessageEvent} 为
     * protected，EventBus 不计入。没有订阅方法的子类直接 register 会抛 EventBusException，
     * 无订阅方法时跳过注册。
     */
    private boolean hasEventSubscriber() {
        Class<?> target = getClass();
        Boolean cached = SUBSCRIBER_CACHE.get(target);
        if (cached != null) {
            return cached;
        }
        boolean found = false;
        Class<?> clazz = target;
        while (!found && clazz != null) {
            for (Method method : clazz.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0
                        && method.isAnnotationPresent(Subscribe.class)) {
                    found = true;
                    break;
                }
            }
            clazz = clazz.getSuperclass();
            if (clazz != null) {
                String name = clazz.getName();
                if (name.startsWith("java.") || name.startsWith("javax.")
                        || name.startsWith("android.")) {
                    break;
                }
            }
        }
        SUBSCRIBER_CACHE.put(target, found);
        return found;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    /** 页面返回直接切换，不播放系统的左右滑动过渡。 */
    @Override public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    protected abstract int initContentView();

    @Subscribe(threadMode = ThreadMode.MAIN)
    protected void onMessageEvent(EventBusModel event) {

    }

    public void postMainHandler(Runnable runnable) {
        mainHandler.post(runnable);
    }

}
