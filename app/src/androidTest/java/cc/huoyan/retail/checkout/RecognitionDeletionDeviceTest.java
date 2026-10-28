package cc.huoyan.retail.checkout;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.RowViewHolder;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.settings.SettingsPageActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 使用真实控件验证保存语义与双方向布局，不连接销售输出，不写商品库。 */
@RunWith(AndroidJUnit4.class)
public class RecognitionDeletionDeviceTest {
    /** 当前测试线程的 UI 调度边界。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    /** 开关即时保存，重新进入正确回显；退出时恢复原值。 */
    @Test public void switchPersistsImmediately() {
        boolean before = SPManager.getInstance().getBoolean(SPManager.SP_DELETE_RECOGNITION_RESULT);
        SettingsPageActivity page = open();
        try {
            SettingsPageActivity first = page;
            ins.runOnMainSync(() -> {
                Switch toggle = first.findViewById(R.id.deleteRecognitionResult);
                assertNotNull(toggle);
                assertEquals(before, toggle.isChecked());
                toggle.setChecked(!before);
                assertEquals(!before, SPManager.getInstance().getBoolean(SPManager.SP_DELETE_RECOGNITION_RESULT));
                first.finish();
            });
            ins.waitForIdleSync();
            page = open();
            SettingsPageActivity reopened = page;
            ins.runOnMainSync(() -> assertEquals(!before,
                    ((Switch) reopened.findViewById(R.id.deleteRecognitionResult)).isChecked()));
        } finally {
            SettingsPageActivity last = page;
            ins.runOnMainSync(() -> {
                SPManager.getInstance().putBoolean(SPManager.SP_DELETE_RECOGNITION_RESULT, before);
                last.finish();
            });
        }
    }

    /** 用横屏、窄横屏和竖屏资源验证中英文两行商品的完整显示及按钮绑定，并导出布局。 */
    @Test public void layoutsAndRowActionsWorkInBothOrientations() {
        ins.runOnMainSync(() -> {
            for (int[] size : new int[][]{{1280, 672}, {900, 540}, {540, 960}}) {
                for (String language : new String[]{"zh", "en"}) {
                    Configuration config = new Configuration(ins.getTargetContext().getResources().getConfiguration());
                    config.setLocale(Locale.forLanguageTag(language));
                    config.orientation = size[0] > size[1] ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
                    config.screenWidthDp = size[0]; config.screenHeightDp = size[1];
                    config.smallestScreenWidthDp = Math.min(size[0], size[1]);
                    Context context = ins.getTargetContext().createConfigurationContext(config);
                    FrameLayout parent = new FrameLayout(context);
                    View settings = LayoutInflater.from(context).inflate(R.layout.page_recognition, parent, false);
                    measure(settings, size[0], size[1]);
                    assertNotNull(settings.findViewById(R.id.deleteRecognitionResult));
                    capture(settings, "recognition-settings-" + size[0] + "-" + language);
                    RecognizeResultAdapter adapter = new RecognizeResultAdapter();
                    RecyclerView recycler = new RecyclerView(context);
                    recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(context));
                    recycler.setAdapter(adapter);
                    GoodsEntity goods = new GoodsEntity(); goods.setName("全麦吐司面包"); goods.setPlu("1001"); goods.setUnit(5); goods.setPrice(1250);
                    GoodsVo row = new GoodsVo(2, 2500, goods, Collections.emptyList());
                    adapter.setList(Collections.singletonList(row));
                    AtomicInteger deleted = new AtomicInteger();
                    adapter.setOnDeleteGoodsClickListener(value -> deleted.incrementAndGet());
                    RowViewHolder holder = adapter.onCreateViewHolder(recycler, 0);
                    adapter.onBindViewHolder(holder, 0);
                    View button = holder.itemView.findViewById(R.id.deleteGoods);
                    assertEquals(View.GONE, button.getVisibility());
                    int width = size[0] > size[1] ? 336 : 508;
                    measure(holder.itemView, width, 0);
                    int singleLineHeight = holder.itemView.getHeight();
                    goods.setName("en".equals(language) ? "Wholemeal toast\n(sliced loaf)" : "全麦吐司面包\n（切片装）");
                    adapter.onBindViewHolder(holder, 0);
                    measure(holder.itemView, width, 0);
                    TextView name = holder.itemView.findViewById(R.id.goodsName);
                    View info = holder.itemView.findViewById(R.id.goodsInfoArea);
                    assertEquals(2, name.getLineCount());
                    assertTrue("两行名称应撑高条目", holder.itemView.getHeight() > singleLineHeight);
                    for (int id : new int[]{R.id.goodsName, R.id.goodsPlu, R.id.goodsPrice}) {
                        View text = holder.itemView.findViewById(id);
                        android.graphics.Rect bounds = new android.graphics.Rect(0, 0, text.getWidth(), text.getHeight());
                        ((android.view.ViewGroup) info).offsetDescendantRectToMyCoords(text, bounds);
                        assertTrue("文字不得超出商品信息区", bounds.top >= info.getPaddingTop()
                                && bounds.bottom <= info.getHeight() - info.getPaddingBottom());
                    }
                    capture(holder.itemView, "recognition-row-" + size[0] + "-" + language + "-two-lines");
                    adapter.setDeletionVisible(true); adapter.onBindViewHolder(holder, 0);
                    assertEquals(View.VISIBLE, button.getVisibility());
                    button.performClick(); assertEquals(1, deleted.get());
                    measure(holder.itemView, width, 0);
                    assertTrue(button.getHeight() >= 48 * context.getResources().getDisplayMetrics().density - 1);
                    capture(holder.itemView, "recognition-row-" + size[0] + "-" + language + "-delete");
                    adapter.setEditingEnabled(false); adapter.onBindViewHolder(holder, 0);
                    assertFalse(button.isEnabled()); button.performClick(); assertEquals(1, deleted.get());
                    adapter.setDeletionVisible(false); adapter.onBindViewHolder(holder, 0);
                    assertEquals(View.GONE, button.getVisibility());
                    recycler.setAdapter(null);
                }
            }
        });
    }

    /** 启动独立识别设置，等主线程完成 Fragment 布局。 */
    private SettingsPageActivity open() {
        SettingsPageActivity page = (SettingsPageActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), SettingsPageActivity.class)
                .putExtra("page", "recognition").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.waitForIdleSync();
        return page;
    }

    /** 按指定 dp 窗口测量，行高为零时由内容决定。 */
    private void measure(View view, int widthDp, int heightDp) {
        float density = view.getResources().getDisplayMetrics().density;
        view.measure(View.MeasureSpec.makeMeasureSpec(Math.round(widthDp * density), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.round(heightDp * density), heightDp == 0 ? View.MeasureSpec.UNSPECIFIED : View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    /** 将真实控件绘制到测试目录，不向网络上传。 */
    private void capture(View view, String name) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        try (FileOutputStream stream = new FileOutputStream(new File(ins.getTargetContext().getExternalFilesDir(null), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
