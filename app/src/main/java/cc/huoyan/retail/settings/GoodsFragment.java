package cc.huoyan.retail.settings;


import android.graphics.Color;
import androidx.lifecycle.ViewModelProvider;
import cc.huoyan.sdk.client.model.RecognitionDataInfo;
import cc.huoyan.sdk.client.model.ImageFlowProgress;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.LoadingDialog;
import cc.huoyan.retail.common.ui.AlertConfirmDialogListener;
import cc.huoyan.retail.common.ui.DialogUtil;
import cc.huoyan.retail.common.ui.BaseFragment;
import cc.huoyan.retail.data.SPManager;
import cc.huoyan.sdk.client.HuoyanSdkClient;
import cc.huoyan.retail.sdk.SdkChannelManager;
import cc.huoyan.retail.common.IOThreadHelper;
import cc.huoyan.retail.common.ui.ToastEx;

import cc.huoyan.sdk.client.model.BaseResult;

/** 识别库设置视图，库查询与备份恢复交给 Presenter，图片批量学习沿用 ViewModel。 */
public class GoodsFragment extends BaseFragment implements LibraryPresenter.View {
    /** 商品数量的后台查询状态；销毁视图时丢弃迟到结果。 */
    private final GoodsCountPresenter goodsCounter = new GoodsCountPresenter(IOThreadHelper.getInstance()::post,
            new android.os.Handler(android.os.Looper.getMainLooper())::post,
            cc.huoyan.retail.goods.SdkGoodsSource.getInstance()::count);
    /** 商品页签按钮。 */
    private TextView topRecord;
    /** 识别库页签按钮。 */
    private TextView topLibrary;
    /** SDK 商品统计容器。 */
    private LinearLayout recordLayout;
    /** 识别库管理容器。 */
    private LinearLayout libraryLayout;
    /** SDK 商品数量展示控件。 */
    private TextView goodsCount;
    /** SDK 学习分类数量展示控件。 */
    private TextView libraryLocalCountTv;
    /** 当前库操作的加载提示，视图销毁时关闭。 */
    private LoadingDialog libraryLoadingDialog;
    /** 备份入口，和恢复、图片学习互斥。 */
    private View backupButton;
    /** 恢复入口，需要用户在页面确认。 */
    private View restoreButton;
    /** 跨视图重建保留库操作状态，图片批量学习继续使用既有 ViewModel。 */
    private final LibraryPresenter library = new LibraryPresenter(IOThreadHelper.getInstance()::post,
            new android.os.Handler(android.os.Looper.getMainLooper())::post, LibraryOperationSource::run,
            LibraryOperationSource::info, LibraryOperationSource::count);
    /** 最后备份时间展示控件。 */
    private TextView backupTimeTv;
    /** 图片包学习入口，沿用现有 ViewModel。 */
    private View imageLearningButton;
    /** 图片批量学习状态持有者，跨视图重建保留进度。 */
    private ImageLearningViewModel imageLearning;


    /** 根据是否独立设置页选择布局。 */
    @Override
    protected int getLayoutResId() {
        if (getArguments() != null && getArguments().getBoolean("standalone")) return R.layout.page_goods;
        return R.layout.fragment_goods;
    }

    /** 绑定库操作、图片学习入口及各状态控件。 */
    @Override
    protected void initView(View view) {
        library.attach(this);
        goodsCount = view.findViewById(R.id.goodsCount);
        goodsCounter.attach(count -> goodsCount.setText(count < 0 ? "--" : String.valueOf(count)));
        topRecord = view.findViewById(R.id.topRecord);
        topLibrary = view.findViewById(R.id.topLibrary);
        recordLayout = view.findViewById(R.id.recordLayout);
        libraryLayout = view.findViewById(R.id.libraryLayout);
        libraryLocalCountTv = view.findViewById(R.id.libraryLocalCount);
        backupTimeTv = view.findViewById(R.id.libraryBackupTime);
        imageLearningButton = view.findViewById(R.id.learnImagePackage);
        imageLearning = new ViewModelProvider(this).get(ImageLearningViewModel.class);
        imageLearningButton.setOnClickListener(v -> {
            if (library.running() || imageLearning.isRunning()) return;
            if (!cc.huoyan.retail.data.SPManager.getInstance().hasBoundShop()) {
                showShopBindingRequired();
                return;
            }
            imageLearning.start(SdkChannelManager.getInstance().getClient());
        });
        backupButton = view.findViewById(R.id.backupRecognitionLibrary);
        restoreButton = view.findViewById(R.id.restoreRecognitionLibrary);
        if (topRecord != null) {
            topRecord.setOnClickListener(v -> changeView(0));
            topLibrary.setOnClickListener(v -> changeView(1));
        }
        for (int id : new int[]{R.id.openLearning, R.id.localGoodsRow, R.id.learnedGoodsRow}) {
            View action = view.findViewById(id);
            if (action != null) action.setOnClickListener(v -> startActivity(new android.content.Intent(
                    requireContext(), cc.huoyan.retail.goods.GoodsManagementActivity.class)));
        }
        backupButton.setOnClickListener(v -> runLibraryOperation(false));
        restoreButton.setOnClickListener(v -> DialogUtil.showAlertDialog(
                getChildFragmentManager(), getString(R.string.library_restore),
                getString(R.string.library_restore_confirm), getString(R.string.button_confirm),
                getString(R.string.button_cancel), new AlertConfirmDialogListener() {
                    /** 用户确认后提交识别库恢复请求。 */
                    @Override
                    public void onConfirm() {
                        runLibraryOperation(true);
                    }

                    /** 取消确认时不提交业务操作。 */
                    @Override
                    public void onCancel() {
                    }
                }));
        renderLibraryOperation();
    }

