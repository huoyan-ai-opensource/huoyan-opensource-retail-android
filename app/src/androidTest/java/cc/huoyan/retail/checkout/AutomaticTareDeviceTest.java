package cc.huoyan.retail.checkout;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.GoodsFixtureCleanup;
import cc.huoyan.retail.goods.SdkGoodsSource;
import cc.huoyan.retail.R;
import cc.huoyan.retail.device.scale.AclasWeightSnapshot;
import cc.huoyan.retail.checkout.domain.CheckoutState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 真秤自动去皮验收；读取现场 SDK 商品和相机结果，不写商品库或提交销售。 */
@RunWith(AndroidJUnit4.class)
public class AutomaticTareDeviceTest {
    /** 测试线程到主线程的调度边界。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 当前真机收银页面，仅在测试存活期间引用。 */
    private MainActivity main;
    /** 页面真实 Presenter，主线程执行修改。 */
    private CheckoutPresenter presenter;

    /** 输出现场商品皮重和识别快照，后续验收依据真实 SDK 配置选择商品。 */
    @Test public void inspectCatalogAndScale() throws Exception {
        GoodsFixtureCleanup.awaitSdk();
        for (GoodsEntity goods : SdkGoodsSource.getInstance().getAllGoods())
            report("goods plu=" + goods.getPlu() + " name=" + goods.getName() + " unit=" + goods.getUnit()
                    + " price_cent=" + goods.getPrice() + " tare_g=" + goods.getTareWeight());
        open();
        await(() -> presenter.state().hasStableWeight && presenter.state().recognitionLocked && !presenter.rows().isEmpty());
        ins.runOnMainSync(() -> {
            report("scale net=" + main.scaleSnapshot().netKg() + " tare=" + main.scaleSnapshot().tareKg()
                    + " gross=" + main.scaleSnapshot().grossKg() + " phase=" + presenter.state().phase);
            for (GoodsVo row : presenter.rows()) report("recognized plu=" + row.getGoods().getPlu()
                    + " count=" + row.getCount() + " tare_g=" + row.getGoods().getTareWeight());
        });
    }

