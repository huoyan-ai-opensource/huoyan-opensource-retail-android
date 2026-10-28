package cc.huoyan.retail.goods;

import cc.huoyan.retail.R;
import cc.huoyan.retail.common.LocalizedText;
import cc.huoyan.retail.common.PinyinUtil;
import cc.huoyan.retail.data.GoodsEntity;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.learning.StudyFiles;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.*;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** SDK 商品访问边界；所有调用在后台执行，仅返回内存快照，不缓存或持久化商品。 */
public final class SdkGoodsSource {
    /** SDK 分页容量。 */
    private static final int PAGE_SIZE = 100;
    /** 当前通道客户端，切换后旧查询结果失效。 */
    private final Supplier<HuoyanSdkClient> clients;
    /** 当前绑定代次，防止跨门店使用在途结果。 */
    private final LongSupplier generation;
    /** 由宿主提供当前语言错误文案，测试可注入纯 Java 实现。 */
    private final IntFunction<String> text;

    /** 注入 SDK、绑定代次及错误文案；不启动请求。 */
    SdkGoodsSource(Supplier<HuoyanSdkClient> clients, LongSupplier generation, IntFunction<String> text) {
        this.clients = clients; this.generation = generation; this.text = text;
    }
    /** 延迟创建无商品缓存的访问实例。 */
    private static final class Holder {
        /** 只持有服务提供器，不持有页面。 */
        private static final SdkGoodsSource INSTANCE = new SdkGoodsSource(
                () -> SdkChannelManager.getInstance().getClient(),
                () -> SPManager.getInstance().getBindGeneration(), id -> LocalizedText.get(id));
    }
    /** 返回共享访问入口；调用方必须使用后台线程。 */
    public static SdkGoodsSource getInstance() { return Holder.INSTANCE; }

