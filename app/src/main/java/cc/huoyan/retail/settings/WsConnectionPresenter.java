package cc.huoyan.retail.settings;

import cc.huoyan.sdk.client.SdkDiscovery;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** WS 无副作用查询及端点校验；两个入口共用，通道切换由设置页负责。 */
final class WsConnectionPresenter {
    /** 查询结果的页面出口。 */
    interface View {
        /** 在地址输入处提示格式错误。 */
        void invalidAddress();
        /** 查询开始时锁定按钮。 */
        void querying(boolean scan, String ip);
        /** 展示当前查询结果，旧请求不会交付。 */
        void queryResult(boolean scan, String ip, boolean connected, List<String> matches);
    }
    /** 可替换的网络探测端口，测试无需打开真实连接。 */
    interface Discovery {
        /** 测试单个固定端点。 */
        boolean probe(String endpoint) throws Exception;
        /** 从输入地址发现同网段服务。 */
        List<String> discover(String ip) throws Exception;
    }
    /** 当前视图，关闭时释放。 */
    private View view;
    /** 查询执行队列，生命周期由宿主管理。 */
    private final ExecutorService worker;
    /** 不引用页面的主线程投递器。 */
    private final Executor ui;
    /** 网络查询实现。 */
    private final Discovery discovery;
    /** 当前可取消的查询。 */
    private Future<?> pending;
    /** 回执代次，即便底层忽略中断也不会回写旧结果。 */
    private int generation;
    /** 生产环境使用 SDK 的只读探测。 */
    WsConnectionPresenter(View view, ExecutorService worker, Executor ui) {
        this(view, worker, ui, new Discovery() {
            /** 仅探测可达性，不切换业务连接。 */
            public boolean probe(String endpoint) throws Exception { return SdkDiscovery.probe(endpoint); }
            /** 查询局域网地址，不保存结果。 */
            public List<String> discover(String ip) throws Exception { return SdkDiscovery.discover(ip); }
        });
    }
    /** 注入网络与线程端口以验证取消和晚到回执。 */
    WsConnectionPresenter(View view, ExecutorService worker, Executor ui, Discovery discovery) {
        this.view = view; this.worker = worker; this.ui = ui; this.discovery = discovery;
    }
    /** 校验后只保存端点；调用方负责成功提示，不隐式切换通道。 */
    boolean saveAddress(String address, Consumer<String> save) {
        String endpoint = endpoint(address);
        if (endpoint == null || view == null) return false;
        save.accept(endpoint);
        return true;
    }
    /** 执行单地址测试或局域网发现；结果只交付当前请求。 */
    void query(boolean scan, String address) {
        if (view == null) return;
        String endpoint = endpoint(address);
        if (endpoint == null) return;
        String ip = SdkDiscovery.ipFromEndpoint(endpoint);
        cancel();
        int request = generation;
        view.querying(scan, ip);
        pending = worker.submit(() -> {
            List<String> matches = Collections.emptyList();
            boolean connected = false;
            try {
                if (scan) matches = discovery.discover(ip);
                else connected = discovery.probe(endpoint);
            } catch (Exception ignored) { }
            List<String> found = matches;
            boolean success = connected;
            ui.execute(() -> {
                if (view != null && request == generation) view.queryResult(scan, ip, success, found);
            });
        });
    }

    /** 作废全部查询，在切换通道后复用当前视图。 */
    void cancel() { ++generation; if (pending != null) pending.cancel(true); }
    /** 释放页面引用，不影响已保存的业务连接。 */
    void detach() { cancel(); view = null; }
    /** 端点解析和错误出口保持一致。 */
    private String endpoint(String address) {
        try { return SdkDiscovery.endpointForIp(address); }
        catch (IllegalArgumentException error) { if (view != null) view.invalidAddress(); return null; }
    }
}
