package cc.huoyan.retail.learning;

import android.graphics.Bitmap;

public class StudyPicEntity {
    private boolean isStudy = false;
    private boolean isFailure = false;
    private Bitmap img;
    private String filePath;
    private int picId;
    private String clazzName;
    private long captureTimestamp;
    // v2 学习样本句柄：itemLearn 回执 uid，单图遗忘 itemForgetByUid
    // 依赖它；会话内绑定（不落库，跨会话单图遗忘不可达，用整组遗忘 itemForget 兜底）
    private long uid;

    public boolean getIsStudy() {
        return isStudy;
    }

    public void setStudy(boolean study) {
        isStudy = study;
    }

    public Bitmap getImg() {
        return img;
    }

    public void setImg(Bitmap img) {
        this.img = img;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }


    public int getPicId() {
        return picId;
    }

    public void setPicId(int picId) {
        this.picId = picId;
    }

    public boolean getIsFailure() {
        return isFailure;
    }

    public void setIsFailure(boolean failure) {
        isFailure = failure;
    }

    public String getClazzName() {
        return clazzName;
    }

    public void setClazzName(String clazzName) {
        this.clazzName = clazzName;
    }

    public long getCaptureTimestamp() {
        return captureTimestamp;
    }

    public void setCaptureTimestamp(long captureTimestamp) {
        this.captureTimestamp = captureTimestamp;
    }

    /** v2 学习样本句柄（itemLearn 回执 uid；0=无句柄）。 */
    public long getUid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }
}
