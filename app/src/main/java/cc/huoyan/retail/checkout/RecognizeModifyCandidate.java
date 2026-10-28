package cc.huoyan.retail.checkout;

import cc.huoyan.retail.data.GoodsEntity;

public class RecognizeModifyCandidate {
    private final GoodsEntity goods;
    private final boolean similarCandidate;
    private final boolean primaryCandidate;
    private final double distance;
    private int supportCount;
    private int totalCount;

    public RecognizeModifyCandidate(GoodsEntity goods, boolean similarCandidate, boolean primaryCandidate, double distance) {
        this.goods = goods;
        this.similarCandidate = similarCandidate;
        this.primaryCandidate = primaryCandidate;
        this.distance = distance;
    }

    public GoodsEntity getGoods() {
        return goods;
    }

    public boolean isSimilarCandidate() {
        return similarCandidate;
    }

    public boolean isPrimaryCandidate() {
        return primaryCandidate;
    }

    public double getDistance() {
        return distance;
    }

    public int getSupportCount() {
        return supportCount;
    }

    public void setSupportCount(int supportCount) {
        this.supportCount = supportCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
