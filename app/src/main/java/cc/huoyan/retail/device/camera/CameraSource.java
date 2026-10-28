package cc.huoyan.retail.device.camera;

import java.util.List;

/** 相机后端边界；控制在主线程调用，帧在后端线程交付。 */
interface CameraSource {
    /** 接收独立帧或打开失败，不保留页面引用。 */
    interface Callback {
        /** 像素已转正，尺寸与预览坐标一致。 */
        void frame(byte[] nv21, int width, int height);
        /** 打开失败或连接中断。 */
        void error(String message);
    }
    /** 枚举设备。 */
    List<CameraDeviceInfo> devices();
    /** 枚举预览及分析支持的尺寸。 */
    List<CameraSize> sizes();
    /** 返回当前设备标识。 */
    String id();
    /** 绑定当前页面并替换旧预览。 */
    void start(AutoFitTextureView view, Callback callback);
    /** 释放硬件，旧回调失效。 */
    void stop();
    /** 是否已供帧。 */
    boolean open();
    /** 是否正在打开。 */
    boolean opening();
    /** 同步预览及分析镜像。 */
    void mirror(boolean enabled);
}
