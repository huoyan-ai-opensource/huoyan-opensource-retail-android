package cc.huoyan.retail.settings;

import cc.huoyan.retail.device.camera.CameraDeviceInfo;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import cc.huoyan.retail.BuildConfig;
import cc.huoyan.retail.R;
import cc.huoyan.retail.common.ui.SelectOptionDialog;
import cc.huoyan.retail.device.camera.AutoFitTextureView;
import cc.huoyan.retail.device.camera.CameraFrame;
import cc.huoyan.retail.device.camera.CameraController;
import cc.huoyan.retail.common.ui.BaseFragment;
import cc.huoyan.retail.device.camera.CameraUtils;
import cc.huoyan.retail.device.camera.ResizableDashedBoxView;
import cc.huoyan.retail.device.camera.CameraSize;

import java.util.ArrayList;
import java.util.List;

public class CameraFragment extends BaseFragment implements CameraSettingsPresenter.View {
    private TextView cameraName;
    private TextView cameraResolution;
    private LinearLayout cameraNameLayout;
    private LinearLayout cameraResolutionLayout;
    private TextView imageViewText;
    private AutoFitTextureView surfaceView;
    /** 首帧回显、选择配置和恢复重启的协调者。 */
    private CameraSettingsPresenter presenter;
    private List<CameraDeviceInfo> cameraList = new ArrayList<>();
    private ResizableDashedBoxView boxView;
    private CameraController cameraController;
    /** 页面实例独立的订阅键，旧页面销毁不能移除新订阅。 */
    private final String cameraListenerKey = getClass().getSimpleName() + "@" + System.identityHashCode(this);
    @Override
    protected int getLayoutResId() {
        if (getArguments() != null && getArguments().getBoolean("standalone")) return R.layout.page_camera;
        return R.layout.fragment_camera;
    }

