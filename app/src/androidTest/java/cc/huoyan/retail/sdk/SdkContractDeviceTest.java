package cc.huoyan.retail.sdk;
import cc.huoyan.sdk.client.samples.LearnedClazzPages;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.Capability;
import cc.huoyan.sdk.client.model.ItemClazzImagePage;
import cc.huoyan.sdk.client.model.ItemClazzList;
import cc.huoyan.sdk.client.model.RecognitionDataInfo;
import cc.huoyan.sdk.client.model.StatusSnapshot;

import static org.junit.Assert.*;

/** 真机 SDK 契约联调：需要同机安装新版 cc.huoyan，仅初始化和查询，不修改商品/识别数据。 */
@RunWith(AndroidJUnit4.class)
public class SdkContractDeviceTest {
    /** 验证真实 Binder 调用及 Parcelable 解码。 */
    @Test
    public void aidlContract() {
        verify(HuoyanSdkClient.create(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(), cc.huoyan.sdk.client.SdkConfig.aidl()), "aidl");
    }

    /** 验证客户端 WS 桥与同机 SDK 的实际通信。 */
    @Test
    public void wsContract() {
        verify(HuoyanSdkClient.create(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(), cc.huoyan.sdk.client.SdkConfig.websocket("ws://127.0.0.1:18086/ws")), "ws");
    }

    private void verify(HuoyanSdkClient bridge, String channel) {
        try {
            bridge.connect();
            long deadline = SystemClock.elapsedRealtime() + 15000;
            while (!bridge.isConnected() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100);
            }
            assertTrue(channel + " connection", bridge.isConnected());
            BaseResult init = bridge.initialize();
            assertNotNull(init);
            assertEquals(channel + " init: " + init.getReason(), 0, init.getCode());
            Capability capabilities = bridge.getCapabilities();
            assertNotNull(capabilities);
            assertEquals(0, capabilities.getCode());
            assertTrue(capabilities.getApiVersion() >= 2);
            StatusSnapshot status = SdkDeviceReadiness.await(bridge);
            assertNotNull(status);
            assertEquals(0, status.getCode());
            assertEquals(channel + " state", "READY", status.getSdkState());
            assertEquals(Boolean.TRUE, status.getModelReady());
            assertNotNull(status.getStateUpdatedAt());
            cc.huoyan.sdk.client.model.CertInfo cert = bridge.getCertInfo();
            assertNotNull(cert);
            assertEquals(cert.getReason(), 0, cert.getCode());
            assertNotNull(cert.getSdkVersionName());
            assertFalse(cert.getSdkVersionName().isEmpty());
            assertTrue(cert.getSdkVersionCode() > 0);
            assertNotNull(cert.getModelCode());
            assertNotNull(cert.getLicenseType());
            cc.huoyan.sdk.client.model.GoodsQueryResult goods = bridge.queryGoods(null, null, null, -1, 1, 10, "goods_id", "asc");
            assertNotNull(goods); assertEquals(goods.getReason(), 0, goods.getCode());
            cc.huoyan.sdk.client.model.GoodsCountResult count = bridge.getGoodsCount(null, null, null, -1);
            assertNotNull(count); assertEquals(count.getReason(), 0, count.getCode());
            assertEquals(goods.getTotal(), count.getCount());
            cc.huoyan.sdk.client.model.GoodsDownloadProgress progress = bridge.getDownloadGoodsProgress();
            assertNotNull(progress); assertEquals(progress.getReason(), 0, progress.getCode());

            ItemClazzList page = bridge.listClasses(1, 1);
            assertNotNull(page);
            assertEquals(page.getReason(), 0, page.getCode());
            assertEquals(1, page.getCurrent());
            assertEquals(1, page.getSize());
            assertEquals(page.getClazz().length, page.getUidCount().length);
            ItemClazzList all = LearnedClazzPages.loadAll(bridge);
            assertNotNull(all);
            assertEquals(all.getReason(), 0, all.getCode());
            Log.i("SdkContractDeviceTest", "[Biz] learned_count page_length=" + page.getClazz().length
                    + " total=" + page.getTotal() + " pages=" + page.getPages()
                    + " all_clazz=" + java.util.Arrays.toString(all.getClazz()));
            assertEquals("单页 total 应等于完整已学类别数", page.getTotal(), all.getClazz().length);
            assertNotNull(bridge.listClasses(2, 1));
            String clazz = page.getClazz().length == 0 ? "1" : page.getClazz()[0];
            ItemClazzImagePage images = bridge.listImages(clazz, 1, 1);
            assertNotNull(images);
            assertEquals(images.getReason(), 0, images.getCode());
            assertEquals(1, images.getCurrent());
            cc.huoyan.sdk.client.model.FailedImagesResult failed = bridge.getFailedImages();
            assertNotNull(failed);
            assertEquals(failed.getReason(), 0, failed.getCode());
            assertNotNull(failed.getItems());
            for (cc.huoyan.sdk.client.model.ImageFlowProgress imageProgress :
                    new cc.huoyan.sdk.client.model.ImageFlowProgress[]{
                            bridge.getImageDownloadProgress(), bridge.getImageLearnProgress()}) {
                assertNotNull(imageProgress);
                assertEquals(imageProgress.getReason(), 0, imageProgress.getCode());
                assertNotNull(imageProgress.getPhase());
                assertNotNull(imageProgress.getFailedImages());
            }
            RecognitionDataInfo info = bridge.getRecognitionDataInfo();
            assertNotNull(info);
            assertEquals(info.getReason(), 0, info.getCode());
            Log.i("SdkContractDeviceTest", "[Biz] device_contract=" + channel
                    + " init=" + init.getCode() + " sdk_state=" + status.getSdkState()
                    + " model_ready=" + status.getModelReady() + " clazz_total=" + page.getTotal()
                    + " image_total=" + images.getTotal() + " backup_time=" + info.getBackupTimeEpochMs()
                    + " sdk_version=" + cert.getSdkVersionName() + " failed_images=" + failed.getItems().size()
                    + " recent_error=" + status.getRecentError());
        } finally {
            bridge.close();
        }
    }
}
