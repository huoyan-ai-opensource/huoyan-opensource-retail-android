package cc.huoyan.retail.common.ui;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 原生适配器的列表所有权、空态切换、删除后点击及局部绑定回归。 */
@RunWith(AndroidJUnit4.class)
public class RetailAdapterDeviceTest {
    /** 空态铺满网格，删除最后一项后仍可追加并绑定新数据。 */
    @Test public void emptyToDataToEmptyAndBack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TestAdapter adapter = new TestAdapter();
            RecyclerView recycler = recycler(adapter);
            GridLayoutManager grid = (GridLayoutManager) recycler.getLayoutManager();
            adapter.setEmptyView(R.layout.empty_view);
            layout(recycler);
            assertEquals(1, adapter.getItemCount());
            assertEquals(3, grid.getSpanSizeLookup().getSpanSize(0));
            List<String> source = new ArrayList<>(Arrays.asList("first", "second"));
            adapter.setList(source);
            source.clear();
            layout(recycler);
            assertEquals(2, adapter.getItemCount());
            assertEquals(1, grid.getSpanSizeLookup().getSpanSize(0));
            adapter.removeAt(1);
            adapter.removeAt(0);
            layout(recycler);
            assertTrue(adapter.getData().isEmpty());
            assertEquals(3, grid.getSpanSizeLookup().getSpanSize(0));
            adapter.addData(Collections.singletonList("recaptured"));
            layout(recycler);
            RowViewHolder row = (RowViewHolder) recycler.findViewHolderForAdapterPosition(0);
            assertNotNull(row);
            assertEquals("recaptured", row.<android.widget.TextView>getView(R.id.pluName).getText().toString());
            recycler.setAdapter(null);
        });
    }

    /** 删除前面的条目后，行及子按钮使用当前位置；载荷通知保留局部绑定。 */
    @Test public void clicksFollowCurrentPositionAndPayloadsArePreserved() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TestAdapter adapter = new TestAdapter();
            RecyclerView recycler = recycler(adapter);
            adapter.setList(Arrays.asList("first", "second", "third"));
            AtomicInteger clicked = new AtomicInteger(-1);
            adapter.setOnItemClickListener((a, view, position) -> clicked.set(position));
            adapter.setOnItemChildClickListener((a, view, position) -> clicked.set(position));
            layout(recycler);
            RecyclerView.ViewHolder second = recycler.findViewHolderForAdapterPosition(1);
            assertNotNull(second);
            adapter.removeAt(0);
            second.itemView.performClick();
            assertEquals(0, clicked.get());
            clicked.set(-1);
            second.itemView.findViewById(R.id.pluName).performClick();
            assertEquals(0, clicked.get());
            adapter.removeAt(0);
            clicked.set(-1);
            second.itemView.performClick();
            assertEquals("已删除的行不能再触发业务点击", -1, clicked.get());
            layout(recycler);
            adapter.notifyItemChanged(0, "selection");
            layout(recycler);
            assertTrue(adapter.sawPayload);
            recycler.setAdapter(null);
        });
    }

    private static RecyclerView recycler(TestAdapter adapter) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RecyclerView recycler = new RecyclerView(context);
        recycler.setLayoutManager(new GridLayoutManager(context, 3));
        recycler.setItemAnimator(null);
        recycler.setAdapter(adapter);
        return recycler;
    }

    private static void layout(RecyclerView recycler) {
        recycler.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        recycler.layout(0, 0, 1200, 800);
    }

    private static final class TestAdapter extends RetailAdapter<String> {
        private boolean sawPayload;
        private TestAdapter() {
            super(R.layout.adapter_study_goods);
            addChildClickViewIds(R.id.pluName);
        }
        @Override protected void convert(RowViewHolder holder, String item) {
            holder.setText(R.id.pluName, item);
        }
        @Override protected void convert(RowViewHolder holder, String item, List<? extends Object> payloads) {
            sawPayload |= payloads.contains("selection");
            super.convert(holder, item, payloads);
        }
    }
}
