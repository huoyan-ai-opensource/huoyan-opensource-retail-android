package cc.huoyan.retail.diagnostics;

import cc.huoyan.retail.common.ui.RetailAdapter;
import cc.huoyan.retail.common.ui.RowViewHolder;

import androidx.annotation.NonNull;

import cc.huoyan.retail.R;

import java.io.File;

public class LogFileAdapter extends RetailAdapter<LogFIleDirectory> {
    private File selectedFile;

    /** 高亮当前正在阅读的日志文件。 */
    public void selectFile(File file) {
        selectedFile = file;
        notifyDataSetChanged();
    }

    public LogFileAdapter() {
        super(R.layout.adapter_log_file);
    }

    @Override
    protected void convert(@NonNull RowViewHolder baseViewHolder, LogFIleDirectory logFIleDirectory) {
        File file = logFIleDirectory.getFile();
        android.view.View row = baseViewHolder.itemView.findViewById(R.id.logRow);
        if (row != null) row.setBackgroundResource(file != null && file.equals(selectedFile)
                ? R.drawable.land_soft : R.drawable.land_field);
        if (file != null) {
            baseViewHolder.setText(R.id.fileName, file.getName());
        } else {
            baseViewHolder.setText(R.id.fileName, getContext().getString(R.string.log_file_missing));
        }
        switch (logFIleDirectory.getType()) {
            case FILE:
                baseViewHolder.setImageResource(R.id.imageView, row != null ? R.drawable.land_icon_file_text : R.drawable.baseline_insert_drive_file_24);
                break;
            case DIRECTORY:
                baseViewHolder.setImageResource(R.id.imageView, R.drawable.baseline_folder_24);
                break;
        }
    }
}
