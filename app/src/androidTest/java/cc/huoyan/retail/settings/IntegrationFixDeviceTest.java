package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.goods.AddGoodsDialog;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** C50D 联调界面回归：仅打开页面、切换表单，不写商品或设备配置。 */
@RunWith(AndroidJUnit4.class)
public class IntegrationFixDeviceTest {
    /** 测试与主线程调度边界。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 指定方向分别运行，Activity 关闭后恢复设备原方向。 */
    private int orientation() {
        return "portrait".equals(InstrumentationRegistry.getArguments().getString("orientation"))
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }
    /** 相机真实帧回显与精简后的选择弹窗，重新创建后仍有预览。 */
    @Test public void cameraFrameAndPicker() throws Exception {
        try (ActivityScenario<SettingsPageActivity> scene = launch("camera")) {
            SystemClock.sleep(2500);
            scene.onActivity(a -> {
                assertNull(a.findViewById(R.id.saveSettings));
                visible(a.findViewById(R.id.cameraTypeLayout));
                visible(a.findViewById(R.id.surfaceView));
                assertFalse(((TextView) a.findViewById(R.id.cameraType)).getText().toString().contains("UVC"));
                assertEquals("1920 × 1080", ((TextView) a.findViewById(R.id.cameraResolution)).getText().toString());
            });
            capture("camera");
            scene.onActivity(a -> a.findViewById(R.id.cameraTypeLayout).performClick()); settle();
            scene.onActivity(a -> {
                DialogFragment dialog = childDialog(a, "selectOption"); assertNotNull(dialog);
                assertEquals(View.GONE, dialog.requireView().findViewById(R.id.selectSubtitle).getVisibility());
                visible(dialog.requireView().findViewById(R.id.selectCancel));
            });
            capture("camera-picker");
            scene.onActivity(a -> childDialog(a, "selectOption").dismiss()); settle();
            scene.recreate(); settle();
        }
    }
    /** 新商品皮重、计价字体状态与底部确认在两个方向均可达。 */
    @Test public void goodsTareAndPricingColors() throws Exception {
        try (ActivityScenario<SettingsPageActivity> scene = launch("goods")) {
            scene.onActivity(a -> new AddGoodsDialog().showNow(a.getSupportFragmentManager(), "goodsFixture")); settle();
            scene.onActivity(a -> {
                View root = ((DialogFragment)a.getSupportFragmentManager().findFragmentByTag("goodsFixture")).requireView();
                RadioButton weight = root.findViewById(R.id.pricingTypeWeight), count = root.findViewById(R.id.pricingTypeCount);
                assertTrue(weight.isChecked());
                assertEquals("商品单价（元/kg）", ((TextView)root.findViewById(R.id.goodsPriceLabel)).getText().toString());
                assertEquals(0xff000000, count.getCurrentTextColor());
                assertNotEquals(count.getCurrentTextColor(), weight.getCurrentTextColor());
                visible(root.findViewById(R.id.goodsTare));
                assertEquals("0", ((TextView)root.findViewById(R.id.goodsTare)).getText().toString());
                count.performClick(); assertEquals(View.GONE,root.findViewById(R.id.goodsTareField).getVisibility());
                assertEquals("商品单价（元/个）", ((TextView)root.findViewById(R.id.goodsPriceLabel)).getText().toString());
                assertEquals(0xff000000, weight.getCurrentTextColor());
                weight.performClick(); visible(root.findViewById(R.id.goodsTare));
                visible(root.findViewById(R.id.confirm));
            });
            capture("goods-tare");
        }
    }
    /** 售价与皮重数字弹窗支持精度限制、取消、清空及旋转后回填。 */
    @Test public void goodsNumberInputAndRestore() throws Exception {
        try (ActivityScenario<SettingsPageActivity> scene = launch("goods")) {
            scene.onActivity(a -> new AddGoodsDialog().showNow(a.getSupportFragmentManager(), "goodsFixture")); settle();
            scene.onActivity(a -> {
                View root = goods(a).requireView();
                Rect price = bounds(root.findViewById(R.id.price)), tare = bounds(root.findViewById(R.id.goodsTare));
                assertEquals(price.top, tare.top); assertTrue(price.right < tare.left);
                root.findViewById(R.id.price).performClick();
            }); settle();
            scene.onActivity(a -> {
                View keyboard = number(a).requireView();
                assertEquals("商品单价（元/kg）", ((TextView)keyboard.findViewById(R.id.keyboardTitle)).getText().toString());
                for (int id : new int[]{R.id.num1,R.id.num2,R.id.dot,R.id.num3,R.id.num4,R.id.num5}) keyboard.findViewById(id).performClick();
                assertEquals("12.34", ((TextView)keyboard.findViewById(R.id.goodsNumberValue)).getText().toString());
                visible(keyboard.findViewById(R.id.confirm));
            }); capture("goods-price-keyboard");
            scene.recreate(); settle();
            scene.onActivity(a -> {
                View keyboard=number(a).requireView();
                assertEquals("12.34", ((TextView)keyboard.findViewById(R.id.goodsNumberValue)).getText().toString());
                keyboard.findViewById(R.id.confirm).performClick();
            }); settle();
            scene.onActivity(a -> {
                assertEquals("12.34", ((TextView)goods(a).requireView().findViewById(R.id.price)).getText().toString());
                goods(a).requireView().findViewById(R.id.goodsTare).performClick();
            }); settle();
            scene.onActivity(a -> {
                View keyboard=number(a).requireView();
                assertEquals(View.INVISIBLE,keyboard.findViewById(R.id.dot).getVisibility());
                keyboard.findViewById(R.id.num2).performClick(); keyboard.findViewById(R.id.num5).performClick();
                keyboard.findViewById(R.id.confirm).performClick();
            }); settle();
            scene.recreate(); settle();
            scene.onActivity(a -> {
                View root=goods(a).requireView();
                assertEquals("25",((TextView)root.findViewById(R.id.goodsTare)).getText().toString());
                assertEquals("12.34",((TextView)root.findViewById(R.id.price)).getText().toString());
                root.findViewById(R.id.goodsTare).performClick();
            }); settle();
            scene.onActivity(a -> { number(a).requireView().findViewById(R.id.clear).performClick();
                assertEquals("0",((TextView)number(a).requireView().findViewById(R.id.goodsNumberValue)).getText().toString()); });
            capture("goods-tare-keyboard");
            scene.onActivity(a -> number(a).requireView().findViewById(R.id.cancel).performClick()); settle();
            scene.onActivity(a -> assertEquals("25",((TextView)goods(a).requireView().findViewById(R.id.goodsTare)).getText().toString()));
        }
    }
    /** 取得本次商品表单。 */
    private DialogFragment goods(SettingsPageActivity a) { return (DialogFragment)a.getSupportFragmentManager().findFragmentByTag("goodsFixture"); }
    /** 取得商品表单所属的数字弹窗。 */
    private DialogFragment number(SettingsPageActivity a) { return (DialogFragment)goods(a).getChildFragmentManager().findFragmentByTag("goodsNumber"); }
    /** 全局边界供并排对齐断言使用。 */
    private Rect bounds(View view) { Rect rect=new Rect(); assertTrue(view.getGlobalVisibleRect(rect)); return rect; }

