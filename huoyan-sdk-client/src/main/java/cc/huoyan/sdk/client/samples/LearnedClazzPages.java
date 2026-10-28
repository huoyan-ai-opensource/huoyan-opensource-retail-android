package cc.huoyan.sdk.client.samples;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import java.util.ArrayList;
import java.util.List;

import cc.huoyan.sdk.client.model.ItemClazzList;

/** 为现有本地分页页面汇总 SDK 的类别分页；中途失败不发布部分列表。 */
public final class LearnedClazzPages {
    /** 默认查询页容量；可由重载覆盖。 */
    private static final int PAGE_SIZE = 100;

    /** 仅提供分页静态方法。 */
    private LearnedClazzPages() { }

    /** 后台汇总全部类别，失败时不返回部分成功。 */
    public static ItemClazzList loadAll(HuoyanSdkClient bridge) {
        return loadAll(bridge, PAGE_SIZE);
    }

    /** 按宿主指定页容量读取类别。 */
    public static ItemClazzList loadAll(HuoyanSdkClient bridge, int pageSize) {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize 必须为正数");
        List<String> classes = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<String> thumbnails = new ArrayList<>();
        int page = 1;
        while (true) {
            ItemClazzList result = bridge.listClasses(page, pageSize);
            if (result == null || result.getCode() != 0) {
                return result;
            }
            if (result.getCurrent() != page) {
                return new ItemClazzList(-1, "SDK 返回的类别页码与请求不一致", null, null);
            }
            String[] pageClasses = result.getClazz();
            long[] pageCounts = result.getUidCount();
            String[] pageThumbnails = result.getThumbnails();
            for (int i = 0; i < pageClasses.length; i++) {
                classes.add(pageClasses[i]);
                counts.add(pageCounts[i]);
                thumbnails.add(pageThumbnails.length == 0 ? null : pageThumbnails[i]);
            }
            if (page >= result.getPages()) {
                break;
            }
            if (pageClasses.length == 0) {
                return new ItemClazzList(-1, "SDK 类别分页在末页前返回空页", null, null);
            }
            page++;
        }
        String[] clazz = new String[classes.size()];
        long[] uidCount = new long[counts.size()];
        for (int i = 0; i < clazz.length; i++) {
            clazz[i] = classes.get(i);
            uidCount[i] = counts.get(i);
        }
        return new ItemClazzList(0, null, clazz, uidCount,
                clazz.length, 1, clazz.length, clazz.length == 0 ? 0 : 1,
                thumbnails.toArray(new String[0]));
    }
}
