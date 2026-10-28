package cc.huoyan.retail.common.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import cc.huoyan.retail.R;
import java.util.ArrayList;
import java.util.List;

/** 横竖屏共用的单选弹窗；选项快照随 Fragment 保存，普通选项取消不发送结果，模型会话显式接收取消。 */
public class SelectOptionDialog extends DialogFragment {
    private static final String TAG = "selectOption";
    private boolean submitted;

    /** 接收选中项的原始位置和稳定值，避免把显示文案当设备身份。 */
    public interface OnSelected {
        /** 用户点选一项后执行；取消或返回不会调用。 */
        void onSelected(int position, String value);
    }

    /** 在宿主初始化时注册结果，宿主重建后仍能接收恢复弹窗的选择。 */
    public static void listen(FragmentManager manager, LifecycleOwner owner, String key, OnSelected listener) {
        manager.setFragmentResultListener(key, owner, (request, result) ->
                listener.onSelected(result.getInt("position"), result.getString("value", "")));
    }

    /** 显示文案同时作为选项值的单选弹窗。 */
    public static void show(FragmentManager manager, String key, String title,
                            List<String> options, String selected) {
        show(manager, key, title, options, options, selected);
    }

    /** 显示独立单选弹窗；复制文案和值，列表刷新不会改变已打开选项的对应关系。 */
    public static void show(FragmentManager manager, String key, String title,
                            List<String> labels, List<String> values, String selected) {
        show(manager, key, title, null, labels, values, selected, false);
    }

    /** 模型会话使用完整结果模式：包括取消和当前项，以便收口会话状态。 */
    public static void show(FragmentManager manager, String key, String title, String subtitle,
                            List<String> labels, List<String> values, String selected, boolean completeResult) {
        if (labels.size() != values.size()) throw new IllegalArgumentException("选项文案和值数量不一致");
        if (manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putString("title", title);
        args.putString("subtitle", subtitle);
        args.putBoolean("completeResult", completeResult);
        args.putStringArrayList("labels", new ArrayList<>(labels));
        args.putStringArrayList("values", new ArrayList<>(values));
        args.putString("selected", selected);
        SelectOptionDialog dialog = new SelectOptionDialog();
        dialog.setArguments(args);
        dialog.showNow(manager, TAG);
    }

    /** 创建可滚动的选项内容，并回显当前选中值。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.dialog_select_option, container, false);
        Bundle args = requireArguments();
        ArrayList<String> labels = args.getStringArrayList("labels");
        ArrayList<String> values = args.getStringArrayList("values");
        int selected = values.indexOf(args.getString("selected"));
        ((TextView) view.findViewById(R.id.selectTitle)).setText(args.getString("title"));
        String subtitle = args.getString("subtitle");
        TextView subtitleView = view.findViewById(R.id.selectSubtitle);
        subtitleView.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        subtitleView.setText(subtitle);
        ListView list = view.findViewById(R.id.selectOptions);
        list.setAdapter(new ArrayAdapter<String>(requireContext(), R.layout.item_select_option, labels) {
            @NonNull @Override public View getView(int position, @Nullable View recycled, @NonNull ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) super.getView(position, recycled, parent);
                row.setChecked(position == selected);
                return row;
            }
        });
        list.setEmptyView(view.findViewById(R.id.selectEmpty));
        if (selected >= 0) {
            list.setItemChecked(selected, true);
            list.setSelection(selected);
        }
        list.setOnItemClickListener((parent, row, position, id) -> {
            if (submitted) return;
            submitted = true;
            Bundle result = new Bundle();
            result.putInt("position", position);
            result.putString("value", values.get(position));
            dismiss();
            // 重选当前项只关闭，不重复保存或重启外设。
            if (position != selected || args.getBoolean("completeResult")) getParentFragmentManager().setFragmentResult(args.getString("key"), result);
        });
        view.findViewById(R.id.selectCancel).setOnClickListener(v -> { cancelled(); dismiss(); });
        return view;
    }

    /** 按当前窗口约束居中显示，长列表滚动且取消操作始终可见。 */
    @Override public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.4f;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
        android.graphics.Rect available = new android.graphics.Rect();
        requireActivity().getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
        int width = Math.min(dp(560), available.width() - dp(40));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        ListView list = requireView().findViewById(R.id.selectOptions);
        int total = 0;
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            View row = list.getAdapter().getView(i, null, list);
            row.measure(View.MeasureSpec.makeMeasureSpec(width - dp(48), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            total += row.getMeasuredHeight() + (i == 0 ? 0 : list.getDividerHeight());
        }
        list.getLayoutParams().height = 0;
        requireView().measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxListHeight = Math.max(dp(64), (int) (available.height() * 0.8f) - requireView().getMeasuredHeight());
        list.getLayoutParams().height = Math.min(total, maxListHeight);
        list.requestLayout();
    }

    /** 返回键或遮罩关闭属于用户取消，不在视图重建时误发事件。 */
    @Override public void onCancel(@NonNull android.content.DialogInterface dialog) {
        cancelled();
        super.onCancel(dialog);
    }

    /** 完整结果模式以位置 -1 表达取消，且只交付一次。 */
    private void cancelled() {
        if (submitted || !requireArguments().getBoolean("completeResult")) return;
        submitted = true;
        Bundle result = new Bundle();
        result.putInt("position", -1);
        getParentFragmentManager().setFragmentResult(requireArguments().getString("key"), result);
    }

    /** 转换弹窗尺寸。 */
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
