package cc.huoyan.retail.device.camera;

/** 与驱动无关的相机设备描述，可安全交给设置页面。 */
public final class CameraDeviceInfo {
    /** 后端内的设备标识。 */
    public final String id;
    /** 供用户选择的名称。 */
    public final String label;
    /** 创建不持有硬件句柄的描述。 */
    public CameraDeviceInfo(String id, String label) { this.id = id; this.label = label; }
}
