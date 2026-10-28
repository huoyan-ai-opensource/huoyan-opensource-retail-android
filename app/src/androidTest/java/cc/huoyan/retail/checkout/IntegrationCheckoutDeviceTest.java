package cc.huoyan.retail.checkout;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.checkout.domain.*;
import cc.huoyan.retail.pricing.PricingMode;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.data.GoodsEntity;
import java.lang.reflect.Field;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 真实主线程验证收银计时与去皮输入；不调用打印或秤写命令。 */
@RunWith(AndroidJUnit4.class)
public class IntegrationCheckoutDeviceTest {
    /** 主线程同步入口。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 真实按钮在打印关闭时仍提交确认，开关仅改变输出选择；不访问打印机或云端。 */
    @Test public void disabledPrinterShowsConfirmationAndCompletesSale() {
        String language = cc.huoyan.retail.common.LocalePreferenceUtil.getLanguageCode(ins.getTargetContext());
        cc.huoyan.retail.common.LocalePreferenceUtil.setLanguageCode(ins.getTargetContext(),
                InstrumentationRegistry.getArguments().getString("language", language));
        boolean previousPrint = ModuleConfig.isPrintEnabled(ins.getTargetContext());
        boolean previousWeighing = ModuleConfig.isWeighingEnabled(ins.getTargetContext());
        ModuleConfig.setPrintEnabled(ins.getTargetContext(), false);
        ModuleConfig.setWeighingEnabled(ins.getTargetContext(), false);
        java.util.concurrent.atomic.AtomicInteger submissions = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<Exception>> finish = new java.util.concurrent.atomic.AtomicReference<>();
        try (ActivityScenario<MainActivity> scene = ActivityScenario.launch(new Intent(ins.getTargetContext(), MainActivity.class))) {
            scene.onActivity(a -> {
                ((cc.huoyan.sdk.client.session.SdkStartupController) field(a, "sdkStartup")).stop();
                CheckoutPresenter presenter = (CheckoutPresenter) field(a, "presenter");
                CheckoutSession session = (CheckoutSession) field(presenter, "checkout");
                presenter.setWeighingEnabled(false);
                session.lockRecognition();
                session.replaceLines(Collections.singletonList(new SaleLine("TEST", "确认结果测试", 100, 0, 1,
                        PricingMode.COUNT, false, false, null)));
                GoodsEntity goods = new GoodsEntity();
                goods.setName("确认结果测试"); goods.setPlu("TEST"); goods.setUnit(5); goods.setPrice(100);
                setField(presenter, "rows", Collections.singletonList(new GoodsVo(1, 100, goods, Collections.emptyList())));
                setField(presenter, "output", new CheckoutPresenter.Output() {
                    /** 捕获真实页面传入的输出模式，延迟回调以验证防重与等待弹窗。 */
                    public void submit(SalePrintJob job, boolean printEnabled, CheckoutRecognition response,
                            java.util.List<GoodsVo> rows, java.util.List<GoodsVo> deleted,
                            java.util.function.Consumer<Exception> completion) {
                        assertFalse(printEnabled);
                        submissions.incrementAndGet();
                        finish.set(completion);
                    }
                    /** 本例不执行删除操作。 */
                    public void submitDeletion(CheckoutRecognition response, java.util.List<GoodsVo> deleted) {
                        fail("Unexpected deletion");
                    }
                });
                presenter.render();
                TextView button = a.findViewById(R.id.confirm);
                assertTrue(button.isEnabled());
                assertEquals(a.getString(R.string.button_confirm_results), button.getText().toString());
                ModuleConfig.setPrintEnabled(a, true);
                presenter.render();
                assertEquals(a.getString(R.string.button_print_ticket), button.getText().toString());
                ModuleConfig.setPrintEnabled(a, false);
                presenter.refreshRows();
            });
            ins.waitForIdleSync();
            captureConfirmation();
            scene.onActivity(a -> {
                CheckoutPresenter presenter = (CheckoutPresenter) field(a, "presenter");
                TextView button = a.findViewById(R.id.confirm);
                button.performClick();
                assertEquals(1, submissions.get());
                assertFalse(button.isEnabled());
                presenter.confirmResult(false);
                assertEquals(1, submissions.get());
            });
            ins.waitForIdleSync();
            scene.onActivity(a -> {
                assertNotNull(a.getSupportFragmentManager().findFragmentByTag("confirmationLoading"));
                // 完成文案必须沿用提交时的模式，不能根据此后的开关误报打印成功。
                ModuleConfig.setPrintEnabled(a, true);
                setField(a, "sdkRecognitionReady", true);
                finish.get().accept(null);
                assertEquals(CheckoutState.Phase.SOLD_OR_REPORTED, ((CheckoutPresenter) field(a, "presenter")).state().phase);
                assertFalse(a.findViewById(R.id.confirm).isEnabled());
            });
            ins.waitForIdleSync();
            scene.onActivity(a -> {
                assertEquals(a.getString(R.string.status_confirmed_auto_resume),
                        ((TextView) a.findViewById(R.id.recognizeText)).getText().toString());
                assertNull(a.getSupportFragmentManager().findFragmentByTag("confirmationLoading"));
            });
        } finally {
            cc.huoyan.retail.common.LocalePreferenceUtil.setLanguageCode(ins.getTargetContext(), language);
            ModuleConfig.setPrintEnabled(ins.getTargetContext(), previousPrint);
            ModuleConfig.setWeighingEnabled(ins.getTargetContext(), previousWeighing);
        }
    }
    /** 用已完成打印的会话夹具验证十秒前不重启、十秒后自动恢复。 */
    @Test public void countPrintResumesAfterTenSeconds() {
        try (ActivityScenario<MainActivity> scene = ActivityScenario.launch(new Intent(ins.getTargetContext(),MainActivity.class))) {
            scene.onActivity(a -> {
                CheckoutPresenter presenter = (CheckoutPresenter) field(a,"presenter");
                CheckoutSession session = (CheckoutSession) field(presenter,"checkout");
                session.setWeighingEnabled(false); session.lockRecognition();
                session.replaceLines(Collections.singletonList(new SaleLine("TEST","计件夹具",100,0,1,PricingMode.COUNT,false,false,null)));
                SalePrintJob job = session.beginPrint(); session.finishPrint(job,true);
                a.showSaleStatus(CheckoutPresenter.SaleStatus.PRINTED,session.state());
            });
            SystemClock.sleep(9000);
            scene.onActivity(a -> assertEquals(CheckoutState.Phase.SOLD_OR_REPORTED,((CheckoutPresenter)field(a,"presenter")).state().phase));
            SystemClock.sleep(1600);
            scene.onActivity(a -> assertNotEquals(CheckoutState.Phase.SOLD_OR_REPORTED,((CheckoutPresenter)field(a,"presenter")).state().phase));
        }
    }
    /** 清空恢复整数零，旋转恢复也保留该值，不发送硬件命令。 */
    @Test public void tareClearKeepsZeroAcrossRecreation() {
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(new Intent(ins.getTargetContext(),MainActivity.class))) {
            scene.onActivity(a -> new ScaleOperationsDialog().showNow(a.getSupportFragmentManager(),"tareFixture"));
            ins.waitForIdleSync();
            scene.onActivity(a -> {
                ScaleOperationsDialog dialog=(ScaleOperationsDialog)a.getSupportFragmentManager().findFragmentByTag("tareFixture");
                View root=dialog.requireView(); root.findViewById(R.id.clearInput).performClick();
                assertEquals("0",((TextView)root.findViewById(R.id.tareInput)).getText().toString());
            });
            scene.recreate(); ins.waitForIdleSync();
            scene.onActivity(a -> assertEquals("0",((TextView)((ScaleOperationsDialog)a.getSupportFragmentManager()
                    .findFragmentByTag("tareFixture")).requireView().findViewById(R.id.tareInput)).getText().toString()));
        }
    }
    /** 仅测试读取现有状态机；不增加生产调试入口。 */
    private Object field(Object object,String name) {
        try { Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    /** 仅在测试主线程替换边界依赖与内存夹具，不新增生产注入入口。 */
    private void setField(Object object, String name, Object value) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    /** 保存当前窗口的确认入口截图，供横竖屏视觉验收。 */
    private void captureConfirmation() {
        SystemClock.sleep(300);
        android.graphics.Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
        java.io.File file = new java.io.File(ins.getTargetContext().getExternalFilesDir(null),
                "confirm-results-" + bitmap.getWidth() + "x" + bitmap.getHeight() + ".png");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
