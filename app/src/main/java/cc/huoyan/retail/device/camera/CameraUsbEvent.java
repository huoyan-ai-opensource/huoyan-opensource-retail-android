package cc.huoyan.retail.device.camera;

public class CameraUsbEvent {
    private String code;
    private String data;

    public CameraUsbEvent(String code, String data) {
        this.code = code;
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

}
