package cc.huoyan.retail.learning;

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
import cc.huoyan.retail.common.ui.DebounceClickListener;
import cc.huoyan.retail.common.ui.WindowUtil;

public class WarningDialog extends DialogFragment {
    private WarningDialogListener listener;
    private String text = null;

    public interface WarningDialogListener {
        void onConfirm();

        void onCancel();
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setListener(WarningDialogListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_warning, container, false);
        view.findViewById(R.id.cancel).setOnClickListener(new DebounceClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                if (listener != null)
                    listener.onCancel();
                dismiss();
            }
        });
        view.findViewById(R.id.confirm).setOnClickListener(new DebounceClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                if (listener != null)
                    listener.onConfirm();
                dismiss();
            }
        });
        TextView textView = view.findViewById(R.id.text);
        if (text != null)
            textView.setText(text);
        return view;
    }
}
