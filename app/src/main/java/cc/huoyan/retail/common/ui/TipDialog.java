package cc.huoyan.retail.common.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import cc.huoyan.retail.R;


public class TipDialog extends DialogFragment {
    private String content;

    public TipDialog(String content) {
        this.content = content;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_tip, container, false);
        TextView textView = view.findViewById(R.id.text);
        textView.setText(content);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 使用Handler延迟1秒后关闭对话框
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // 检查对话框是否还在显示
                if (isAdded() && getDialog() != null && getDialog().isShowing()) {
                    dismiss();
                }
            }
        }, 1000);
    }
}
