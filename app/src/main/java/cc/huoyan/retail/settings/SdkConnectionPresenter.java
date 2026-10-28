package cc.huoyan.retail.settings;

import cc.huoyan.retail.sdk.SdkChannelManager;
import java.util.*;
import java.util.concurrent.*;

/** SDK 连接页交互：保存连接与无副作用的探测严格分开。 */
final class SdkConnectionPresenter {
    /** 连接页的显示能力。 */
    interface View extends WsConnectionPresenter.View {
        /** 通道改变时清空旧测试和发现结果。 */
        void showChannel(cc.huoyan.sdk.client.SdkConfig.Channel channel);
        /** 展示保存成功的通道名。 */
        void saved(String channel);
        /** 展示 AIDL 当前连接状态。 */
        void aidlStatus(boolean connected);
    }
    /** 当前视图，视图销毁时清空。 */
    private View view;
    /** 与地址弹窗共用的 WS 查询协调器。 */
    private final WsConnectionPresenter ws;
    /** 当前选中的 SDK 通道。 */
    private cc.huoyan.sdk.client.SdkConfig.Channel channel;
    /** 注入调度器；每次创建 Fragment View 使用新的 Presenter。 */
    SdkConnectionPresenter(View view, ExecutorService worker, Executor ui) {
        this.view = view; this.ws = new WsConnectionPresenter(view, worker, ui);
    }
    /** 返回当前通道供按钮及地址框渲染。 */
    cc.huoyan.sdk.client.SdkConfig.Channel channel() { return channel; }
    /** 同步已保存通道，同时作废旧探测结果。 */
    void showCurrent() { changeChannel(SdkChannelManager.getInstance().getChannel()); }
    /** 局域网 Tab 先展示地址表单，保存后才修改实际连接。 */
    void selectLan() { changeChannel(cc.huoyan.sdk.client.SdkConfig.Channel.WS); }
    /** 保存通道；WS 必须先验证并保存端点，再连接客户端。 */
    boolean save(cc.huoyan.sdk.client.SdkConfig.Channel selected, String address) {
        SdkChannelManager manager = SdkChannelManager.getInstance();
        if (selected == cc.huoyan.sdk.client.SdkConfig.Channel.WS) {
            if (!ws.saveAddress(address, manager::setWsEndpoint)) return false;
        }
        manager.setChannel(selected);
        changeChannel(manager.getChannel());
        manager.getClient().connect();
        if (view != null) view.saved(channel.name());
        return true;
    }
    /** AIDL 查询本地连接状态，WS 对输入地址执行无副作用探测。 */
    void probe(String address) {
        if (view == null) return;
        if (channel == cc.huoyan.sdk.client.SdkConfig.Channel.WS) query(false, address);
        else view.aidlStatus(SdkChannelManager.getInstance().getChannel() == channel
                && SdkChannelManager.getInstance().getClient().isConnected());
    }
    /** 转交两个入口共享的 WS 查询策略。 */
    void query(boolean scan, String address) { ws.query(scan, address); }
    /** 销毁当前视图并撤销只读查询。 */
    void detach() { ws.detach(); view = null; }
    /** 切换通道后使所有旧结果失效。 */
    private void changeChannel(cc.huoyan.sdk.client.SdkConfig.Channel channel) {
        ws.cancel();
        this.channel = channel;
        if (view != null) view.showChannel(channel);
    }
}
