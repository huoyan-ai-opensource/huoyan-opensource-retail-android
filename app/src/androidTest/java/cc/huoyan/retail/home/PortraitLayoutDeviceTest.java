package cc.huoyan.retail.home;

import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.learning.ProductLearningActivity;
import cc.huoyan.retail.settings.SettingsPageActivity;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** 竖屏设备检查布局分区、固定操作区和滚动可达性，保存各页验收截图。 */
@RunWith(AndroidJUnit4.class)
public class PortraitLayoutDeviceTest {
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    /** 在竖屏运行；不修改商品、店铺信息或硬件配置。 */
    @Test public void portraitPagesKeepActionsReachable() throws Exception {
        HomeActivity home = (HomeActivity) launch(HomeActivity.class, null);
        try {
            assertEquals(Configuration.ORIENTATION_PORTRAIT, home.getResources().getConfiguration().orientation);
            View checkout = home.findViewById(R.id.enterCheckout);
            int top = rect(checkout).top;
            ScrollView scroll = ancestorScroll(home.findViewById(R.id.homeMaintenance));
            assertNotNull(scroll);
            capture("home");
            ins.runOnMainSync(() -> scroll.fullScroll(View.FOCUS_DOWN));
            settle();
            fullyVisible(home.findViewById(R.id.homeMaintenance));
            assertEquals(top, rect(checkout).top);
            capture("home-scrolled");
            for (String name : new String[]{"camera", "scale", "print", "recognition", "sdk", "shop", "goods", "maintenance", "authorization", "update"}) {
                Activity page = launch(SettingsPageActivity.class, name);
                try {
                    fullyVisible(page.findViewById(R.id.navBack));
                    ViewGroup content = page.findViewById(R.id.pageContent);
                    ViewGroup layout = (ViewGroup) content.getChildAt(0);
                    if ("sdk".equals(name)) {
                        fullyVisible(page.findViewById(R.id.sdkAidl));
                        fullyVisible(page.findViewById(R.id.sdkWs));
                        ins.runOnMainSync(() -> page.findViewById(R.id.sdkWs).performClick());
                        settle();
                        for (int id : new int[]{R.id.wsAddress, R.id.wsProbe, R.id.wsDiscover, R.id.confirm}) {
                            fullyVisible(page.findViewById(id));
                        }
                        View results = page.findViewById(R.id.wsResults);
                        fullyVisible(results.getVisibility() == View.VISIBLE
                                ? results : page.findViewById(R.id.sdkDiscoveryEmpty));
                        capture(name);
                        continue;
                    }
                    ScrollView body = firstScroll(layout);
                    assertNotNull(name, body);
                    capture(name);
                    ins.runOnMainSync(() -> body.fullScroll(View.FOCUS_DOWN));
                    settle();
                    if (!"sdk".equals(name) && layout.getChildCount() > 1) {
                        ViewGroup actions = (ViewGroup) layout.getChildAt(1);
                        for (int i=0; i<actions.getChildCount(); i++) fullyVisible(actions.getChildAt(i));
                    }
                    capture(name + "-scrolled");
                } finally { ins.runOnMainSync(page::finish); settle(); }
            }
            ProductLearningActivity learning = (ProductLearningActivity) launch(ProductLearningActivity.class, null);
            try {
                ins.runOnMainSync(() -> {
                    DialogFragment guide = (DialogFragment) learning.getSupportFragmentManager().findFragmentByTag("learningGuide");
                    if (guide != null) guide.dismiss();
                });
                settle();
                View images = learning.findViewById(R.id.learningImageStage);
                View camera = learning.findViewById(R.id.learningCameraStage);
                assertTrue(rect(camera).bottom < rect(images).top);
                assertEquals(camera.getWidth(), images.getWidth());
                for (int id : new int[]{R.id.capture, R.id.clearStudy, R.id.saveStudy}) fullyVisible(learning.findViewById(id));
                capture("learning");
            } finally { ins.runOnMainSync(learning::finish); settle(); }
            MainActivity main = (MainActivity) launch(MainActivity.class, null);
            try {
                View results = main.findViewById(R.id.recognitionResults);
                View preview = (View) main.findViewById(R.id.surfaceView).getParent();
                assertTrue(rect(preview).bottom <= rect(results).top);
                assertTrue(rect(results).bottom <= rect(main.findViewById(R.id.confirm)).top);
                for (int id : new int[]{R.id.version, R.id.retryRecognize, R.id.modifyGoods, R.id.confirm}) fullyVisible(main.findViewById(id));
                capture("main");
            } finally { ins.runOnMainSync(main::finish); settle(); }
        } finally { ins.runOnMainSync(home::finish); }
    }

    private Activity launch(Class<? extends Activity> type, String page) {
        Intent intent = new Intent(ins.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (page != null) intent.putExtra("page", page);
        Activity activity = ins.startActivitySync(intent);
        settle();
        return activity;
    }

    /** 递归寻找普通设置页面的滚动内容区。 */
    private ScrollView firstScroll(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof ScrollView) return (ScrollView) child;
            if (child instanceof ViewGroup) {
                ScrollView found = firstScroll((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private ScrollView ancestorScroll(View view) {
        while (view.getParent() instanceof View) {
            view = (View) view.getParent();
            if (view instanceof ScrollView) return (ScrollView) view;
        }
        return null;
    }

    private Rect rect(View view) {
        int[] xy = new int[2]; view.getLocationOnScreen(xy);
        return new Rect(xy[0], xy[1], xy[0]+view.getWidth(), xy[1]+view.getHeight());
    }

    private void fullyVisible(View view) {
        assertNotNull(view);
        Rect visible = new Rect();
        assertTrue(view.toString(), view.getGlobalVisibleRect(visible));
        assertEquals(view.toString(), view.getWidth(), visible.width());
        assertEquals(view.toString(), view.getHeight(), visible.height());
    }

    private void settle() { ins.waitForIdleSync(); SystemClock.sleep(500); }

    private void capture(String name) throws Exception {
        File directory = new File(ins.getTargetContext().getExternalFilesDir(null), "portrait-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap screenshot = ins.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name+".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { screenshot.recycle(); }
    }
}
