package cc.huoyan.retail.feedback;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.CompoundButton;
import android.widget.ListView;
import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.checkout.CheckoutPresenter;
import cc.huoyan.retail.checkout.domain.CheckoutState;
import cc.huoyan.retail.settings.SettingsPageActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.goods.GoodsManagementActivity;
import cc.huoyan.retail.goods.SdkGoodsSource;
import cc.huoyan.retail.learning.ProductLearningActivity;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.BindingResult;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 显式启用的真实客户端验收；只有绑定直接调用 SDK 写接口，其余写操作走界面，保留数据供后端对账。 */
@RunWith(AndroidJUnit4.class)
public class RealFeedbackClientDeviceTest {
    /** 仪器入口，UI 操作统一切到主线程。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 独立测试商品，保留前导零和大小写以核对字符串 PLU。 */
    private static final String PLU = "00FbA1016";
    /** 用于后续改品的独立商品。 */
    private static final String ALTERNATE = "00FbB1016";

    /** 仅在显式参数启用时连接真实 SDK。 */
    private HuoyanSdkClient connected() {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("realFeedbackTest")));
        HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
        client.connect();
        await("SDK 连接", client::isConnected);
        return client;
    }

    /** 绑定独立门店，再通过新增商品表单创建测试商品。 */
    @Test public void bindAndCreateGoodsThroughClient() throws Exception {
        String externalShopId = InstrumentationRegistry.getArguments().getString("externalShopId");
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("realFeedbackTest")));
        assertNotNull("请传入已核对的 externalShopId", externalShopId);
        assertFalse("externalShopId 不能为空", externalShopId.trim().isEmpty());
        HuoyanSdkClient client = connected();
        BindingResult binding = client.bindDevice(externalShopId.trim(), "客户端反馈验收门店1016", null);
        assertNotNull(binding); assertEquals(binding.getReason(), 0, binding.getCode());
        assertTrue(binding.hasBindingScope());
        Log.i("RealFeedbackTest", "binding account=" + binding.getAccountId() + " shop=" + binding.getShopId());
        GoodsManagementActivity activity = launch(GoodsManagementActivity.class);
        try {
            createGoods(activity, PLU, "反馈验收商品A", "1.23");
            createGoods(activity, ALTERNATE, "反馈验收商品B", "2.34");
            screenshot("goods-created");
        } finally { ins.runOnMainSync(activity::finish); }
    }

    /** 从学习页选择商品、使用当前真实相机拍照并保存；不直接调用 learn 或反馈接口。 */
    @Test public void learnThroughClientCamera() throws Exception {
        connected();
        assertNotNull("先完成商品表单准备", SdkGoodsSource.getInstance().getGoodsByPlu(PLU));
        ProductLearningActivity activity = launch(ProductLearningActivity.class);
        try {
            ins.runOnMainSync(() -> {
                DialogFragment guide = dialog(activity, "learningGuide");
                if (guide != null) guide.requireView().findViewById(R.id.confirm).performClick();
            });
            click(activity, R.id.learningSwitchGoods);
            awaitUi("商品选择框", () -> dialog(activity, "goodsSearch") != null);
            ins.runOnMainSync(() -> ((TextView) dialogRoot(activity, "goodsSearch").findViewById(R.id.searchQuery)).setText(PLU));
            awaitUi("商品搜索结果", () -> first(dialogRoot(activity, "goodsSearch"), R.id.searchResults) != null);
            ins.runOnMainSync(() -> first(dialogRoot(activity, "goodsSearch"), R.id.searchResults).performClick());
            awaitUi("学习目标已选", () -> dialog(activity, "goodsSearch") == null);
            SystemClock.sleep(2000);
            screenshot("before-capture");
            java.util.concurrent.atomic.AtomicInteger previous = new java.util.concurrent.atomic.AtomicInteger();
            ins.runOnMainSync(() -> previous.set(((RecyclerView) activity.findViewById(R.id.picRecycler)).getAdapter().getItemCount()));
            click(activity, R.id.capture);
            awaitUi("真实相机学习回执", () -> ((RecyclerView) activity.findViewById(R.id.picRecycler)).getAdapter().getItemCount() > previous.get());
            screenshot("captured-items");
            click(activity, R.id.saveStudy);
            awaitUi("保存完成", () -> dialog(activity, "loading") == null);
            SystemClock.sleep(1500);
            screenshot("learning-saved");
            Log.i("RealFeedbackTest", "PASS client_camera_learning plu=" + PLU);
        } finally { ins.runOnMainSync(activity::finish); }
    }

    /** 从客户端设置启用删行、关闭称重并选择真实 USB 打印机。 */
    @Test public void configureCheckoutThroughClient() throws Exception {
        connected();
        SettingsPageActivity activity = settings("recognition");
        awaitUi("识别设置", () -> activity.findViewById(R.id.deleteRecognitionResult) != null);
        ins.runOnMainSync(() -> {
            CompoundButton toggle = activity.findViewById(R.id.deleteRecognitionResult);
            if (!toggle.isChecked()) toggle.performClick();
            activity.findViewById(R.id.saveSettings).performClick();
        });
        SystemClock.sleep(1500);
        ins.runOnMainSync(activity::finish);
        SettingsPageActivity scale = settings("scale");
        awaitUi("称重设置", () -> scale.findViewById(R.id.weighingModule) != null);
        ins.runOnMainSync(() -> {
            CompoundButton toggle = scale.findViewById(R.id.weighingModule);
            if (toggle.isChecked()) toggle.performClick();
        });
        ins.runOnMainSync(scale::finish);
        SettingsPageActivity printer = settings("print");
        try {
            awaitUi("打印设置", () -> printer.findViewById(R.id.printModule) != null);
            ins.runOnMainSync(() -> {
                CompoundButton toggle = printer.findViewById(R.id.printModule);
                if (!toggle.isChecked()) toggle.performClick();
                printer.findViewById(R.id.printerModelLayout).performClick();
            });
            awaitUi("打印机选择", () -> printerDialog(printer) != null);
            ins.runOnMainSync(() -> {
                ListView list = printerDialog(printer).requireView().findViewById(R.id.selectOptions);
                assertEquals("仅选择已核对的实机打印机", "BTP-N90LF", list.getAdapter().getItem(0));
                list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0));
            });
            screenshot("printer-selected");
            click(printer, R.id.communicationTest);
            // 首次连接的系统 USB 授权由真实系统界面操作，预留人工/ADB 点击时间。
            SystemClock.sleep(12000);
            screenshot("printer-connection");
        } finally { ins.runOnMainSync(printer::finish); }
    }

    /** 使用真实识别结果完成正常打印、改品打印或改品后删空，不注入框或调用输出端口。 */
    @Test public void checkoutThroughClient() throws Exception {
        connected();
        String mode = InstrumentationRegistry.getArguments().getString("feedbackMode", "normal");
        assertTrue(java.util.Arrays.asList("normal", "modified", "deleted").contains(mode));
        MainActivity activity = launch(MainActivity.class);
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("presenter");
        field.setAccessible(true);
        CheckoutPresenter presenter = (CheckoutPresenter) field.get(activity);
        try {
            awaitUi("真实识别锁定", () -> presenter.state().canEdit && !presenter.rows().isEmpty());
            screenshot(mode + "-recognized");
            ins.runOnMainSync(() -> {
                assertTrue("必须来自真实识别的测试商品", presenter.rows().stream().allMatch(row -> PLU.equals(row.getGoods().getPlu())));
                Log.i("RealFeedbackTest", "mode=" + mode + " real_rows=" + presenter.rows().size()
                        + " targets=" + presenter.rows().stream().mapToInt(row -> row.getRecognitionSelection().size()).sum());
            });
            if (!"normal".equals(mode)) {
                ins.runOnMainSync(() -> first(activity.getWindow().getDecorView(), R.id.resultRecycler).findViewById(R.id.cardContent).performClick());
                click(activity, R.id.modifyGoods);
                awaitUi("改品弹窗", () -> dialog(activity, "mainRecognizeModify") != null);
                ins.runOnMainSync(() -> ((TextView) dialogRoot(activity, "mainRecognizeModify").findViewById(R.id.editText)).setText(ALTERNATE));
                awaitUi("改品目标", () -> {
                    View row = first(dialogRoot(activity, "mainRecognizeModify"), R.id.resultRecycler);
                    return row != null && ((TextView) row.findViewById(R.id.plu)).getText().toString().endsWith(ALTERNATE);
                });
                ins.runOnMainSync(() -> first(dialogRoot(activity, "mainRecognizeModify"), R.id.resultRecycler).performClick());
                awaitUi("改品完成", () -> dialog(activity, "mainRecognizeModify") == null
                        && presenter.rows().stream().allMatch(row -> ALTERNATE.equals(row.getGoods().getPlu())));
                screenshot(mode + "-changed");
            }
            if ("deleted".equals(mode)) {
                ins.runOnMainSync(() -> {
                    View button = first(activity.getWindow().getDecorView(), R.id.resultRecycler).findViewById(R.id.deleteGoods);
                    assertEquals(View.VISIBLE, button.getVisibility()); assertTrue(button.isEnabled()); button.performClick();
                });
                awaitUi("全部删除", () -> presenter.rows().isEmpty());
            } else {
                awaitUi("真实打印按钮可用", () -> activity.findViewById(R.id.confirm).isEnabled());
                click(activity, R.id.confirm);
                awaitUi("打印完成", () -> presenter.state().phase == CheckoutState.Phase.SOLD_OR_REPORTED);
            }
            // 等待独立反馈队列结束；最终成功仍需后端三表对账。
            SystemClock.sleep(5000);
            screenshot(mode + "-submitted");
            Log.i("RealFeedbackTest", "PASS client_checkout_ui mode=" + mode + " backend_upload_requires_separate_verification=true");
        } finally { ins.runOnMainSync(activity::finish); }
    }

    /** 启动指定客户端设置页。 */
    private SettingsPageActivity settings(String page) {
        return (SettingsPageActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), SettingsPageActivity.class)
                .putExtra("page", page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    /** 打印设置弹窗归所属 Fragment 的子管理器持有。 */
    private DialogFragment printerDialog(SettingsPageActivity activity) {
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.pageContent);
        return fragment == null ? null : (DialogFragment) fragment.getChildFragmentManager().findFragmentByTag("selectOption");
    }

    /** 在新增商品弹窗填表并点击确认，只用 SDK 查询验证保存结果。 */
    private void createGoods(GoodsManagementActivity activity, String plu, String name, String price) {
        assertNull("测试 PLU 不得覆盖已有商品", SdkGoodsSource.getInstance().getGoodsByPlu(plu));
        awaitUi("新增按钮可用", () -> activity.findViewById(R.id.managementAdd).isEnabled());
        click(activity, R.id.managementAdd);
        awaitUi("商品表单", () -> dialog(activity, "managedGoodsForm") != null);
        ins.runOnMainSync(() -> {
            View form = dialogRoot(activity, "managedGoodsForm");
            ((TextView) form.findViewById(R.id.plu)).setText(plu);
            ((TextView) form.findViewById(R.id.name)).setText(name);
            ((TextView) form.findViewById(R.id.price)).setText(price);
            form.findViewById(R.id.pricingTypeCount).performClick();
            form.findViewById(R.id.confirm).performClick();
        });
        await("商品保存", () -> SdkGoodsSource.getInstance().getGoodsByPlu(plu) != null);
        awaitUi("商品表单关闭", () -> dialog(activity, "managedGoodsForm") == null);
        SystemClock.sleep(600);
    }

    /** 启动真实页面，不向 Presenter 注入业务数据。 */
    private <T extends FragmentActivity> T launch(Class<T> type) {
        return type.cast(ins.startActivitySync(new Intent(ins.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
    }
    /** 通过页面控件触发操作。 */
    private void click(FragmentActivity activity, int id) { ins.runOnMainSync(() -> assertTrue(activity.findViewById(id).performClick())); }
    /** 读取当前页面弹窗。 */
    private DialogFragment dialog(FragmentActivity activity, String tag) { return (DialogFragment) activity.getSupportFragmentManager().findFragmentByTag(tag); }
    /** 从 DialogFragment 的 View 或自建 Dialog 读取当前可见内容。 */
    private View dialogRoot(FragmentActivity activity, String tag) {
        DialogFragment dialog = dialog(activity, tag);
        return dialog.getView() != null ? dialog.requireView() : dialog.requireDialog().getWindow().getDecorView();
    }
    /** 读取列表首个可点击商品卡片。 */
    private View first(View root, int id) {
        RecyclerView.ViewHolder holder = ((RecyclerView) root.findViewById(id)).findViewHolderForAdapterPosition(0);
        return holder == null ? null : holder.itemView;
    }
    /** 主线程轮询 UI，避免跨线程读 View。 */
    private void awaitUi(String label, BooleanSupplier condition) {
        await(label, () -> { AtomicBoolean result = new AtomicBoolean(); ins.runOnMainSync(() -> result.set(condition.getAsBoolean())); return result.get(); });
    }
    /** 有界等待真实异步结果。 */
    private void await(String label, BooleanSupplier condition) {
        long end = SystemClock.elapsedRealtime() + 30000;
        do { if (condition.getAsBoolean()) return; SystemClock.sleep(200); } while (SystemClock.elapsedRealtime() < end);
        fail("等待超时：" + label);
    }
    /** 保存客户端画面，供后端记录对账。 */
    private void screenshot(String name) throws Exception {
        ins.waitForIdleSync();
        SystemClock.sleep(350);
        Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        File dir = new File(ins.getTargetContext().getExternalFilesDir(null), "real-feedback-review"); dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        finally { bitmap.recycle(); }
    }
}
