package cc.huoyan.retail.learning;

import cc.huoyan.retail.home.HomeActivity;

import cc.huoyan.retail.checkout.MainActivity;
import cc.huoyan.retail.goods.GoodsManagementActivity;
import cc.huoyan.retail.goods.GoodsFixtureCleanup;
import cc.huoyan.retail.settings.SettingsPageActivity;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.goods.AddGoodsDialog;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.SdkGoodsSource;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 使用独立商品验收管理、学习选择和双方向布局，不拍照、不清除应用数据。 */
@RunWith(AndroidJUnit4.class)
public class NewUiDeviceTest {
    /** 商品查询及测试数据创建都使用已连接的 SDK。 */
    @org.junit.Before public void connectGoodsSdk() { cc.huoyan.retail.goods.GoodsFixtureCleanup.awaitSdk(); }

    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    private static final String PLU = "99160928", OTHER = "99160929";
    private HomeActivity home;
    private GoodsManagementActivity management;
    private ProductLearningActivity learning;

    @Test public void managementLearningSearchAndFullWidthNavigation() throws Exception {
        assertNull(SdkGoodsSource.getInstance().getGoodsByPlu(PLU));
        assertNull(SdkGoodsSource.getInstance().getGoodsByPlu(OTHER));
        home = (HomeActivity) launch(HomeActivity.class);
        try {
            await(() -> home.findViewById(R.id.homeCloudStatus).getWidth() > 0);
            SystemClock.sleep(6000); capture("home");
            ins.runOnMainSync(() -> home.findViewById(R.id.homeCloudStatus).performClick());
            await(() -> home.getSupportFragmentManager().findFragmentByTag("cloudInfo") != null);
            SystemClock.sleep(250); capture("cloud");
            ins.runOnMainSync(() -> ((DialogFragment) home.getSupportFragmentManager().findFragmentByTag("cloudInfo")).dismiss());
            Instrumentation.ActivityMonitor monitor = ins.addMonitor(GoodsManagementActivity.class.getName(), null, false);
            ins.runOnMainSync(() -> home.findViewById(R.id.homeLearning).performClick());
            management = (GoodsManagementActivity) ins.waitForMonitorWithTimeout(monitor, 5000); ins.removeMonitor(monitor);
            assertNotNull(management);
            ins.runOnMainSync(() -> management.findViewById(R.id.managementAdd).performClick());
            await(() -> form() != null && form().getView() != null);
            ins.runOnMainSync(() -> {
                View view = form().requireView();
                ((TextView) view.findViewById(R.id.plu)).setText(PLU);
                ((TextView) view.findViewById(R.id.name)).setText("全麦 UI 测试面包");
                ((TextView) view.findViewById(R.id.price)).setText("12.34");
            });
            capture("goods-add");
            ins.runOnMainSync(() -> form().requireView().findViewById(R.id.confirm).performClick());
            await(() -> SdkGoodsSource.getInstance().getGoodsByPlu(PLU) != null);
            assertEquals(1234, SdkGoodsSource.getInstance().getGoodsByPlu(PLU).getPrice());
            ins.runOnMainSync(() -> ((EditText) management.findViewById(R.id.managementSearch)).setText(PLU));
            await(() -> managementRow() != null);
            ins.runOnMainSync(() -> managementRow().findViewById(R.id.manageEdit).performClick());
            await(() -> form() != null && form().getView() != null);
            capture("goods-edit");
            ins.runOnMainSync(() -> {
                assertFalse(form().requireView().findViewById(R.id.plu).isEnabled());
                ((TextView) form().requireView().findViewById(R.id.price)).setText("23.45");
                form().requireView().findViewById(R.id.confirm).performClick();
            });
            await(() -> SdkGoodsSource.getInstance().getGoodsByPlu(PLU).getPrice() == 2345);
            GoodsEntity other = new GoodsEntity(); other.setPlu(OTHER); other.setName("苹果 UI 测试");
            SdkGoodsSource.getInstance().save(other, false);
            capture("management");
            monitor = ins.addMonitor(ProductLearningActivity.class.getName(), null, false);
            ins.runOnMainSync(() -> managementRow().findViewById(R.id.manageLearn).performClick());
            learning = (ProductLearningActivity) ins.waitForMonitorWithTimeout(monitor, 5000); ins.removeMonitor(monitor);
            assertNotNull(learning);
            await(() -> learning.mSelectedGoods != null && PLU.equals(learning.mSelectedGoods.getPlu()));
            ins.runOnMainSync(() -> {
                DialogFragment guide = (DialogFragment) learning.getSupportFragmentManager().findFragmentByTag("learningGuide");
                if (guide != null) guide.dismiss();
                learning.findViewById(R.id.learningSwitchGoods).performClick();
            });
            await(() -> search() != null && search().getDialog() != null);
            View[] content = new View[1];
            ins.runOnMainSync(() -> {
                content[0] = search().requireDialog().getWindow().getDecorView();
                EditText query = content[0].findViewById(R.id.searchQuery);
                assertFalse(query.getShowSoftInputOnFocus());
                assertEquals(View.GONE, content[0].findViewById(R.id.searchKeyboard).getVisibility());
                query.performClick();
                assertEquals(View.VISIBLE, content[0].findViewById(R.id.searchKeyboard).getVisibility());
                key(content[0].findViewById(R.id.searchKeyboard), "Q").performClick();
                key(content[0].findViewById(R.id.searchKeyboard), "M").performClick();
            });
            await(() -> ((RecyclerView) content[0].findViewById(R.id.searchResults)).getAdapter().getItemCount() == 1);
            capture("keyboard");
            int[] fixedHeight = new int[1];
            ins.runOnMainSync(() -> {
                View card = (View) content[0].findViewById(R.id.searchQuery).getParent().getParent();
                fixedHeight[0] = card.getHeight();
                android.graphics.Rect visible = new android.graphics.Rect();
                learning.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
                int[] xy = new int[2]; card.getLocationOnScreen(xy);
                assertEquals("搜索弹窗水平居中", visible.centerX(), xy[0] + card.getWidth() / 2, 3);
                assertEquals("搜索弹窗垂直居中", visible.centerY(), xy[1] + card.getHeight() / 2, 3);
                ((EditText) content[0].findViewById(R.id.searchQuery)).setText("zzzz-no-result");
            });
            await(() -> ((RecyclerView) content[0].findViewById(R.id.searchResults)).getAdapter().getItemCount() == 0);
            ins.runOnMainSync(() -> assertEquals("空结果不改变弹窗高度", fixedHeight[0],
                    ((View) content[0].findViewById(R.id.searchQuery).getParent().getParent()).getHeight()));
            ins.runOnMainSync(() -> {
                assertEquals(PLU, learning.mSelectedGoods.getPlu());
                content[0].findViewById(R.id.searchChinese).performClick();
                assertTrue(((EditText) content[0].findViewById(R.id.searchQuery)).getShowSoftInputOnFocus());
                assertEquals(View.GONE, content[0].findViewById(R.id.searchKeyboard).getVisibility());
                ((EditText) content[0].findViewById(R.id.searchQuery)).setText("苹果 UI");
            });
            SystemClock.sleep(1000); capture("chinese");
            ins.runOnMainSync(() -> {
                content[0].findViewById(R.id.searchChinese).performClick();
                assertEquals("苹果 UI", ((EditText) content[0].findViewById(R.id.searchQuery)).getText().toString());
            });
            await(() -> ((RecyclerView) content[0].findViewById(R.id.searchResults)).findViewHolderForAdapterPosition(0) != null);
            ins.runOnMainSync(() -> ((RecyclerView) content[0].findViewById(R.id.searchResults))
                    .findViewHolderForAdapterPosition(0).itemView.performClick());
            await(() -> OTHER.equals(learning.mSelectedGoods.getPlu()));
            capture("learning");
            ins.runOnMainSync(learning::finish); ins.waitForIdleSync();
            ins.runOnMainSync(() -> assertEquals(PLU,
                    ((EditText) management.findViewById(R.id.managementSearch)).getText().toString()));
            // 删除尚未学习的商品也应成功；通过实际确认按钮验证。
            await(() -> managementRow() != null);
            ins.runOnMainSync(() -> managementRow().findViewById(R.id.manageDelete).performClick());
            await(() -> management.getSupportFragmentManager().findFragmentByTag("alertDialog") != null);
            ins.runOnMainSync(() -> {
                DialogFragment dialog = (DialogFragment) management.getSupportFragmentManager().findFragmentByTag("alertDialog");
                assertTrue(dialog instanceof cc.huoyan.retail.common.ui.AlertDialog);
                dialog.requireView().findViewById(R.id.cancel).performClick();
            });
            ins.waitForIdleSync();
            assertNotNull("取消删除应保留商品", SdkGoodsSource.getInstance().getGoodsByPlu(PLU));
            ins.runOnMainSync(() -> managementRow().findViewById(R.id.manageDelete).performClick());
            await(() -> management.getSupportFragmentManager().findFragmentByTag("alertDialog") != null);
            capture("goods-delete");
            ins.runOnMainSync(() -> ((DialogFragment) management.getSupportFragmentManager().findFragmentByTag("alertDialog"))
                    .requireView().findViewById(R.id.confirm).performClick());
            await(() -> SdkGoodsSource.getInstance().getGoodsByPlu(PLU) == null);
            ins.runOnMainSync(management::finish); ins.waitForIdleSync();
            SettingsPageActivity library = (SettingsPageActivity) ins.startActivitySync(
                    new Intent(ins.getTargetContext(), SettingsPageActivity.class).putExtra("page", "goods")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> library.findViewById(R.id.openLearning) != null);
            capture("library");
            if (library.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                ins.runOnMainSync(() -> {
                    int[] first = new int[2], second = new int[2];
                    library.findViewById(R.id.openLearning).getLocationOnScreen(first);
                    library.findViewById(R.id.learnImagePackage).getLocationOnScreen(second);
                    assertEquals("面板底部按钮对齐", first[1], second[1]);
                });
            }
            ins.runOnMainSync(library::finish);
            MainActivity main = (MainActivity) launch(MainActivity.class);
            try {
                await(() -> main.findViewById(R.id.checkoutNavigation).getWidth() > 0);
                String[] before = new String[1];
                ins.runOnMainSync(() -> {
                    assertEquals(main.findViewById(R.id.main).getWidth(), main.findViewById(R.id.checkoutNavigation).getWidth());
                    before[0] = ((TextView) main.findViewById(R.id.checkoutClock)).getText().toString();
                });
                SystemClock.sleep(1300);
                ins.runOnMainSync(() -> assertNotEquals(before[0], ((TextView) main.findViewById(R.id.checkoutClock)).getText().toString()));
                ins.runOnMainSync(() -> {
                    int[] shop = new int[2], clock = new int[2];
                    main.findViewById(R.id.shopName).getLocationOnScreen(shop);
                    main.findViewById(R.id.checkoutClock).getLocationOnScreen(clock);
                    assertTrue("实时时间位于店铺名称下方", clock[1] > shop[1]);
                });
                capture("recognition");
            } finally { ins.runOnMainSync(main::finish); }
        } finally {
            if (learning != null) ins.runOnMainSync(learning::finish);
            if (management != null) ins.runOnMainSync(management::finish);
            ins.runOnMainSync(home::finish);
            GoodsEntity fixture = SdkGoodsSource.getInstance().getGoodsByPlu(PLU);
            if (fixture != null) GoodsFixtureCleanup.delete(ins.getTargetContext(), fixture);
            cc.huoyan.retail.goods.GoodsFixtureCleanup.deleteByPlu(OTHER);
        }
    }

