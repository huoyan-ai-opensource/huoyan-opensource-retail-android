package cc.huoyan.retail.common.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import cc.huoyan.retail.R;

public class AlertDialog extends DialogFragment {
    private AlertConfirmDialogListener listener;

    /** 为页面重建后的业务弹窗重新绑定确认操作；销毁视图时可传 null 释放引用。 */
    public void setConfirmListener(@Nullable AlertConfirmDialogListener listener) {
        this.listener = listener;
    }

    /** 供系统在页面重建后恢复弹窗内容。 */
    public AlertDialog() {
    }

    /** 创建项目统一的确认弹窗。 */
    public AlertDialog(@NonNull String titleText, @NonNull String contentText, @NonNull String confirmText, @NonNull String cancelText,
                       AlertConfirmDialogListener listener) {
        Bundle args = new Bundle();
        args.putString("title", titleText);
        args.putString("content", contentText);
        args.putString("confirm", confirmText);
        args.putString("cancel", cancelText);
        setArguments(args);
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_alert, container, false);
        Bundle args = requireArguments();
        TextView title = view.findViewById(R.id.title);
        title.setText(args.getString("title"));
        TextView cancel = view.findViewById(R.id.cancel);
        cancel.setText(args.getString("cancel"));
        TextView confirm = view.findViewById(R.id.confirm);
        confirm.setText(args.getString("confirm"));
        TextView content = view.findViewById(R.id.textView);
        content.setText(args.getString("content"));
        cancel.setOnClickListener(new DebounceClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                if (listener != null) listener.onCancel();
                dismiss();
            }
        });
        confirm.setOnClickListener(new DebounceClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                if (listener != null)
                    listener.onConfirm();
                dismiss();
            }
        });
        return view;
    }
}
