package cc.huoyan.retail.device.camera;

public class CameraUsbEntity {
    private byte[] nv;
    private int width;
    private int height;

    public byte[] getNv() {
        return nv;
    }

    public void setNv(byte[] nv) {
        this.nv = nv;
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

    public CameraUsbEntity(byte[] nv, int width, int height) {
        this.nv = nv;
        this.width = width;
        this.height = height;
    }
}
