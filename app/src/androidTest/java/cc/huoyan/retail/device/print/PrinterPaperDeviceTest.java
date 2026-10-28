package cc.huoyan.retail.device.print;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.SelectOptionDialog;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.settings.SettingsPageActivity;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 实机检查纸宽选择、重进持久化以及横竖屏可见边界；不发送出纸命令。 */
@RunWith(AndroidJUnit4.class)
public class PrinterPaperDeviceTest {
    /** 切换纸张后重进回显，并在横竖屏检查选项及固定操作栏，结束恢复配置。 */
    @Test public void paperChoicePersistsAndFitsWindow() throws Exception {
        SPManager sp = SPManager.getInstance();
        int original = sp.getPrinterPaperWidthMm();
        try {
            String direction = InstrumentationRegistry.getArguments().getString("paperOrientation", "landscape");
            int orientation = "portrait".equals(direction)
                    ? Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
            {
                try (ActivityScenario<SettingsPageActivity> scenario = launch()) {
                    scenario.onActivity(activity -> assertEquals("实际窗口方向必须匹配验收条件", orientation,
                            activity.getResources().getConfiguration().orientation));
                    settle();
                    for (int width : new int[]{80, 58}) {
                        scenario.onActivity(activity -> {
                            View row = activity.findViewById(R.id.printerPaperLayout);
                            assertVisible(row);
                            row.performClick();
                            SelectOptionDialog dialog = dialog(activity);
                            ListView list = dialog.requireView().findViewById(R.id.selectOptions);
                            assertEquals(2, list.getCount());
                            int index = width == 80 ? 1 : 0;
                            list.performItemClick(list.getChildAt(index), index, list.getAdapter().getItemId(index));
                        });
                        settle();
                        scenario.onActivity(activity -> {
                            assertEquals(width, sp.getPrinterPaperWidthMm());
                            assertEquals(width + "mm", ((TextView) activity.findViewById(R.id.printerPaperWidth))
                                    .getText().toString());
                            assertVisible(activity.findViewById(R.id.printTest));
                        });
                    }
                    capture(orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait");
                    scenario.onActivity(activity -> activity.findViewById(R.id.printerPaperLayout).performClick());
                    settle();
                    capture(orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape-options" : "portrait-options");
                    scenario.onActivity(activity -> dialog(activity).dismiss());
                }
            }
            try (ActivityScenario<SettingsPageActivity> scenario = launch()) {
                settle();
                scenario.onActivity(activity -> assertEquals("58mm",
                        ((TextView) activity.findViewById(R.id.printerPaperWidth)).getText().toString()));
            }
        } finally {
            sp.putPrinterPaperWidthMm(original);
        }
    }

    /** 启动实际打印设置页。 */
    private static ActivityScenario<SettingsPageActivity> launch() {
        return ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                SettingsPageActivity.class).putExtra("page", "print"));
    }

    /** 从当前页面的子管理器找到通用选项框。 */
    private static SelectOptionDialog dialog(SettingsPageActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            Fragment dialog = fragment.getChildFragmentManager().findFragmentByTag("selectOption");
            if (dialog instanceof SelectOptionDialog) return (SelectOptionDialog) dialog;
        }
        throw new AssertionError("纸宽选择框未显示");
    }

    /** 控件必须完整可见，不能只露出可点击的一小部分。 */
    private static void assertVisible(View view) {
        Rect bounds = new Rect();
        assertTrue(view.getGlobalVisibleRect(bounds));
        assertEquals(view.getHeight(), bounds.height());
        assertEquals(view.getWidth(), bounds.width());
    }

    /** 等待旋转、布局和关闭动画完成。 */
    private static void settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(700);
    }

    /** 保存实际运行图，用于人工检查选项行与弹窗。 */
    private static void capture(String name) throws Exception {
        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), "printer-paper-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }
}
