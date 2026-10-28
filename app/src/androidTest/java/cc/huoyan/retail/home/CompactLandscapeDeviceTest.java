package cc.huoyan.retail.home;

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
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.checkout.RecognizeCountKeyboardDialog;
import cc.huoyan.retail.checkout.WeightInputDialog;
import cc.huoyan.retail.goods.AddGoodsDialog;
import cc.huoyan.retail.goods.GoodsManagementActivity;
import cc.huoyan.retail.learning.ProductLearningActivity;
import cc.huoyan.retail.learning.GoodsSearchDialog;
import cc.huoyan.retail.learning.LearningGuideDialog;
import cc.huoyan.retail.settings.SettingsPageActivity;
import cc.huoyan.retail.settings.ActivateCodeDialog;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** 小横屏可达性与文字布局验收；只打开页面和弹窗，不提交商品或设备配置。 */
@RunWith(AndroidJUnit4.class)
public class CompactLandscapeDeviceTest {
    /** 测试线程持有仪器，所有视图操作切回主线程。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 首轮采集允许记录问题，修复验收默认启用可达性断言。 */
    private final boolean auditOnly = "true".equals(InstrumentationRegistry.getArguments().getString("auditOnly"));

    /** 真实触摸输入框并等待系统输入法出现，验证小横屏表单确认区。 */
    @Test public void formRemainsReachableWithIme() throws Exception {
        org.junit.Assume.assumeTrue("此回归使用 API 30 输入法可见性接口及小屏设备",
                android.os.Build.VERSION.SDK_INT >= 30
                        && ins.getTargetContext().getResources().getConfiguration().smallestScreenWidthDp < 600);
        ins.getUiAutomation().setRotation(1);
        SystemClock.sleep(1000);
        GoodsManagementActivity page = (GoodsManagementActivity) launch(GoodsManagementActivity.class, null);
        AddGoodsDialog form = new AddGoodsDialog();
        try {
            ins.runOnMainSync(() -> form.showNow(page.getSupportFragmentManager(), "compactIme"));
            settle();
            View input = form.requireView().findViewById(R.id.name);
            int[] point = new int[2];
            ins.runOnMainSync(() -> input.getLocationOnScreen(point));
            long time = SystemClock.uptimeMillis();
            for (int action : new int[]{0, 1}) {
                android.view.MotionEvent event = android.view.MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                        point[0] + input.getWidth() / 2f, point[1] + input.getHeight() / 2f, 0);
                try { ins.sendPointerSync(event); } finally { event.recycle(); }
            }
            SystemClock.sleep(2000);
            capture("form-real-ime");
            android.view.WindowInsets insets = form.requireDialog().getWindow().getDecorView().getRootWindowInsets();
            assertTrue("必须真实弹出系统输入法", insets.isVisible(android.view.WindowInsets.Type.ime()));
            check(input); check(form.requireView().findViewById(R.id.confirm));
        } finally {
            ins.runOnMainSync(() -> { form.dismiss(); page.finish(); });
            ins.getUiAutomation().setRotation(-2);
        }
    }

    /** 覆盖设置页滚动终点、主要业务页与输入弹窗的实际窗口。 */
    @Test public void pagesAndDialogsRemainUsable() throws Exception {
        ins.getUiAutomation().setRotation(Integer.parseInt(InstrumentationRegistry.getArguments().getString("rotation", "1")));
        SystemClock.sleep(1000);
        HomeActivity home = (HomeActivity) launch(HomeActivity.class, null);
        try {
            assertEquals(Configuration.ORIENTATION_LANDSCAPE, home.getResources().getConfiguration().orientation);
            capture("home");
            dialog(home, new CloudServiceDialog(), "cloud");
            for (String name : new String[]{"camera", "scale", "print", "recognition", "sdk", "shop", "goods", "maintenance", "authorization"}) {
                Activity page = launch(SettingsPageActivity.class, name);
                try {
                    capture(name);
                    check(page.findViewById(R.id.navBack));
                    scrollAll(page.findViewById(R.id.pageContent));
                    settle(); capture(name + "-end");
                    int[] actions;
                    switch (name) {
                        case "camera": case "recognition": actions = new int[]{R.id.saveSettings}; break;
                        case "scale": actions = new int[]{R.id.testScalePort, R.id.zeroScale, R.id.tareScale}; break;
                        case "print": actions = new int[]{R.id.communicationTest, R.id.printTest}; break;
                        case "shop": actions = new int[]{R.id.saveShop}; break;
                        case "goods": actions = new int[]{R.id.openLearning, R.id.backupRecognitionLibrary, R.id.restoreRecognitionLibrary, R.id.learnImagePackage}; break;
                        default: actions = new int[]{};
                    }
                    for (int id : actions) check(page.findViewById(id));
                    if (name.equals("authorization")) dialog((FragmentActivity) page, new ActivateCodeDialog(), "activation");
                } finally { ins.runOnMainSync(page::finish); settle(); }
            }
            GoodsManagementActivity management = (GoodsManagementActivity) launch(GoodsManagementActivity.class, null);
            try {
                capture("management");
                check(management.findViewById(R.id.managementSearch));
                dialog(management, new AddGoodsDialog(), "add-goods");
            } finally { ins.runOnMainSync(management::finish); settle(); }
            ProductLearningActivity learning = (ProductLearningActivity) launch(ProductLearningActivity.class, null);
            try {
                ins.runOnMainSync(() -> {
                    DialogFragment guide = (DialogFragment) learning.getSupportFragmentManager().findFragmentByTag("learningGuide");
                    if (guide != null) guide.dismiss();
                });
                settle(); capture("learning");
                for (int id : new int[]{R.id.capture, R.id.clearStudy, R.id.saveStudy}) check(learning.findViewById(id));
                dialog(learning, new LearningGuideDialog(), "guide");
                GoodsSearchDialog search = GoodsSearchDialog.create(null);
                ins.runOnMainSync(() -> search.showNow(learning.getSupportFragmentManager(), "compactQa"));
                settle(); capture("search");
                ins.runOnMainSync(() -> search.requireDialog().getWindow().getDecorView().findViewById(R.id.searchQuery).performClick());
                settle(); capture("search-keyboard");
                check(search.requireDialog().getWindow().getDecorView().findViewById(R.id.searchResults));
                View searchRoot = search.requireDialog().getWindow().getDecorView();
                ins.runOnMainSync(() -> searchRoot.findViewById(R.id.searchChinese).performClick());
                settle(); capture("search-ime");
                check(searchRoot.findViewById(R.id.searchQuery));
                ins.runOnMainSync(() -> ((android.view.inputmethod.InputMethodManager) learning.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(searchRoot.getWindowToken(), 0));
                ins.runOnMainSync(search::dismiss);
            } finally { ins.runOnMainSync(learning::finish); settle(); }
            MainActivity main = (MainActivity) launch(MainActivity.class, null);
            try {
                capture("checkout");
                for (int id : new int[]{R.id.retryRecognize, R.id.modifyGoods, R.id.confirm, R.id.summaryAmount}) check(main.findViewById(id));
                dialog(main, new RecognizeCountKeyboardDialog(), "count");
                dialog(main, new WeightInputDialog(), "weight");
                cc.huoyan.retail.data.GoodsEntity sample = new cc.huoyan.retail.data.GoodsEntity();
                sample.setPlu("layout-only"); sample.setName("布局检查商品"); sample.setPrice(1234); sample.setUnit(5);
                cc.huoyan.retail.checkout.RecognizeModifyGoodsDialog modify = new cc.huoyan.retail.checkout.RecognizeModifyGoodsDialog();
                modify.setGoodsVo(new cc.huoyan.retail.checkout.GoodsVo(1, 1234, sample, new java.util.ArrayList<>()));
                dialog(main, modify, "modify-goods");
                dialog(main, new cc.huoyan.retail.common.ui.AlertDialog("操作确认", "是否继续当前操作？", "确定", "取消", null), "alert");
            } finally { ins.runOnMainSync(main::finish); settle(); }
        } finally { ins.runOnMainSync(home::finish); ins.getUiAutomation().setRotation(-2); }
    }

    /** 打开后仅截图、检查和取消弹窗，不触发确认业务。 */
    private void dialog(FragmentActivity host, DialogFragment dialog, String name) throws Exception {
        ins.runOnMainSync(() -> dialog.showNow(host.getSupportFragmentManager(), "compactQa"));
        settle(); capture(name);
        View root = dialog.requireDialog().getWindow().getDecorView();
        scrollAll(root); settle();
        if (root.findViewById(R.id.confirm) != null) check(root.findViewById(R.id.confirm));
        capture(name + "-end");
        if (dialog instanceof AddGoodsDialog) {
            View input = root.findViewById(R.id.name);
            ins.runOnMainSync(() -> {
                input.requestFocus();
                ((android.view.inputmethod.InputMethodManager) host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
            settle(); capture(name + "-ime");
            if (host.getResources().getConfiguration().screenWidthDp < 1100) {
                check(input); check(root.findViewById(R.id.confirm));
            }
            ins.runOnMainSync(() -> ((android.view.inputmethod.InputMethodManager) host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(input.getWindowToken(), 0));
        }
        ins.runOnMainSync(dialog::dismiss); settle();
    }

    /** 等待新页面布局完成，未传 page 时保留页面默认入口。 */
    private Activity launch(Class<? extends Activity> type, String page) {
        Intent intent = new Intent(ins.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (page != null) intent.putExtra("page", page);
        Activity activity = ins.startActivitySync(intent); settle(); return activity;
    }

    /** 递归将独立滚动内容移到末尾，固定按钮必须继续可见。 */
    private void scrollAll(View root) {
        ins.runOnMainSync(() -> scrollOnMain(root));
    }

    /** 在主线程遍历当前视图树，不持有页面外的引用。 */
    private void scrollOnMain(View view) {
        if (view instanceof ScrollView) ((ScrollView) view).fullScroll(View.FOCUS_DOWN);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scrollOnMain(group.getChildAt(i));
        }
    }

    /** 断言操作有非零空间且完全可见，文本排版行不能超过内容高度。 */
    private void check(View view) {
        if (auditOnly) return;
        ins.runOnMainSync(() -> {
            assertNotNull(view);
            if (view.getVisibility() == View.GONE) return;
            Rect visible = new Rect();
            assertTrue(view.toString(), view.getWidth() > 0 && view.getHeight() > 0 && view.getGlobalVisibleRect(visible));
            assertEquals(view.toString(), view.getWidth(), visible.width());
            assertEquals(view.toString(), view.getHeight(), visible.height());
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                assertNotNull(text.getLayout());
                assertTrue(text.getText().toString(), text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
            }
        });
    }

    /** 等待主线程空闲及弹窗动画，避免截到切换过程。 */
    private void settle() { ins.waitForIdleSync(); SystemClock.sleep(700); }

    /** 保存本轮截图供人工检查文字和视觉层次。 */
    private void capture(String name) throws Exception {
        File directory = new File(ins.getTargetContext().getExternalFilesDir(null), "compact-landscape-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap screenshot = ins.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { screenshot.recycle(); }
    }
}
