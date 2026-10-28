package cc.huoyan.retail.diagnostics;

import cc.huoyan.retail.common.ui.RetailAdapter;
import cc.huoyan.retail.common.ui.RowViewHolder;

import androidx.annotation.NonNull;

import cc.huoyan.retail.R;

public class LogPathAdapter extends RetailAdapter<String> {
    public LogPathAdapter() {
        super(R.layout.adapter_log_path);
    }

    @Override
    protected void convert(@NonNull RowViewHolder baseViewHolder, String s) {
        baseViewHolder.setText(R.id.fileName, s);
    }
}
