package cc.huoyan.retail.common;

public class EventBusModel {
    // 初始化摄像头的回调
    public static final int EVENT_CAMERA_INIT = 1;
    // 初始化FTP获取商品回调
    public static final int EVENT_GOODS_TASK = 2;
    // 初始化ws链接回调
    public static final int EVENT_WS_CONNECT = 3;
    // 摄像头初始化执行结果
    public static final int EVENT_CAMERA_STATUS_SUCCESS = 11;
    public static final int EVENT_CAMERA_STATUS_ERROR = 12;
    public static final int EVENT_CAMERA_STATUS_SWITCH = 13;
    // 初始化FTP获取商品结果
    public static final int EVENT_GOODS_TASK_SUCCESS = 21;
    public static final int EVENT_GOODS_TASK_FALSE = 22;
    // 初始化ws链接结果
    public static final int EVENT_WS_CONNECT_OPENED = 31;
    public static final int EVENT_WS_CONNECT_CLOSE = 32;
    // RemoteWebSocketClient
    public static final int REMOTE_EVENT_WS_CONNECT = 10001;
    public static final int REMOTE_EVENT_WS_FINISH = 20001;


    private int code;
    private int status;
    private String msg;

    public EventBusModel(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public EventBusModel(int code, int status) {
        this.code = code;
        this.status = status;
    }

    public EventBusModel(int code, int status, String msg) {
        this.code = code;
        this.status = status;
        this.msg = msg;
    }

    public EventBusModel(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
