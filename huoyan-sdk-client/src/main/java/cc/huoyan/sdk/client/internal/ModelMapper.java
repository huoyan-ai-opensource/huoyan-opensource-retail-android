package cc.huoyan.sdk.client.internal;

import cc.huoyan.app.aidl.*;
import cc.huoyan.sdk.client.model.*;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

/** 显式转换公共数据对象与 SDK 线契约，保留空值与可选字段。 */
final class ModelMapper {
    private ModelMapper() { }

    /** 保留授权模型动态名称、代码和当前标记。 */
    static LicenseModels fromWire(LicenseModelsParcel value) {
        if (value == null) return null;
        return new LicenseModels(value.getCode(), value.getReason(), map(value.getModels(),
                row -> new LicenseModel(row.getModelCode(), row.getModelName(), row.getChip(), row.isCurrent())));
    }

    static FailedImagesResult fromWire(FailedImagesResultParcel value) {
        return value == null ? null : new FailedImagesResult(value.getCode(), value.getReason(),
                map(value.getItems(), ModelMapper::fromWire));
    }

    static <A, B> List<B> map(List<A> values, Function<A, B> convert) {
        if (values == null) return null;
        List<B> result = new ArrayList<>(values.size());
        for (A value : values) result.add(convert.apply(value));
        return result;
    }

    static ItemClazzList fromWire(ItemClazzListParcel value) {
        if (value == null) return null;
        return new ItemClazzList(value.getCode(),
                value.getReason(),
                value.getClazz(),
                value.getUidCount(),
                value.getTotal(),
                value.getCurrent(),
                value.getSize(),
                value.getPages(),
                value.getThumbnails().length == 0 ? null : value.getThumbnails());
    }

    static ItemClazzListParcel toWire(ItemClazzList value) {
        if (value == null) return null;
        return new ItemClazzListParcel(value.getCode(),
                value.getReason(),
                value.getClazz(),
                value.getUidCount(),
                value.getTotal(),
                value.getCurrent(),
                value.getSize(),
                value.getPages(),
                value.getThumbnails().length == 0 ? null : value.getThumbnails());
    }

    static StatusSnapshot fromWire(StatusSnapshotParcel value) {
        if (value == null) return null;
        return new StatusSnapshot(value.getCode(),
                value.getReason(),
                value.getDeviceId(),
                value.getCertificateValidUntil(),
                value.getTrustLevel(),
                value.getModelVersion(),
                value.getKeyLost(),
                value.getBackendConnected(),
                value.getSdkState(),
                value.getModelReady(),
                value.getBusy(),
                value.getBusyOperation(),
                value.getStateUpdatedAt(),
                value.getCertificateValid(),
                value.getRecentError());
    }

    static StatusSnapshotParcel toWire(StatusSnapshot value) {
        if (value == null) return null;
        return new StatusSnapshotParcel(value.getCode(),
                value.getReason(),
                value.getDeviceId(),
                value.getCertificateValidUntil(),
                value.getTrustLevel(),
                value.getModelVersion(),
                value.getKeyLost(),
                value.getBackendConnected(),
                value.getSdkState(),
                value.getModelReady(),
                value.getBusy(),
                value.getBusyOperation(),
                value.getStateUpdatedAt(),
                value.getCertificateValid(),
                value.getRecentError());
    }

    static ItemLearnBatch fromWire(ItemLearnBatchParcel value) {
        if (value == null) return null;
        return new ItemLearnBatch(value.getCode(),
                value.getReason(),
                map(value.getBoxes(), ModelMapper::fromWire));
    }

    static ItemLearnBatchParcel toWire(ItemLearnBatch value) {
        if (value == null) return null;
        return new ItemLearnBatchParcel(value.getCode(),
                value.getReason(),
                map(value.getBoxes(), ModelMapper::toWire));
    }

    static GoodsResult fromWire(GoodsResultParcel value) {
        if (value == null) return null;
        return new GoodsResult(value.getCode(),
                value.getReason(),
                fromWire(value.getRecord()));
    }

    static GoodsResultParcel toWire(GoodsResult value) {
        if (value == null) return null;
        return new GoodsResultParcel(value.getCode(),
                value.getReason(),
                toWire(value.getRecord()));
    }

    static BindDevice fromWire(BindDeviceParcel value) {
        if (value == null) return null;
        return new BindDevice(value.getAccountId(),
                value.getShopId(),
                value.getShopName(),
                value.getShopNo());
    }

