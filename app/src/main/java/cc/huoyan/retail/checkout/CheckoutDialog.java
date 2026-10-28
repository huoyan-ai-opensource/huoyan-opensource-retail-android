package cc.huoyan.retail.checkout;

import androidx.fragment.app.DialogFragment;
import cc.huoyan.retail.common.ui.LandscapeDialogWindow;
import cc.huoyan.retail.common.ui.LoadingDialog;
import cc.huoyan.retail.R;

/** 识别页自定义弹窗的窗口与等待提示；等待由子 FragmentManager 管理。 */
public abstract class CheckoutDialog extends DialogFragment {
    /** 保留尚未执行的加载事务，避免短时间内重复添加遮罩。 */
    private LoadingDialog pendingLoading;
    /** 窗口由横竖屏 XML 绘制遮罩。 */
    @Override public void onStart() { super.onStart(); LandscapeDialogWindow.apply(this);
        if (getView() != null) {
            android.view.View fixed = getView().findViewById(R.id.checkoutDialogCard);
            cc.huoyan.retail.common.ui.BoundedDialogLayout card = getView().findViewById(R.id.boundedCheckoutDialog);
            if (card != null) card.setMaxHeight(getResources().getDisplayMetrics().heightPixels
                    - Math.round(32 * getResources().getDisplayMetrics().density));
            if (fixed == null) fixed = card;
            if (fixed != null && fixed.getLayoutParams().width > 0) {
                android.view.ViewGroup.LayoutParams params = fixed.getLayoutParams();
                params.width = Math.min(params.width, getResources().getDisplayMetrics().widthPixels
                        - getView().getPaddingLeft() - getView().getPaddingRight());
                fixed.setLayoutParams(params);
            }
        } }
    /** 跟随在途状态恢复等待提示，禁止重复提交。 */
    protected void showBusy(boolean busy, int textRes) {
        setCancelable(!busy);
        DialogFragment existing = (DialogFragment) getChildFragmentManager().findFragmentByTag("operationLoading");
        if (existing == null) existing = pendingLoading;
        if (busy && existing == null && !getChildFragmentManager().isStateSaved()) {
            LoadingDialog loading = new LoadingDialog(getString(textRes));
            loading.setCancelable(false);
            pendingLoading = loading;
            loading.show(getChildFragmentManager(), "operationLoading");
        } else if (!busy && existing != null) {
            existing.dismissAllowingStateLoss();
            pendingLoading = null;
        }
    }
    /** 子弹窗由 FragmentManager 恢复，不保留旧视图所属实例。 */
    @Override public void onDestroyView() { pendingLoading = null; super.onDestroyView(); }
}
