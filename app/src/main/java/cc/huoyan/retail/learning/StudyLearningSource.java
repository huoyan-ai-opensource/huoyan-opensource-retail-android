package cc.huoyan.retail.learning;
import cc.huoyan.retail.goods.SdkClassMapping;
import cc.huoyan.sdk.client.samples.LearnedImagePages;

import static cc.huoyan.retail.common.FileUtil.corpRecognizeBitmap;
import static cc.huoyan.retail.common.FileUtil.cropBitmap;
import static cc.huoyan.retail.common.FileUtil.saveBitmapToExternalStorage;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.R;
import cc.huoyan.retail.data.*;
import cc.huoyan.retail.device.camera.NV21ToBitmap;
import cc.huoyan.retail.diagnostics.Logger;
import cc.huoyan.retail.common.FileUtil;
import cc.huoyan.retail.common.IOThreadHelper;
import android.graphics.BitmapFactory;
import cc.huoyan.retail.goods.FileExpiringMap;
import cc.huoyan.retail.sdk.*;
import cc.huoyan.sdk.client.*;
import cc.huoyan.sdk.client.model.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** 学习 SDK、帧转换与样本文件适配，仅持有应用 Context。 */
final class StudyLearningSource implements StudyPresenter.Source {
    /** 保留平台依赖的日志来源。 */
    private static final String LOG_TAG = "StudyActivity";
    /** 页面内部图片编号；SDK 样本仍以 UID 标识。 */
    private static final AtomicInteger PIC_ID_SEQ = new AtomicInteger();
    /** 应用 Context，不延长学习页生命周期。 */
    private final Context context;
    /** 学习帧专用转换器，不与预览线程共享可变缓冲。 */
    private final NV21ToBitmap converter;
    /** 将宿主 Context 收敛为应用级资源。 */
    StudyLearningSource(Context context) {
        this.context = context.getApplicationContext();
        converter = new NV21ToBitmap(this.context);
    }
    /** 后台完成批量学习；图片查询失败仍返回已成功的 UID，并始终回收 Bitmap。 */
    @Override public StudyPresenter.Capture capture(byte[] captureBytes, int captureWidth, int captureHeight,
                                                   GoodsEntity captureGoods, BooleanSupplier active) throws Exception {
        Bitmap fullBitmap = null;
        Bitmap cropBitmap = null;
        List<StudyPicEntity> learnedPics = new ArrayList<>();
        try {
            if (captureGoods == null || captureBytes == null || captureWidth <= 0 || captureHeight <= 0) {
                throw new IllegalStateException(context.getString(R.string.study_frame_invalid));
            }
            String clazz = SdkClassMapping.clazzFromPlu(captureGoods.getPlu());
            if (!cc.huoyan.sdk.client.operation.SdkInputs.isClazz(clazz)) {
                throw new IllegalStateException(context.getString(R.string.study_invalid_plu, captureGoods.getPlu()));
            }
            HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
            if (!bridge.isConnected()) {
                throw new IllegalStateException(context.getString(R.string.sdk_error_not_connected));
            }
            fullBitmap = converter.nv21ToBitmap(captureBytes, captureWidth, captureHeight);
            cropBitmap = corpRecognizeBitmap(fullBitmap);
            // JAR 统一编码，客户端仅负责相机与裁剪坐标。
            SdkImage batchRequest = SdkImage.rgb888(cropBitmap);
            if (batchRequest == null) {
                throw new IllegalStateException(context.getString(R.string.study_image_failed));
            }
            ItemLearnBatch batchResult = bridge.batchLearn(batchRequest, clazz);
            if (batchResult == null) {
                throw new IllegalStateException(context.getString(R.string.sdk_no_response));
            }
            if (batchResult.getCode() != 0) {
                throw new IllegalStateException(batchResult.getCode() + " " + batchResult.getReason());
            }
            List<ItemLearnBox> learnedBoxes = cc.huoyan.sdk.client.operation.BatchLearning.learnedBoxesOf(batchResult);
            if (learnedBoxes.isEmpty()) {
                if (batchResult.getBoxes() == null || batchResult.getBoxes().isEmpty()) {
                    // SDK 契约：boxes 空 = 无检测框的正常空结果（业务侧提示对准重拍）
                    throw new IllegalStateException(context.getString(R.string.study_no_detection));
                }
                throw new IllegalStateException(context.getString(R.string.study_no_learnable_detection));
            }

            for (ItemLearnBox box : learnedBoxes) {
                saveLearnedThumbnail(cropBitmap, captureGoods.getPlu(), box);
            }
            SdkGoodsImages.invalidate();
            java.util.Map<Long, String> imageUrls = new java.util.HashMap<>();
            try {
                imageUrls.putAll(LearnedImagePages.findUrls(bridge, clazz,
                        learnedBoxes.stream().mapToLong(ItemLearnBox::getUid).toArray(), 24, active));
            } catch (RuntimeException e) {
                // 图片查询失败不能把已入库的学习报告为失败；保留 UID 及占位图。
                Log.w(LOG_TAG, "学习成功，图片查询失败", e);
            }
            // 每框保存 SDK UID 和图片地址。
            long captureTimestamp = System.currentTimeMillis();
            for (ItemLearnBox box : learnedBoxes) {
                int picId = PIC_ID_SEQ.incrementAndGet();
                String imageUrl = imageUrls.get(box.getUid());
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "[Biz] study_image_url=" + imageUrl
                            + " uid=" + box.getUid() + " plu=" + captureGoods.getPlu());
                }
                StudyPicEntity pic = buildLearnedPicEntity(captureGoods, captureTimestamp, picId, box.getUid(), imageUrl);
                if (pic != null) {
                    learnedPics.add(pic);

                }
            }
            Logger.logDebug(LOG_TAG, "[Biz] batch_learn=ok clazz=" + clazz + " plu=" + captureGoods.getPlu()
                    + " boxes=" + (batchResult.getBoxes() == null ? 0 : batchResult.getBoxes().size())
                    + " learned=" + learnedBoxes.size() + " pics=" + learnedPics.size()
                    + " detail=" + cc.huoyan.sdk.client.operation.BatchLearning.boxDetailOf(batchResult));
            return new StudyPresenter.Capture(learnedPics);
        } catch (Exception error) {
            Logger.logDebug(LOG_TAG, "[Biz] batch_learn=fail plu="
                    + (captureGoods == null ? "null" : captureGoods.getPlu()) + " reason=" + error.getMessage());
            throw error;
        } finally {
            // 全尺寸裁剪可能与原图同实例，避免重复回收。
            if (cropBitmap != null && cropBitmap != fullBitmap) {
                cropBitmap.recycle();
            }
            if (fullBitmap != null) {
                fullBitmap.recycle();
            }
        }
    }

    /** 从发送给 SDK 的识别区帧裁剪成功框，以 UID 保存；本地失败不影响已生效的学习。 */
    private void saveLearnedThumbnail(Bitmap source, String plu, ItemLearnBox box) {
        Bitmap thumbnail = null;
        try {
            int left = Math.max(0, box.getX());
            int top = Math.max(0, box.getY());
            int right = (int) Math.min(source.getWidth(), (long) box.getX() + box.getW());
            int bottom = (int) Math.min(source.getHeight(), (long) box.getY() + box.getH());
            thumbnail = cropBitmap(source, left, top, right - left, bottom - top, 0);
            String path = saveBitmapToExternalStorage(context, thumbnail,
                    "retail/study/" + plu, box.getUid() + ".jpg");
            if (TextUtils.isEmpty(path)) {
                Log.w(LOG_TAG, "学习成功，本地小图保存失败 uid=" + box.getUid());
            }
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "[Biz] study_thumbnail=" + (TextUtils.isEmpty(path) ? "fail" : "ok")
                        + " uid=" + box.getUid() + " path=" + path);
            }
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "学习成功，本地小图裁剪失败 uid=" + box.getUid(), e);
        } finally {
            // 全图框可能直接返回源图，由拍照流程统一回收源图。
            if (thumbnail != null && thumbnail != source) {
                thumbnail.recycle();
            }
        }
    }

    /** 保存 SDK 样本地址及 UID，缺图时仍保留可删除的学习记录。 */
    private StudyPicEntity buildLearnedPicEntity(GoodsEntity goods, long timestamp,
                                                int picId, long uid, String imageUrl) {
        StudyPicEntity pic = new StudyPicEntity();
        pic.setClazzName(goods.getPlu());
        pic.setCaptureTimestamp(timestamp);
        pic.setFilePath(imageUrl);
        pic.setPicId(picId);
        pic.setUid(uid);
        return pic;
    }


    /** 遗忘指定快照商品；成功后清理的始终是提交时 PLU。 */
    @Override public void forgetClass(GoodsEntity goods) {
        String clazz = SdkClassMapping.clazzFromPlu(goods.getPlu());
        try {
            if (!cc.huoyan.sdk.client.operation.SdkInputs.isClazz(clazz)) throw new IllegalStateException(context.getString(R.string.study_invalid_plu, goods.getPlu()));
            HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
            if (!bridge.isConnected()) throw new IllegalStateException(context.getString(R.string.sdk_error_not_connected));
            BaseResult result = bridge.forgetClass(clazz);
            if (result == null) throw new IllegalStateException(context.getString(R.string.sdk_no_response));
            if (result.getCode() != 0) throw new IllegalStateException(result.getCode() + " " + result.getReason());
            Logger.logDebug(LOG_TAG, "[Biz] item_forget=ok clazz=" + clazz + " plu=" + goods.getPlu());
            StudyFiles.deleteProduct(new File(Environment.getExternalStorageDirectory(), "retail/study"), goods.getPlu());
            SPManager.getInstance().removeStudyPlu(goods.getPlu());
        } catch (RuntimeException error) {
            Logger.logDebug(LOG_TAG, "[Biz] item_forget=fail plu=" + goods.getPlu() + " reason=" + error.getMessage());
            throw error;
        } finally { invalidate(goods); }
    }
    /** 按 UID 遗忘样本，成功后删除对应商品下的小图。 */
    @Override public void forgetSample(StudyPicEntity picture) {
        HuoyanSdkClient bridge = SdkChannelManager.getInstance().getClient();
        BaseResult result = bridge.isConnected() ? bridge.forgetSample(picture.getUid()) : null;
        boolean ok = result != null && result.getCode() == 0;
        String reason = result == null ? context.getString(R.string.sdk_error_not_connected) : result.getCode() + " " + result.getReason();
        Logger.logDebug(LOG_TAG, ok ? "[Biz] item_forget_uid=ok uid=" + picture.getUid()
                : "[Biz] item_forget_uid=fail uid=" + picture.getUid() + " reason=" + reason);
        if (!ok) throw new IllegalStateException(reason);
        deleteSampleFile(new File(Environment.getExternalStorageDirectory(),
                "retail/study/" + picture.getClazzName() + "/" + picture.getUid() + ".jpg").getAbsolutePath());
        SdkGoodsImages.invalidate();
    }
    /** 保存或整组遗忘后使图片与学习标记缓存过期。 */
    @Override public void invalidate(GoodsEntity goods) {
        SdkGoodsImages.invalidate(); FileExpiringMap.remove(goods.getPlu());
    }
    /** 删除已遗忘样本的本地缩略图。 */
    private void deleteSampleFile(String path) {
        if (path == null || path.isEmpty()) return;
        File file = new File(path);
        if (file.exists() && !file.delete()) Log.w(LOG_TAG, "deleteFileQuietly: 删除失败 " + path);
    }
    /** 串行队列完成全部写入后释放 RenderScript 资源。 */
    @Override public void close() { converter.release(); }
}
