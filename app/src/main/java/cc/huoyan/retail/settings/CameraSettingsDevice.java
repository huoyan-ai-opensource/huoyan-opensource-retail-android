package cc.huoyan.retail.settings;

import cc.huoyan.retail.device.camera.CameraController;

/** 相机配置适配，只操作已存在的相机管理器和偏好。 */
final class CameraSettingsDevice implements CameraSettingsPresenter.Device {
    /** 应用级统一相机入口，不持有 Fragment。 */
    private final CameraController camera;
    /** 注入已有设备管理器，避免创建第二条相机连接。 */
    CameraSettingsDevice(CameraController camera) { this.camera = camera; }
    /** 读取设备打开状态。 */
    @Override public boolean open() { return camera.isCameraOpen(); }
    /** 读取相机重启在途状态。 */
    @Override public boolean restarting() { return camera.isCameraRestarting(); }
    /** 保存相机配置与设备键后切换设备。 */
    @Override public void selectCamera(String id, String resolution) {
        camera.selectCamera(id);
    }
    /** 保存通用尺寸与当前设备尺寸后重新协商。 */
    @Override public void selectResolution(String resolution) {
        camera.selectResolution(resolution);
    }
}