    // 回执快照的反向映射，不用于公共绑定请求。
    static BindDeviceParcel toWire(BindDevice value) {
        if (value == null) return null;
        return new BindDeviceParcel(value.getAccountId(), value.getShopId(),
                value.getShopName(), value.getShopNo());
    }

    static FeedbackItem fromWire(FeedbackItemParcel value) {
        if (value == null) return null;
        return new FeedbackItem(value.getItemType(), map(value.getDetect(), ModelMapper::fromWire),
                fromWire(value.getResult()), value.getX(), value.getY(), value.getWidth(), value.getHeight());
    }
    static FeedbackItemParcel toWire(FeedbackItem value) {
        if (value == null) return null;
        return new FeedbackItemParcel(value.getItemType(), map(value.getDetect(), ModelMapper::toWire),
                toWire(value.getResult()), value.getX(), value.getY(), value.getWidth(), value.getHeight());
    }
    static FeedbackDetect fromWire(FeedbackDetectParcel value) {
        return new FeedbackDetect(value.getClazz(), value.getName(), value.getDistance());
    }
    static FeedbackDetectParcel toWire(FeedbackDetect value) {
        return new FeedbackDetectParcel(value.getClazz(), value.getName(), value.getDistance());
    }
    static FeedbackResult fromWire(FeedbackResultParcel value) {
        return value == null ? null : new FeedbackResult(value.getClazz(), value.getName(), value.getSource());
    }
    static FeedbackResultParcel toWire(FeedbackResult value) {
        return value == null ? null : new FeedbackResultParcel(value.getClazz(), value.getName(), value.getSource());
    }

    static LearnResult fromWire(LearnResultParcel value) {
        if (value == null) return null;
        return new LearnResult(value.getCode(),
                value.getReason(),
                value.getUid());
    }

    static LearnResultParcel toWire(LearnResult value) {
        if (value == null) return null;
        return new LearnResultParcel(value.getCode(),
                value.getReason(),
                value.getUid());
    }

    static GoodsSave fromWire(GoodsSaveParcel value) {
        if (value == null) return null;
        return new GoodsSave(value.getName(),
                value.getPlu(),
                value.getImage(),
                value.getIngredientsList(),
                value.getShelfLife(),
                value.getPinyin(),
                value.hasCategory() ? value.getCategory() : null,
                value.getCategoryName(),
                value.getUnit(),
                value.hasSaleMode() ? value.getSaleMode() : null,
                value.hasTareWeight() ? value.getTareWeight() : null,
                value.hasPrice() ? value.getPrice() : null,
                value.hasSort() ? value.getSort() : null,
                value.hasStatus() ? value.getStatus() : null);
    }

    static GoodsSaveParcel toWire(GoodsSave value) {
        if (value == null) return null;
        return new GoodsSaveParcel(value.getName(),
                value.getPlu(),
                value.getImage(),
                value.getIngredientsList(),
                value.getShelfLife(),
                value.getPinyin(),
                value.hasCategory() ? value.getCategory() : null,
                value.getCategoryName(),
                value.getUnit(),
                value.hasSaleMode() ? value.getSaleMode() : null,
                value.hasTareWeight() ? value.getTareWeight() : null,
                value.hasPrice() ? value.getPrice() : null,
                value.hasSort() ? value.getSort() : null,
                value.hasStatus() ? value.getStatus() : null);
    }

    static ItemClazzImage fromWire(ItemClazzImageParcel value) {
        if (value == null) return null;
        return new ItemClazzImage(value.getUid(),
                value.isImageAvailable(),
                value.getImageUrl(),
                value.getMimeType());
    }

    static ItemClazzImageParcel toWire(ItemClazzImage value) {
        if (value == null) return null;
        return new ItemClazzImageParcel(value.getUid(),
                value.isImageAvailable(),
                value.getImageUrl(),
                value.getMimeType());
    }

    static DetectBox fromWire(DetectBoxParcel value) {
        if (value == null) return null;
        return new DetectBox(value.getX(),
                value.getY(),
                value.getW(),
                value.getH(),
                value.getScore());
    }

    static DetectBoxParcel toWire(DetectBox value) {
        if (value == null) return null;
        return new DetectBoxParcel(value.getX(),
                value.getY(),
                value.getW(),
                value.getH(),
                value.getScore());
    }