    @Test public void continuousPaginationAndSearchReset() throws Exception {
        final String prefix = "99170928";
        for (int i = 0; i < 55; i++) assertNull(SdkGoodsSource.getInstance().getGoodsByPlu(prefix + String.format(java.util.Locale.ROOT, "%03d", i)));
        GoodsManagementActivity page = null;
        try {
            for (int i = 0; i < 55; i++) {
                GoodsEntity goods = new GoodsEntity();
                goods.setPlu(prefix + String.format(java.util.Locale.ROOT, "%03d", i));
                goods.setName("分页验证 " + i);
                goods.setUnit(i % 2 == 0 ? 0 : 5); goods.setPrice(100 + i);
                SdkGoodsSource.getInstance().save(goods, false);
            }
            page = (GoodsManagementActivity) launch(GoodsManagementActivity.class);
            GoodsManagementActivity screen = page;
            RecyclerView list = screen.findViewById(R.id.managementList);
            ins.runOnMainSync(() -> ((EditText) screen.findViewById(R.id.managementSearch)).setText(prefix));
            await(() -> list.getAdapter().getItemCount() == 24);
            ins.runOnMainSync(() -> list.scrollToPosition(23));
            await(() -> list.getAdapter().getItemCount() == 48);
            ins.runOnMainSync(() -> list.scrollToPosition(47));
            await(() -> list.getAdapter().getItemCount() == 55);
            ins.runOnMainSync(() -> list.scrollToPosition(54));
            await(() -> list.findViewHolderForAdapterPosition(54) != null);
            ins.runOnMainSync(() -> {
                java.util.Set<String> values = new java.util.HashSet<>();
                for (int i = 0; i < list.getAdapter().getItemCount(); i++) {
                    RecyclerView.ViewHolder holder = list.getAdapter().createViewHolder(list, 0);
                    ((RecyclerView.Adapter) list.getAdapter()).bindViewHolder(holder, i);
                    assertTrue("分页不得重复商品", values.add(((TextView) holder.itemView.findViewById(R.id.managePlu)).getText().toString()));
                }
                ((EditText) screen.findViewById(R.id.managementSearch)).setText(prefix + "054");
            });
            await(() -> list.getAdapter().getItemCount() == 1);
            ins.runOnMainSync(() -> ((EditText) screen.findViewById(R.id.managementSearch)).setText(prefix));
            await(() -> list.getAdapter().getItemCount() == 24);
            capture("pagination");
            chooseManagementFilter(screen, R.id.managementPricing, 2);
            await(() -> list.getAdapter().getItemCount() == 24);
            chooseManagementFilter(screen, R.id.managementSort, 3);
            await(() -> list.findViewHolderForAdapterPosition(0) != null);
            ins.runOnMainSync(() -> assertEquals(prefix + "001", ((TextView) list.findViewHolderForAdapterPosition(0)
                    .itemView.findViewById(R.id.managePlu)).getText().toString()));
            ins.runOnMainSync(() -> list.scrollToPosition(23));
            await(() -> list.getAdapter().getItemCount() == 27);
            ins.runOnMainSync(() -> {
                for (int i = 0; i < list.getAdapter().getItemCount(); i++) {
                    RecyclerView.ViewHolder holder = list.getAdapter().createViewHolder(list, 0);
                    ((RecyclerView.Adapter) list.getAdapter()).bindViewHolder(holder, i);
                    String plu = ((TextView) holder.itemView.findViewById(R.id.managePlu)).getText().toString();
                    assertEquals("计数筛选不混入称重商品", 5, SdkGoodsSource.getInstance().getGoodsByPlu(plu).getUnit());
                }
            });
            chooseManagementFilter(screen, R.id.managementPricing, 1);
            chooseManagementFilter(screen, R.id.managementSort, 4);
            await(() -> list.findViewHolderForAdapterPosition(0) != null);
            ins.runOnMainSync(() -> assertEquals(prefix + "054", ((TextView) list.findViewHolderForAdapterPosition(0)
                    .itemView.findViewById(R.id.managePlu)).getText().toString()));
            capture("filters");
            ins.runOnMainSync(() -> ((EditText) screen.findViewById(R.id.managementSearch)).setText(prefix + "001"));
            await(() -> list.getAdapter().getItemCount() == 0);
            chooseManagementFilter(screen, R.id.managementPricing, 0);
            await(() -> list.getAdapter().getItemCount() == 1);
        } finally {
            if (page != null) { GoodsManagementActivity screen = page; ins.runOnMainSync(screen::finish); }
            for (int i = 0; i < 55; i++) cc.huoyan.retail.goods.GoodsFixtureCleanup.deleteByPlu(prefix + String.format(java.util.Locale.ROOT, "%03d", i));
        }
    }