    /** 用现场两个识别框改品制造异价冲突，删除后检查真秤皮重、净重、锁定和金额。 */
    @Test public void deletingConflictAppliesRemainingTareOnRealScale() throws Exception {
        GoodsFixtureCleanup.awaitSdk();
        String targetPlu = InstrumentationRegistry.getArguments().getString("tarePlu", "10000");
        GoodsEntity target = SdkGoodsSource.getInstance().getGoodsByPlu(targetPlu);
        assertNotNull("指定皮重商品必须真实存在", target);
        assertTrue("需选择有皮重的称重商品", target.getUnit() != 5 && target.getTareWeight() > 0);
        open();
        await(() -> presenter.state().hasStableWeight && presenter.state().recognitionLocked && !presenter.rows().isEmpty());
        BigDecimal originalTare = main.scaleSnapshot().tareKg();
        boolean deletionEnabled = presenter.isDeletionEnabled();
        BigDecimal targetTare = BigDecimal.valueOf(target.getTareWeight(), 3);
        try {
            List<RecognitionSelection> selections = new ArrayList<>();
            ins.runOnMainSync(() -> {
                for (GoodsVo row : presenter.rows()) selections.addAll(row.getRecognitionSelection());
            });
            assertEquals("现场必须有两个识别框", 2, selections.size());
            ins.runOnMainSync(() -> {
                presenter.setDeletionEnabled(true);
                presenter.toggleItem(selections.get(0));
                replaceSelected(target);
                assertEquals(CheckoutState.Phase.LOCKED_PRICE_CONFLICT, presenter.state().phase);
                assertFalse(presenter.state().automaticTarePending);
                assertFalse(presenter.state().canPrint);
                report("conflict rows=" + presenter.rows().size() + " tare=" + main.scaleSnapshot().tareKg());
            });
            capture("conflict");
            ins.runOnMainSync(() -> {
                GoodsVo other = presenter.rows().stream().filter(row -> !targetPlu.equals(row.getGoods().getPlu())).findFirst().get();
                presenter.deleteRow(other);
                assertEquals(1, presenter.rows().size());
                assertTrue("瞬时抖动可以延后发命令，但旧净重不可结算", presenter.state().automaticTareRequired);
                assertFalse(presenter.state().canPrint);
            });
            await(() -> !presenter.state().automaticTarePending && presenter.state().canPrint);
            ins.runOnMainSync(() -> {
                AclasWeightSnapshot scale = main.scaleSnapshot();
                assertEquals(0, targetTare.compareTo(scale.tareKg()));
                assertEquals(0, scale.grossKg().subtract(targetTare).compareTo(scale.netKg()));
                assertTrue(presenter.state().recognitionLocked);
                assertEquals(1, presenter.rows().size());
                assertEquals(targetPlu, presenter.rows().get(0).getGoods().getPlu());
                long expected = scale.netKg().multiply(BigDecimal.valueOf(target.getPrice()))
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
                assertEquals(expected, presenter.state().receipt.getTotalAmountCent());
                report("remaining plu=" + targetPlu + " count=1 tare=" + scale.tareKg() + " net=" + scale.netKg()
                        + " gross=" + scale.grossKg() + " amount_cent=" + expected + " canPrint=" + presenter.state().canPrint);
            });
            // 持续观察数帧，不能因自动扣重重新识别出已删除的另一项。
            SystemClock.sleep(2500);
            await(() -> {
                androidx.recyclerview.widget.RecyclerView list = main.findViewById(R.id.resultRecycler);
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
                if (holder == null) return false;
                String displayed = ((android.widget.TextView) holder.itemView.findViewById(R.id.goodsCount)).getText().toString();
                boolean matches = new BigDecimal(displayed).compareTo(main.scaleSnapshot().netKg()) == 0;
                if (matches) report("row_weight=" + displayed);
                return matches;
            });
            ins.runOnMainSync(() -> {
                assertEquals(1, presenter.rows().size());
                assertTrue(presenter.state().recognitionLocked);
                assertFalse(presenter.state().automaticTarePending);
            });
            capture("remaining-tare-applied");
        } finally {
            await(() -> !presenter.state().automaticTarePending);
            ins.runOnMainSync(() -> {
                presenter.setDeletionEnabled(deletionEnabled);
                main.findViewById(R.id.retryRecognize).performClick();
            });
            await(() -> presenter.state().recognitionLocked && presenter.state().hasStableWeight
                    && !presenter.state().automaticTarePending && main.scaleSnapshot().tareKg() != null);
            java.util.concurrent.atomic.AtomicReference<Boolean> restored = new java.util.concurrent.atomic.AtomicReference<>();
            ins.runOnMainSync(() -> main.commandScale(originalTare, (ok, message) -> {
                report("restore result=" + ok + " message=" + message);
                restored.set(ok);
            }));
            await(() -> restored.get() != null);
            assertTrue("恢复测试前设备皮重", restored.get());
            report("restored original tare=" + originalTare);
        }
    }

    /** 调用生产改品确认入口，只修改当前销售清单，不写商品资料。 */
    private void replaceSelected(GoodsEntity target) {
        try {
            Method method = CheckoutPresenter.class.getDeclaredMethod("replaceSelected", GoodsEntity.class);
            method.setAccessible(true);
            assertEquals(Boolean.TRUE, method.invoke(presenter, target));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    /** 保存真机屏幕供验收，不向外部服务上传。 */
    private void capture(String name) {
        File file = new File(ins.getTargetContext().getExternalFilesDir(null), "auto-tare-" + name + ".png");
        android.graphics.Bitmap screenshot = ins.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { screenshot.recycle(); }
    }

    /** 启动生产收银页，连接真实相机和串口。 */
    private void open() throws Exception {
        main = (MainActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Field field = MainActivity.class.getDeclaredField("presenter");
        field.setAccessible(true);
        presenter = (CheckoutPresenter) field.get(main);
    }

    /** 有界等待设备和 SDK 异步结果，测试不注入重量。 */
    private void await(BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 20000;
        while (SystemClock.elapsedRealtime() < deadline) {
            boolean[] ready = {false};
            ins.runOnMainSync(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            SystemClock.sleep(100);
        }
        ins.runOnMainSync(() -> report("timeout phase=" + presenter.state().phase
                + " pending=" + presenter.state().automaticTarePending + " required=" + presenter.state().automaticTareRequired
                + " rows=" + presenter.rows().size() + " net=" + presenter.state().currentWeightKg
                + " tare=" + (main.scaleSnapshot() == null ? null : main.scaleSnapshot().tareKg())));
        fail("等待现场设备或识别状态超时");
    }

    /** 输出可保留的现场业务证据，不包含 SDK 密钥或门店认证。 */
    private void report(String text) {
        Bundle status = new Bundle(); status.putString("stream", text + "\n"); ins.sendStatus(0, status);
    }
}
