package cc.huoyan.sdk.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 授权模型列表回执，保持服务端顺序且不允许调用方修改快照。 */
public final class LicenseModels {
    /** 查询结果码，零表示成功。 */
    private final int code;
    /** 失败原因。 */
    private final String reason;
    /** 授权范围内的模型快照。 */
    private final List<LicenseModel> models;

    /** 复制查询结果。 */
    public LicenseModels(int code, String reason, List<LicenseModel> models) {
        this.code = code;
        this.reason = reason;
        this.models = Collections.unmodifiableList(new ArrayList<>(models == null ? Collections.emptyList() : models));
    }
    /** 取得结果码。 */
    public int getCode() { return code; }
    /** 取得错误原因。 */
    public String getReason() { return reason; }
    /** 取得不可变模型列表。 */
    public List<LicenseModel> getModels() { return models; }
}
