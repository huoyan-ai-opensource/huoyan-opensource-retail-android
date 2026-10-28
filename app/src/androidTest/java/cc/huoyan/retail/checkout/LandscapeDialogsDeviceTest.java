package cc.huoyan.retail.checkout;

import cc.huoyan.retail.learning.ProductLearningActivity;
import cc.huoyan.retail.learning.GoodsSearchDialog;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.fragment.app.DialogFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.goods.AddGoodsDialog;
import cc.huoyan.retail.learning.LearningGuideDialog;
import cc.huoyan.retail.data.GoodsEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import static org.junit.Assert.*;

/** 横屏弹窗实际窗口截图，以及原生键盘确认结果回归。 */
@RunWith(AndroidJUnit4.class)
public class LandscapeDialogsDeviceTest {
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    private ProductLearningActivity learning;
    private MainActivity main;

    @Test public void dialogsMatchAndInputWorks() throws Exception {
        main = (MainActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        learning = (ProductLearningActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), ProductLearningActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ins.runOnMainSync(() -> {
                DialogFragment automatic = (DialogFragment) learning.getSupportFragmentManager().findFragmentByTag("learningGuide");
                if (automatic != null) automatic.dismiss();
            });
            settle(); SystemClock.sleep(3000); capture("learning");
            LearningGuideDialog guide = new LearningGuideDialog();
            show(guide, true); capture("learning-guide"); dismiss(guide);
            verifyLearningSearch();
            AddGoodsDialog add = new AddGoodsDialog();
            show(add, true); capture("add-goods"); dismiss(add);
            GoodsEntity goods = new GoodsEntity(); goods.setPlu("1001");goods.setName("示例商品 1001");goods.setPrice(1800);goods.setUnit(5);
            AddGoodsDialog edit = new AddGoodsDialog(); edit.setEditingGoods(goods);
            show(edit, true); assertFalse(edit.requireView().findViewById(R.id.plu).isEnabled());capture("edit-goods");dismiss(edit);
            ins.runOnMainSync(learning::finish); settle();
            RecognizeCountKeyboardDialog count = new RecognizeCountKeyboardDialog(); count.setCount(2);
            int[] result = {0};count.setListener(v -> result[0]=v);
            show(count,false);
            // 相机恢复后的识别提示会短暂覆盖数字键，待提示消失再保存视觉证据。
            SystemClock.sleep(3500);capture("modify-count");
            ins.runOnMainSync(() -> {
                View root = count.requireView();
                TextView number = root.findViewById(R.id.num1);
                assertEquals(28f, number.getTextSize() / number.getResources().getDisplayMetrics().scaledDensity, .1f);
                int[] lastKey = new int[2], action = new int[2];
                View zero = root.findViewById(R.id.num0);
                zero.getLocationOnScreen(lastKey);
                root.findViewById(R.id.confirm).getLocationOnScreen(action);
                assertEquals(16f, (action[1] - lastKey[1] - zero.getHeight())
                        / zero.getResources().getDisplayMetrics().density, 1f);
            });
            press(count,R.id.num3);press(count,R.id.confirm);assertEquals(3,result[0]);
            WeightInputDialog weight = new WeightInputDialog();weight.setWeight(.650);
            double[] measured = {0};weight.setListener(v -> measured[0]=v);
            show(weight,false);capture("modify-weight");
            ins.runOnMainSync(() -> assertEquals(28f,
                    ((TextView) weight.requireView().findViewById(R.id.num1)).getTextSize()
                    / weight.getResources().getDisplayMetrics().scaledDensity, .1f));
            for(int key:new int[]{R.id.num0,R.id.dot,R.id.num7,R.id.num5,R.id.num0,R.id.confirm})press(weight,key);
            assertEquals(.75,measured[0],.00001);
            RecognizeModifyGoodsDialog modify = new RecognizeModifyGoodsDialog();
            modify.setGoodsVo(new GoodsVo(2,3600,goods,new ArrayList<>()));
            java.util.concurrent.atomic.AtomicInteger confirmations = new java.util.concurrent.atomic.AtomicInteger();
            modify.setListener(selected -> {
                assertEquals("1001", selected.getPlu());
                return confirmations.incrementAndGet() >= 2;
            });
            show(modify,false);capture("modify-goods");
            ins.runOnMainSync(() -> {
                View root = modify.requireView();
                assertEquals(View.GONE, root.findViewById(R.id.searchKeyboard).getVisibility());
                touch(root.findViewById(R.id.editText));
                assertEquals("改品首次触摸即展开", View.VISIBLE, root.findViewById(R.id.searchKeyboard).getVisibility());
                findKey(root.findViewById(R.id.searchKeyboard), "1").performClick();
                assertEquals("1", ((EditText) root.findViewById(R.id.editText)).getText().toString());
            });
            settle(); capture("modify-goods-keyboard");
            ins.runOnMainSync(() -> {
                View root = modify.requireView();
                findKey(root.findViewById(R.id.searchKeyboard), main.getString(R.string.goods_done)).performClick();
                assertEquals(View.GONE, root.findViewById(R.id.searchKeyboard).getVisibility());
                assertNull(root.findViewById(R.id.confirm));
                assertNull(root.findViewById(R.id.cancel));
                ((EditText) root.findViewById(R.id.editText)).setText("");
            });
            settle();
            ins.runOnMainSync(() -> modify.candidates(java.util.Collections.singletonList(
                    new RecognizeModifyCandidate(goods, true, true, 0)), goods.getPlu()));
            settle();
            capture("modify-goods-card");
            ins.runOnMainSync(() -> {
                RecyclerView grid = modify.requireView().findViewById(R.id.resultRecycler);
                assertEquals(grid.getResources().getInteger(R.integer.goods_choice_columns),
                        ((GridLayoutManager) grid.getLayoutManager()).getSpanCount());
                View card = grid.findViewHolderForAdapterPosition(0).itemView;
                assertEquals(View.VISIBLE, card.findViewById(R.id.similarBadge).getVisibility());
                GoodsEntity first = ((RecognizeModifyCandidateAdapter) grid.getAdapter()).getItem(0).getGoods();
                assertEquals(main.getString(R.string.goods_choice_price,
                        java.math.BigDecimal.valueOf(first.getPrice(), 2).toPlainString(),
                        cc.huoyan.retail.common.LocalizedText.unit(main, first.getUnit())),
                        ((TextView) card.findViewById(R.id.price)).getText().toString());
            });
            clickFirstCandidate(modify); assertEquals(1, confirmations.get());
            assertTrue("宿主拒绝改品时保留弹窗", modify.isAdded());
            clickFirstCandidate(modify); settle(); assertEquals(2, confirmations.get());
            assertFalse("点击商品并通过校验后立即关闭", modify.isAdded());
            RecognizeModifyGoodsDialog cancel = new RecognizeModifyGoodsDialog();
            java.util.concurrent.atomic.AtomicInteger cancellations = new java.util.concurrent.atomic.AtomicInteger();
            cancel.setGoodsVo(new GoodsVo(2,3600,goods,new ArrayList<>()));
            cancel.setCancelListener(cancellations::incrementAndGet);
            cancel.setListener(selected -> { fail("遮罩关闭不得提交商品"); return true; });
            show(cancel, false);
            tapOutside(cancel);
            assertFalse("点击遮罩关闭改品弹窗", cancel.isAdded());
            assertEquals("取消只通知宿主一次", 1, cancellations.get());
        } finally {
            ins.runOnMainSync(() -> {if(learning!=null)learning.finish();if(main!=null)main.finish();});
        }
    }
    /** 验证默认收键盘、显式输入、完成收起，以及三层宫格与末项可达性；样例仅在内存中展示。 */
    private void verifyLearningSearch() throws Exception {
        GoodsSearchDialog search = GoodsSearchDialog.create("1001");
        show(search, true);
        View root = search.requireDialog().getWindow().getDecorView();
        RecyclerView grid = root.findViewById(R.id.searchResults);
        EditText query = root.findViewById(R.id.searchQuery);
        awaitSearchLoaded(root);
        ArrayList<GoodsEntity> samples = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            GoodsEntity goods = new GoodsEntity();
            goods.setPlu(String.valueOf(1001 + i));
            goods.setName(i == 0 ? "全麦吐司面包" : "商品名称较长的测试餐包 " + i);
            goods.setPrice(1250 + i); goods.setUnit(i % 2 == 0 ? 5 : 0);
            samples.add(goods);
        }
        ins.runOnMainSync(() -> {
            assertFalse(query.hasFocus());
            assertEquals(View.GONE, root.findViewById(R.id.searchKeyboard).getVisibility());
            assertEquals(grid.getResources().getInteger(R.integer.goods_choice_columns),
                    ((GridLayoutManager) grid.getLayoutManager()).getSpanCount());
            search.results(samples);
        });
        settle(); capture("learning-search-hidden");
        ins.runOnMainSync(() -> {
            View card = grid.findViewHolderForAdapterPosition(0).itemView;
            assertEquals("￥12.50/个", ((TextView) card.findViewById(R.id.price)).getText().toString());
            assertEquals(View.GONE, card.findViewById(R.id.similarBadge).getVisibility());
            touch(query);
            assertEquals(View.VISIBLE, root.findViewById(R.id.searchKeyboard).getVisibility());
            findKey(root.findViewById(R.id.searchKeyboard), "Q").performClick();
            assertEquals("Q", query.getText().toString());
        });
        settle();
        ins.runOnMainSync(() -> search.results(samples));
        settle(); capture("learning-search-keyboard");
        ins.runOnMainSync(() -> {
            findKey(root.findViewById(R.id.searchKeyboard), learning.getString(R.string.goods_done)).performClick();
            assertEquals(View.GONE, root.findViewById(R.id.searchKeyboard).getVisibility());
            assertFalse(query.hasFocus());
            touch(query);
            assertEquals(View.VISIBLE, root.findViewById(R.id.searchKeyboard).getVisibility());
            root.findViewById(R.id.searchChinese).performClick();
            assertTrue(query.getShowSoftInputOnFocus());
            assertEquals(View.GONE, root.findViewById(R.id.searchKeyboard).getVisibility());
        });
        settle();
        ins.runOnMainSync(() -> {
            root.findViewById(R.id.searchChinese).performClick();
            assertFalse(query.getShowSoftInputOnFocus());
            assertEquals("Q", query.getText().toString());
            findKey(root.findViewById(R.id.searchKeyboard), learning.getString(R.string.goods_done)).performClick();
        });
        settle();
        ins.runOnMainSync(() -> { search.results(samples); grid.scrollToPosition(samples.size() - 1); });
        settle();
        ins.runOnMainSync(() -> assertNotNull(grid.findViewHolderForAdapterPosition(samples.size() - 1)));
        capture("learning-search-last");
        ins.runOnMainSync(() -> grid.findViewHolderForAdapterPosition(samples.size() - 1).itemView.performClick());
        settle(); assertFalse("学习商品点击即关闭", search.isAdded());
        GoodsSearchDialog cancel = GoodsSearchDialog.create("1001");
        show(cancel, true); tapOutside(cancel);
        assertFalse("点击遮罩关闭学习选择器", cancel.isAdded());
    }

    /** 点击实际候选卡片，覆盖适配器至宿主提交链路。 */
    private void clickFirstCandidate(RecognizeModifyGoodsDialog dialog) {
        ins.runOnMainSync(() -> {
            RecyclerView grid = dialog.requireView().findViewById(R.id.resultRecycler);
            grid.findViewHolderForAdapterPosition(0).itemView.performClick();
        });
        settle();
    }

    /** 向窗口上方遮罩注入一次触摸，验证平台的窗口外取消路径。 */
    private void tapOutside(DialogFragment dialog) {
        int[] location = new int[2];
        ins.runOnMainSync(() -> dialog.requireDialog().getWindow().getDecorView().getLocationOnScreen(location));
        assertTrue("弹窗上方必须保留可点击遮罩", location[1] > 0);
        long time = SystemClock.uptimeMillis();
        for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
            android.view.MotionEvent event = android.view.MotionEvent.obtain(time, SystemClock.uptimeMillis(),
                    action, location[0] + 24, 1, 0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try { ins.sendPointerSync(event); } finally { event.recycle(); }
        }
        settle();
    }

    /** 等待真实索引加载完成，避免异步首批结果覆盖仅供布局验收的内存样例。 */
    private void awaitSearchLoaded(View root) {
        java.util.concurrent.atomic.AtomicBoolean loaded = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 15000;
        do {
            ins.runOnMainSync(() -> loaded.set(!learning.getString(R.string.goods_loading)
                    .contentEquals(((TextView) root.findViewById(R.id.searchEmpty)).getText())));
            if (loaded.get()) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("学习商品索引未在 15 秒内加载完成");
    }

    /** 按可见键帽文案定位自定义键盘操作。 */
    private View findKey(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findKey(group.getChildAt(i), label);
                if (match != null) return match;
            }
        }
        return null;
    }

    /** 在主线程派发一次真实的按下与抬起，覆盖 EditText 首次触摸只获焦的系统行为。 */
    private void touch(View view) {
        long time = SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(time, time,
                android.view.MotionEvent.ACTION_DOWN, view.getWidth() / 2f, view.getHeight() / 2f, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(time, time + 60,
                android.view.MotionEvent.ACTION_UP, view.getWidth() / 2f, view.getHeight() / 2f, 0);
        try { view.dispatchTouchEvent(down); view.dispatchTouchEvent(up); }
        finally { down.recycle(); up.recycle(); }
    }
    private void show(DialogFragment d,boolean onLearning){ins.runOnMainSync(()->d.show((onLearning?learning:main).getSupportFragmentManager(),"qaDialog"));settle();}
    private void dismiss(DialogFragment d){ins.runOnMainSync(d::dismiss);settle();}
    private void press(DialogFragment d,int id){ins.runOnMainSync(()->d.requireView().findViewById(id).performClick());ins.waitForIdleSync();}
    private void settle(){ins.waitForIdleSync();SystemClock.sleep(700);}
    private void capture(String name)throws Exception{
        File dir=new File(ins.getTargetContext().getExternalFilesDir(null),"landscape-qa");assertTrue(dir.exists()||dir.mkdirs());
        Bitmap bitmap=ins.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
}
