package cc.huoyan.retail.learning;

import cc.huoyan.retail.checkout.MainActivity;

import android.os.Bundle;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.goods.SdkGoodsSource;
import cc.huoyan.sdk.client.samples.LearnedImagePages;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.retail.sdk.SdkChannelManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** 使用独立测试商品验证原业务链路，不修改既有商品；需要摄像头里有可检测商品。 */
@RunWith(AndroidJUnit4.class)
public class ProductLearningDeviceTest {
    /** 商品查询及测试数据创建都使用已连接的 SDK。 */
    @org.junit.Before public void connectGoodsSdk() { cc.huoyan.retail.goods.GoodsFixtureCleanup.awaitSdk(); }

    private Instrumentation ins;
    private ProductLearningActivity activity;
    private final String plu = "99140914";

    /** 新页完整操作链路、SDK 回执、持久化以及布局比例的真机回归。 */
    @Test public void existingBusinessFlowAndNewLayout() throws Exception {
        ins = InstrumentationRegistry.getInstrumentation();
        assertNull("测试 PLU 必须尚未占用", SdkGoodsSource.getInstance().getGoodsByPlu(plu));
        MainActivity main = (MainActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
        long deadline = SystemClock.elapsedRealtime()+20000;
        while (!bridge.isConnected() && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100);
        assertTrue("SDK 通道必须就绪",bridge.isConnected());
        assertTrue("测试类别必须尚未占用",LearnedImagePages.loadAll(bridge,plu).isEmpty());
        activity = (ProductLearningActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                ProductLearningActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean created = false;
        try {
            ins.runOnMainSync(() -> {
                Fragment guide = dialog("learningGuide");
                if (guide != null) guide.getView().findViewById(R.id.confirm).performClick();
            });
            await(() -> activity.findViewById(R.id.imageView).getWidth()>0);
            ins.runOnMainSync(() -> {
                View preview = (View) activity.findViewById(R.id.imageView).getParent();
                assertEquals("预览保持 16:9",16d/9,preview.getWidth()/(double)preview.getHeight(),.01);
                View header = activity.findViewById(R.id.learningHeader);
                assertSame(header, activity.findViewById(R.id.quit).getParent());
                assertSame(header, activity.findViewById(R.id.learningSwitchGoods).getParent());
                assertEquals(((View) header.getParent()).getWidth(), header.getWidth());
                android.graphics.Rect previewBounds = new android.graphics.Rect();
                android.graphics.Rect imagesBounds = new android.graphics.Rect();
                activity.findViewById(R.id.learningCameraStage).getGlobalVisibleRect(previewBounds);
                activity.findViewById(R.id.learningImageStage).getGlobalVisibleRect(imagesBounds);
                if (activity.getResources().getConfiguration().orientation == 2)
                    assertTrue(previewBounds.right < imagesBounds.left);
                else assertTrue(previewBounds.bottom < imagesBounds.top);
                android.graphics.Rect card = new android.graphics.Rect(), actions = new android.graphics.Rect();
                activity.findViewById(R.id.learningImagesCard).getGlobalVisibleRect(card);
                activity.findViewById(R.id.learningActionButtons).getGlobalVisibleRect(actions);
                assertEquals("底部按钮左边缘对齐图片卡片", card.left, actions.left);
                assertEquals("底部按钮右边缘对齐图片卡片", card.right, actions.right);
            });
            GoodsEntity draft = new GoodsEntity();
            draft.setPlu(plu); draft.setName("学习页回归测试"); draft.setPrice(1234); draft.setUnit(5);
            SdkGoodsSource.getInstance().save(draft, false);
            GoodsEntity fixture = SdkGoodsSource.getInstance().getGoodsByPlu(plu);
            created = true;
            ins.runOnMainSync(() -> {
                Bundle result = new Bundle(); result.putString(ProductLearningActivity.EXTRA_GOODS_PLU, plu);
                activity.getSupportFragmentManager().setFragmentResult(
                        cc.huoyan.retail.learning.GoodsSearchDialog.RESULT, result);
            });
            await(() -> activity.mSelectedGoods != null && plu.equals(activity.mSelectedGoods.getPlu()));
            // 真机预览稳定后，连续拍照覆盖多框回执、新图置前和两行图片。
            SystemClock.sleep(2000);
            AtomicInteger fullPictureRefreshes = new AtomicInteger();
            RecyclerView.AdapterDataObserver captureObserver = new RecyclerView.AdapterDataObserver() {
                @Override public void onChanged() { fullPictureRefreshes.incrementAndGet(); }
            };
            ins.runOnMainSync(() -> activity.studyPicAdapter.registerAdapterDataObserver(captureObserver));
            long firstUid=0;
            for(int shot=0;shot<3;shot++) {
                int before=readSize();
                click(R.id.capture);
                await(() -> activity.mStudyPicList.size()>before && dialog("loading")==null);
                long uid=readFirstUid();
                assertTrue("后拍样本置于最前",uid>firstUid);
                firstUid=uid;
                SystemClock.sleep(500);
                if (shot==0) {
                    ins.runOnMainSync(() -> {
                        assertNotNull("拍照不能丢失学习目标",activity.mSelectedGoods);
                        assertEquals(plu,activity.mSelectedGoods.getPlu());
                        assertTrue(activity.mStudyPicList.stream().anyMatch(p -> p.getCaptureTimestamp()>0));
                        StudyPicEntity firstPicture = activity.mStudyPicList.get(0);
                        int size = activity.mStudyPicList.size();
                        activity.selectLearningGoods(fixture);
                        assertEquals("重复点击选中商品不清空图片", size, activity.mStudyPicList.size());
                        assertSame("重复点击保留本次回执", firstPicture, activity.mStudyPicList.get(0));
                    });
                }
            }
            ins.runOnMainSync(() -> activity.studyPicAdapter.unregisterAdapterDataObserver(captureObserver));
            assertEquals("连续拍照不能触发图片全量刷新", 0, fullPictureRefreshes.get());
            int learned=readSize();
            assertTrue(learned>=3);
            ins.runOnMainSync(() -> {
                List<Long> before = pictureUids();
                StudyPicEntity first = activity.mStudyPicList.get(0);
                StudyPicEntity second = activity.mStudyPicList.get(1);
                activity.showCapturedPictures(Arrays.asList(first, second, first));
                assertEquals("重复回执和现有图片按 UID 合并", before, pictureUids());
                assertEquals("适配器与业务列表一致", before,
                        activity.studyPicAdapter.getData().stream().map(StudyPicEntity::getUid).collect(Collectors.toList()));
            });
            assertEquals("拍照成功已在 SDK 中生效",learned,LearnedImagePages.loadAll(bridge,plu).size());
            ins.runOnMainSync(() -> {
                RecyclerView pics=activity.findViewById(R.id.picRecycler);
                GridLayoutManager grid=(GridLayoutManager)pics.getLayoutManager();
                View first=grid.findViewByPosition(0);
                assertNotNull(first);
                ImageView image = first.findViewById(R.id.img);
                assertEquals("小图保持正方形", image.getWidth(), image.getHeight());
                assertTrue("小图不超出分配的列宽", image.getWidth() <= first.getWidth());
                View last = grid.findViewByPosition(grid.getSpanCount() - 1);
                assertNotNull(last);
                assertEquals("宫格首列贴合可用区域", pics.getPaddingLeft(), first.getLeft());
                assertEquals("宫格末列铺满可用宽度", pics.getWidth() - pics.getPaddingRight(), last.getRight());
                int inset = Math.round(16 * activity.getResources().getDisplayMetrics().density);
                assertEquals("图片随列宽均分，保留固定内边距", first.getWidth() - inset, image.getWidth());
                assertTrue("图片容器至少容纳两行",pics.getHeight()>=first.getHeight()*2);
            });
            java.io.File evidence = new java.io.File(ins.getTargetContext().getExternalFilesDir(null), "new-ui-qa");
            evidence.mkdirs();
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(evidence, "learned-images.png"))) {
                ins.getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            }
            // 未保存的多批图片按回执框序显示，删除中间项不能触发 UID 重排。
            List<Long> beforeDelete = new ArrayList<>();
            ins.runOnMainSync(() -> beforeDelete.addAll(pictureUids()));
            int middle = learned / 2;
            long deleteUid = beforeDelete.remove(middle);
            AtomicInteger pictureMoves = new AtomicInteger();
            RecyclerView.AdapterDataObserver deletionObserver = new RecyclerView.AdapterDataObserver() {
                @Override public void onItemRangeMoved(int from, int to, int count) { pictureMoves.addAndGet(count); }
                @Override public void onChanged() { fullPictureRefreshes.incrementAndGet(); }
            };
            ins.runOnMainSync(() -> {
                activity.studyPicAdapter.registerAdapterDataObserver(deletionObserver);
                ((RecyclerView) activity.findViewById(R.id.picRecycler)).scrollToPosition(middle);
            });
            await(() -> ((RecyclerView) activity.findViewById(R.id.picRecycler)).findViewHolderForAdapterPosition(middle) != null);
            ins.runOnMainSync(() -> {
                ((RecyclerView) activity.findViewById(R.id.picRecycler))
                        .findViewHolderForAdapterPosition(middle).itemView.findViewById(R.id.del).performClick();
                // 模拟删除请求期间历史刷新替换了同 UID 的实体实例。
                StudyPicEntity old = activity.mStudyPicList.get(middle);
                StudyPicEntity refreshed = new StudyPicEntity();
                refreshed.setUid(old.getUid()); refreshed.setClazzName(old.getClazzName());
                refreshed.setCaptureTimestamp(old.getCaptureTimestamp()); refreshed.setPicId(old.getPicId());
                refreshed.setFilePath(old.getFilePath());
                activity.mStudyPicList.set(middle, refreshed);
                activity.studyPicAdapter.getData().set(middle, refreshed);
            });
            await(() -> activity.mStudyPicList.size()==learned-1);
            assertTrue(LearnedImagePages.loadAll(bridge,plu).stream().noneMatch(p -> p.getUid()==deleteUid));
            SystemClock.sleep(800);
            ins.runOnMainSync(() -> {
                assertEquals("删除中间项后剩余图片顺序不变", beforeDelete, pictureUids());
                activity.studyPicAdapter.unregisterAdapterDataObserver(deletionObserver);
            });
            assertEquals(0, pictureMoves.get());
            assertEquals(0, fullPictureRefreshes.get());

            AtomicBoolean saveDisplayedEmpty = new AtomicBoolean();
            RecyclerView.AdapterDataObserver saveObserver = new RecyclerView.AdapterDataObserver() {
                private void checkEmpty() { if (activity.studyPicAdapter.getData().isEmpty()) saveDisplayedEmpty.set(true); }
                @Override public void onChanged() { checkEmpty(); }
                @Override public void onItemRangeRemoved(int start, int count) { checkEmpty(); }
                @Override public void onItemRangeInserted(int start, int count) { checkEmpty(); }
                @Override public void onItemRangeChanged(int start, int count) { checkEmpty(); }
            };
            ins.runOnMainSync(() -> {
                activity.studyPicAdapter.registerAdapterDataObserver(saveObserver);
                activity.findViewById(R.id.saveStudy).performClick();
                assertEquals("保存请求发出后保留当前图片", beforeDelete, pictureUids());
                assertEquals(View.GONE, activity.findViewById(R.id.learningEmpty).getVisibility());
            });
            await(() -> activity.mStudyPicList.size()==learned-1
                    && activity.mStudyPicList.stream().allMatch(p -> p.getCaptureTimestamp()==0));
            ins.runOnMainSync(() -> {
                assertEquals("保存回写保持图片顺序", beforeDelete, pictureUids());
                activity.studyPicAdapter.unregisterAdapterDataObserver(saveObserver);
            });
            assertFalse("保存全程不能短暂显示空列表", saveDisplayedEmpty.get());

            // 再次保存不重绑未变化的小图，删除按钮必须使用当前绑定位置。
            SystemClock.sleep(900);
            StudyPicEntity[] beforeRefresh = new StudyPicEntity[1];
            ins.runOnMainSync(() -> {
                beforeRefresh[0] = activity.mStudyPicList.get(0);
                ((RecyclerView) activity.findViewById(R.id.picRecycler)).scrollToPosition(0);
                activity.findViewById(R.id.saveStudy).performClick();
            });
            await(() -> activity.mStudyPicList.get(0) != beforeRefresh[0]);
            await(() -> ((RecyclerView) activity.findViewById(R.id.picRecycler)).findViewHolderForAdapterPosition(0) != null);
            long secondDeleteUid = readFirstUid();
            ins.runOnMainSync(() -> ((RecyclerView) activity.findViewById(R.id.picRecycler))
                    .findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.del).performClick());
            await(() -> activity.mStudyPicList.size() == learned - 2);
            assertTrue(LearnedImagePages.loadAll(bridge, plu).stream()
                    .noneMatch(p -> p.getUid() == secondDeleteUid));
            click(R.id.clearStudy); confirm("confirm");
            await(() -> activity.mStudyPicList.isEmpty() && dialog("loading")==null);
            assertTrue(LearnedImagePages.loadAll(bridge,plu).isEmpty());
            click(R.id.quit);
            await(activity::isFinishing);
        } finally {
            if(created) {
                bridge.forgetClass(plu);
                cc.huoyan.retail.goods.GoodsFixtureCleanup.deleteByPlu(plu);
            }
            ins.getTargetContext().getSharedPreferences("learning_display_times",0).edit().remove(plu).apply();
            ins.runOnMainSync(() -> {activity.finish();main.finish();});
        }
    }
    /** 自动指导偏好确认后持久保存，手动入口始终可用，取消操作不改变偏好。 */
    @Test public void guidePreferenceControlsAutomaticDisplayOnly() {
        ins = InstrumentationRegistry.getInstrumentation();
        android.content.SharedPreferences preferences = ins.getTargetContext().getSharedPreferences("learning_guide", 0);
        boolean existed = preferences.contains("dont_show_again");
        boolean original = preferences.getBoolean("dont_show_again", false);
        preferences.edit().remove("dont_show_again").commit();
        try {
            openLearningPage();
            await(() -> dialog("learningGuide") != null);
            ins.runOnMainSync(() -> {
                View guide = dialog("learningGuide").getView();
                ((CheckBox) guide.findViewById(R.id.learningGuideDontShowAgain)).setChecked(true);
                guide.findViewById(R.id.confirm).performClick();
            });
            await(() -> dialog("learningGuide") == null);
            assertTrue(preferences.getBoolean("dont_show_again", false));
            reopenLearningPage();
            ins.runOnMainSync(() -> assertNull("勾选后重新进入不自动显示", dialog("learningGuide")));
            ins.runOnMainSync(() -> new cc.huoyan.retail.learning.LearningGuideDialog()
                    .showNow(activity.getSupportFragmentManager(), "learningGuide"));
            await(() -> dialog("learningGuide") != null);
            ins.runOnMainSync(() -> {
                View guide = dialog("learningGuide").getView();
                CheckBox remember = guide.findViewById(R.id.learningGuideDontShowAgain);
                assertTrue("手动打开仍回显已保存偏好", remember.isChecked());
                remember.setChecked(false);
                guide.findViewById(R.id.cancel).performClick();
            });
            await(() -> dialog("learningGuide") == null);
            assertTrue("取消不保存修改", preferences.getBoolean("dont_show_again", false));
            ins.runOnMainSync(() -> new cc.huoyan.retail.learning.LearningGuideDialog()
                    .showNow(activity.getSupportFragmentManager(), "learningGuide"));
            await(() -> dialog("learningGuide") != null);
            ins.runOnMainSync(() -> {
                View guide = dialog("learningGuide").getView();
                ((CheckBox) guide.findViewById(R.id.learningGuideDontShowAgain)).setChecked(false);
                guide.findViewById(R.id.confirm).performClick();
            });
            await(() -> dialog("learningGuide") == null);
            reopenLearningPage();
            await(() -> dialog("learningGuide") != null);
        } finally {
            if (activity != null) ins.runOnMainSync(() -> activity.finish());
            android.content.SharedPreferences.Editor editor = preferences.edit();
            if (existed) editor.putBoolean("dont_show_again", original);
            else editor.remove("dont_show_again");
            editor.commit();
        }
    }

    private void openLearningPage() {
        activity = (ProductLearningActivity) ins.startActivitySync(new Intent(ins.getTargetContext(),
                ProductLearningActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void reopenLearningPage() {
        ins.runOnMainSync(() -> activity.finish());
        ins.waitForIdleSync();
        openLearningPage();
    }

    private Fragment dialog(String tag) {
        Fragment f=activity.getSupportFragmentManager().findFragmentByTag(tag);
        return f!=null && f.isAdded() && f.getView()!=null ? f:null;
    }
    private void confirm(String tag) {
        await(() -> dialog(tag)!=null);
        ins.runOnMainSync(() -> dialog(tag).getView().findViewById(R.id.confirm).performClick());
    }
    private void click(int id) {ins.runOnMainSync(() -> activity.findViewById(id).performClick());}
    private List<Long> pictureUids() {
        return activity.mStudyPicList.stream().map(StudyPicEntity::getUid).collect(Collectors.toList());
    }
    private int readSize(){int[] n={0};ins.runOnMainSync(() -> n[0]=activity.mStudyPicList.size());return n[0];}
    private long readFirstUid(){long[] uid={0};ins.runOnMainSync(() -> uid[0]=activity.mStudyPicList.get(0).getUid());return uid[0];}
    private void await(BooleanSupplier predicate) {
        AtomicBoolean ok=new AtomicBoolean();long end=SystemClock.elapsedRealtime()+25000;
        do {ins.runOnMainSync(() -> ok.set(predicate.getAsBoolean()));if(ok.get())return;SystemClock.sleep(100);} while(SystemClock.elapsedRealtime()<end);
        fail("操作在 25 秒内未达到预期状态");
    }
}
