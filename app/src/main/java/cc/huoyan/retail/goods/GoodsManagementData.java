package cc.huoyan.retail.goods;

import cc.huoyan.retail.data.*;
import cc.huoyan.retail.sdk.*;
import cc.huoyan.sdk.client.model.ItemClazzList;
import java.util.*;

/** 商品管理的数据适配器；仓库不持有页面或 Context。 */
final class GoodsManagementData implements GoodsManagementPresenter.Data {
    /** 复用既有商品写入和学习资料删除规则。 */
    private final GoodsRepository repository;
    /** 注入业务仓库；所有方法由 Presenter 的后台队列调用。 */
    GoodsManagementData(GoodsRepository repository) { this.repository = repository; }
    /** 分页读取 SDK 全部未删除商品。 */
    @Override public List<GoodsEntity> goods() { return SdkGoodsSource.getInstance().getAllGoods(); }
    /** 将 SDK 类别计数映射到本地 PLU。 */
    @Override public Map<String, Long> learnedCounts(List<GoodsEntity> goods) {
        Map<String, Long> counts = new HashMap<>();
        ItemClazzList result = SdkChannelManager.getInstance().listClasses();
        if (result != null && result.getCode() == 0) {
            Map<String, Long> classes = new HashMap<>();
            String[] clazz = result.getClazz();
            long[] sizes = result.getUidCount();
            for (int i = 0; i < clazz.length; i++) classes.put(clazz[i], sizes[i]);
            for (GoodsEntity item : goods) counts.put(item.getPlu(),
                    classes.getOrDefault(SdkClassMapping.clazzFromPlu(item.getPlu()), 0L));
        }
        return counts;
    }
    /** 等待 SDK 保存回执，失败交由页面提示。 */
    @Override public void save(GoodsEntity goods, boolean editing) { repository.save(goods, editing); }
    /** 使用既有删除规则同时处理商品与样本。 */
    @Override public void delete(GoodsEntity goods) { repository.delete(goods); }
}