    private void chooseManagementFilter(GoodsManagementActivity screen, int id, int position) {
        ins.runOnMainSync(() -> {
            screen.findViewById(id).performClick();
            DialogFragment dialog = (DialogFragment) screen.getSupportFragmentManager().findFragmentByTag("selectOption");
            assertNotNull(dialog);
            android.widget.ListView options = dialog.requireView().findViewById(R.id.selectOptions);
            options.performItemClick(options.getAdapter().getView(position, null, options), position, position);
        });
        ins.waitForIdleSync();
    }

    private AddGoodsDialog form() { return (AddGoodsDialog) management.getSupportFragmentManager().findFragmentByTag("managedGoodsForm"); }
    private GoodsSearchDialog search() { return (GoodsSearchDialog) learning.getSupportFragmentManager().findFragmentByTag("goodsSearch"); }
    private View managementRow() {
        RecyclerView.ViewHolder row = ((RecyclerView) management.findViewById(R.id.managementList)).findViewHolderForAdapterPosition(0);
        return row == null ? null : row.itemView;
    }
    private View key(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView) root).getText().toString())) return root;
        if (root instanceof ViewGroup) for (int i=0; i<((ViewGroup) root).getChildCount(); i++) {
            View found = key(((ViewGroup) root).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private Activity launch(Class<? extends Activity> type) {
        return ins.startActivitySync(new Intent(ins.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void await(BooleanSupplier predicate) {
        AtomicBoolean ok = new AtomicBoolean(); long deadline = SystemClock.elapsedRealtime() + 15000;
        do { ins.runOnMainSync(() -> ok.set(predicate.getAsBoolean())); if (ok.get()) return; SystemClock.sleep(100); }
        while (SystemClock.elapsedRealtime() < deadline);
        fail("界面未在 15 秒内达到预期");
    }
    private void capture(String name) throws Exception {
        ins.waitForIdleSync(); SystemClock.sleep(name.equals("management") || name.equals("library") || name.equals("goods-edit") ? 3200 : 250);
        File dir = new File(ins.getTargetContext().getExternalFilesDir(null), "new-ui-qa"); dir.mkdirs();
        Bitmap image = ins.getUiAutomation().takeScreenshot(); assertNotNull(image);
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        image.recycle();
    }
}