    /** 获取全部未删除商品；任一页失败时整次查询失败，不交付半份目录。 */
    public List<GoodsEntity> getAllGoods() {
        HuoyanSdkClient client = connected();
        long scope = generation.getAsLong();
        return query(client, scope, null);
    }
    /** 精确按 PLU 查询；只有成功的空回执才代表商品不存在。 */
    public GoodsEntity getGoodsByPlu(String plu) {
        HuoyanSdkClient client = connected();
        return find(client, generation.getAsLong(), plu);
    }
    /** 按去重 PLU 查询识别结果，整个批次固定同一 SDK 和绑定代次。 */
    public List<GoodsEntity> getGoodsByPlu(Collection<String> plus) {
        List<GoodsEntity> rows = new ArrayList<>();
        if (plus.isEmpty()) return rows;
        HuoyanSdkClient client = connected();
        long scope = generation.getAsLong();
        for (String plu : new LinkedHashSet<>(plus)) {
            GoodsEntity goods = find(client, scope, plu);
            if (goods != null) rows.add(goods);
        }
        return rows;
    }
    /** 从 SDK 读取当前未删除商品总数，不保存到 SharedPreferences。 */
    public long count() {
        HuoyanSdkClient client = connected();
        long scope = generation.getAsLong();
        GoodsCountResult result = client.getGoodsCount(null, null, null, 0);
        checkScope(client, scope);
        require(result == null ? -1 : result.getCode(), result == null ? null : result.getReason());
        return result.getCount();
    }
    /** 新建或更新 SDK 商品；失败或结果未知直接向调用方报告，不重试写操作。 */
    public void save(GoodsEntity goods, boolean editing) {
        if (goods == null || !StudyFiles.isValidPlu(goods.getPlu()) || blank(goods.getName()) || goods.getPrice() < 0)
            throw new IllegalArgumentException(text.apply(R.string.toast_plu_invalid));
        HuoyanSdkClient client = connected();
        long scope = generation.getAsLong();
        GoodsEntity existing = find(client, scope, goods.getPlu());
        if (editing) requireIdentity(goods, existing);
        else if (existing != null) throw new IllegalArgumentException(text.apply(R.string.toast_plu_exists));
        GoodsSave changes = changes(goods);
        checkScope(client, scope);
        GoodsResult result = editing ? client.updateGoods(goods.getGoodsId(), changes) : client.createGoods(changes);
        require(result == null ? -1 : result.getCode(), result == null ? null : result.getReason());
        checkScope(client, scope);
        if (result.getRecord() == null || blank(result.getRecord().getGoodsId()))
            throw new IllegalStateException(text.apply(R.string.sdk_no_response));
    }
    /** 删除已校验身份的 SDK 商品；只接受明确成功，不自动重发。 */
    public void delete(GoodsEntity goods) {
        HuoyanSdkClient client = connected();
        long scope = generation.getAsLong();
        requireIdentity(goods, find(client, scope, goods.getPlu()));
        checkScope(client, scope);
        BaseResult result = client.deleteGoods(goods.getGoodsId());
        require(result == null ? -1 : result.getCode(), result == null ? null : result.getReason());
        checkScope(client, scope);
    }
    /** 比较 SDK ID 与归属，禁止把旧页面的商品写入新绑定。 */
    private void requireIdentity(GoodsEntity goods, GoodsEntity current) {
        if (current == null || blank(goods.getGoodsId()) || !goods.getGoodsId().equals(current.getGoodsId())
                || !Objects.equals(goods.getAccount_id(), current.getAccount_id())
                || !Objects.equals(goods.getShop_id(), current.getShop_id()))
            throw new IllegalStateException(text.apply(R.string.cloud_scope_changed));
    }
    /** 分页读取并核对 SDK 回执；精确 PLU 判断不依赖远端模糊匹配语义。 */
    private List<GoodsEntity> query(HuoyanSdkClient client, long scope, String plu) {
        List<GoodsEntity> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        long expectedTotal = -1;
        for (int page = 1; ; page++) {
            checkScope(client, scope);
            GoodsQueryResult result = client.queryGoods(null, null, plu, 0, page, PAGE_SIZE, null, null);
            checkScope(client, scope);
            require(result == null ? -1 : result.getCode(), result == null ? null : result.getReason());
            if (result.getCurrent() != page || result.getTotal() < 0 || result.getPages() < 0
                    || (expectedTotal >= 0 && expectedTotal != result.getTotal()))
                throw new IllegalStateException(text.apply(R.string.cloud_sync_incomplete));
            expectedTotal = result.getTotal();
            for (Goods record : result.getRecords()) {
                GoodsEntity item = fromSdk(record);
                if (!ids.add(item.getGoodsId())) throw new IllegalStateException(text.apply(R.string.cloud_sync_incomplete));
                if (plu == null || plu.equals(item.getPlu())) rows.add(item);
            }
            if (page >= result.getPages()) break;
            if (result.getRecords().isEmpty() || page == Integer.MAX_VALUE)
                throw new IllegalStateException(text.apply(R.string.cloud_sync_incomplete));
        }
        if (ids.size() != expectedTotal) throw new IllegalStateException(text.apply(R.string.cloud_sync_incomplete));
        return rows;
    }
    /** 精确匹配唯一商品，重复 PLU 不猜测选取哪条记录。 */
    private GoodsEntity find(HuoyanSdkClient client, long scope, String plu) {
        if (blank(plu)) return null;
        List<GoodsEntity> rows = query(client, scope, plu);
        if (rows.size() > 1) throw new IllegalStateException(text.apply(R.string.toast_plu_exists));
        return rows.isEmpty() ? null : rows.get(0);
    }
    /** 校验 SDK 商品可用于当前计价链路，保留 long 分金额与远端身份。 */
    static GoodsEntity fromSdk(Goods goods) {
        if (goods == null || blank(goods.getGoodsId()) || !goods.getGoodsId().matches("[0-9]+")
                || !StudyFiles.isValidPlu(goods.getPlu()) || blank(goods.getName()) || goods.getPrice() < 0
                || goods.getIsDeleted() != 0 || (goods.getSaleMode() != SaleModeMapper.SALE_MODE_COUNT
                && goods.getSaleMode() != SaleModeMapper.SALE_MODE_WEIGHT))
            throw new IllegalStateException("SDK 商品数据无效");
        GoodsEntity item = new GoodsEntity();
        item.setGoodsId(goods.getGoodsId()); item.setPlu(goods.getPlu()); item.setName(goods.getName());
        item.setPrice(goods.getPrice()); item.setUnit(SaleModeMapper.toLocalUnit(goods.getSaleMode()));
        item.setTareWeight(goods.getTareWeight());
        item.setImage(goods.getImage()); item.setPinyin(goods.getPinyin());
        item.setAccount_id(goods.getAccountId()); item.setShop_id(goods.getShopId());
        item.setCreate_time(goods.getCreateTime()); item.setUpdate_time(goods.getUpdateTime());
        return item;
    }
    /** 只发送表单实际编辑字段；图片、类别等未编辑的 SDK 元数据不被表单覆盖。 */
    static GoodsSave changes(GoodsEntity goods) {
        return new GoodsSave(goods.getName(), goods.getPlu(), null, null, null,
                PinyinUtil.getTextToPinyinByCharFirst(goods.getName()), null, null,
                SaleModeMapper.toUnitLabel(goods.getUnit()), SaleModeMapper.toSaleMode(goods.getUnit()),
                goods.getUnit() == 5 ? 0L : goods.getTareWeight(), goods.getPrice(), null, null);
    }
    /** 未连接时直接失败，不把断连当作空商品库。 */
    private HuoyanSdkClient connected() {
        HuoyanSdkClient client = clients.get();
        if (client == null || !client.isConnected()) throw new IllegalStateException(text.apply(R.string.sdk_error_not_connected));
        return client;
    }
    /** 通道或门店切换后丢弃在途回执。 */
    private void checkScope(HuoyanSdkClient client, long scope) {
        if (clients.get() != client || generation.getAsLong() != scope)
            throw new IllegalStateException(text.apply(R.string.cloud_scope_changed));
    }
    /** SDK 非成功回执和缺失回执均不能触发页面成功提示。 */
    private void require(int code, String reason) {
        if (code != 0) throw new IllegalStateException(blank(reason) ? text.apply(R.string.sdk_no_response) : reason);
    }
    /** 判断必需文本字段是否为空。 */
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
