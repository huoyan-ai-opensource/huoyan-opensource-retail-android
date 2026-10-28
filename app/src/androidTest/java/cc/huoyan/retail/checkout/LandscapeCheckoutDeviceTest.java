package cc.huoyan.retail.checkout;

import cc.huoyan.retail.home.HomeActivity;
import cc.huoyan.retail.settings.SettingsPageActivity;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.SdkGoodsSource;
import cc.huoyan.retail.receiver.DebugInputReceiver;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** 首页重新进入收银刷新清单，计件和真实称重结算回归。 */
@RunWith(AndroidJUnit4.class)
public class LandscapeCheckoutDeviceTest {
    /** 商品查询及测试数据创建都使用已连接的 SDK。 */
    @org.junit.Before public void connectGoodsSdk() { cc.huoyan.retail.goods.GoodsFixtureCleanup.awaitSdk(); }

    private cc.huoyan.retail.checkout.domain.CheckoutState state() {
        return ((CheckoutPresenter) field("presenter")).state();
    }

    private final Instrumentation ins=InstrumentationRegistry.getInstrumentation();
    private MainActivity main;
    private static final String PLU="99140923";

    @Test public void manualCountCanCheckoutAndHomeStartsFresh() throws Exception {
        SharedPreferences sp=ins.getTargetContext().getSharedPreferences("HuoYanApplication",0);
        Object weigh=sp.getAll().get(ModuleConfig.SP_WEIGHING_ENABLED);
        Object print=sp.getAll().get(ModuleConfig.SP_PRINT_ENABLED);
        assertNull(SdkGoodsSource.getInstance().getGoodsByPlu(PLU));
        GoodsEntity goods=new GoodsEntity();goods.setPlu(PLU);goods.setName("横屏收银回归");goods.setPrice(1234);goods.setUnit(5);
        SdkGoodsSource.getInstance().save(goods, false);
        try {
            ModuleConfig.setWeighingEnabled(ins.getTargetContext(),false);
            ModuleConfig.setPrintEnabled(ins.getTargetContext(),false);
            main=(MainActivity)ins.startActivitySync(new Intent(ins.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ins.waitForIdleSync();SystemClock.sleep(1000);
            ins.runOnMainSync(()->main.findViewById(R.id.retryRecognize).performClick());
            ins.getTargetContext().sendBroadcast(new Intent(DebugInputReceiver.ACTION_RECOGNIZE)
                    .setPackage(ins.getTargetContext().getPackageName()).putExtra(DebugInputReceiver.EXTRA_RECOGNIZE_PLU,PLU));
            await(()->cart().size()==1&&PLU.equals(cart().get(0).getGoods().getPlu()));
            RecyclerView list=main.findViewById(R.id.resultRecycler);
            await(()->list.findViewHolderForAdapterPosition(0)!=null);
            ins.runOnMainSync(()->list.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.modifyCount).performClick());
            await(()->countDialog()!=null);
            ins.runOnMainSync(()->{
                countDialog().requireView().findViewById(R.id.num3).performClick();
                countDialog().requireView().findViewById(R.id.confirm).performClick();
            });
            await(()->cart().get(0).getCount()==3);
            ins.runOnMainSync(()->{assertTrue(main.findViewById(R.id.confirm).isEnabled());main.findViewById(R.id.confirm).performClick();});
            await(()->"SOLD_OR_REPORTED".equals(state().phase.toString()));
            assertEquals(3,(int)cart().get(0).getCount());
            MainActivity previous = main;
            ins.runOnMainSync(() -> previous.onBackPressed());
            ins.waitForIdleSync(); await(previous::isDestroyed);
            HomeActivity[] resumedHome = {null};
            ins.runOnMainSync(() -> {
                for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                    if (activity instanceof HomeActivity) resumedHome[0] = (HomeActivity) activity;
                }
            });
            assertNotNull(resumedHome[0]);
            HomeActivity home = resumedHome[0];
            Instrumentation.ActivityMonitor monitor=ins.addMonitor(MainActivity.class.getName(),null,false);
            ins.runOnMainSync(()->home.findViewById(R.id.enterCheckout).performClick());
            main=(MainActivity)ins.waitForMonitorWithTimeout(monitor,5000);ins.removeMonitor(monitor);
            assertNotNull(main);assertNotSame(previous,main);ins.waitForIdleSync();
            assertFalse("新一轮不得携带上一单商品",cart().stream().anyMatch(item->item.getGoods()!=null&&PLU.equals(item.getGoods().getPlu())));
            assertNotEquals("SOLD_OR_REPORTED",state().phase.toString());
        } finally {
            if(main!=null)ins.runOnMainSync(main::finish);
            cc.huoyan.retail.goods.GoodsFixtureCleanup.deleteByPlu(PLU);
            SharedPreferences.Editor e=sp.edit();restore(e,ModuleConfig.SP_WEIGHING_ENABLED,weigh);restore(e,ModuleConfig.SP_PRINT_ENABLED,print);e.commit();
        }
    }
    /** 串口真秤与计件混合计价，返回设置后仍可打印；称重关闭时按前置校验拦截。 */
    @Test public void weightedAndMixedReceiptOnRealScale() throws Exception {
        SharedPreferences sp=ins.getTargetContext().getSharedPreferences("HuoYanApplication",0);
        Object weigh=sp.getAll().get(ModuleConfig.SP_WEIGHING_ENABLED);
        Object print=sp.getAll().get(ModuleConfig.SP_PRINT_ENABLED);
        String weightPlu="99140924", countPlu="99140925";
        assertNull(SdkGoodsSource.getInstance().getGoodsByPlu(weightPlu));
        assertNull(SdkGoodsSource.getInstance().getGoodsByPlu(countPlu));
        GoodsEntity weighted=new GoodsEntity();weighted.setPlu(weightPlu);weighted.setName("称重回归");weighted.setPrice(1000);weighted.setUnit(0);
        GoodsEntity counted=new GoodsEntity();counted.setPlu(countPlu);counted.setName("计件回归");counted.setPrice(250);counted.setUnit(5);
        SdkGoodsSource.getInstance().save(weighted, false);SdkGoodsSource.getInstance().save(counted, false);
        try {
            ModuleConfig.setWeighingEnabled(ins.getTargetContext(),true);
            boolean printReceipt = !"true".equals(InstrumentationRegistry.getArguments().getString("skipReceiptPrinting"));
            ModuleConfig.setPrintEnabled(ins.getTargetContext(),printReceipt);
            main=(MainActivity)ins.startActivitySync(new Intent(ins.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->state().scaleConnected && state().currentWeightKg.doubleValue()>0 && state().stableReadCount>=3);
            ins.runOnMainSync(()->main.findViewById(R.id.retryRecognize).performClick());
            inject(weightPlu+","+countPlu);
            await(()->cart().size()==2);
            await(()->countDialog()!=null);
            ins.runOnMainSync(()->countDialog().requireView().findViewById(R.id.confirm).performClick());
            await(()->state().receipt!=null && main.findViewById(R.id.confirm).isEnabled());
            cc.huoyan.retail.checkout.domain.WeightedSaleResult result=state().receipt;
            long expected=result.getWeightKg().multiply(java.math.BigDecimal.valueOf(1000))
                    .setScale(0,java.math.RoundingMode.HALF_UP).longValueExact()+250;
            assertEquals(expected,result.getTotalAmountCent());
            SettingsPageActivity page=(SettingsPageActivity)ins.startActivitySync(new Intent(ins.getTargetContext(),SettingsPageActivity.class)
                    .putExtra("page","shop").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ins.waitForIdleSync();SystemClock.sleep(400);
            ins.runOnMainSync(page::finish);ins.waitForIdleSync();SystemClock.sleep(1500);
            assertEquals(2,cart().size());
            cc.huoyan.retail.checkout.domain.WeightedSaleResult resumed = state().receipt;
            assertNotNull(resumed);
            assertEquals("返回设置后仍是同一单", result.getSaleSessionId(), resumed.getSaleSessionId());
            // 真秤允许 5g 内波动而不重开识别；金额应跟随当前读数。
            long resumedExpected = resumed.getWeightKg().multiply(java.math.BigDecimal.valueOf(1000))
                    .setScale(0,java.math.RoundingMode.HALF_UP).longValueExact()+250;
            assertEquals(resumedExpected, resumed.getTotalAmountCent());
            ins.runOnMainSync(()->main.findViewById(R.id.confirm).performClick());
            await(()->"SOLD_OR_REPORTED".equals(state().phase.toString()));
            SettingsPageActivity scale=(SettingsPageActivity)ins.startActivitySync(new Intent(ins.getTargetContext(),SettingsPageActivity.class)
                    .putExtra("page","scale").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ModuleConfig.setWeighingEnabled(ins.getTargetContext(),false);
            ins.runOnMainSync(scale::finish);ins.waitForIdleSync();
            ins.runOnMainSync(()->main.findViewById(R.id.retryRecognize).performClick());inject(weightPlu);
            await(()->cart().size()==1&&weightPlu.equals(cart().get(0).getGoods().getPlu()));
            assertFalse("称重关闭后不可结算称重商品",main.findViewById(R.id.confirm).isEnabled());
        } finally {
            if(main!=null)ins.runOnMainSync(main::finish);
            cc.huoyan.retail.goods.GoodsFixtureCleanup.deleteByPlu(weightPlu);cc.huoyan.retail.goods.GoodsFixtureCleanup.deleteByPlu(countPlu);
            SharedPreferences.Editor e=sp.edit();restore(e,ModuleConfig.SP_WEIGHING_ENABLED,weigh);restore(e,ModuleConfig.SP_PRINT_ENABLED,print);e.commit();
        }
    }
    private void inject(String plus){ins.getTargetContext().sendBroadcast(new Intent(DebugInputReceiver.ACTION_RECOGNIZE)
            .setPackage(ins.getTargetContext().getPackageName()).putExtra(DebugInputReceiver.EXTRA_RECOGNIZE_PLU,plus));}
    private void restore(SharedPreferences.Editor e,String key,Object v){if(v==null)e.remove(key);else e.putBoolean(key,(Boolean)v);}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(main);}catch(Exception e){throw new AssertionError(e);}}
    @SuppressWarnings("unchecked") private List<GoodsVo> cart(){return ((CheckoutPresenter) field("presenter")).rows();}
    private DialogFragment countDialog(){for(androidx.fragment.app.Fragment f:main.getSupportFragmentManager().getFragments())if(f instanceof cc.huoyan.retail.checkout.RecognizeCountKeyboardDialog)return (DialogFragment)f;return null;}
    private void await(BooleanSupplier c){long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){if(c.getAsBoolean())return;SystemClock.sleep(100);}assertTrue(c.getAsBoolean());}
}
