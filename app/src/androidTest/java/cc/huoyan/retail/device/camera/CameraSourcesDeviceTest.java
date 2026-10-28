package cc.huoyan.retail.device.camera;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.settings.SettingsPageActivity;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.SdkImage;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** 参数化真机验收，沿用设备 SDK 连接，不写商品或学习数据。 */
@RunWith(AndroidJUnit4.class)
public class CameraSourcesDeviceTest {
    /** 真机 UI 调度器。 */
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    /** 本次相机设置页面。 */
    private SettingsPageActivity page;
    /** 当前接收到的真实帧。 */
    private final AtomicReference<CameraFrame> latest = new AtomicReference<>();
    /** 两类相机共用设置、镜像、恢复和图像接口验收。 */
    @Test public void previewSwitchAndSdk() throws Exception {
        String type = InstrumentationRegistry.getArguments().getString("cameraSource", CameraController.UVC);
        CameraController camera = CameraController.getInstance();
        String endpoint = SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT);
        ins.runOnMainSync(() -> camera.addListener("device_test", latest::set));
        try {
            open();
            // 通过现有选择框的结果通道选择类型，覆盖真实页面绑定。
            ins.runOnMainSync(() -> {
                Fragment fragment = page.getSupportFragmentManager().findFragmentById(R.id.pageContent);
                android.os.Bundle result = new android.os.Bundle(); result.putString("value", type);
                fragment.getChildFragmentManager().setFragmentResult("camera_type", result);
            });
            await("相机真实帧", () -> camera.isCurrent(latest.get()), 25000);
            assertEquals(type,camera.type());
            assertTrue(((TextView)page.findViewById(R.id.cameraType)).getText().toString().contains(
                    CameraController.CAMERA2.equals(type) ? "Camera2" : "UVC"));
            CameraFrame frame = latest.get();
            assertEquals(frame.getWidth()*frame.getHeight()*3/2,frame.getNv().length);
            saveFrame(frame,"frame");
            savePreview("preview");
            saveScreen("screen");
            String savedResolution = camera.resolution();
            java.util.List<CameraSize> sizes = camera.getCameraSize();
            if (!sizes.isEmpty()) {
                CameraSize smallest = sizes.get(sizes.size()-1);
                ins.runOnMainSync(() -> camera.selectResolution(smallest.key()));
                await("分辨率切换", () -> camera.isCurrent(latest.get()), 20000);
                ins.runOnMainSync(() -> camera.selectResolution(savedResolution));
                await("分辨率恢复", () -> camera.isCurrent(latest.get()), 20000);
            }
            boolean mirror = camera.mirrorEnabled();
            ins.runOnMainSync(() -> camera.setMirrorEnabled(!mirror));
            assertFalse("切换后旧帧必须失效", camera.isCurrent(frame));
            await("镜像后的新帧", () -> camera.isCurrent(latest.get()), 20000);
            saveFrame(latest.get(),"mirrored-frame"); savePreview("mirrored-preview");
            ins.runOnMainSync(() -> camera.setMirrorEnabled(mirror));
            await("镜像恢复", () -> camera.isCurrent(latest.get()), 20000);
            // 模拟设置页面离开再返回，验证释放与重新绑定。
            ins.runOnMainSync(page::finish); ins.waitForIdleSync(); open();
            await("重进设置供帧", () -> camera.isCurrent(latest.get()), 20000);
            if (CameraController.CAMERA2.equals(type)) {
                String original = camera.getCurrentCameraId();
                for (CameraDeviceInfo info : camera.getDevices()) {
                    ins.runOnMainSync(() -> camera.selectCamera(info.id));
                    await("原生设备 "+info.id, () -> camera.isCurrent(latest.get()), 20000);
                    SystemClock.sleep(1500);
                    saveFrame(latest.get(),"device-"+info.id);
                    saveScreen("screen-device-"+info.id);
                }
                ins.runOnMainSync(() -> camera.selectCamera(original));
                await("恢复原生设备", () -> camera.isCurrent(latest.get()), 20000);
            }
            ins.runOnMainSync(() -> camera.selectType(CameraController.CAMERA2.equals(type) ? CameraController.UVC : CameraController.CAMERA2));
            SystemClock.sleep(600);
            ins.runOnMainSync(() -> camera.selectType(type));
            await("类型往返切换恢复", () -> camera.isCurrent(latest.get()), 25000);
            if ("true".equals(InstrumentationRegistry.getArguments().getString("verifySdk"))) verifySdk(latest.get());
            assertEquals("不得覆盖局域网 SDK 地址",endpoint,SPManager.getInstance().getString(SPManager.SP_WS_ENDPOINT));
        } finally {
            ins.runOnMainSync(() -> { camera.removeListener("device_test"); if(page!=null)page.finish(); });
        }
    }
    /** 启动用户已有的相机设置页面。 */
    private void open() {
        page=(SettingsPageActivity)ins.startActivitySync(new Intent(ins.getTargetContext(),SettingsPageActivity.class)
                .putExtra("page","camera").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.waitForIdleSync();
    }
    /** 保存规范化分析帧，供与预览截图逐项比对。 */
    private void saveFrame(CameraFrame frame,String name) throws Exception {
        NV21ToBitmap converter=new NV21ToBitmap(ins.getTargetContext());
        Bitmap bitmap=converter.nv21ToBitmap(frame.getNv(),frame.getWidth(),frame.getHeight());
        try { save(bitmap,name); } finally { bitmap.recycle();converter.release(); }
    }
    /** 屏幕截图包含 TextureView 变换，缓冲截图仅用于原始纹理诊断。 */
    private void saveScreen(String name) throws Exception {
        SystemClock.sleep(800);
        Bitmap screen=ins.getUiAutomation().takeScreenshot();
        assertNotNull(screen);
        try { save(screen,name); } finally { screen.recycle(); }
    }
    /** 从同一预览组件截取图像，不依赖窗口截屏权限。 */
    private void savePreview(String name) throws Exception {
        AtomicReference<Bitmap> image=new AtomicReference<>();
        ins.runOnMainSync(() -> image.set(((AutoFitTextureView)page.findViewById(R.id.surfaceView)).getBitmap()));
        assertNotNull(image.get());try { save(image.get(),name); } finally { image.get().recycle(); }
    }
    /** 将验收图保存到应用私有目录，测试后由 adb 提取。 */
    private void save(Bitmap image,String name) throws Exception {
        File dir=new File(ins.getTargetContext().getFilesDir(),"camera-evidence"); assertTrue(dir.exists()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
    }
    /** 用真实相机图像访问用户已配置 SDK，验证初始化、检测和识别回执。 */
    private void verifySdk(CameraFrame frame) throws Exception {
        HuoyanSdkClient client=SdkChannelManager.getInstance().getClient();
        client.connect();await("SDK 连接",client::isConnected,20000);
        assertEquals("SDK 初始化",0,client.initialize().getCode());
        assertEquals("SDK 状态",0,client.getStatus().getCode());
        NV21ToBitmap converter=new NV21ToBitmap(ins.getTargetContext());
        Bitmap bitmap=converter.nv21ToBitmap(frame.getNv(),frame.getWidth(),frame.getHeight());
        try {
            SdkImage image=SdkImage.rgb888(bitmap);
            assertEquals("真实帧检测",0,client.detect(image).getCode());
            assertEquals("真实帧识别",0,client.recognize(image).getCode());
            android.util.Log.i("CameraSourcesDeviceTest","[Biz] camera_sdk=passed source="+CameraController.getInstance().type());
        } finally { bitmap.recycle();converter.release(); }
    }
    /** 等待真实事件，有界超时并保留断言原因。 */
    private void await(String label,BooleanSupplier condition,long timeout) {
        long end=SystemClock.elapsedRealtime()+timeout;
        while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
        assertTrue(label,condition.getAsBoolean());
    }
}
