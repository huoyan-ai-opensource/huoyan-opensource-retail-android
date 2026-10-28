package cc.huoyan.retail.diagnostics;

import cc.huoyan.retail.common.QRCodeUtil;

public class SdkLogEntity {
    public int ret;
    public String time;
    public long cost;
    public cc.huoyan.sdk.client.model.RecognizeResult sdk_result;
    public boolean is_print;
    public String path;

    public SdkLogEntity() {
        this.time = QRCodeUtil.getProductionDay("yyyy-MM-dd HH:mm:ss:SSS");
    }

}
