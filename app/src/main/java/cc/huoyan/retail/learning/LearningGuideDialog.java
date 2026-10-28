package cc.huoyan.retail.learning;

import cc.huoyan.retail.common.ui.LandscapeDialogWindow;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.WindowUtil;

/** 商品学习指导，沿用客户端提示弹窗外观并记住自动显示偏好。 */
public class LearningGuideDialog extends DialogFragment {
    /** 使用对应方向 XML 的指导弹窗和遮罩。 */
    @Override public void onStart() {
        super.onStart();
        LandscapeDialogWindow.apply(this);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("learning_guide", Context.MODE_PRIVATE);
    }

    /** 未关闭自动提示时，在进入学习页后显示指导。 */
    public static boolean shouldShowAutomatically(Context context) {
        return !preferences(context).getBoolean("dont_show_again", false);
    }

    /** 创建指导内容及下次自动显示的勾选项。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        WindowUtil.setDialogFragmentBackground(this, new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_learning_guide, container, false);
        ((TextView) view.findViewById(R.id.title)).setText(R.string.learning_help);
        CheckBox remember = view.findViewById(R.id.learningGuideDontShowAgain);
        remember.setVisibility(View.VISIBLE);
        remember.setChecked(!shouldShowAutomatically(requireContext()));
        view.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());
        TextView confirm = view.findViewById(R.id.confirm);
        confirm.setText(R.string.learning_understood);
        confirm.setOnClickListener(v -> {
            preferences(requireContext()).edit().putBoolean("dont_show_again", remember.isChecked()).apply();
            dismiss();
        });
        return view;
    }
}