    /** 未绑定门店时先说明操作前提，确认后进入真实扫码绑定入口。 */
    private void showShopBindingRequired() {
        if (getChildFragmentManager().findFragmentByTag("learningShopRequired") != null) return;
        cc.huoyan.retail.common.ui.AlertDialog dialog = new cc.huoyan.retail.common.ui.AlertDialog(
                getString(R.string.shop_binding_required_title), getString(R.string.learning_bind_shop_first),
                getString(R.string.shop_binding_open), getString(R.string.button_cancel), shopBindingListener());
        dialog.show(getChildFragmentManager(), "learningShopRequired");
    }

    /** 绑定提示恢复后重新接上导航，取消不提交学习。 */
    private AlertConfirmDialogListener shopBindingListener() {
        return new AlertConfirmDialogListener() {
            /** 打开店铺信息页完成绑定。 */
            @Override public void onConfirm() { SettingsPageActivity.open(requireContext(), "shop"); }
            /** 用户取消时保留当前页。 */
            @Override public void onCancel() { }
        };
    }

    /** 观察小图包学习状态，视图销毁后自动解除观察。 */
    @Override
    public void onViewCreated(View view, android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        androidx.fragment.app.Fragment bindingDialog = getChildFragmentManager().findFragmentByTag("learningShopRequired");
        if (bindingDialog instanceof cc.huoyan.retail.common.ui.AlertDialog)
            ((cc.huoyan.retail.common.ui.AlertDialog) bindingDialog).setConfirmListener(shopBindingListener());
        imageLearning.state().observe(getViewLifecycleOwner(), state -> {
            renderLibraryOperation();
        });
    }