    /** 编辑表单加载原值，切换计价不改变高度，数字弹窗确认后回填。 */
    @Test public void editGoodsNumberAndFixedHeight() throws Exception {
        try (ActivityScenario<SettingsPageActivity> scene = launch("goods")) {
            scene.onActivity(a -> {
                cc.huoyan.retail.data.GoodsEntity fixture = new cc.huoyan.retail.data.GoodsEntity();
                fixture.setPlu("UITEST"); fixture.setName("界面验收商品");
                fixture.setPrice(1234); fixture.setTareWeight(25); fixture.setUnit(1);
                AddGoodsDialog dialog = new AddGoodsDialog(); dialog.setEditingGoods(fixture);
                dialog.showNow(a.getSupportFragmentManager(), "goodsFixture");
            }); settle();
            int[] height = new int[1];
            scene.onActivity(a -> {
                View root = goods(a).requireView(); height[0] = bounds(root.findViewById(R.id.close)).top;
                assertFalse(root.findViewById(R.id.plu).isEnabled());
                assertEquals("12.34", ((TextView)root.findViewById(R.id.price)).getText().toString());
                assertEquals("25", ((TextView)root.findViewById(R.id.goodsTare)).getText().toString());
                root.findViewById(R.id.pricingTypeCount).performClick();
            }); settle();
            scene.onActivity(a -> {
                View root = goods(a).requireView();
                assertEquals(View.GONE, root.findViewById(R.id.goodsTareField).getVisibility());
                assertEquals(height[0], bounds(root.findViewById(R.id.close)).top);
                root.findViewById(R.id.price).performClick();
            }); settle();
            scene.recreate(); settle();
            scene.onActivity(a -> {
                assertEquals("商品单价（元/个）", ((TextView)number(a).requireView().findViewById(R.id.keyboardTitle)).getText().toString());
                number(a).requireView().findViewById(R.id.cancel).performClick();
            }); settle();
            capture("goods-edit-count");
            scene.onActivity(a -> {
                View root = goods(a).requireView();
                assertEquals("商品单价（元/个）", ((TextView)root.findViewById(R.id.goodsPriceLabel)).getText().toString());
                root.findViewById(R.id.pricingTypeWeight).performClick();
                root.findViewById(R.id.price).performClick();
            }); settle();
            scene.onActivity(a -> {
                View keyboard = number(a).requireView();
                assertEquals("12.34", ((TextView)keyboard.findViewById(R.id.goodsNumberValue)).getText().toString());
                keyboard.findViewById(R.id.backspace).performClick();
                assertEquals("12.3", ((TextView)keyboard.findViewById(R.id.goodsNumberValue)).getText().toString());
                keyboard.findViewById(R.id.confirm).performClick();
            }); settle();
            scene.onActivity(a -> {
                View root = goods(a).requireView();
                assertEquals("12.30", ((TextView)root.findViewById(R.id.price)).getText().toString());
                assertEquals(height[0], bounds(root.findViewById(R.id.close)).top);
            }); capture("goods-edit");
        }
    }

