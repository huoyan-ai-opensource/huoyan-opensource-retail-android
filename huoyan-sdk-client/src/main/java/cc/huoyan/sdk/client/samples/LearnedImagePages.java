package cc.huoyan.sdk.client.samples;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import java.util.ArrayList;
import java.util.List;
import cc.huoyan.sdk.client.model.ItemClazzImagePage;
import cc.huoyan.sdk.client.model.ItemClazzImage;

/** 按 SDK 分页读取样本，保留缺图样本的 UID 供遗忘操作使用。 */
public final class LearnedImagePages {
    /** 仅提供分页静态方法。 */
    private LearnedImagePages() {}

    /** 取最新的有效样本图片地址，跳过缺图记录，保留完整长整型 UID。 */
    public static List<String> latestUrls(List<ItemClazzImage> images, int limit) {
        List<ItemClazzImage> available = new ArrayList<>();
        for (ItemClazzImage image : images) {
            if (image != null && image.isImageAvailable() && isHttpUrl(image.getImageUrl())) {
                available.add(image);
            }
        }
        available.sort((left, right) -> Long.compare(right.getUid(), left.getUid()));
        List<String> urls = new ArrayList<>();
        for (ItemClazzImage image : available) {
            if (urls.size() >= limit) break;
            if (!urls.contains(image.getImageUrl())) urls.add(image.getImageUrl());
        }
        return urls;
    }

    /** 判断 SDK 返回的图片地址是否为有效 HTTP(S) URL。 */
    public static boolean isHttpUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url == null ? "" : url);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (java.net.URISyntaxException ignored) { return false; }
    }

    /** 只取最新一页；SDK 按 UID 升序分页，先查询页数再读取末页。须在后台调用。 */
    public static ItemClazzImagePage loadNewestPage(HuoyanSdkClient bridge, String clazz,
            int size, java.util.function.BooleanSupplier isCurrent) {
        ItemClazzImagePage first = loadPage(bridge, clazz, 1, size, isCurrent);
        if (first.getPages() <= 1) return first;
        ItemClazzImagePage last = loadPage(bridge, clazz, first.getPages(), size, isCurrent);
        // 查询期间其他客户端可能删除样本，最多重新定位一次末页。
        if (last.getItems().isEmpty() && last.getPages() > 0 && last.getPages() < first.getPages()) {
            return loadPage(bridge, clazz, last.getPages(), size, isCurrent);
        }
        return last;
    }

    /** 读取单页并校验回执；失败不推进游标，过期请求不回写页面。 */
    public static ItemClazzImagePage loadPage(HuoyanSdkClient bridge, String clazz, int page,
            int size, java.util.function.BooleanSupplier isCurrent) {
        checkCurrent(isCurrent);
        ItemClazzImagePage result = bridge.listImages(clazz, page, size);
        checkCurrent(isCurrent);
        if (result == null || result.getCode() != 0) {
            throw new IllegalStateException(result == null ? "SDK 未返回查询回执"
                    : result.getCode() + " " + result.getReason());
        }
        if (result.getCurrent() != page || (result.getItems().isEmpty() && page < result.getPages())) {
            throw new IllegalStateException("SDK 图片分页异常");
        }
        return result;
    }

    /** 请求过期后停止后续只读查询。 */
    private static void checkCurrent(java.util.function.BooleanSupplier isCurrent) {
        if (Thread.currentThread().isInterrupted() || !isCurrent.getAsBoolean()) {
            throw new java.util.concurrent.CancellationException();
        }
    }

    /** 读取类别全部图片；任一页失败时抛错，避免展示不完整图集。须在后台调用。 */
    public static List<ItemClazzImage> loadAll(HuoyanSdkClient bridge, String clazz) {
        return loadAll(bridge, clazz, () -> true);
    }

    /** 分页读取时检查请求是否仍有效，商品切换后不继续查询旧商品。 */
    public static List<ItemClazzImage> loadAll(HuoyanSdkClient bridge, String clazz,
            java.util.function.BooleanSupplier isCurrent) {
        List<ItemClazzImage> images = new ArrayList<>();
        for (int page = 1; ; page++) {
            ItemClazzImagePage result = loadPage(bridge, clazz, page, 100, isCurrent);
            images.addAll(result.getItems());
            if (page >= result.getPages()) {
                return images;
            }
            if (result.getItems().isEmpty() || result.getCurrent() != page) {
                throw new IllegalStateException("SDK 图片分页异常");
            }
        }
    }
    /** 从最新页倒查指定样本 UID；查询失败交由宿主保留已成功的学习结果。 */
    public static java.util.Map<Long, String> findUrls(HuoyanSdkClient client, String clazz,
            long[] uids, int size, java.util.function.BooleanSupplier active) {
        java.util.Set<Long> wanted = new java.util.HashSet<>();
        long earliest = Long.MAX_VALUE;
        for (long uid : uids) if (uid > 0) { wanted.add(uid); earliest = Math.min(earliest, uid); }
        java.util.Map<Long, String> urls = new java.util.HashMap<>();
        if (wanted.isEmpty()) return urls;
        ItemClazzImagePage page = loadNewestPage(client, clazz, size, active);
        while (true) {
            long firstUid = Long.MAX_VALUE;
            for (ItemClazzImage item : page.getItems()) {
                firstUid = Math.min(firstUid, item.getUid());
                if (wanted.contains(item.getUid()) && item.isImageAvailable() && isHttpUrl(item.getImageUrl()))
                    urls.put(item.getUid(), item.getImageUrl());
            }
            if (page.getCurrent() <= 1 || page.getItems().isEmpty() || firstUid <= earliest
                    || urls.size() == wanted.size()) return urls;
            page = loadPage(client, clazz, page.getCurrent() - 1, size, active);
        }
    }
}
