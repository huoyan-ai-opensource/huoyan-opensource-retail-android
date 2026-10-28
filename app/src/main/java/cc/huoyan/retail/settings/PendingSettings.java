package cc.huoyan.retail.settings;

/** 设置宿主的退出保护边界，协调当前页面未保存的用户输入。 */
interface PendingSettings {
    /** 主线程比较当前输入与持久化配置，不把已自动保存的选项视为未保存。 */
    boolean hasUnsavedSettings();
    /** 主线程校验并保存当前草稿；成功才允许宿主退出页面。 */
    boolean savePendingSettings();
}
