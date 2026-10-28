package cc.huoyan.retail.settings;

import cc.huoyan.retail.data.SPManager;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.BaseResult;
import cc.huoyan.sdk.client.model.CertInfo;
import cc.huoyan.sdk.client.model.LicenseModels;
import cc.huoyan.sdk.client.operation.SdkOperations;

/** 模型会话固定查询通道，禁止跨设备使用刚查询的授权列表。 */
final class ModelActivationSource implements ModelActivationPresenter.Data {
    /** 最近一次查询所用客户端，仅串行工作队列访问。 */
    private HuoyanSdkClient client;
    /** 正常激活按输入码查询；切换按设备查询并读取 SDK 保存的激活码，允许缺省。 */
    @Override public ModelActivationPresenter.Options query(String code, boolean switching) {
        client = SdkChannelManager.getInstance().getClient();
        LicenseModels result = switching ? client.queryDeviceModels() : client.queryLicenseModels(code);
        if (switching && result != null && result.getCode() == 0) {
            CertInfo cert = client.getCertInfo();
            code = cert != null && cert.getCode() == 0 ? cert.getActivationSerial() : null;
        }
        if (client != SdkChannelManager.getInstance().getClient())
            result = new LicenseModels(-1, "连接已变更，请重新选择模型", null);
        return new ModelActivationPresenter.Options(code, result);
    }
    /** 使用原查询通道；失效时拒绝提交，由用户重新打开列表。 */
    @Override public BaseResult activate(String code, String modelCode) {
        if (client == null || client != SdkChannelManager.getInstance().getClient())
            return new BaseResult(HuoyanSdkClient.NOT_EXECUTED, "连接已变更，请重新选择模型");
        return SdkOperations.activate(client, code, modelCode);
    }
    /** 保留规范化激活码，取消选模后输入弹窗复用。 */
    @Override public void remember(String code) { SPManager.getInstance().putString(SPManager.SP_ACTIVATE_CODE, code); }
}