    /** 只渲染库元信息，查询和过期回执由 Presenter 管理。 */
    private void renderLibraryMetadata() {
        RecognitionDataInfo result = library.info();
        String time = getString(R.string.text_unavailable);
        if (result != null && result.getCode() == 0) {
            time = result.getBackupTimeEpochMs() == 0 ? getString(R.string.library_never_backed_up)
                    : result.getBackupTimeText();
            if (time == null || time.isEmpty()) {
                time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date(result.getBackupTimeEpochMs()));
            }
        }
        backupTimeTv.setText(time);
        libraryLocalCountTv.setText(library.count() < 0 ? getString(R.string.text_unavailable) : String.valueOf(library.count()));
    }

    /** 按 ViewModel 进度展示图片下载、学习和完成结果。 */
    private void renderImageLearning(ImageLearningViewModel.State state) {
        String text = getString(R.string.library_image_learning_running);
        if (state.running) {
            boolean learning = state.learn != null && state.learn.learningStarted();
            ImageFlowProgress progress = learning ? state.learn : state.download;
            text = getString(learning ? R.string.library_image_learning_stage
                    : R.string.library_image_downloading_stage,
                    progress == null ? 0L : progress.getCurrent(),
                    progress == null ? 0L : progress.getTotal());
            showLibraryLoadingDialog(text);
            return;
        }
        if (state.result != null) {
            ImageFlowProgress result = state.result;
            if (result.outcome() != ImageFlowProgress.Outcome.SUCCESS && result.outcome() != ImageFlowProgress.Outcome.PARTIAL_SUCCESS) {
                text = getString(R.string.library_image_learning_failed, imageFlowError(result));
            } else if (result.outcome() == ImageFlowProgress.Outcome.PARTIAL_SUCCESS) {
                text = getString(R.string.library_image_learning_partial);
            }
        }
        if (imageLearning.consumeResult(state)) {
            dismissLibraryLoadingDialog();
            boolean success = state.result.outcome() == ImageFlowProgress.Outcome.SUCCESS;
            if (success) ToastEx.showSuccessToast(requireContext(), getString(R.string.library_image_learning_finished));
            else ToastEx.createToast(requireContext(), text);
            library.refreshCount();
        }
    }

    /** 优先采用 SDK 原因，未知结果保持原提示。 */
    private String imageFlowError(ImageFlowProgress result) {
        if (result.outcome() == ImageFlowProgress.Outcome.UNKNOWN) {
            return getString(R.string.library_image_result_unknown);
        }
        if (result.getReason() != null && !result.getReason().isEmpty()) return result.getReason();
        if (result.getFailReason() != null && !result.getFailReason().isEmpty()) return result.getFailReason();
        return String.valueOf(result.getCode() != 0 ? result.getCode() : result.getFailCode());
    }

    /** 提交已确认的库操作，与图片学习共享忙碌约束。 */
    private void runLibraryOperation(boolean restore) { library.run(restore, imageLearning.isRunning()); }
    /** 后台回执到达时只更新仍存在的视图。 */
    @Override public void libraryChanged() { if (isViewCreated) renderLibraryOperation(); }
    /** 解绑视图，不取消已经提交的库操作。 */
    @Override public void onDestroyView() { library.detach(); goodsCounter.detach(); dismissLibraryLoadingDialog(); super.onDestroyView(); }

    /** 根据 Presenter 和 ViewModel 状态控制按钮及安全消费回执。 */
    private void renderLibraryOperation() {
        renderLibraryMetadata();
        boolean busy = library.running() || imageLearning.isRunning();
        backupButton.setEnabled(!busy);
        restoreButton.setEnabled(!busy);
        imageLearningButton.setEnabled(!busy);
        if (!isResumed() || getChildFragmentManager().isStateSaved()) return;
        ImageLearningViewModel.State learningState = imageLearning.state().getValue();
        if (learningState != null && (learningState.running || learningState.result != null)) {
            renderImageLearning(learningState);
            if (learningState.running) return;
        }
        if (library.running()) {
            String text = getString(library.restoring() ? R.string.library_restoring : R.string.library_backing_up);
            showLibraryLoadingDialog(text);
            return;
        }
        BaseResult result = library.result();
        if (result == null) return;
        library.consumeResult();
        String text = libraryResultText(result);
        if (libraryLoadingDialog != null) {
            libraryLoadingDialog.setText(text);
            dismissLibraryLoadingDialog();
        }
        if (result.getCode() == 0) {
            ToastEx.showSuccessToast(requireContext(), text);
            if (library.restoring()) library.refreshCount();
            library.refreshBackup();
        } else {
            ToastEx.createToast(requireContext(), text);
        }
    }

    /** 将 SDK 备份恢复回执转换为本地化文案。 */
    private String libraryResultText(BaseResult result) {
        if (result.getCode() == 0) {
            return getString(library.restoring() ? R.string.library_restore_success : R.string.library_backup_success);
        }
        if (result.getCode() == HuoyanSdkClient.RESULT_UNKNOWN) {
            return getString(R.string.sdk_result_unknown);
        }
        if (result.getCode() == HuoyanSdkClient.LOCAL_DISCONNECTED) {
            return getString(R.string.sdk_error_not_connected);
        }
        String reason = result.getReason();
        if (reason == null || reason.isEmpty()) reason = String.valueOf(result.getCode());
        return getString(library.restoring() ? R.string.library_restore_failed : R.string.library_backup_failed, reason);
    }

    /** 在当前 Fragment 状态允许时展示或更新加载提示。 */
    private void showLibraryLoadingDialog(String text) {
        if (libraryLoadingDialog == null) {
            libraryLoadingDialog = new LoadingDialog(text);
            libraryLoadingDialog.setCancelable(false);
            libraryLoadingDialog.showNow(getChildFragmentManager(), "libraryLoading");
        } else {
            libraryLoadingDialog.setText(text);
        }
    }

    /** 关闭当前加载提示并释放引用。 */
    private void dismissLibraryLoadingDialog() {
        if (libraryLoadingDialog != null) {
            libraryLoadingDialog.dismissAllowingStateLoss();
            libraryLoadingDialog = null;
        }
    }

    /** 离开页面时关闭操作弹窗，返回后按后台任务状态恢复。 */
    @Override
    public void onPause() {
        dismissLibraryLoadingDialog();
        super.onPause();
    }

    /** 切换SDK 商品和识别库容器的显示状态。 */
    private void changeView(int position) {
        topRecord.setTypeface(null, position == 0 ? Typeface.BOLD : Typeface.NORMAL);
        topLibrary.setTypeface(null, position == 1 ? Typeface.BOLD : Typeface.NORMAL);
        topRecord.setTextColor(Color.parseColor(position == 0 ? "#3F3EED" : "#000000"));
        topLibrary.setTextColor(Color.parseColor(position == 1 ? "#3F3EED" : "#000000"));
        recordLayout.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        libraryLayout.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
    }

    /** 回显SDK 商品数量并请求最新库元信息。 */
    @Override
    protected void loadData() {
        goodsCounter.refresh();
        library.refreshCount();
        library.refreshBackup();
        renderLibraryOperation();
    }

    /** 页面重新进入时刷新数量并恢复后台操作状态。 */
    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }
}
