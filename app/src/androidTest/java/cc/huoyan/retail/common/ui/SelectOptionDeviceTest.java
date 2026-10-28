package cc.huoyan.retail.common.ui;

import cc.huoyan.retail.goods.GoodsManagementActivity;
import cc.huoyan.retail.home.HomeActivity;
import cc.huoyan.retail.settings.SettingsPageActivity;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.SPManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** 两方向复用：验证选择快照、取消、重建恢复、长列表与真实页面入口。 */
@RunWith(AndroidJUnit4.class)
public class SelectOptionDeviceTest {
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    /** 弹窗重建后仍回传正确稳定值，长列表可滚动，取消与重选不产生写入。 */
    @Test public void selectionSurvivesRecreationAndKeepsSnapshot() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> value = new AtomicReference<>();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                FragmentManager manager = activity.getSupportFragmentManager();
                SelectOptionDialog.listen(manager, activity, "testSelect", (position, selected) -> {
                    calls.incrementAndGet(); value.set(selected);
                });
                ArrayList<String> labels = new ArrayList<>();
                ArrayList<String> values = new ArrayList<>();
                for (int i=0; i<18; i++) { labels.add("测试选项 " + i); values.add("stable-" + i); }
                SelectOptionDialog.show(manager, "testSelect", "长列表选择", labels, values, "stable-12");
                labels.clear(); values.clear();
                assertEquals(18, list(dialog(manager)).getCount());
            });
            settle();
            scenario.recreate();
            settle();
            scenario.onActivity(activity -> {
                FragmentManager manager = activity.getSupportFragmentManager();
                SelectOptionDialog.listen(manager, activity, "testSelect", (position, selected) -> {
                    calls.incrementAndGet(); value.set(selected);
                });
                SelectOptionDialog dialog = dialog(manager);
                assertEquals(12, list(dialog).getCheckedItemPosition());
                visible(dialog.requireView().findViewById(R.id.selectCancel));
                list(dialog).setSelection(17);
            });
            settle(); capture("long-list");
            scenario.onActivity(activity -> clickOption(dialog(activity.getSupportFragmentManager()), 17));
            settle();
            assertEquals(1, calls.get()); assertEquals("stable-17", value.get());
            scenario.onActivity(activity -> SelectOptionDialog.show(activity.getSupportFragmentManager(), "testSelect",
                    "取消与重选", Arrays.asList("甲", "乙"), "甲"));
            settle();
            scenario.onActivity(activity -> clickOption(dialog(activity.getSupportFragmentManager()), 0));
            settle(); assertEquals(1, calls.get());
            scenario.onActivity(activity -> SelectOptionDialog.show(activity.getSupportFragmentManager(), "testSelect",
                    "空选项", new ArrayList<>(), null));
            settle(); capture("empty");
            scenario.onActivity(activity -> {
                SelectOptionDialog dialog = dialog(activity.getSupportFragmentManager());
                visible(dialog.requireView().findViewById(R.id.selectEmpty));
                dialog.requireView().findViewById(R.id.selectCancel).performClick();
            });
            settle(); assertEquals(1, calls.get());
        }
    }

    /** 真实配置与学习入口使用通用弹窗，选中回显正确并还原打印配置。 */
    @Test public void settingsAndLearningUseCommonSelection() throws Exception {
        String originalDevice = SPManager.getInstance().getPrinterDevice();
        try {
            for (String page : new String[]{"print", "camera", "scale", "maintenance"}) {
                Intent intent = new Intent(ins.getTargetContext(), SettingsPageActivity.class).putExtra("page", page);
                try (ActivityScenario<SettingsPageActivity> scenario = ActivityScenario.launch(intent)) {
                    settle();
                    int[] entries = page.equals("print") ? new int[]{R.id.printInitNameLayout, R.id.printerModelLayout}
                            : page.equals("camera") ? new int[]{R.id.cameraNameLayout, R.id.cameraResolutionLayout}
                            : page.equals("scale") ? new int[]{R.id.selectScalePort} : new int[]{R.id.languageSetting};
                    for (int entry : entries) {
                        scenario.onActivity(activity -> activity.findViewById(entry).performClick());
                        settle(); capture(page + "-" + entry);
                        scenario.onActivity(activity -> {
                            SelectOptionDialog dialog = pageDialog(activity);
                            visible(dialog.requireView().findViewById(R.id.selectTitle));
                            visible(dialog.requireView().findViewById(R.id.selectCancel));
                            if (entry == R.id.languageSetting) {
                                ListView options = list(dialog);
                                assertEquals(3, options.getCount());
                                String mode = cc.huoyan.retail.common.LocalePreferenceUtil.getLanguageCode(activity);
                                assertEquals("zh".equals(mode) ? 1 : "en".equals(mode) ? 2 : 0,
                                        options.getCheckedItemPosition());
                            }
                            if (entry == R.id.printInitNameLayout) {
                                ListView list = list(dialog);
                                assertEquals(1, list.getCount());
                                assertEquals(0, list.getCheckedItemPosition());
                                clickOption(dialog, 0);
                            } else dialog.requireView().findViewById(R.id.selectCancel).performClick();
                        });
                        settle();
                    }
                    if (page.equals("print")) {
                        // 协议固定为通用 ESC/POS，选择展示项不能修改 USB 设备配置。
                        assertEquals(originalDevice, SPManager.getInstance().getPrinterDevice());
                    }
                }
            }
            try (ActivityScenario<GoodsManagementActivity> scenario = ActivityScenario.launch(GoodsManagementActivity.class)) {
                settle();
                scenario.onActivity(activity -> {
                    Fragment guide = activity.getSupportFragmentManager().findFragmentByTag("learningGuide");
                    if (guide instanceof DialogFragment) ((DialogFragment) guide).dismiss();
                    activity.getSupportFragmentManager().executePendingTransactions();
                    activity.findViewById(R.id.managementSort).performClick();
                });
                settle(); capture("management-sort");
                scenario.onActivity(activity -> clickOption(dialog(activity.getSupportFragmentManager()), 3));
                settle();
                scenario.recreate();
                settle();
                scenario.onActivity(activity -> {
                    assertTrue(activity.findViewById(R.id.managementSort).getContentDescription().toString()
                            .contains(activity.getResources().getStringArray(R.array.goods_management_sort_options)[3]));
                    activity.findViewById(R.id.managementPricing).performClick();
                });
                settle();
                scenario.onActivity(activity -> dialog(activity.getSupportFragmentManager()).requireView().findViewById(R.id.selectCancel).performClick());
            }
        } finally { SPManager.getInstance().putPrinterDevice(originalDevice); }
    }

    private SelectOptionDialog pageDialog(SettingsPageActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            Fragment dialog = f.getChildFragmentManager().findFragmentByTag("selectOption");
            if (dialog instanceof SelectOptionDialog) return (SelectOptionDialog)dialog;
        }
        throw new AssertionError("页面未显示通用选择弹窗");
    }

    private SelectOptionDialog dialog(FragmentManager manager) {
        SelectOptionDialog dialog = (SelectOptionDialog) manager.findFragmentByTag("selectOption");
        assertNotNull(dialog); return dialog;
    }

    private ListView list(SelectOptionDialog dialog) { return dialog.requireView().findViewById(R.id.selectOptions); }

    private void clickOption(SelectOptionDialog dialog, int position) {
        ListView list = list(dialog);
        View row = list.getChildAt(position - list.getFirstVisiblePosition());
        assertNotNull("目标选项必须可见", row); visible(row);
        list.performItemClick(row, position, list.getItemIdAtPosition(position));
    }

    private void visible(View view) {
        Rect visible = new Rect(); assertTrue(view.getGlobalVisibleRect(visible));
        assertEquals(view.getHeight(), visible.height()); assertEquals(view.getWidth(), visible.width());
    }

    private void settle() { ins.waitForIdleSync(); SystemClock.sleep(350); }

    private void capture(String name) throws Exception {
        String orientation = ins.getTargetContext().getResources().getConfiguration().orientation == 1 ? "portrait" : "landscape";
        File dir = new File(ins.getTargetContext().getExternalFilesDir(null), "select-qa/" + orientation);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }
}