    static Capability fromWire(CapabilityParcel value) {
        if (value == null) return null;
        return new Capability(value.getCode(),
                value.getReason(),
                value.getApiVersion(),
                value.isSharedMemoryImage(),
                value.isDetect(),
                value.isRecognize(),
                value.isItemLearn(),
                value.isItemForget(),
                value.isItemQuery(),
                value.getSdkVersionName(),
                value.getSdkVersionCode());
    }

    static CapabilityParcel toWire(Capability value) {
        if (value == null) return null;
        return new CapabilityParcel(value.getCode(),
                value.getReason(),
                value.getApiVersion(),
                value.isSharedMemoryImage(),
                value.isDetect(),
                value.isRecognize(),
                value.isItemLearn(),
                value.isItemForget(),
                value.isItemQuery(),
                value.getSdkVersionName(),
                value.getSdkVersionCode());
    }

    static ImageFlowProgress fromWire(ImageFlowProgressParcel value) {
        if (value == null) return null;
        return new ImageFlowProgress(value.getCode(),
                value.getReason(),
                value.isActive(),
                value.getPhase(),
                value.getCurrent(),
                value.getTotal(),
                value.getSuccessCount(),
                value.getFailCount(),
                value.getFailStats(),
                value.getFailCode(),
                value.getFailReason(),
                value.getPublicDir(),
                value.getSkippedCount(),
                value.getSkipReasonStats(),
                value.getDownloadSuccess(), value.getDownloadFail(), value.getDownloadFailStats(),
                map(value.getFailedImages(), ModelMapper::fromWire));
    }

    static ImageFlowProgressParcel toWire(ImageFlowProgress value) {
        if (value == null) return null;
        return new ImageFlowProgressParcel(value.getCode(),
                value.getReason(),
                value.isActive(),
                value.getPhase(),
                value.getCurrent(),
                value.getTotal(),
                value.getSuccessCount(),
                value.getFailCount(),
                value.getFailStats(),
                value.getFailCode(),
                value.getFailReason(),
                value.getPublicDir(),
                value.getSkippedCount(),
                value.getSkipReasonStats(),
                value.getDownloadSuccess(), value.getDownloadFail(), value.getDownloadFailStats(),
                map(value.getFailedImages(), ModelMapper::toWire));
    }

    static BindingResult fromWire(BindingResultParcel value) {
        if (value == null) return null;
        return new BindingResult(value.getCode(),
                value.getReason(),
                fromWire(value.getRequest()),
                value.getAccountId(),
                value.getAccountName(),
                value.getShopId(),
                value.getShopName(),
                value.getShopNo(), value.getShopAddress(), value.getShopPhone());
    }

    static BindingResultParcel toWire(BindingResult value) {
        if (value == null) return null;
        return new BindingResultParcel(value.getCode(),
                value.getReason(),
                toWire(value.getRequest()),
                value.getAccountId(),
                value.getAccountName(),
                value.getShopId(),
                value.getShopName(),
                value.getShopNo(), value.getShopAddress(), value.getShopPhone());
    }

    static RecognitionItem fromWire(RecognitionItemParcel value) {
        if (value == null) return null;
        return new RecognitionItem(value.getClazz(),
                value.getDistance(),
                value.getLeft(),
                value.getTop(),
                value.getRight(),
                value.getBottom(),
                value.getSquare(),
                value.getRank(),
                map(value.getCandidates(), ModelMapper::fromWire));
    }

    static RecognitionItemParcel toWire(RecognitionItem value) {
        if (value == null) return null;
        return new RecognitionItemParcel(value.getClazz(),
                value.getDistance(),
                value.getLeft(),
                value.getTop(),
                value.getRight(),
                value.getBottom(),
                value.getSquare(),
                value.getRank(),
                map(value.getCandidates(), ModelMapper::toWire));
    }

    static CertInfo fromWire(CertInfoParcel value) {
        if (value == null) return null;
        return new CertInfo(value.getCode(),
                value.getReason(),
                value.getModelCode(),
                value.getModelChannel(),
                value.getLicenseType(),
                value.getActivatedAt(),
                value.getIssuedAt(),
                value.getRenewedAt(),
                value.hasRenewedAt(),
                value.getNextRenewAt(),
                value.hasNextRenewAt(),
                value.getExpiresAt(),
                value.getSdkVersionName(),
                value.getSdkVersionCode(),
                value.getActivationSerial());
    }

