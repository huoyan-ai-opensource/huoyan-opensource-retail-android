package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * 状态查询回执（B4 六字段 + 综合状态扩展）：code=0 时字段有效
 * （null 表示尚未取得），非 0 时 reason 携带错误说明、字段全为 null——与 WS 通道
 * getStatus 响应同语义（C4 两通道语义一致）。
 *
 * <p>sdkState 使用包含 STOPPED、CLEANUP_PENDING 的 18 项综合状态；modelReady、busy/
 * busyOperation、stateUpdatedAt、certificateValid 与其余字段合计 13 个状态字段，
 * 保持既有 Parcel 线序。STOPPED 清除活动事实，modelReady/busy 固定 false。</p>
 */
public final class StatusSnapshotParcel implements Parcelable {

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
    public StatusSnapshotParcel(int code, String reason, String deviceId, String certificateValidUntil,
                                String trustLevel, String modelVersion, Boolean keyLost, Boolean backendConnected) {
        this(code, reason, deviceId, certificateValidUntil, trustLevel, modelVersion,
                keyLost, backendConnected, null, null, null, null, null, null, null);
    }

    /** 构造不含最近错误的状态快照。 */
    public StatusSnapshotParcel(int code, String reason, String deviceId, String certificateValidUntil,
            String trustLevel, String modelVersion, Boolean keyLost, Boolean backendConnected,
            String sdkState, Boolean modelReady, Boolean busy, String busyOperation,
            Long stateUpdatedAt, Boolean certificateValid) {
        this(code, reason, deviceId, certificateValidUntil, trustLevel, modelVersion,
                keyLost, backendConnected, sdkState, modelReady, busy, busyOperation,
                stateUpdatedAt, certificateValid, null);
    }

    /** 构造状态快照。 */
    public StatusSnapshotParcel(int code, String reason, String deviceId, String certificateValidUntil,
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

    private StatusSnapshotParcel(Parcel parcel) {
        code = parcel.readInt();
        reason = parcel.readString();
        deviceId = parcel.readString();
        certificateValidUntil = parcel.readString();
        trustLevel = parcel.readString();
        modelVersion = parcel.readString();
        keyLost = (Boolean) parcel.readValue(StatusSnapshotParcel.class.getClassLoader());
        backendConnected = (Boolean) parcel.readValue(StatusSnapshotParcel.class.getClassLoader());
        sdkState = parcel.readString();
        modelReady = (Boolean) parcel.readValue(StatusSnapshotParcel.class.getClassLoader());
        busy = (Boolean) parcel.readValue(StatusSnapshotParcel.class.getClassLoader());
        busyOperation = parcel.readString();
        stateUpdatedAt = (Long) parcel.readValue(StatusSnapshotParcel.class.getClassLoader());
        certificateValid = (Boolean) parcel.readValue(StatusSnapshotParcel.class.getClassLoader());
        recentError = parcel.readString();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeString(deviceId);
        parcel.writeString(certificateValidUntil);
        parcel.writeString(trustLevel);
        parcel.writeString(modelVersion);
        parcel.writeValue(keyLost);
        parcel.writeValue(backendConnected);
        parcel.writeString(sdkState);
        parcel.writeValue(modelReady);
        parcel.writeValue(busy);
        parcel.writeString(busyOperation);
        parcel.writeValue(stateUpdatedAt);
        parcel.writeValue(certificateValid);
        parcel.writeString(recentError);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StatusSnapshotParcel)) {
            return false;
        }
        StatusSnapshotParcel that = (StatusSnapshotParcel) other;
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
        return "StatusSnapshotParcel(code=" + code + ", reason=" + reason + ", deviceId=" + deviceId
                + ", certificateValidUntil=" + certificateValidUntil + ", trustLevel=" + trustLevel
                + ", modelVersion=" + modelVersion + ", keyLost=" + keyLost
                + ", backendConnected=" + backendConnected + ", sdkState=" + sdkState
                + ", modelReady=" + modelReady + ", busy=" + busy
                + ", busyOperation=" + busyOperation + ", stateUpdatedAt=" + stateUpdatedAt
                + ", certificateValid=" + certificateValid + ", recentError=" + recentError + ")";
    }

    public static final Creator<StatusSnapshotParcel> CREATOR = new Creator<StatusSnapshotParcel>() {
        @Override
        public StatusSnapshotParcel createFromParcel(Parcel parcel) {
            return new StatusSnapshotParcel(parcel);
        }

        @Override
        public StatusSnapshotParcel[] newArray(int size) {
            return new StatusSnapshotParcel[size];
        }
    };
}
