package cc.huoyan.sdk.client.model;

import java.util.Objects;

/** SDK 状态快照。code=0 仅表示读取成功；READY/OFFLINE 确认 SDK 启动，本地能力由 SdkStatus 判断。 */
public final class StatusSnapshot {

    private final int code;
    private final String reason;
    private final String deviceId;
    private final String certificateValidUntil;
    private final String trustLevel;
    private final String modelVersion;
    private final Boolean keyLost;
    private final Boolean backendConnected;
    private final String sdkState;
    private final Boolean modelReady;
    private final Boolean busy;
    private final String busyOperation;
    private final Long stateUpdatedAt;
    private final Boolean certificateValid;
    private final String recentError;

    /** 构造状态快照。 */
    public StatusSnapshot(int code, String reason, String deviceId, String certificateValidUntil,
                                String trustLevel, String modelVersion, Boolean keyLost, Boolean backendConnected) {
        this(code, reason, deviceId, certificateValidUntil, trustLevel, modelVersion,
                keyLost, backendConnected, null, null, null, null, null, null, null);
    }

    /** 构造不含最近错误的状态快照。 */
    public StatusSnapshot(int code, String reason, String deviceId, String certificateValidUntil,
            String trustLevel, String modelVersion, Boolean keyLost, Boolean backendConnected,
            String sdkState, Boolean modelReady, Boolean busy, String busyOperation,
            Long stateUpdatedAt, Boolean certificateValid) {
        this(code, reason, deviceId, certificateValidUntil, trustLevel, modelVersion,
                keyLost, backendConnected, sdkState, modelReady, busy, busyOperation,
                stateUpdatedAt, certificateValid, null);
    }

    /** 构造状态快照。 */
    public StatusSnapshot(int code, String reason, String deviceId, String certificateValidUntil,
                                String trustLevel, String modelVersion, Boolean keyLost, Boolean backendConnected,
                                String sdkState, Boolean modelReady, Boolean busy, String busyOperation,
                                Long stateUpdatedAt, Boolean certificateValid, String recentError) {
        this.code = code;
        this.reason = reason;
        this.deviceId = deviceId;
        this.certificateValidUntil = certificateValidUntil;
        this.trustLevel = trustLevel;
        this.modelVersion = modelVersion;
        this.keyLost = keyLost;
        this.backendConnected = backendConnected;
        this.sdkState = sdkState;
        this.modelReady = modelReady;
        this.busy = busy;
        this.busyOperation = busyOperation;
        this.stateUpdatedAt = stateUpdatedAt;
        this.certificateValid = certificateValid;
        this.recentError = recentError;
    }

    /** 取得回执码，0 表示成功。 */
    public int getCode() {
        return code;
    }

    /** 错误说明；code=0 时为 null。 */
    public String getReason() {
        return reason;
    }

    /** 取得 SDK 设备标识。 */
    public String getDeviceId() {
        return deviceId;
    }

    /** 取得证书有效期文本。 */
    public String getCertificateValidUntil() {
        return certificateValidUntil;
    }

    /** 取得信任等级。 */
    public String getTrustLevel() {
        return trustLevel;
    }

    /** 取得模型版本。 */
    public String getModelVersion() {
        return modelVersion;
    }

    /** 取得密钥丢失状态。 */
    public Boolean getKeyLost() {
        return keyLost;
    }

    /** 取得后端连接状态。 */
    public Boolean getBackendConnected() {
        return backendConnected;
    }

    /** 综合状态 wire 名（18 枚举 {@code SdkState#name()}；null = 尚未确定）。 */
    public String getSdkState() {
        return sdkState;
    }

    /** 取得模型就绪状态。 */
    public Boolean getModelReady() {
        return modelReady;
    }

    /** 取得忙碌状态。 */
    public Boolean getBusy() {
        return busy;
    }

    /** 取得当前忙碌操作。 */
    public String getBusyOperation() {
        return busyOperation;
    }

    /** 取得状态更新时间。 */
    public Long getStateUpdatedAt() {
        return stateUpdatedAt;
    }

    /** 取得证书有效状态。 */
    public Boolean getCertificateValid() {
        return certificateValid;
    }

    /** 当前仍存在的最近错误说明（脱敏；null = 无错误）。 */
    public String getRecentError() {
        return recentError;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StatusSnapshot)) {
            return false;
        }
        StatusSnapshot that = (StatusSnapshot) other;
        return code == that.code
                && Objects.equals(reason, that.reason)
                && Objects.equals(deviceId, that.deviceId)
                && Objects.equals(certificateValidUntil, that.certificateValidUntil)
                && Objects.equals(trustLevel, that.trustLevel)
                && Objects.equals(modelVersion, that.modelVersion)
                && Objects.equals(keyLost, that.keyLost)
                && Objects.equals(backendConnected, that.backendConnected)
                && Objects.equals(sdkState, that.sdkState)
                && Objects.equals(modelReady, that.modelReady)
                && Objects.equals(busy, that.busy)
                && Objects.equals(busyOperation, that.busyOperation)
                && Objects.equals(stateUpdatedAt, that.stateUpdatedAt)
                && Objects.equals(certificateValid, that.certificateValid)
                && Objects.equals(recentError, that.recentError);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + Objects.hashCode(reason);
        result = 31 * result + Objects.hashCode(deviceId);
        result = 31 * result + Objects.hashCode(certificateValidUntil);
        result = 31 * result + Objects.hashCode(trustLevel);
        result = 31 * result + Objects.hashCode(modelVersion);
        result = 31 * result + Objects.hashCode(keyLost);
        result = 31 * result + Objects.hashCode(backendConnected);
        result = 31 * result + Objects.hashCode(sdkState);
        result = 31 * result + Objects.hashCode(modelReady);
        result = 31 * result + Objects.hashCode(busy);
        result = 31 * result + Objects.hashCode(busyOperation);
        result = 31 * result + Objects.hashCode(stateUpdatedAt);
        result = 31 * result + Objects.hashCode(certificateValid);
        return 31 * result + Objects.hashCode(recentError);
    }

    @Override
    public String toString() {
        return "StatusSnapshot(code=" + code + ", reason=" + reason + ", deviceId=" + deviceId
                + ", certificateValidUntil=" + certificateValidUntil + ", trustLevel=" + trustLevel
                + ", modelVersion=" + modelVersion + ", keyLost=" + keyLost
                + ", backendConnected=" + backendConnected + ", sdkState=" + sdkState
                + ", modelReady=" + modelReady + ", busy=" + busy
                + ", busyOperation=" + busyOperation + ", stateUpdatedAt=" + stateUpdatedAt
                + ", certificateValid=" + certificateValid + ", recentError=" + recentError + ")";
    }

}