    /** 串口列表在每次打开时读取，重建后没有残留加载遮罩。 */
    @Test public void serialPickerRefreshAndRecreation() throws Exception {
        try (ActivityScenario<SettingsPageActivity> scene = launch("scale")) {
            for (int i = 0; i < 2; i++) {
                scene.onActivity(a -> a.findViewById(R.id.selectScalePort).performClick()); settle();
                scene.onActivity(a -> {
                    assertNull(childDialog(a,"portsLoading"));
                    assertEquals(View.GONE, childDialog(a,"selectOption").requireView().findViewById(R.id.selectSubtitle).getVisibility());
                    DialogFragment dialog = childDialog(a,"selectOption"); assertNotNull(dialog);
                    visible(dialog.requireView().findViewById(R.id.selectCancel));
                });
                capture("serial-picker-" + i);
                scene.onActivity(a -> childDialog(a,"selectOption").dismiss()); settle();
                scene.recreate(); settle();
            }
            capture("scale-settings");
        }
    }
    /** 创建指定页面并等待方向重建完成。 */
    private ActivityScenario<SettingsPageActivity> launch(String page) {
        ActivityScenario<SettingsPageActivity> scene = ActivityScenario.launch(new Intent(ins.getTargetContext(),SettingsPageActivity.class).putExtra("page",page));
        scene.onActivity(a -> a.setRequestedOrientation(orientation())); settle();
        scene.onActivity(a -> assertEquals(orientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ? android.content.res.Configuration.ORIENTATION_PORTRAIT : android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                a.getResources().getConfiguration().orientation));
        return scene;
    }
    /** 设置页当前 Fragment 所属弹窗。 */
    private DialogFragment childDialog(SettingsPageActivity activity, String tag) {
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.pageContent);
        return (DialogFragment)fragment.getChildFragmentManager().findFragmentByTag(tag);
    }
    /** 等待异步串口与布局，不执行设备写入。 */
    private void settle() { ins.waitForIdleSync(); SystemClock.sleep(700); ins.waitForIdleSync(); }
    /** 关键操作不能被父容器裁切。 */
    private void visible(View view) {
        Rect bounds = new Rect(); assertTrue(view.getGlobalVisibleRect(bounds));
        assertEquals(view.getWidth(),bounds.width()); assertEquals(view.getHeight(),bounds.height());
    }
    /** 保存真实运行截图用于逐页验收。 */
    private void capture(String name) throws Exception {
        settle();
        Bitmap bitmap=ins.getUiAutomation().takeScreenshot();
        File dir=new File(ins.getTargetContext().getExternalFilesDir(null),"integration-review"); dir.mkdirs();
        try(FileOutputStream stream=new FileOutputStream(new File(dir,name+"-"+orientation()+".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);
        } finally { bitmap.recycle(); }
    }
}
