package cc.huoyan.retail.goods;

import android.os.Environment;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.learning.StudyFiles;
import cc.huoyan.retail.sdk.SdkGoodsImages;
import java.io.File;

/** 商品管理写入协调；SDK 是唯一商品来源，学习资料清理仍由宿主管理。 */
final class GoodsRepository {
    /** 不持久化商品的 SDK 数据入口。 */
    private final SdkGoodsSource source = SdkGoodsSource.getInstance();
    /** 等待 SDK 保存成功后才完成页面提交。 */
    void save(GoodsEntity goods, boolean editing) {
        source.save(goods, editing);
        FileExpiringMap.remove(goods.getPlu());
    }
    /** SDK 删除接口负责商品、样本及图片；成功后仅清理宿主学习文件及 UID。 */
    void delete(GoodsEntity goods) {
        source.delete(goods);
        SPManager.getInstance().removeStudyPlu(goods.getPlu());
        StudyFiles.deleteProduct(new File(Environment.getExternalStorageDirectory(), "retail/study"), goods.getPlu());
        FileExpiringMap.remove(goods.getPlu());
        SdkGoodsImages.invalidate();
    }
}
