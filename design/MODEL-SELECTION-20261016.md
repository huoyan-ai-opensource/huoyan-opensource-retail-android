# 模型选择接入与验收

> 开源版本已脱敏设备、账号及部署标识；大写标识和 example.test 地址均为占位符。

SDK 壳契约基准：`662cde4`。客户端 AIDL、WS、公共 DTO、示例和随 app 交付的 JAR 已同步。唯一设计源 `huoyan-retail.pen` 的接口说明已更新并保存。

后续真实验收已完成：切换到 8001，清空指定设备后端与本地密钥、卸载重装后，真实激活 bakery 并重新激活切换至 fresh，全流程通过。见 [真实验收记录](../docs/model-activation-real-device-20261016.md)。下面的 `-42` 记录属于切换服务前的历史排查。

| 页面 | 横屏设计 | 竖屏设计 | 实现 |
| --- | --- | --- | --- |
| 激活选模 | `IQ1YZ` | `N7Z0q` | `ActivationFlowFragment`、`SelectOptionDialog` |
| 识别设置 | `mDi4z` | `cONPs` | 各方向 `page_recognition.xml`、`SystemFragment` |
| 切换模型 | `hoFON` | `y5nZv9` | `ModelActivationPresenter`、`ModelActivationViewModel` |

手输或扫码提交后先查询模型，点选后才调用激活。取消选模返回输入；设置页当前项和取消均不写入。切换使用当前设备授权列表与证书激活序列号，提交前核对查询通道。ViewModel 保留查询和提交状态，旋转不重发写请求；结果未知时不自动重试。等待使用 LoadingDialog，结果使用 ToastEx。

横竖屏都将模型名称与版本合并展示。横屏切换按钮位于标题旁，竖屏按钮位于卡片右侧；竖屏模型卡片上下内边距 12、左右 20，三张卡片间距均为 8。名称可换行，列表可滚动，取消与底部保存按钮保持可达。

## 本轮验证

- JBR 17：Debug APK、测试 APK、SDK 独立示例编译通过；SDK JAR 与 app 引用文件的 SHA-256 一致。
- 单元测试：app 452 项、SDK 237 项全部通过，覆盖动态模型、取消、当前项、空列表、失败、结果未知、旋转期间状态恢复和 feedback 新契约。
- Android Lint：0 个错误。
- 设备 `DEVICE_SERIAL`：横屏 1920×1080、240 dpi，11 项定向设备测试通过；竖屏临时尺寸 810×1440、240 dpi，1 项模型选择设备测试通过。包括首页入口、手输/扫码接线、长列表、取消、当前项，以及选择和提交过程中的 Activity 重建。
- 竖屏尺寸验收后已恢复原始 1920×1080、240 dpi。最新 Debug APK 已安装。

模型提交设备测试使用隔离的 SDK 回执，未修改真实授权。22:00 的只读复核确认设备已激活：状态接口 `code=0`、`sdkState=READY`、`certificateValid=true`、`modelReady=true`；证书接口 `code=0`、`modelCode=bakery`。但 `queryLicenseModels()` 返回 `-42 / 操作要求先激活`，且证书回执没有激活序列号。模型查询失败与设备授权状态不能混为一谈；此前将 `-42` 直接解释为设备未激活的结论已撤回。后续已定位为后端新增路由未部署及错误码映射问题，真实模型切换尚未验收。截图中的动态模型名称为测试数据，版本缺失时显示占位。

后续链路排查已确认：运行后端容器缺少新增模型查询路由，路由 404 经 C 库与 Java 映射后成为 `-42`；详见 [链路排查记录](../docs/model-query-diagnosis-20261016.md)。本次未部署后端。

截图与日志位于本地 `qa-model-selection-20261016/`：

- [横屏识别设置](qa-model-selection-20261016/model-selection-review/settings-0.png)
- [竖屏识别设置](qa-model-selection-20261016/model-selection-review/settings-1.png)
- [竖屏长模型列表](qa-model-selection-20261016/model-selection-review/model-list-1.png)

feedback 的字符串 PLU、每目标 item、候选顺序、三位距离精度和删除后保留最终选择与 SDK 基准一致。本次补齐了设备测试中遗漏的字符串 PLU 调用；学习小图继续由 SDK 壳上报。