    @Override
    protected boolean useEventBus() {
        return false;
    }
    @Override
    protected void initView(View view) {
        registerSelections();
        boxView = view.findViewById(R.id.boxView);
        cameraName = view.findViewById(R.id.cameraName);
        cameraResolution = view.findViewById(R.id.cameraResolution);
        cameraNameLayout = view.findViewById(R.id.cameraNameLayout);
        cameraResolutionLayout = view.findViewById(R.id.cameraResolutionLayout);
        view.findViewById(R.id.cameraTypeLayout).setOnClickListener(v -> SelectOptionDialog.show(
                getChildFragmentManager(), "camera_type", getString(R.string.settings_camera_type),
                java.util.Arrays.asList(getString(R.string.camera_type_uvc), getString(R.string.camera_type_native)),
                java.util.Arrays.asList(CameraController.UVC, CameraController.CAMERA2), cameraController.type()));
        cameraController = CameraController.getInstance();
        presenter = new CameraSettingsPresenter(this, new CameraSettingsDevice(cameraController),
                new android.os.Handler(android.os.Looper.getMainLooper())::post,
                cameraController.resolution());
        String saveResolution = presenter.resolution();
        if (saveResolution != null && !saveResolution.isEmpty()) {
            cameraResolution.setText(saveResolution.replace("*", " × "));
        }
//        imageView = view.findViewById(R.id.imageView);
        surfaceView = view.findViewById(R.id.surfaceView);
        surfaceView.setFitInside(true);
        imageViewText = view.findViewById(R.id.imageViewText);
        cameraNameLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraList = cameraController.getDevices();
                showCameraSelect();
            }
        });
        cameraResolutionLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showResolutionSelect();
            }
        });
        cameraController = CameraController.getInstance();
        cameraList = cameraController.getDevices();
        CameraSettingsPresenter framePresenter = presenter;
        cameraController.addListener(cameraListenerKey, new CameraController.FrameListener() {
            @Override public void onFrame(CameraFrame entity) {
                framePresenter.frame(cameraController.getCurrentCameraId(), entity.getWidth(), entity.getHeight());
            }
        });
        refreshSelection(view);
        startCameraPreView();
        CameraUtils.getInstance().setPreview(surfaceView);
        android.widget.Switch mirror = view.findViewById(R.id.cameraMirror);
        if (mirror != null) {
            mirror.setChecked(cameraController.mirrorEnabled());
            mirror.setOnCheckedChangeListener((button, checked) -> { if (button.isPressed()) cameraController.setMirrorEnabled(checked); });
        }
    }

    /** 视图销毁时移除帧监听，避免旧页面更新已释放的控件。 */
    @Override
    public void onDestroyView() {
        presenter.detach();
        cameraController.removeListener(cameraListenerKey);
        cameraController.unbind(surfaceView);
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (surfaceView != null) cameraController.bind(surfaceView);
        reopenCameraIfClosed();
    }

    /** 页面进入后台时释放相机，不影响新页面已经取得的绑定。 */
    @Override public void onPause() {
        cameraController.unbind(surfaceView);
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            reopenCameraIfClosed();
        }
    }

    /** 页面恢复时只提交设备状态巡检，节流由 Presenter 管理。 */
    private void reopenCameraIfClosed() { if (surfaceView != null && presenter != null) presenter.resume(SystemClock.elapsedRealtime()); }
    /** 将真实帧尺寸映射到预览、检测框和设备标签。 */
    @Override public void showFrame(String deviceId, int width, int height) {
        surfaceView.setAspectRatio(width, height);
        boxView.setOriginalImageSize(width, height);
        cameraList = cameraController.getDevices();
        for (CameraDeviceInfo usbDevice : cameraList) {
            if (cameraKey(usbDevice).equals(deviceId)) {
                cameraName.setText(usbDevice.label);
                cameraName.setTextColor(android.graphics.Color.parseColor("#293A51"));
            }
        }
        cameraResolution.setText(width + " × " + height);
        TextView status = requireView().findViewById(R.id.cameraStatus);
        if (status != null) { status.setText(R.string.land_camera_connected); status.setTextColor(android.graphics.Color.parseColor("#27835B")); }
        View content = requireView();
        surfaceView.post(() -> {
            if (!isViewCreated || getView() != content) return;
            android.widget.FrameLayout.LayoutParams bounds = new android.widget.FrameLayout.LayoutParams(surfaceView.getWidth(), surfaceView.getHeight(), Gravity.CENTER);
            if (boxView.getParent() instanceof android.widget.FrameLayout) boxView.setLayoutParams(bounds);
        });
    }
    /** 配置切换前同步检测框及分辨率标签。 */
    @Override public void prepareSelection(String resolution) {
        cameraResolution.setText(resolution.replace("*", " × ")); boxView.setSwitch(false);
    }
    /** 使用当前页面 Surface 恢复预览。 */
    @Override public void restartPreview() { CameraUtils.getInstance().setPreview(surfaceView); }
    /** 保留平台依赖的重启原因日志。 */
    @Override public void restartDecision(boolean skipped) {
        if (BuildConfig.DEBUG) Log.d("CameraFragment", "[Biz] camera_restart=" + (skipped ? "skipped_inflight" : "camera_page_closed"));
    }
    /** 本页由设备事件驱动，无额外懒加载请求。 */
    @Override protected void loadData() { }

    private void startCameraPreView() {
        imageViewText.setVisibility(View.GONE);
        surfaceView.setVisibility(View.VISIBLE);
        boxView.setVisibility(View.VISIBLE);
    }

    private void stopCameraPreView() {
        imageViewText.setVisibility(View.VISIBLE);
        surfaceView.setVisibility(View.GONE);
        boxView.setVisibility(View.GONE);
    }

    /** 类型切换只更新现有控件数据，界面结构不变。 */
    private void refreshSelection() { refreshSelection(getView()); }

    /** 首次创建时直接使用尚未挂接的根视图。 */
    private void refreshSelection(View view) {
        if (view == null) return;
        ((TextView)view.findViewById(R.id.cameraType)).setText(CameraController.CAMERA2.equals(cameraController.type())
                ? R.string.camera_type_native : R.string.camera_type_uvc);
        cameraName.setText(R.string.settings_camera_select);
        cameraName.setTextColor(android.graphics.Color.parseColor("#C93636"));
        for (CameraDeviceInfo device : cameraController.getDevices())
            if (device.id.equals(cameraController.getCurrentCameraId())) {
                cameraName.setText(device.label);
                cameraName.setTextColor(android.graphics.Color.parseColor("#293A51"));
            }
        cameraResolution.setText(cameraController.resolution().replace("*", " × "));
        android.widget.Switch mirror = view.findViewById(R.id.cameraMirror);
        if (mirror != null) mirror.setChecked(cameraController.mirrorEnabled());
        presenter.resetFrame();
    }

    private void registerSelections() {
        SelectOptionDialog.listen(getChildFragmentManager(), this, "camera_type", (position, value) -> {
            cameraController.selectType(value);
            refreshSelection();
        });
        SelectOptionDialog.listen(getChildFragmentManager(), this, "camera", (position, value) -> {
            for (CameraDeviceInfo device : cameraController.getDevices()) {
                if (cameraKey(device).equals(value)) {
                    cameraName.setText(cameraLabel(device));
                    presenter.selectCamera(cameraKey(device));
                    refreshSelection();
                    return;
                }
            }
            cc.huoyan.retail.common.ui.ToastEx.createToast(requireContext(), getString(R.string.select_option_unavailable));
        });
        SelectOptionDialog.listen(getChildFragmentManager(), this, "resolution", (position, value) -> {
            List<CameraSize> sizes = cameraController.getCameraSize();
            if (sizes != null) for (CameraSize size : sizes) {
                if ((size.width + "*" + size.height).equals(value)) {
                    presenter.selectResolution(value);
                    return;
                }
            }
            cc.huoyan.retail.common.ui.ToastEx.createToast(requireContext(), getString(R.string.select_option_unavailable));
        });
    }

    private void showCameraSelect() {
        List<String> labels = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (CameraDeviceInfo device : cameraController.getDevices()) {
            labels.add(cameraLabel(device));
            keys.add(cameraKey(device));
        }
        SelectOptionDialog.show(getChildFragmentManager(), "camera", getString(R.string.settings_camera_select),
                labels, keys, cameraController.getCurrentCameraId());
    }

    private void showResolutionSelect() {
        List<String> options = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<CameraSize> sizes = cameraController.getCameraSize();
        if (sizes != null) for (CameraSize size : sizes) {
            options.add(size.width + "*" + size.height);
            labels.add(size.width + " × " + size.height);
        }
        SelectOptionDialog.show(getChildFragmentManager(), "resolution", getString(R.string.settings_camera_resolution_pick),
                labels, options, presenter.resolution());
    }

    private String cameraKey(CameraDeviceInfo device) {
        return device.id;
    }

    private String cameraLabel(CameraDeviceInfo device) {
        return device.label;
    }
}
