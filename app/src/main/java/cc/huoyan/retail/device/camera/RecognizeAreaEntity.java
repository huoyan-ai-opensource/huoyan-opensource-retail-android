package cc.huoyan.retail.device.camera;

import android.graphics.PointF;

import java.util.List;

public class RecognizeAreaEntity {
    private int width;
    private int height;

    private List<PointF> points;


    public RecognizeAreaEntity() {
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public List<PointF> getPoints() {
        return points;
    }

    public void setPoints(List<PointF> points) {
        this.points = points;
    }

}
