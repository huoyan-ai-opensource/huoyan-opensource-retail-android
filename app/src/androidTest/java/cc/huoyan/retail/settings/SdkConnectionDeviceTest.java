package cc.huoyan.retail.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.sdk.SdkChannelManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 回归本机点击保存、局域网填写后保存，以及重新进入页面后的配置回显。 */
@RunWith(AndroidJUnit4.class)
public class SdkConnectionDeviceTest {
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    private SettingsPageActivity page;

    /** 局域网 Tab 不修改连接；保存后保留选择，非法地址不能覆盖配置。 */
    @Test public void channelSelectionSurvivesLeavingPage() {
        assumeTrue(Build.VERSION.SDK_INT >= 27);
        SdkChannelManager manager = SdkChannelManager.getInstance();
        cc.huoyan.sdk.client.SdkConfig.Channel originalChannel = manager.getChannel();
        String originalEndpoint = SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT);
        try {
            ins.runOnMainSync(() -> {
                manager.setChannel(cc.huoyan.sdk.client.SdkConfig.Channel.AIDL);
                manager.setWsEndpoint("ws://127.0.0.1:18086/ws");
            });
            open();
            click(R.id.sdkWs);
            assertEquals("aidl", SPManager.getInstance().getString(SPManager.SP_SDK_CHANNEL));
            click(R.id.confirm);
            assertEquals("ws", SPManager.getInstance().getString(SPManager.SP_SDK_CHANNEL));
            close();
            open();
            ins.runOnMainSync(() -> {
                assertTrue(page.findViewById(R.id.sdkWs).isSelected());
                assertFalse(page.findViewById(R.id.sdkAidl).isSelected());
                assertTrue(page.findViewById(R.id.wsAddress).isEnabled());
                ((EditText) page.findViewById(R.id.wsAddress)).setText("invalid-ip");
            });
            click(R.id.confirm);
            assertEquals("ws://127.0.0.1:18086/ws",
                    SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT));
            ins.runOnMainSync(() -> assertNotNull(
                    ((EditText) page.findViewById(R.id.wsAddress)).getError()));
            click(R.id.sdkAidl);
            assertEquals("aidl", SPManager.getInstance().getString(SPManager.SP_SDK_CHANNEL));
            close();
            open();
            ins.runOnMainSync(() -> {
                assertTrue(page.findViewById(R.id.sdkAidl).isSelected());
                assertFalse(page.findViewById(R.id.sdkWs).isSelected());
                assertFalse(page.findViewById(R.id.wsAddress).isEnabled());
                assertEquals("127.0.0.1", ((EditText) page.findViewById(R.id.wsAddress)).getText().toString());
            });
        } finally {
            close();
            ins.runOnMainSync(() -> {
                manager.setChannel(cc.huoyan.sdk.client.SdkConfig.Channel.WS);
                // 先重置桥再恢复原始端点，兼容原本没有显式配置地址的设备。
                manager.setWsEndpoint("ws://127.0.0.2:18086/ws");
                SPManager.getInstance().putString(SPManager.SP_WS_ENDPOINT, originalEndpoint);
                manager.setChannel(originalChannel);
            });
        }
    }

    /** 地址弹窗只保存端点，不把当前 AIDL 切成 WS；非法输入不得覆盖配置。 */
    @Test public void addressDialogPreservesChannel() {
        SdkChannelManager manager = SdkChannelManager.getInstance();
        cc.huoyan.sdk.client.SdkConfig.Channel originalChannel = manager.getChannel();
        String originalEndpoint = SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT);
        try {
            ins.runOnMainSync(() -> manager.setChannel(cc.huoyan.sdk.client.SdkConfig.Channel.AIDL));
            open();
            WsConnectionDialog dialog = new WsConnectionDialog();
            ins.runOnMainSync(() -> {
                dialog.showNow(page.getSupportFragmentManager(), "wsAddressTest");
                ((EditText) dialog.requireView().findViewById(R.id.wsAddress)).setText("invalid");
                dialog.requireView().findViewById(R.id.confirm).performClick();
                assertNotNull(((EditText) dialog.requireView().findViewById(R.id.wsAddress)).getError());
                assertEquals(originalEndpoint, SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT));
                ((EditText) dialog.requireView().findViewById(R.id.wsAddress)).setText("127.0.0.2");
                dialog.requireView().findViewById(R.id.confirm).performClick();
                assertEquals(cc.huoyan.sdk.client.SdkConfig.Channel.AIDL, manager.getChannel());
                assertEquals("ws://127.0.0.2:18086/ws", SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT));
            });
            ins.waitForIdleSync();
            assertFalse(dialog.isAdded());
        } finally {
            close();
            ins.runOnMainSync(() -> {
                manager.setChannel(cc.huoyan.sdk.client.SdkConfig.Channel.WS);
                manager.setWsEndpoint("ws://127.0.0.3:18086/ws");
                SPManager.getInstance().putString(SPManager.SP_WS_ENDPOINT, originalEndpoint);
                manager.setChannel(originalChannel);
            });
        }
    }

    private void open() {
        page = (SettingsPageActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                SettingsPageActivity.class).putExtra("page", "sdk").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.waitForIdleSync();
    }

    private void close() {
        if (page != null) {
            ins.runOnMainSync(page::finish);
            ins.waitForIdleSync();
            page = null;
        }
    }

    private void click(int id) {
        ins.runOnMainSync(() -> assertTrue(page.findViewById(id).performClick()));
        ins.waitForIdleSync();
    }
}
