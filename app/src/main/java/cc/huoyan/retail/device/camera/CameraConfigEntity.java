package cc.huoyan.retail.device.camera;

import java.util.ArrayList;

public class CameraConfigEntity {
    private boolean camera_enable;
    private String camera_name;
    private int camera_index;
    private String camera_resolution;
    private int camera_working_model;
    private int left_top_x;
    private int left_top_y;
    private int left_bottom_x;
    private int left_bottom_y;
    private int right_top_x;
    private int right_top_y;
    private int right_bottom_x;
    private int right_bottom_y;
    private int camera_fps;
    private ArrayList<String> camera_resolutions;
    private String camera_type;
    private int camera_rotation = 0;

    public int getCamera_rotation() {
        return camera_rotation;
    }

    public void setCamera_rotation(int camera_rotation) {
        this.camera_rotation = camera_rotation;
    }

    public String getCamera_type() {
        return camera_type;
    }

    public void setCamera_type(String camera_type) {
        this.camera_type = camera_type;
    }

    public boolean isCamera_enable() {
        return camera_enable;
    }

    public void setCamera_enable(boolean camera_enable) {
        this.camera_enable = camera_enable;
    }

    public String getCamera_name() {
        return camera_name;
    }

    public void setCamera_name(String camera_name) {
        this.camera_name = camera_name;
    }

    public int getCamera_index() {
        return camera_index;
    }

    public void setCamera_index(int camera_index) {
        this.camera_index = camera_index;
    }

    public String getCamera_resolution() {
        return camera_resolution;
    }

    public void setCamera_resolution(String camera_resolution) {
        this.camera_resolution = camera_resolution;
    }

    public int getCamera_working_model() {
        return camera_working_model;
    }

    public void setCamera_working_model(int camera_working_model) {
        this.camera_working_model = camera_working_model;
    }

    public int getLeft_top_x() {
        return left_top_x;
    }

    public void setLeft_top_x(int left_top_x) {
        this.left_top_x = left_top_x;
    }

    public int getLeft_top_y() {
        return left_top_y;
    }

    public void setLeft_top_y(int left_top_y) {
        this.left_top_y = left_top_y;
    }

    public int getLeft_bottom_x() {
        return left_bottom_x;
    }

    public void setLeft_bottom_x(int left_bottom_x) {
        this.left_bottom_x = left_bottom_x;
    }

    public int getLeft_bottom_y() {
        return left_bottom_y;
    }

    public void setLeft_bottom_y(int left_bottom_y) {
        this.left_bottom_y = left_bottom_y;
    }

    public int getRight_top_x() {
        return right_top_x;
    }

    public void setRight_top_x(int right_top_x) {
        this.right_top_x = right_top_x;
    }

    public int getRight_top_y() {
        return right_top_y;
    }

    public void setRight_top_y(int right_top_y) {
        this.right_top_y = right_top_y;
    }

    public int getRight_bottom_x() {
        return right_bottom_x;
    }

    public void setRight_bottom_x(int right_bottom_x) {
        this.right_bottom_x = right_bottom_x;
    }

    public int getRight_bottom_y() {
        return right_bottom_y;
    }

    public void setRight_bottom_y(int right_bottom_y) {
        this.right_bottom_y = right_bottom_y;
    }

    public int getCamera_fps() {
        return camera_fps;
    }

    public void setCamera_fps(int camera_fps) {
        this.camera_fps = camera_fps;
    }

    public ArrayList<String> getCamera_resolutions() {
        return camera_resolutions;
    }

    public void setCamera_resolutions(ArrayList<String> camera_resolutions) {
        this.camera_resolutions = camera_resolutions;
    }
}
