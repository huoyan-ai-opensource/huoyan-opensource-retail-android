package cc.huoyan.retail.learning;

import cc.huoyan.retail.home.HomeActivity;


import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.ItemClazzImage;
import cc.huoyan.sdk.client.model.ItemClazzImagePage;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 真实窗口与相机，图片分页用隔离回执模拟大量样本，不写入商品或 SDK 样本。 */
@RunWith(AndroidJUnit4.class)
public class HomeAndLearningPagingDeviceTest {
    private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();

    /** 已安装 SDK 的设备也能隔离验证统一安装提示，无需卸载 SDK 或启动下载。 */
    @Test public void apkInstallPromptUsesProjectDialog() throws Exception {
        HomeActivity home = (HomeActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        cc.huoyan.retail.common.install.ApkInstallController controller =
                new cc.huoyan.retail.common.install.ApkInstallController(home, null);
        java.lang.reflect.Method show = controller.getClass().getDeclaredMethod("showPrompt");
        show.setAccessible(true);
        try {
            ins.runOnMainSync(() -> {
                controller.onResume(false);
                try {
                    show.invoke(controller);
                    show.invoke(controller);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            ins.waitForIdleSync();
            DialogFragment prompt = (DialogFragment) home.getSupportFragmentManager()
                    .findFragmentByTag("apkInstallPrompt");
            assertTrue(prompt instanceof cc.huoyan.retail.common.ui.AlertDialog);
            ins.runOnMainSync(() -> {
                String content = ((android.widget.TextView) prompt.requireView()
                        .findViewById(R.id.textView)).getText().toString();
                assertFalse(content.contains("壳"));
                assertTrue(content.contains("SDK"));
                assertNotNull(prompt.getArguments());
            });
            SystemClock.sleep(400);
            capture("sdk-install-" + (home.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT ? "portrait" : "landscape"));
            ins.runOnMainSync(() -> prompt.requireView().findViewById(R.id.cancel).performClick());
            ins.waitForIdleSync();
            assertNull(home.getSupportFragmentManager().findFragmentByTag("apkInstallPrompt"));
            assertNull(home.getSupportFragmentManager().findFragmentByTag("apkInstallProgress"));
        } finally {
            ins.runOnMainSync(() -> { controller.onDestroy(); home.finish(); });
        }
    }

    /** 提示仅在还有内容时显示；滚到底后所有入口可达，收银入口保持固定。 */
    @Test public void homeScrollHintTracksRemainingFeatures() throws Exception {
        HomeActivity home = (HomeActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            await(() -> home.findViewById(R.id.enterCheckout).getHeight() > 0);
            if (home.getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) {
                capture("home-landscape");
                return;
            }
            ScrollView scroll = home.findViewById(R.id.homeFeaturesScroll);
            View hint = home.findViewById(R.id.homeMoreFeatures);
            await(() -> (hint.getVisibility() == View.VISIBLE) == scroll.canScrollVertically(1));
            int[] checkoutPosition = new int[2];
            ins.runOnMainSync(() -> {
                home.findViewById(R.id.enterCheckout).getLocationOnScreen(checkoutPosition);
                LinearLayout card = home.findViewById(R.id.homeLearning);
                assertEquals(LinearLayout.HORIZONTAL, card.getOrientation());
                assertTrue(card.getChildAt(0).getRight() <= card.getChildAt(1).getLeft());
                assertTrue(card.getHeight() <= Math.round(96 * home.getResources().getDisplayMetrics().density));
            });
            capture("home-portrait");
            ins.runOnMainSync(() -> scroll.fullScroll(View.FOCUS_DOWN));
            await(() -> !scroll.canScrollVertically(1) && hint.getVisibility() == View.INVISIBLE);
            ins.runOnMainSync(() -> {
                android.graphics.Rect visible = new android.graphics.Rect();
                View last = home.findViewById(R.id.homeMaintenance);
                assertTrue(last.getGlobalVisibleRect(visible));
                assertEquals(last.getHeight(), visible.height());
                int[] now = new int[2];
                home.findViewById(R.id.enterCheckout).getLocationOnScreen(now);
                assertArrayEquals(checkoutPosition, now);
            });
            capture("home-portrait-bottom");
            // 用更短的功能视口覆盖小屏，不改变设备显示配置。
            ins.runOnMainSync(() -> {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) scroll.getLayoutParams();
                params.weight = 0;
                params.height = 180;
                scroll.setLayoutParams(params);
                scroll.scrollTo(0, 0);
            });
            await(() -> scroll.canScrollVertically(1) && hint.getVisibility() == View.VISIBLE);
        } finally { ins.runOnMainSync(home::finish); }
    }

    /** 两万四千条历史只按需取页，失败可重试，切品不会接纳过期回执，相机持续产帧。 */
    @Test public void galleryLoadsOnDemandAndRejectsStalePages() throws Exception {
        SdkChannelManager manager = SdkChannelManager.getInstance();
        HuoyanSdkClient real = manager.getClient();
        Field sessionField = SdkChannelManager.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        Object session = sessionField.get(manager);
        Field clientField = session.getClass().getDeclaredField("client");
        clientField.setAccessible(true);
        long firstClazz = 99150928L, secondClazz = 99150929L;
        AtomicInteger requests = new AtomicInteger();
        AtomicBoolean failNext = new AtomicBoolean();
        AtomicBoolean holdNext = new AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        HuoyanSdkClient fake = (HuoyanSdkClient) Proxy.newProxyInstance(real.getClass().getClassLoader(),
                new Class[]{HuoyanSdkClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("listImages") && ((long) args[0] == firstClazz || (long) args[0] == secondClazz)) {
                        long clazz = (long) args[0];
                        int page = (int) args[1], size = (int) args[2];
                        assertEquals(24, size);
                        requests.incrementAndGet();
                        if (holdNext.getAndSet(false)) {
                            entered.countDown();
                            long deadline = SystemClock.elapsedRealtime() + 10000;
                            while (release.getCount() > 0 && SystemClock.elapsedRealtime() < deadline) {
                                try { release.await(100, TimeUnit.MILLISECONDS); }
                                catch (InterruptedException ignored) { /* 模拟不能中断的 Binder 调用。 */ }
                            }
                        }
                        if (failNext.getAndSet(false)) return null;
                        if (clazz == secondClazz) return new ItemClazzImagePage(0, null, 1, page, size, 1,
                                Collections.singletonList(new ItemClazzImage(70001, false, null, null)));
                        List<ItemClazzImage> images = new ArrayList<>();
                        for (long uid = (page - 1L) * size + 1; uid <= page * (long) size; uid++)
                            images.add(new ItemClazzImage(uid, false, null, null));
                        return new ItemClazzImagePage(0, null, 24000, page, size, 1000, images);
                    }
                    try { return method.invoke(real, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
        ProductLearningActivity page = null;
        try {
            synchronized (session) { clientField.set(session, fake); }
            page = (ProductLearningActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                    ProductLearningActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ProductLearningActivity activity = page;
            ins.runOnMainSync(() -> {
                DialogFragment guide = (DialogFragment) activity.getSupportFragmentManager().findFragmentByTag("learningGuide");
                if (guide != null) guide.dismiss();
                activity.selectLearningGoods(goods(firstClazz));
            });
            await(() -> activity.mStudyPicList.size() == 24);
            SystemClock.sleep(1200);
            assertEquals("静置不连续读取全库", 2, requests.get());
            ins.runOnMainSync(() -> assertEquals(24000, activity.mStudyPicList.get(0).getUid()));
            Field frame = StudyActivity.class.getDeclaredField("latestFrame");
            frame.setAccessible(true);
            long deadline = SystemClock.elapsedRealtime() + 15000;
            while (frame.get(activity) == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
            assertNotNull("分页时相机应持续产帧", frame.get(activity));
            Object previous = frame.get(activity);
            failNext.set(true);
            RecyclerView list = activity.findViewById(R.id.picRecycler);
            ins.runOnMainSync(() -> list.scrollBy(0, list.getHeight() * 3));
            await(() -> ((android.widget.TextView) activity.findViewById(R.id.learningMoreImages)).getText()
                    .toString().equals(activity.getString(R.string.learning_more_retry)));
            assertEquals(3, requests.get());
            assertEquals(24, activity.mStudyPicList.size());
            ins.runOnMainSync(() -> activity.findViewById(R.id.learningMoreImages).performClick());
            await(() -> activity.mStudyPicList.size() == 48);
            assertNotSame("读取图片时不能停住相机", previous, frame.get(activity));
            assertEquals(4, requests.get());
            capture(activity.getResources().getConfiguration().orientation == 1 ? "learning-paged-portrait" : "learning-paged-landscape");
            holdNext.set(true);
            ins.runOnMainSync(() -> activity.findViewById(R.id.learningMoreImages).performClick());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            ins.runOnMainSync(() -> activity.selectLearningGoods(goods(secondClazz)));
            release.countDown();
            await(() -> activity.mStudyPicList.size() == 1 && activity.mStudyPicList.get(0).getUid() == 70001);
            SystemClock.sleep(300);
            ins.runOnMainSync(() -> assertEquals(View.GONE, activity.findViewById(R.id.learningMoreImages).getVisibility()));
        } finally {
            release.countDown();
            ProductLearningActivity activity = page;
            if (activity != null) ins.runOnMainSync(activity::finish);
            synchronized (session) { clientField.set(session, real); }
        }
    }

    private GoodsEntity goods(long plu) {
        GoodsEntity goods = new GoodsEntity(); goods.setPlu(String.valueOf(plu)); goods.setName("分页验收商品");
        return goods;
    }

    private void await(BooleanSupplier condition) {
        AtomicBoolean ready = new AtomicBoolean(); long end = SystemClock.elapsedRealtime() + 15000;
        do {
            ins.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < end);
        fail("15 秒内未达到预期状态");
    }

    private void capture(String name) throws Exception {
        File dir = new File(ins.getTargetContext().getExternalFilesDir(null), "home-paging-qa");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap screenshot = ins.getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { screenshot.recycle(); }
    }
}
