package cc.huoyan.retail.settings;

import cc.huoyan.retail.checkout.MainActivity;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.config.ModuleConfig;
import cc.huoyan.retail.config.Scene;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.retail.device.camera.CameraUtils;
import cc.huoyan.retail.common.ui.FloatSeekBar;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** 独立设置页真实控件与硬件回归；结束后还原改动的本地配置。 */
@RunWith(AndroidJUnit4.class)
public class LandscapeSettingsDeviceTest {
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    private SettingsPageActivity page;

    @Test public void settingsPersistAndHardwareWorks() throws Exception {
        SharedPreferences sp = ins.getTargetContext().getSharedPreferences("HuoYanApplication", Context.MODE_PRIVATE);
        String[] keys = {ModuleConfig.SP_SCENE, ModuleConfig.SP_WEIGHING_ENABLED, ModuleConfig.SP_PRINT_ENABLED,
                SPManager.SP_STABLE_RECOGNITION, SPManager.SP_RECOGNITION_SENSITIVITY_LEVEL,
                SPManager.SP_SHOP_NAME, SPManager.SP_SHOP_ADDRESS, SPManager.SP_SHOP_PHONE, "cameraMirror",
                "recognition_confidence_percent", "recognition_iou_percent"};
        Map<String, Object> before = new HashMap<>();
        for (String key : keys) before.put(key, sp.getAll().get(key));
        ins.startActivitySync(new Intent(ins.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            open("recognition");
            assertFalse(page.findViewById(R.id.currentModel).isClickable());
            assertTrue(page.findViewById(R.id.switchModel).isClickable());
            ins.runOnMainSync(() -> {
                ((FloatSeekBar) page.findViewById(R.id.confSeekBar)).setFloatValue(.58f);
                ((FloatSeekBar) page.findViewById(R.id.iouSeekBar)).setFloatValue(.61f);
                ((SeekBar) page.findViewById(R.id.recognitionSensitivity)).setProgress(3);
                ((Switch)page.findViewById(R.id.stableRecognition)).setChecked(true);
            });
            click(R.id.saveSettings);
            back(); open("recognition");
            assertEquals("0.58",value(R.id.confText));
            assertEquals("0.61",value(R.id.iouText));
            assertEquals(3, ((SeekBar) page.findViewById(R.id.recognitionSensitivity)).getProgress());
            android.view.ViewGroup labels = page.findViewById(R.id.recognitionSensitivityLabels);
            assertTrue(labels.getChildAt(3).isSelected());
            assertTrue(((Switch)page.findViewById(R.id.stableRecognition)).isChecked());
            back(); open("shop");
            ins.runOnMainSync(() -> {
                ((EditText)page.findViewById(R.id.shop_name)).setText("横屏验收门店");
                ((EditText)page.findViewById(R.id.shopPhone)).setText("01012345678");
                ((EditText)page.findViewById(R.id.shopAddress)).setText("测试地址");
            });
            click(R.id.saveShop);back();open("shop");
            assertEquals("横屏验收门店",value(R.id.shop_name));
            assertEquals("01012345678",value(R.id.shopPhone));
            assertEquals("测试地址",value(R.id.shopAddress));back();
            open("camera");
            await("真实 USB 相机画面",() -> value(R.id.cameraStatus).contains("已连接"),15000);
            boolean originalMirror=sp.getBoolean("cameraMirror",false);
            click(R.id.cameraMirror);
            assertEquals(!originalMirror,sp.getBoolean("cameraMirror",false));
            assertEquals(originalMirror ? 1f:-1f,page.findViewById(R.id.surfaceView).getScaleX(),.001f);
            click(R.id.cameraMirror);click(R.id.saveSettings);back();
            open("scale");
            await("串口列表",() -> !value(R.id.scaleTestStatus).contains("读取串口"),10000);
            click(R.id.testScalePort);
            await("真实串口秤读数",() -> value(R.id.scaleTestStatus).contains("正在读取重量"),15000);
            assertTrue("重量应为三位小数",value(R.id.scaleWeight).matches("-?\\d+\\.\\d{3}"));
            assertTrue(value(R.id.scaleStable).contains("稳定"));
            click(R.id.testScalePort);back();
            open("print");
            await("打印机型号枚举",() -> value(R.id.printerModel).contains("["),10000);
            click(R.id.communicationTest);
            await("真实打印机连接",() -> toastValue().equals(page.getString(R.string.toast_printer_connected)),55000);
            // 连续回归时可跳过真实出纸；设备枚举和连接检查仍执行。
            if (!"true".equals(InstrumentationRegistry.getArguments().getString("skipReceiptPrinting"))) {
                click(R.id.printTest);
                await("试打完成",() -> toastValue().equals(page.getString(R.string.toast_test_print_done)),55000);
            }
            back();open("sdk");
            if (SdkChannelManager.getInstance().getChannel()==cc.huoyan.sdk.client.SdkConfig.Channel.WS) {
                click(R.id.wsProbe);
                await("真实 WS 地址可达",() -> value(R.id.wsStatus).contains("成功"),15000);
            } else {
                assertEquals(View.GONE, page.findViewById(R.id.sdkLanContent).getVisibility());
                assertTrue(value(R.id.sdkLocalStatus).contains("已连接"));
            }
            back();open("authorization");
            await("授权查询",() -> value(R.id.app_version).startsWith("v"),15000);
            assertNotNull(page.findViewById(R.id.activate));
            back();
        } finally {
            if (page!=null&&!page.isFinishing())ins.runOnMainSync(page::finish);
            SharedPreferences.Editor editor=sp.edit();
            for(String key:keys){Object v=before.get(key);if(v==null)editor.remove(key);
                else if(v instanceof String)editor.putString(key,(String)v);
                else if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);
                else if(v instanceof Integer)editor.putInt(key,(Integer)v);
                else throw new AssertionError("未处理的配置类型");}
            editor.commit();
            ins.runOnMainSync(() -> CameraUtils.getInstance().getCameraUsbManager().setMirrorEnabled(
                    before.get("cameraMirror") instanceof Boolean && (Boolean)before.get("cameraMirror")));
        }
    }

    /** 读取一次性 Toast 回执，打印页不再在模块内展示操作结果。 */
    private String toastValue() {
        String[] result = {""};
        ins.runOnMainSync(() -> {
            result[0] = cc.huoyan.retail.common.ui.ToastTestProbe.text();
        });
        return result[0];
    }

    private void open(String name) {
        page=(SettingsPageActivity)ins.startActivitySync(new Intent(ins.getTargetContext(),SettingsPageActivity.class)
                .putExtra("page",name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.waitForIdleSync();SystemClock.sleep(500);
    }
    private void back(){click(R.id.navBack);ins.waitForIdleSync();SystemClock.sleep(300);}
    private void click(int id){ins.runOnMainSync(()->{View v=page.findViewById(id);assertNotNull(v);assertTrue(v.isEnabled());v.performClick();});ins.waitForIdleSync();}
    private String value(int id){return ((TextView)page.findViewById(id)).getText().toString();}
    private void await(String label,BooleanSupplier condition,long timeout){long end=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<end){if(condition.getAsBoolean())return;SystemClock.sleep(100);}assertTrue(label,condition.getAsBoolean());}
}
