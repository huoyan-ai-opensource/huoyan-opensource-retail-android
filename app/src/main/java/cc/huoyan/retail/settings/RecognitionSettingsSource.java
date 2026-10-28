package cc.huoyan.retail.settings;

import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.sdk.client.model.LicenseModel;
import cc.huoyan.sdk.client.model.LicenseModels;
import cc.huoyan.sdk.client.model.StatusSnapshot;

/** 当前模型名称取授权列表的 current 项，版本取状态回执，不映射四类业态。 */
final class RecognitionSettingsSource implements RecognitionSettingsPresenter.Source {
    /** 工作线程查询并确保完成时仍为同一客户端。 */
    @Override public RecognitionSettingsPresenter.Model currentModel() {
        SdkChannelManager manager = SdkChannelManager.getInstance();
        HuoyanSdkClient client = manager.getClient();
        LicenseModels models = client.queryDeviceModels();
        StatusSnapshot status = client.getStatus();
        return manager.getClient() == client ? currentModel(models, status) : null;
    }
    /** 只展示服务端确认的当前模型及实际版本。 */
    static RecognitionSettingsPresenter.Model currentModel(LicenseModels models, StatusSnapshot status) {
        if (models == null || models.getCode() != 0) return null;
        for (LicenseModel model : models.getModels()) {
            if (model != null && model.isCurrent()) return new RecognitionSettingsPresenter.Model(
                    model.getModelName(), status != null && status.getCode() == 0 ? status.getModelVersion() : null);
        }
        return null;
    }
}
