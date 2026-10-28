package cc.huoyan.retail.home;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 使用模拟有效期验证实际弹窗和续期入口，不提交激活或修改证书。 */
@RunWith(AndroidJUnit4.class)
public class ActivationExpiryDeviceTest {
    /** 主线程交互及窗口截图桥接。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    /** 三天与不足一天文案可见，恢复后的按钮仍打开既有输入弹窗。 */
    @Test public void reminderAndRestoredRenewalAction() throws Exception {
        HomeActivity activity = (HomeActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ins.waitForIdleSync();
            long now = System.currentTimeMillis() / 1000L;
            ins.runOnMainSync(() -> {
                ActivationExpiryDialog dialog = ActivationExpiryDialog.create(activity,
                        ActivationExpiry.check(true, now + 259200, now));
                dialog.showNow(activity.getSupportFragmentManager(), ActivationExpiryDialog.TAG);
                assertDialog(dialog, activity.getResources().getQuantityString(R.plurals.activation_expiry_days, 3, 3));
            });
            capture(activity, "three-days");
            ins.runOnMainSync(() -> {
                ActivationExpiryDialog dialog = (ActivationExpiryDialog) activity.getSupportFragmentManager()
                        .findFragmentByTag(ActivationExpiryDialog.TAG);
                dialog.requireView().findViewById(R.id.cancel).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();
                assertNull(activity.getSupportFragmentManager().findFragmentByTag(ActivationExpiryDialog.TAG));
                assertNull(activity.getSupportFragmentManager().findFragmentByTag("modelActivation"));
                ActivationExpiryDialog urgent = ActivationExpiryDialog.create(activity,
                        ActivationExpiry.check(true, now + 3600, now));
                urgent.showNow(activity.getSupportFragmentManager(), ActivationExpiryDialog.TAG);
                assertDialog(urgent, activity.getString(R.string.activation_expiry_less_than_day));
            });
            capture(activity, "less-than-day");
            ins.runOnMainSync(() -> {
                ActivationExpiryDialog old = (ActivationExpiryDialog) activity.getSupportFragmentManager()
                        .findFragmentByTag(ActivationExpiryDialog.TAG);
                Fragment.SavedState state = activity.getSupportFragmentManager().saveFragmentInstanceState(old);
                android.os.Bundle args = new android.os.Bundle(old.requireArguments());
                old.dismiss(); activity.getSupportFragmentManager().executePendingTransactions();
                ActivationExpiryDialog restored = new ActivationExpiryDialog();
                restored.setArguments(args); restored.setInitialSavedState(state);
                restored.showNow(activity.getSupportFragmentManager(), ActivationExpiryDialog.TAG);
                assertDialog(restored, activity.getString(R.string.activation_expiry_less_than_day));
                restored.requireView().findViewById(R.id.confirm).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();
                Fragment flow = activity.getSupportFragmentManager().findFragmentByTag("modelActivation");
                assertNotNull(flow);
                Fragment input = flow.getChildFragmentManager().findFragmentByTag("activateCode");
                assertNotNull(input);
                assertNotNull(input.requireView().findViewById(R.id.scan));
                assertNotNull(input.requireView().findViewById(R.id.activate));
            });
        } finally { ins.runOnMainSync(activity::finish); }
    }

    /** 创建和恢复均使用当前文案。 */
    private void assertDialog(ActivationExpiryDialog dialog, String remaining) {
        View root = dialog.requireView();
        assertTrue(((TextView) root.findViewById(R.id.textView)).getText().toString().startsWith(remaining));
    }

    /** 检查控件无裁切且可见。 */
    private void visible(View view) {
        Rect bounds = new Rect();
        assertTrue(view.getGlobalVisibleRect(bounds));
        assertEquals(view.getWidth(), bounds.width());
        assertEquals(view.getHeight(), bounds.height());
    }

    /** 保存当前方向的模拟到期提醒截图。 */
    private void capture(HomeActivity activity, String name) throws Exception {
        ins.waitForIdleSync(); SystemClock.sleep(300);
        ins.runOnMainSync(() -> {
            Fragment dialog = activity.getSupportFragmentManager().findFragmentByTag(ActivationExpiryDialog.TAG);
            assertNotNull(dialog);
            for (int id : new int[]{R.id.title, R.id.textView, R.id.cancel, R.id.confirm})
                visible(dialog.requireView().findViewById(id));
        });
        File folder = new File(activity.getExternalFilesDir(null), "activation-expiry-qa");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { bitmap.recycle(); }
    }
}
