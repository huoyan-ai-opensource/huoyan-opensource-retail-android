package cc.huoyan.retail.home;

import android.os.Bundle;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.AlertDialog;
import cc.huoyan.retail.common.ui.AlertConfirmDialogListener;
import cc.huoyan.retail.settings.ActivationFlowFragment;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 首页临期提醒，复用项目确认框；系统重建后重新绑定续期入口。 */
public final class ActivationExpiryDialog extends AlertDialog {
    /** 独立标识，避免与其他业务确认框混淆。 */
    static final String TAG = "activationExpiry";

    /** 系统恢复 Fragment 使用的空构造。 */
    public ActivationExpiryDialog() { }

    /** 在主线程创建本地化提醒，参数随 Fragment 状态保存。 */
    static ActivationExpiryDialog create(android.content.Context context, ActivationExpiry expiry) {
        String remaining = expiry.days == 0 ? context.getString(R.string.activation_expiry_less_than_day)
                : context.getResources().getQuantityString(R.plurals.activation_expiry_days, expiry.days, expiry.days);
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(expiry.expiresAt * 1000L));
        Bundle args = new Bundle();
        args.putString("title", context.getString(R.string.activation_expiry_title));
        args.putString("content", context.getString(R.string.activation_expiry_message, remaining, date,
                context.getString(expiry.days == 0 ? R.string.activation_expiry_urgent : R.string.activation_expiry_advice)));
        args.putString("confirm", context.getString(R.string.activation_expiry_renew));
        args.putString("cancel", context.getString(R.string.activation_expiry_later));
        ActivationExpiryDialog dialog = new ActivationExpiryDialog();
        dialog.setArguments(args);
        return dialog;
    }

    /** 每次视图恢复都绑定当前宿主，续期沿用激活码与扫码流程。 */
    @Override public void onStart() {
        super.onStart();
        setConfirmListener(new AlertConfirmDialogListener() {
            /** 确认后打开当前首页的既有激活流程。 */
            @Override public void onConfirm() { ActivationFlowFragment.open(requireActivity()); }
            /** 稍后提醒只关闭本次弹窗，无持久免打扰设置。 */
            @Override public void onCancel() { }
        });
    }

    /** 视图销毁时释放回调，避免持有旧宿主。 */
    @Override public void onDestroyView() {
        setConfirmListener(null);
        super.onDestroyView();
    }
}
