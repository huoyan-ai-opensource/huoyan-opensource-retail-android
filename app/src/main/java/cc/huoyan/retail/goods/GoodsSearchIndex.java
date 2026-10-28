package cc.huoyan.retail.goods;

import cc.huoyan.retail.common.PinyinUtil;

import cc.huoyan.retail.data.GoodsEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 商品名称、英文、拼音首字母与 PLU 的内存检索索引。 */
public final class GoodsSearchIndex {
    private final List<Entry> entries = new ArrayList<>();

    /** 构建一次索引；多音字沿用项目拼音工具的首个读音。 */
    public GoodsSearchIndex(List<GoodsEntity> goods) {
        for (GoodsEntity item : goods) entries.add(new Entry(item));
    }

    /** 返回模糊匹配结果，PLU 精确命中排在最前，其他商品保留原顺序。 */
    public List<GoodsEntity> search(String query) {
        String needle = normalize(query);
        List<GoodsEntity> exact = new ArrayList<>(), matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (!needle.isEmpty() && normalize(entry.goods.getPlu()).equals(needle)) exact.add(entry.goods);
            else if (entry.searchable.contains(needle)) matches.add(entry.goods);
        }
        exact.addAll(matches);
        return exact;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Entry {
        final GoodsEntity goods;
        final String searchable;
        Entry(GoodsEntity goods) {
            this.goods = goods;
            String name = goods.getName() == null ? "" : goods.getName();
            searchable = normalize(goods.getPlu()) + " " + normalize(name) + " "
                    + normalize(PinyinUtil.getTextToPinyinByCharFirst(name));
        }
    }
}
