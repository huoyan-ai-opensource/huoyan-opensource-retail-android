package cc.huoyan.retail.learning;

import cc.huoyan.retail.common.ui.RetailAdapter;
import cc.huoyan.retail.common.ui.RowViewHolder;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import cc.huoyan.retail.R;
import cc.huoyan.retail.sdk.SdkGoodsImages;
import cc.huoyan.retail.recognition.DrawUtil;

public class StudyPicAdapter extends RetailAdapter<StudyPicEntity> {
    private PicOnClickListener listener;

    /** 使用指定布局复用学习列表行为。 */
    protected StudyPicAdapter(int layout) {
        super(layout);
    }

    public void setListener(PicOnClickListener listener) {
        this.listener = listener;
    }

    /** 条目离开复用缓存时释放缩略图请求，避免继续解码已不可见的图片。 */
    @Override
    public void onViewRecycled(@NonNull RowViewHolder holder) {
        ImageView image = holder.getView(R.id.img);
        com.bumptech.glide.Glide.with(image).clear(image);
        super.onViewRecycled(holder);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void convert(@NonNull RowViewHolder baseViewHolder, StudyPicEntity entity) {
        ImageView delIv = baseViewHolder.getView(R.id.del);
        ImageView imgIv = baseViewHolder.getView(R.id.img);
        SdkGoodsImages.load(imgIv, entity.getFilePath());
        delIv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 按下时缩小图片
                        DrawUtil.scaleImage(delIv, 0.5f);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 抬起或取消时恢复图片大小
                        DrawUtil.scaleImage(delIv, 1.0f);
                        break;
                }
                return false;
            }
        });
        delIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int position = baseViewHolder.getBindingAdapterPosition();
                if (listener != null && position >= 0 && position < getData().size()) {
                    listener.onDeleted(position);
                }
            }
        });
    }


    public interface PicOnClickListener {
        void onDeleted(int position);
    }
}
