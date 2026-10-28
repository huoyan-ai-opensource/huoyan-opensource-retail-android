package cc.huoyan.retail.common.ui;

import android.graphics.Color;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import cc.huoyan.retail.R;

public class LoadingDialog extends DialogFragment {
    /** 当前提示文案，视图重建时从参数恢复。 */
    private String text;
    /** 当前视图，销毁时释放。 */
    private TextView textView;
    /** 可取消任务的主线程回调；弹窗销毁时释放宿主引用。 */
    private Runnable cancelAction;
    /** 下载模式的横向进度条，普通等待弹窗不持有；销毁视图时释放。 */
    private ProgressBar downloadProgress;

    /** 供 FragmentManager 恢复加载弹窗。 */
    public LoadingDialog() { }

    /** 使用参数保留提示文案，支持配置变化后重建。 */
    public LoadingDialog(String text) {
        this.text = text;
        Bundle args = new Bundle();
        args.putString("text", text);
        setArguments(args);
    }

    /** 更新加载提示，视图尚未创建时保留最新文案。 */
    public void setText(String text) {
        this.text = text;
        if (getArguments() != null) getArguments().putString("text", text);
        if (textView != null)
            textView.setText(text);
    }

    /** 启用共用下载样式并更新真实字节进度；首次显示前调用，总量未知时显示不定进度。 */
    public void setDownloadProgress(long downloaded, long total) {
        Bundle args = getArguments();
        if (args == null) {
            args = new Bundle();
            setArguments(args);
        }
        args.putBoolean("download", true);
        args.putLong("downloaded", downloaded);
        args.putLong("total", total);
        renderDownloadProgress();
    }

    /** 在主线程渲染进度；恢复视图时仍从参数读取，不伪造未知总量的百分比。 */
    private void renderDownloadProgress() {
        if (downloadProgress == null || getArguments() == null) return;
        long total = getArguments().getLong("total", -1);
        long downloaded = Math.max(0, getArguments().getLong("downloaded"));
        downloadProgress.setIndeterminate(total <= 0);
        if (total > 0) {
            int percent = (int) (100.0 * Math.min(downloaded, total) / total);
            downloadProgress.setProgress(percent);
        }
    }

    /** 绑定用户取消等待时停止任务的操作，主动关闭弹窗不会触发。 */
    public void setCancelAction(@Nullable Runnable action) {
        cancelAction = action;
    }

    /** 用户按返回键取消时通知任务所属页面，避免后台继续提交。 */
    @Override public void onCancel(@NonNull DialogInterface dialog) {
        if (cancelAction != null) cancelAction.run();
        super.onCancel(dialog);
    }

    /** 释放任务回调，恢复后的弹窗由所属页面重新绑定。 */
    @Override public void onDestroy() {
        cancelAction = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        boolean download = getArguments() != null && getArguments().getBoolean("download");
        View view = inflater.inflate(download ? R.layout.dialog_loading_download : R.layout.dialog_loading,
                container, false);
        if (text == null && getArguments() != null) text = getArguments().getString("text");
        textView = view.findViewById(R.id.textView);
        if (text != null && !TextUtils.isEmpty(text)) {
            textView.setText(text);
        }
        if (download) {
            downloadProgress = view.findViewById(R.id.progressBar);
            view.findViewById(R.id.close).setOnClickListener(v -> requireDialog().cancel());
            renderDownloadProgress();
        }
        return view;
    }

    /** 释放已销毁的文本控件。 */
    @Override public void onDestroyView() {
        textView = null;
        downloadProgress = null;
        super.onDestroyView();
    }
}
