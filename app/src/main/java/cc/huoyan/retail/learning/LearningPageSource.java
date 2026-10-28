package cc.huoyan.retail.learning;
import cc.huoyan.retail.goods.SdkClassMapping;
import cc.huoyan.retail.goods.SdkGoodsSource;
import cc.huoyan.sdk.client.samples.LearnedImagePages;

import cc.huoyan.retail.data.*;
import cc.huoyan.retail.sdk.*;
import cc.huoyan.sdk.client.model.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** 将 SDK 商品和历史样本转换为学习页数据。 */
final class LearningPageSource implements ProductLearningPresenter.Data {
    /** 每页样本数量，与页面既有分页大小一致。 */
    private static final int PAGE_SIZE = 24;
    /** 从SDK 商品接口查找学习目标。 */
    @Override public GoodsEntity findGoods(String plu) { return SdkGoodsSource.getInstance().getGoodsByPlu(plu); }
    /** 使用后台优先级读取样本，保留 SDK 最新页定位及协作取消逻辑。 */
    @Override public ProductLearningPresenter.Page load(String plu, int page, BooleanSupplier current) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        String clazz = SdkClassMapping.clazzFromPlu(plu);
        List<StudyPicEntity> pictures = new ArrayList<>();
        if (!cc.huoyan.sdk.client.operation.SdkInputs.isClazz(clazz)) return new ProductLearningPresenter.Page(pictures, 0, 0);
        cc.huoyan.sdk.client.HuoyanSdkClient client = SdkChannelManager.getInstance().getClient();
        ItemClazzImagePage result = page == 0 ? LearnedImagePages.loadNewestPage(client, clazz, PAGE_SIZE, current)
                : LearnedImagePages.loadPage(client, clazz, page, PAGE_SIZE, current);
        for (ItemClazzImage image : result.getItems()) {
            StudyPicEntity picture = new StudyPicEntity();
            picture.setClazzName(plu); picture.setUid(image.getUid());
            picture.setFilePath(image.isImageAvailable() ? image.getImageUrl() : null);
            pictures.add(picture);
        }
        return new ProductLearningPresenter.Page(pictures, result.getCurrent(), result.getTotal());
    }
}