    static CertInfoParcel toWire(CertInfo value) {
        if (value == null) return null;
        return new CertInfoParcel(value.getCode(),
                value.getReason(),
                value.getModelCode(),
                value.getModelChannel(),
                value.getLicenseType(),
                value.getActivatedAt(),
                value.getIssuedAt(),
                value.getRenewedAt(),
                value.hasRenewedAt(),
                value.getNextRenewAt(),
                value.hasNextRenewAt(),
                value.getExpiresAt(),
                value.getSdkVersionName(),
                value.getSdkVersionCode(),
                value.getActivationSerial());
    }

    static DownloadProgress fromWire(DownloadProgressParcel value) {
        if (value == null) return null;
        return new DownloadProgress(value.getCode(),
                value.getReason(),
                value.getDownloaded(),
                value.getTotal());
    }

    static DownloadProgressParcel toWire(DownloadProgress value) {
        if (value == null) return null;
        return new DownloadProgressParcel(value.getCode(),
                value.getReason(),
                value.getDownloaded(),
                value.getTotal());
    }

    static ItemClazzImagePage fromWire(ItemClazzImagePageParcel value) {
        if (value == null) return null;
        return new ItemClazzImagePage(value.getCode(),
                value.getReason(),
                value.getTotal(),
                value.getCurrent(),
                value.getSize(),
                value.getPages(),
                map(value.getItems(), ModelMapper::fromWire));
    }

    static ItemClazzImagePageParcel toWire(ItemClazzImagePage value) {
        if (value == null) return null;
        return new ItemClazzImagePageParcel(value.getCode(),
                value.getReason(),
                value.getTotal(),
                value.getCurrent(),
                value.getSize(),
                value.getPages(),
                map(value.getItems(), ModelMapper::toWire));
    }

    static Goods fromWire(GoodsParcel value) {
        if (value == null) return null;
        return new Goods(value.getGoodsId(),
                value.getShopId(),
                value.getName(),
                value.getPlu(),
                value.getBaseGoodsId(),
                value.getImage(),
                value.getCategory(),
                value.getCategoryName(),
                value.getUnit(),
                value.getSaleMode(),
                value.getPrice(),
                value.getTareWeight(),
                value.getIngredientsList(),
                value.getShelfLife(),
                value.getPinyin(),
                value.getStatus(),
                value.getSort(),
                value.getIsDeleted(),
                value.getUpdateTime(),
                value.getShopName(),
                value.getAccountId(),
                value.getAccountName(),
                value.getCreateTime());
    }

    static GoodsParcel toWire(Goods value) {
        if (value == null) return null;
        return new GoodsParcel(value.getGoodsId(),
                value.getShopId(),
                value.getName(),
                value.getPlu(),
                value.getBaseGoodsId(),
                value.getImage(),
                value.getCategory(),
                value.getCategoryName(),
                value.getUnit(),
                value.getSaleMode(),
                value.getPrice(),
                value.getTareWeight(),
                value.getIngredientsList(),
                value.getShelfLife(),
                value.getPinyin(),
                value.getStatus(),
                value.getSort(),
                value.getIsDeleted(),
                value.getUpdateTime(),
                value.getShopName(),
                value.getAccountId(),
                value.getAccountName(),
                value.getCreateTime());
    }

    static RecognizeResult fromWire(RecognizeResultParcel value) {
        if (value == null) return null;
        return new RecognizeResult(value.getCode(),
                value.getReason(),
                map(value.getItems(), ModelMapper::fromWire));
    }

    static RecognizeResultParcel toWire(RecognizeResult value) {
        if (value == null) return null;
        return new RecognizeResultParcel(value.getCode(),
                value.getReason(),
                map(value.getItems(), ModelMapper::toWire));
    }

    static RecognitionCandidate fromWire(RecognitionCandidateParcel value) {
        if (value == null) return null;
        return new RecognitionCandidate(value.getClazz(),
                value.getDistance(),
                value.getSquare(),
                value.getRank());
    }

    static RecognitionCandidateParcel toWire(RecognitionCandidate value) {
        if (value == null) return null;
        return new RecognitionCandidateParcel(value.getClazz(),
                value.getDistance(),
                value.getSquare(),
                value.getRank());
    }

    static FailedImage fromWire(FailedImageParcel value) {
        if (value == null) return null;
        return new FailedImage(value.getClazz(),
                value.getImageId(),
                value.getFailureReason(),
                value.getImageUrl());
    }

    static FailedImageParcel toWire(FailedImage value) {
        if (value == null) return null;
        return new FailedImageParcel(value.getClazz(),
                value.getImageId(),
                value.getFailureReason(),
                value.getImageUrl());
    }

