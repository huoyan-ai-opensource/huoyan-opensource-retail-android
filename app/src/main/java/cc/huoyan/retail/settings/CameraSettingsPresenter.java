package cc.huoyan.retail.settings;

import java.util.Objects;
import java.util.concurrent.Executor;

/** 相机设置交互和首帧回显协调，Surface、检测框及 USB 监听归视图管理。 */
final class CameraSettingsPresenter {
    /** 页面只执行预览渲染和视图绑定。 */
    interface View {
        /** 根据真实首帧更新设备名、预览比例和检测框。 */
        void showFrame(String deviceId, int width, int height);
        /** 切换前同步分辨率标签和检测框配置。 */
        void prepareSelection(String resolution);
        /** 把当前 Surface 重新绑定给相机。 */
        void restartPreview();
        /** 保留相机重启的调试打点。 */
        void restartDecision(boolean skipped);
    }
    /** 设备状态与配置持久化能力。 */
    interface Device {
        /** 相机是否已经打开。 */
        boolean open();
        /** 是否已有重启操作在途。 */
        boolean restarting();
        /** 保存设备及默认分辨率后切换相机。 */
        void selectCamera(String id, String resolution);
        /** 保存分辨率后按当前设备重新协商。 */
        void selectResolution(String resolution);
    }
    /** 当前页面，视图销毁时断开。 */
    private View view;
    /** 不持有 Surface 的设备访问适配。 */
    private final Device device;
    /** 相机帧回调转投主线程。 */
    private final Executor ui;
    /** 当前配置或真实首帧给出的分辨率。 */
    private String resolution;
    /** 已展示设备标识，跨帧线程可见。 */
    private volatile String renderedId;
    /** 已展示宽度，跨帧线程可见。 */
    private volatile int renderedWidth;
    /** 已展示高度，跨帧线程可见。 */
    private volatile int renderedHeight;
    /** 上次重启的单调时间，0 表示尚未发起。 */
    private long lastRestart;
    /** 重启节流五秒，覆盖 USB 协商耗时。 */
    private static final long RESTART_INTERVAL = 5000L;
    /** 注入已保存分辨率及设备、主线程能力。 */
    CameraSettingsPresenter(View view, Device device, Executor ui, String resolution) {
        this.view = view; this.device = device; this.ui = ui; this.resolution = resolution;
    }
    /** 返回当前分辨率供选择框回显。 */
    String resolution() { return resolution; }
    /** 相机线程只传递元数据，避免把帧缓冲和 Bitmap 留在页面任务中。 */
    void frame(String id, int width, int height) {
        if (Objects.equals(id, renderedId) && width == renderedWidth && height == renderedHeight) return;
        ui.execute(() -> {
            if (view == null) return;
            renderedId = id; renderedWidth = width; renderedHeight = height;
            resolution = width + "*" + height; view.showFrame(id, width, height);
        });
    }
    /** 用户选择已确认存在的设备，沿用 1280×720 默认值。 */
    void selectCamera(String id) {
        if (view == null) return;
        resolution = "1280*720"; view.prepareSelection(resolution); device.selectCamera(id, resolution);
    }
    /** 用户选择设备支持的分辨率，先更新检测框再协商尺寸。 */
    void selectResolution(String resolution) {
        if (view == null) return;
        this.resolution = resolution; view.prepareSelection(resolution); device.selectResolution(resolution);
    }
    /** 页面重新可见时重开关闭的相机；在途和五秒节流避免重复协商。 */
    void resume(long now) {
        if (view == null || device.open() || (now - lastRestart < RESTART_INTERVAL)) return;
        if (device.restarting()) { view.restartDecision(true); return; }
        lastRestart = now; view.restartDecision(false); view.restartPreview();
    }
    /** 配置变化后允许相同尺寸的首帧重新加载识别区。 */
    void resetFrame() { renderedId = null; renderedWidth = 0; renderedHeight = 0; }
    /** 释放视图引用，相机监听移除仍由 Fragment 执行。 */
    void detach() { view = null; }
}
