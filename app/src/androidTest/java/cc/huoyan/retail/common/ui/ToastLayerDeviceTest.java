package cc.huoyan.retail.common.ui;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import cc.huoyan.retail.R;
import cc.huoyan.retail.home.HomeActivity;
import cc.huoyan.retail.settings.ActivateCodeDialog;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** 用真实屏幕像素验证提示未被弹窗遮挡；不提交授权或其他业务写请求。 */
@RunWith(AndroidJUnit4.class)
public class ToastLayerDeviceTest {
    /** 测试线程通过仪器接口切换主线程及注入真实触摸。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    /** 每个用例清理提示和计时器，避免跨用例残留。 */
    @After public void clearToast() {
        ins.runOnMainSync(ToastTestProbe::clear);
    }

    /** 加载结束后连续错误提示必须显示在嵌套弹窗前方。 */
    @Test public void errorsAboveNestedDialogsAfterLoading() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                new AlertDialog("层级回归", "弹窗应位于提示下方", "确认", "取消", null)
                        .showNow(activity.getSupportFragmentManager(), "toastLayer");
                new ActivateCodeDialog().showNow(activity.getSupportFragmentManager(), "activationLayer");
                ActivateCodeDialog dialog = (ActivateCodeDialog) activity.getSupportFragmentManager().findFragmentByTag("activationLayer");
                new LoadingDialog("正在读取…").showNow(dialog.getChildFragmentManager(), "toastLoading");
            });
            settle();
            scenario.onActivity(activity -> {
                ActivateCodeDialog dialog = (ActivateCodeDialog) activity.getSupportFragmentManager().findFragmentByTag("activationLayer");
                LoadingDialog loading = (LoadingDialog) dialog.getChildFragmentManager().findFragmentByTag("toastLoading");
                loading.dismiss();
                ToastEx.createToast(dialog.requireContext(), "操作失败");
                ToastEx.showFailureToast(dialog.requireDialog().getContext(), "弹窗上方错误提示");
            });
            assertVisibleIcon("nested-error", "弹窗上方错误提示");
        }
    }

    /** 提示显示后再打开弹窗仍保持置顶；成功提示替换为错误时更新图标及文案。 */
    @Test public void staysAboveLaterDialogsAndReplacesSuccess() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> ToastEx.showSuccessToast(activity, "操作成功"));
            assertVisibleIcon("success", "操作成功");
            scenario.onActivity(activity -> new ActivateCodeDialog()
                    .showNow(activity.getSupportFragmentManager(), "activationLayer"));
            assertVisibleIcon("success-above-later-dialog", "操作成功");
            scenario.onActivity(activity -> ToastEx.showFailureToast(activity, "后续操作失败"));
            assertVisibleIcon("replacement-error", "后续操作失败");
            // 原成功提示的两秒计时器不得提前关闭新错误提示。
            SystemClock.sleep(1300);
            ins.runOnMainSync(() -> assertEquals("后续操作失败", ToastTestProbe.text()));
            SystemClock.sleep(2200);
            ins.runOnMainSync(() -> assertNull(ToastEx.currentView));
        }
    }

    /** 提示不抢焦点；即使覆盖确认框按钮，其下方真实点击仍可关闭弹窗。 */
    @Test public void keepsDialogFocusAndPassesTouchesThrough() throws Exception {
        Rect overlap = new Rect();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                new AlertDialog("层级回归", "弹窗应位于提示下方", "确认", "取消", null)
                        .showNow(activity.getSupportFragmentManager(), "toastLayer");
                ToastEx.showFailureToast(activity, "不拦截弹窗操作");
            });
            assertVisibleIcon("touch-through", "不拦截弹窗操作");
            scenario.onActivity(activity -> {
                AlertDialog dialog = (AlertDialog) activity.getSupportFragmentManager().findFragmentByTag("toastLayer");
                assertTrue("提示不能抢走业务弹窗焦点", dialog.requireDialog().getWindow().getDecorView().hasWindowFocus());
                Rect button = screenRect(dialog.requireView().findViewById(R.id.cancel));
                Rect toast = screenRect(ToastEx.currentView);
                assertTrue("必须实际点击提示覆盖的按钮部分", overlap.setIntersect(button, toast));
            });
            tap(overlap.exactCenterX(), overlap.exactCenterY());
            settle();
            scenario.onActivity(activity -> assertNull(activity.getSupportFragmentManager().findFragmentByTag("toastLayer")));
            ins.runOnMainSync(() -> assertEquals("不拦截弹窗操作", ToastTestProbe.text()));
        }
    }

    /** 应用上下文的后台回调可显示提示，离开或重建页面立即清理，不向后台回显。 */
    @Test public void backgroundCallbackAndActivityLifecycle() throws Exception {
        View[] shown = {null};
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            // 测试线程不是主线程，模拟相机等应用级回调。
            ToastEx.showFailureToast(ins.getTargetContext().getApplicationContext(), "后台回调提示");
            assertVisibleIcon("background-callback", "后台回调提示");
            ins.runOnMainSync(() -> shown[0] = ToastEx.currentView);
            scenario.moveToState(Lifecycle.State.CREATED);
            ins.runOnMainSync(() -> {
                assertNull(ToastEx.currentView);
                assertFalse(shown[0].isAttachedToWindow());
            });
            ToastEx.showFailureToast(ins.getTargetContext().getApplicationContext(), "后台不可显示");
            ins.waitForIdleSync();
            ins.runOnMainSync(() -> assertNull(ToastEx.currentView));
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> ToastEx.showSuccessToast(activity, "重建前提示"));
            settle();
            scenario.recreate();
            ins.runOnMainSync(() -> assertNull(ToastEx.currentView));
        }
    }

    /** 等待窗口绘制与系统弹窗动画完成，避免只检测到已添加但尚未绘制的 View。 */
    private void settle() {
        ins.waitForIdleSync();
        SystemClock.sleep(350);
    }

    /** 获取实际屏幕坐标，与 UiAutomation 全屏截图使用同一坐标系。 */
    private Rect screenRect(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
    }

    /** 注入真正的触屏事件，验证窗口穿透而非直接调用按钮监听。 */
    private void tap(float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, x, y, 0);
        try {
            assertTrue(ins.getUiAutomation().injectInputEvent(down, true));
            assertTrue(ins.getUiAutomation().injectInputEvent(up, true));
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    /** 将截图中的图标与自身渲染比较；被遮挡或被蒙层压暗都会失败，不能仅检查 attached。 */
    private void assertVisibleIcon(String name, String message) throws Exception {
        settle();
        Rect[] bounds = {null};
        Bitmap[] expected = {null};
        ins.runOnMainSync(() -> {
            assertEquals(message, ToastTestProbe.text());
            assertNotNull(ToastEx.currentView);
            ImageView icon = ToastEx.currentView.findViewById(R.id.toast_image);
            bounds[0] = screenRect(icon);
            assertTrue(bounds[0].width() > 0 && bounds[0].height() > 0);
            expected[0] = Bitmap.createBitmap(icon.getWidth(), icon.getHeight(), Bitmap.Config.ARGB_8888);
            icon.draw(new Canvas(expected[0]));
        });
        Bitmap screen = ins.getUiAutomation().takeScreenshot();
        assertNotNull(screen);
        File directory = new File(ins.getTargetContext().getExternalFilesDir(null), "toast-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(screen.compress(Bitmap.CompressFormat.PNG, 100, output));
            int checked = 0;
            int matched = 0;
            for (int y = 0; y < expected[0].getHeight(); y++) {
                for (int x = 0; x < expected[0].getWidth(); x++) {
                    int reference = expected[0].getPixel(x, y);
                    int max = Math.max(Color.red(reference), Math.max(Color.green(reference), Color.blue(reference)));
                    int min = Math.min(Color.red(reference), Math.min(Color.green(reference), Color.blue(reference)));
                    if (Color.alpha(reference) < 250 || max - min < 80) continue;
                    checked++;
                    int actual = screen.getPixel(bounds[0].left + x, bounds[0].top + y);
                    if (Math.abs(Color.red(actual) - Color.red(reference)) < 24
                            && Math.abs(Color.green(actual) - Color.green(reference)) < 24
                            && Math.abs(Color.blue(actual) - Color.blue(reference)) < 24) matched++;
                }
            }
            assertTrue("图标应包含足够的可验证像素", checked > 100);
            assertTrue("提示图标被弹窗遮挡或蒙层压暗：" + matched + "/" + checked, matched >= checked * 0.9);
        } finally {
            expected[0].recycle();
            screen.recycle();
        }
    }
}