    static Feedback fromWire(FeedbackParcel value) {
        if (value == null) return null;
        return new Feedback(value.getRequestId(),
                value.getPhotoFileUrl(),
                value.getFeedbackType(),
                map(value.getItems(), ModelMapper::fromWire));
    }

    static FeedbackParcel toWire(Feedback value) {
        if (value == null) return null;
        return new FeedbackParcel(value.getRequestId(),
                value.getPhotoFileUrl(),
                value.getFeedbackType(),
                map(value.getItems(), ModelMapper::toWire));
    }

    static ItemLearnBox fromWire(ItemLearnBoxParcel value) {
        if (value == null) return null;
        return new ItemLearnBox(value.getX(),
                value.getY(),
                value.getW(),
                value.getH(),
                value.getScore(),
                value.getUid(),
                value.getCode(),
                value.getReason());
    }

    static ItemLearnBoxParcel toWire(ItemLearnBox value) {
        if (value == null) return null;
        return new ItemLearnBoxParcel(value.getX(),
                value.getY(),
                value.getW(),
                value.getH(),
                value.getScore(),
                value.getUid(),
                value.getCode(),
                value.getReason());
    }

    static BaseResult fromWire(BaseResultParcel value) {
        if (value == null) return null;
        return new BaseResult(value.getCode(),
                value.getReason());
    }

    static BaseResultParcel toWire(BaseResult value) {
        if (value == null) return null;
        return new BaseResultParcel(value.getCode(),
                value.getReason());
    }

    static DetectResult fromWire(DetectResultParcel value) {
        if (value == null) return null;
        return new DetectResult(value.getCode(),
                value.getReason(),
                map(value.getBoxes(), ModelMapper::fromWire));
    }

    static DetectResultParcel toWire(DetectResult value) {
        if (value == null) return null;
        return new DetectResultParcel(value.getCode(),
                value.getReason(),
                map(value.getBoxes(), ModelMapper::toWire));
    }

    static RecognitionDataInfo fromWire(RecognitionDataInfoParcel value) {
        if (value == null) return null;
        return new RecognitionDataInfo(value.getCode(),
                value.getReason(),
                value.getBackupTimeEpochMs(),
                value.getBackupTimeText(),
                value.getSchemaVersion(),
                value.getFileFormatVersion());
    }

    static RecognitionDataInfoParcel toWire(RecognitionDataInfo value) {
        if (value == null) return null;
        return new RecognitionDataInfoParcel(value.getCode(),
                value.getReason(),
                value.getBackupTimeEpochMs(),
                value.getBackupTimeText(),
                value.getSchemaVersion(),
                value.getFileFormatVersion());
    }

    static PhotoUploadResult fromWire(PhotoUploadResultParcel value) {
        if (value == null) return null;
        return new PhotoUploadResult(value.getCode(),
                value.getReason(),
                value.getPhotoFileUrl());
    }

    static PhotoUploadResultParcel toWire(PhotoUploadResult value) {
        if (value == null) return null;
        return new PhotoUploadResultParcel(value.getCode(),
                value.getReason(),
                value.getPhotoFileUrl());
    }

    static CloudStatus fromWire(CloudStatusParcel value) {
        if (value == null) return null;
        return new CloudStatus(value.getCode(),
                value.getReason(),
                value.isEnabled());
    }

    static CloudStatusParcel toWire(CloudStatus value) {
        if (value == null) return null;
        return new CloudStatusParcel(value.getCode(),
                value.getReason(),
                value.isEnabled());
    }

    static GoodsListResult fromWire(GoodsListResultParcel value) {
        if (value == null) return null;
        return new GoodsListResult(value.getCode(),
                value.getReason(),
                value.getShopId(),
                value.getSyncMode(),
                value.getSyncTimestamp(),
                value.getServerTime(),
                value.getTotal(),
                value.getPages(),
                value.getCurrent(),
                value.getSize(),
                map(value.getRecords(), ModelMapper::fromWire));
    }

    static GoodsListResultParcel toWire(GoodsListResult value) {
        if (value == null) return null;
        return new GoodsListResultParcel(value.getCode(),
                value.getReason(),
                value.getShopId(),
                value.getSyncMode(),
                value.getSyncTimestamp(),
                value.getServerTime(),
                value.getTotal(),
                value.getPages(),
                value.getCurrent(),
                value.getSize(),
                map(value.getRecords(), ModelMapper::toWire));
    }

