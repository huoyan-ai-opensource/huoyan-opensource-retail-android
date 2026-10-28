package cc.huoyan.retail.checkout;

import cc.huoyan.retail.data.GoodsEntity;

import cc.huoyan.retail.checkout.RecognitionSelection;

import java.util.List;

public class PointGoodsEntity {

    private GoodsEntity goods;

    private List<float[]> pointFloats;
    private List<RecognitionSelection> detectItemEntities;

    public GoodsEntity getGoods() {
        return goods;
    }

    public void setGoods(GoodsEntity goods) {
        this.goods = goods;
    }

    public List<float[]> getPointFloats() {
        return pointFloats;
    }

    public void setPointFloats(List<float[]> pointFloats) {
        this.pointFloats = pointFloats;
    }

    public List<RecognitionSelection> getDetectItemEntities() {
        return detectItemEntities;
    }

    public void setDetectItemEntities(List<RecognitionSelection> detectItemEntities) {
        this.detectItemEntities = detectItemEntities;
    }
}
