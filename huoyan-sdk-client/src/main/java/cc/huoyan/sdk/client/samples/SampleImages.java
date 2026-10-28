package cc.huoyan.sdk.client.samples;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.ItemClazzList;
import java.util.*;

/** 按客户端身份隔离样本图片地址；版本使学习和遗忘后的旧查询失效。 */
public final class SampleImages {
    /** 地址有效时间，由宿主指定。 */
    private final long ttlMillis;
    /** 预览数量，由宿主指定。 */
    private final int previewLimit;
    /** 按分类保存的缩略图地址。 */
    private final Map<String, String> THUMBNAILS = new HashMap<>();
    /** 按分类保存的预览地址，不对外提供可变列表。 */
    private final Map<String, List<String>> PREVIEWS = new HashMap<>();
    /** 最近刷新时间，锁保护。 */
    private long refreshedAt;
    /** 失效版本，锁保护。 */
    private long cacheVersion;
    /** 当前缓存所属的客户端身份。 */
    private HuoyanSdkClient cachedBridge;

    /** 创建可配置缓存，所有查询由调用线程执行。 */
    public SampleImages(long ttlMillis, int previewLimit) {
        if (ttlMillis < 0 || previewLimit < 0) throw new IllegalArgumentException("缓存策略无效");
        this.ttlMillis = ttlMillis; this.previewLimit = previewLimit;
    }

    /** 学习、遗忘后刷新地址及所有图片请求的版本，防止复用旧 UID 的图片。 */
    public synchronized void invalidate() {
        cacheVersion++;
        refreshedAt = 0;
        THUMBNAILS.clear();
        PREVIEWS.clear();
    }

    /** 用成功的全量类别回执刷新缩略图及版本。 */
    public synchronized void update(ItemClazzList result, HuoyanSdkClient bridge) {
        if (result == null || result.getCode() != 0) return;
        invalidate();
        String[] classes = result.getClazz();
        String[] urls = result.getThumbnails();
        for (int i = 0; i < classes.length; i++) {
            THUMBNAILS.put(classes[i], i < urls.length ? urls[i] : null);
        }
        cachedBridge = bridge;
        refreshedAt = System.currentTimeMillis();
    }

    /** 在调用线程刷新过期地址，查询期间失效的结果不进入缓存。 */
    public void refreshAddresses(HuoyanSdkClient bridge) {
        long version;
        synchronized (this) {
            if (cachedBridge == bridge && System.currentTimeMillis() - refreshedAt <= ttlMillis) return;
            if (cachedBridge != bridge) { invalidate(); cachedBridge = bridge; }
            version = cacheVersion;
        }
        ItemClazzList result = bridge.isConnected() ? LearnedClazzPages.loadAll(bridge) : null;
        synchronized (this) {
            if (version == cacheVersion) update(result, bridge);
        }
    }

    /** 查询指定分类的最新有效样本图片。 */
    public List<String> previews(String clazz, HuoyanSdkClient bridge) {
        refreshAddresses(bridge);
        long version;
        synchronized (this) {
            List<String> cached = PREVIEWS.get(clazz);
            if (cachedBridge == bridge && cached != null) return cached;
            version = cacheVersion;
        }
        if (!cc.huoyan.sdk.client.operation.SdkInputs.isClazz(clazz)) return Collections.emptyList();
        List<String> urls = Collections.unmodifiableList(LearnedImagePages.latestUrls(
                LearnedImagePages.loadAll(bridge, clazz), previewLimit));
        synchronized (this) {
            if (version == cacheVersion && cachedBridge == bridge) PREVIEWS.put(clazz, urls);
        }
        return urls;
    }

    /** 当前失效版本，可用于宿主图片缓存签名。 */
    public synchronized long version() { return cacheVersion; }
    /** 取得类别封面；缺少缩略图时查询最新可用样本。 */
    public String thumbnail(String clazz, HuoyanSdkClient client) {
        refreshAddresses(client);
        synchronized (this) {
            String url = cachedBridge == client ? THUMBNAILS.get(clazz) : null;
            if (LearnedImagePages.isHttpUrl(url)) return url;
        }
        List<String> images = previews(clazz, client);
        return images.isEmpty() ? null : images.get(0);
    }
}
