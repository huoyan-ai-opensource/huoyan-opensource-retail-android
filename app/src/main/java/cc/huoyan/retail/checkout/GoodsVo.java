package cc.huoyan.retail.checkout;

import cc.huoyan.retail.data.GoodsEntity;

import android.graphics.Bitmap;

import cc.huoyan.retail.checkout.RecognitionSelection;

import java.util.List;

public class GoodsVo {
    private Integer count;
    /** 商品金额快照，单位为分。 */
    private long amount;

    private GoodsEntity goods;

    private List<RecognitionSelection> detectItemEntity;

    private Bitmap recognizeBitmap;

    /** 虚拟商品标志：SDK 商品目录无匹配的识别项（名称=clazz_name，仅显示，不入车/不点选/不参与计价分发） */
    private boolean unmatched;

    public GoodsVo(Integer count, long amount, GoodsEntity goods, List<RecognitionSelection> detectItemEntity) {
        this.count = count;
        this.amount = amount;
        this.goods = goods;
        this.detectItemEntity = detectItemEntity;
    }

    public boolean isUnmatched() {
        return unmatched;
    }

    public void setUnmatched(boolean unmatched) {
        this.unmatched = unmatched;
    }

    public List<RecognitionSelection> getRecognitionSelection() {
        return detectItemEntity;
    }

    public void setRecognitionSelection(List<RecognitionSelection> detectItemEntity) {
        this.detectItemEntity = detectItemEntity;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public GoodsEntity getGoods() {
        return goods;
    }

    public void setGoods(GoodsEntity goods) {
        this.goods = goods;
    }

    public Bitmap getRecognizeBitmap() {
        return recognizeBitmap;
    }

    public void setRecognizeBitmap(Bitmap recognizeBitmap) {
        this.recognizeBitmap = recognizeBitmap;
    }
}
