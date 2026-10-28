package cc.huoyan.retail.common.ui;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;


public class DialogUtil {

    private static LoadingDialog dialog;

    public static void showLoadingDialog(FragmentActivity mActivity, String text) {
        FragmentManager fragmentManager = mActivity.getSupportFragmentManager();
        WindowUtil.hideKeyboard(mActivity);
        dialog = new LoadingDialog(text);
        dialog.setCancelable(false);
        dialog.show(fragmentManager, "loading");
    }

    public static void setLoadingDialogText(String text) {
        if (dialog != null) {
            dialog.setText(text);
        }
    }

    public static void showAlertDialog(FragmentManager manager, String title, String content, String confirmText, String cancelText,
                                       AlertConfirmDialogListener listener) {
        // 检查是否已有相同 tag 的弹窗
        AlertDialog existingDialog = (AlertDialog) manager.findFragmentByTag("alertDialog");
        if (existingDialog == null) {
            // 不存在时才创建并显示
            AlertDialog alertDialog = new AlertDialog(title, content, confirmText, cancelText, listener);
            alertDialog.show(manager, "alertDialog");
        }
    }

    public static void showTipDialog(FragmentManager manager, String content) {
        TipDialog existingDialog = (TipDialog) manager.findFragmentByTag("tipDialog");
        if (existingDialog == null) {
            // 不存在时才创建并显示
            TipDialog alertDialog = new TipDialog(content);
            alertDialog.show(manager, "tipDialog");
        }
    }

    public static void dismissLoadingDialog() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }

}
