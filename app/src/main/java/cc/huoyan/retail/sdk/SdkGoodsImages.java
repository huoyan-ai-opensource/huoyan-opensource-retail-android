package cc.huoyan.retail.sdk;
import cc.huoyan.retail.goods.SdkClassMapping;
import cc.huoyan.sdk.client.samples.LearnedImagePages;

import cc.huoyan.sdk.client.HuoyanSdkClient;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.MainHandler;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import cc.huoyan.sdk.client.model.ItemClazzList;

/** SDK 图片展示：UID 和地址可能复用，学习、遗忘后使用新版本请求图片。 */
public final class SdkGoodsImages {
    private static final ExecutorService QUERIES = Executors.newSingleThreadExecutor();
    /** JAR 管理地址与失效版本，页面仅保留图片加载。 */
    private static final cc.huoyan.sdk.client.samples.SampleImages IMAGES =
            new cc.huoyan.sdk.client.samples.SampleImages(30_000L, 3);
    /** 工具类不实例化。 */
    private SdkGoodsImages() {}
    /** 学习或遗忘后刷新 SDK 图片版本。 */
    public static void invalidate() { IMAGES.invalidate(); }
    /** 已有类别回执用于更新封面。 */
    public static void update(ItemClazzList result) {
        IMAGES.update(result, SdkChannelManager.getInstance().getClient());
    }
    /** 本地 PLU 映射到 SDK 分类后查询预览。 */
    private static List<String> previews(String plu, HuoyanSdkClient bridge) {
        return IMAGES.previews(SdkClassMapping.clazzFromPlu(plu), bridge);
    }

    /** 异步加载商品封面；缺少类别缩略图时取最新可用样本。须在主线程调用。 */
    public static void loadThumbnail(ImageView view, String plu) {
        Object request = new Object();
        view.setTag(R.id.sdk_image_request, request);
        clearForDifferentGoods(view, plu);
        QUERIES.execute(() -> {
            String url = null;
            long version;
            try {
                HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
                url = IMAGES.thumbnail(SdkClassMapping.clazzFromPlu(plu), bridge);
            } catch (RuntimeException e) {
                Log.w("SdkGoodsImages", "商品封面查询失败：" + plu, e);
            }
            version = IMAGES.version();
            String resolved = url;
            long signature = version;
            MainHandler.runOnUiThread(() -> {
                if (view.getTag(R.id.sdk_image_request) == request && canLoad(view)) {
                    load(view, resolved, signature);
                }
            });
        });
    }

    /** 加载卡片的三张有效样本图，空位显示占位图；视图复用后忽略过期回调。 */
    public static void loadPreviews(String plu, ImageView... views) {
        Object request = new Object();
        for (ImageView view : views) {
            view.setTag(R.id.sdk_image_request, request);
            clearForDifferentGoods(view, plu);
        }
        QUERIES.execute(() -> {
            List<String> urls;
            try {
                urls = previews(plu, SdkChannelManager.getInstance().getClient());
            } catch (RuntimeException e) {
                Log.w("SdkGoodsImages", "商品卡片图片查询失败：" + plu, e);
                urls = Collections.emptyList();
            }
            List<String> resolved = urls;
            MainHandler.runOnUiThread(() -> {
                for (int i = 0; i < views.length; i++) {
                    ImageView view = views[i];
                    if (view.getTag(R.id.sdk_image_request) == request && canLoad(view)) {
                        load(view, i < resolved.size() ? resolved.get(i) : null);
                    }
                }
            });
        });
    }

    /** 加载可被 SDK 覆盖的样本地址；缺图显示占位图，不提交空请求。 */
    public static void load(ImageView view, String url) {
        long version;
        version = IMAGES.version();
        load(view, url, version);
    }

    private static void load(ImageView view, String url, long version) {
        if (!LearnedImagePages.isHttpUrl(url)) {
            Glide.with(view).clear(view);
            view.setImageResource(R.drawable.baseline_retail_dining_24);
            return;
        }
        // 按显示尺寸解码并缓存；学习/遗忘的版本签名保证同 UID 被覆盖后不复用旧图。
        Glide.with(view).load(url)
                .diskCacheStrategy(DiskCacheStrategy.NONE).signature(new ObjectKey(version))
                .override(view.getLayoutParams().width > 0 ? view.getLayoutParams().width : 240,
                        view.getLayoutParams().height > 0 ? view.getLayoutParams().height : 240)
                .priority(com.bumptech.glide.Priority.LOW)
                .dontAnimate()
                .skipMemoryCache(false)
                .placeholder(R.drawable.baseline_retail_dining_24)
                .error(R.drawable.baseline_retail_dining_24).into(view);
    }

    private static void clearForDifferentGoods(ImageView view, String plu) {
        if (!java.util.Objects.equals(view.getTag(R.id.sdk_image_goods), plu)) {
            load(view, null);
            view.setTag(R.id.sdk_image_goods, plu);
        }
    }

    private static boolean canLoad(ImageView view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return !((Activity) context).isDestroyed();
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return true;
    }
}
