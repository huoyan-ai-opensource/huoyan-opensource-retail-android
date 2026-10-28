package cc.huoyan.retail.home;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.LocalizedText;
import cc.huoyan.retail.data.SPManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 未绑定设备的同步失败提示应优先说明业务前提，不依赖 SDK 连接。 */
@RunWith(AndroidJUnit4.class)
public class CloudBindingMessageDeviceTest {
    /** 商品和识别库同步均明确引导绑定，恢复原门店标识。 */
    @Test public void unboundSyncExplainsRequiredShopBinding() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences sp = context.getSharedPreferences("HuoYanApplication", Context.MODE_PRIVATE);
        String before = sp.getString(SPManager.SP_SHOP_ID, null);
        try {
            sp.edit().remove(SPManager.SP_SHOP_ID).commit();
            for (CloudServicePresenter.Action action : new CloudServicePresenter.Action[]{
                    CloudServicePresenter.Action.GOODS, CloudServicePresenter.Action.IMAGES}) {
                CloudServicePresenter.Result result = new CloudServiceSource().run(action, null);
                assertFalse(result.success);
                assertEquals(LocalizedText.get(R.string.cloud_sync_bind_shop_first), result.message);
            }
        } finally {
            if (before == null) sp.edit().remove(SPManager.SP_SHOP_ID).commit();
            else sp.edit().putString(SPManager.SP_SHOP_ID, before).commit();
        }
    }
}
