package cc.huoyan.sdk.client;

/** 调用方提供日志出口；默认不输出，业务端可仅在调试构建中记录。 */
@FunctionalInterface
public interface SdkLog {
    /** 接收 SDK 通信日志，不包含图像字节。 */
    void log(String tag, String message);
}