    static GoodsQueryResult fromWire(GoodsQueryResultParcel value) {
        if (value == null) return null;
        return new GoodsQueryResult(value.getCode(),
                value.getReason(),
                value.getTotal(),
                value.getPages(),
                value.getCurrent(),
                value.getSize(),
                map(value.getRecords(), ModelMapper::fromWire));
    }

    static GoodsQueryResultParcel toWire(GoodsQueryResult value) {
        if (value == null) return null;
        return new GoodsQueryResultParcel(value.getCode(),
                value.getReason(),
                value.getTotal(),
                value.getPages(),
                value.getCurrent(),
                value.getSize(),
                map(value.getRecords(), ModelMapper::toWire));
    }

    static GoodsDownloadProgress fromWire(GoodsDownloadProgressParcel value) {
        if (value == null) return null;
        return new GoodsDownloadProgress(value.getCode(),
                value.getReason(),
                value.isActive(),
                value.getPhase(),
                value.getCurrent(),
                value.getTotal(),
                value.getSuccessCount(),
                value.getFailCount(),
                value.getAddedCount(),
                value.getUpdatedCount(),
                value.getDeletedCount(),
                value.isHasChanges(),
                value.getSyncMode(),
                value.getSyncTimestamp(),
                value.getFailReason());
    }

    static GoodsDownloadProgressParcel toWire(GoodsDownloadProgress value) {
        if (value == null) return null;
        return new GoodsDownloadProgressParcel(value.getCode(),
                value.getReason(),
                value.isActive(),
                value.getPhase(),
                value.getCurrent(),
                value.getTotal(),
                value.getSuccessCount(),
                value.getFailCount(),
                value.getAddedCount(),
                value.getUpdatedCount(),
                value.getDeletedCount(),
                value.isHasChanges(),
                value.getSyncMode(),
                value.getSyncTimestamp(),
                value.getFailReason());
    }

    static GoodsBatchResult fromWire(GoodsBatchResultParcel value) {
        if (value == null) return null;
        return new GoodsBatchResult(value.getCode(),
                value.getReason(),
                map(value.getItems(), ModelMapper::fromWire));
    }

    static GoodsBatchResultParcel toWire(GoodsBatchResult value) {
        if (value == null) return null;
        return new GoodsBatchResultParcel(value.getCode(),
                value.getReason(),
                map(value.getItems(), ModelMapper::toWire));
    }

    static GoodsBatchItem fromWire(GoodsBatchItemParcel value) {
        if (value == null) return null;
        return new GoodsBatchItem(value.getGoodsId(),
                value.getCode(),
                value.getReason());
    }

    static GoodsBatchItemParcel toWire(GoodsBatchItem value) {
        if (value == null) return null;
        return new GoodsBatchItemParcel(value.getGoodsId(),
                value.getCode(),
                value.getReason());
    }

    static GoodsDownloadResult fromWire(GoodsDownloadResultParcel value) {
        if (value == null) return null;
        return new GoodsDownloadResult(value.getCode(),
                value.getReason(),
                value.getPhase(),
                value.getSyncMode(),
                value.getSyncTimestamp(),
                value.getTotal(),
                value.getAdded(),
                value.getUpdated(),
                value.getDeleted(),
                value.getSuccessCount(),
                value.getFailCount(),
                value.isHasChanges(),
                value.getFailCode(),
                value.getFailReason());
    }

    static GoodsDownloadResultParcel toWire(GoodsDownloadResult value) {
        if (value == null) return null;
        return new GoodsDownloadResultParcel(value.getCode(),
                value.getReason(),
                value.getPhase(),
                value.getSyncMode(),
                value.getSyncTimestamp(),
                value.getTotal(),
                value.getAdded(),
                value.getUpdated(),
                value.getDeleted(),
                value.getSuccessCount(),
                value.getFailCount(),
                value.isHasChanges(),
                value.getFailCode(),
                value.getFailReason());
    }

    static GoodsCountResult fromWire(GoodsCountResultParcel value) {
        if (value == null) return null;
        return new GoodsCountResult(value.getCode(),
                value.getReason(),
                value.getCount());
    }

    static GoodsCountResultParcel toWire(GoodsCountResult value) {
        if (value == null) return null;
        return new GoodsCountResultParcel(value.getCode(),
                value.getReason(),
                value.getCount());
    }
}
