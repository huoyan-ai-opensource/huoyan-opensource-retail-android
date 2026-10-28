# rk3576 清空重装后的真实激活与切换验收

> 开源版本已脱敏设备、账号及部署标识；大写标识和 example.test 地址均为占位符。

设备 `DEVICE_SERIAL`，后端 `http://backend.example.test:8001`。2026-10-16 22:43–22:44 最终完整设备测试通过；激活与切换均通过零售客户端真实按钮调用 SDK，未使用模拟回执。

## 环境与清理

8001 的 `backend-dev.service` 使用 `--noreload`，原进程早于 POST 模型查询接口改动，最初返回 405。重启开发服务加载现有源码后查询成功；8000 旧容器未改动。

按用户要求，仅清理指定设备：删除原后端设备、旧授权、轮换记录、关联旧公钥封禁、命令、模型 token、请求 nonce 和审计记录；卸载 `cc.huoyan` 与 `cc.huoyan.retail`，清除应用数据、证书及 Android Keystore 中应用密钥，再重新安装。首次启动前只配置 8001 地址和系统必要权限，没有回填设备身份、证书或密钥。

最终注册设备 ID 为 `DEVICE_ID`；原设备 `OLD_DEVICE_ID` 和首轮测试设备 `FIRST_TEST_DEVICE_ID` 在后端均已不存在。新测试授权 ID 为 `LICENSE_ID`，模型范围只含下述两个 rk3576 模型。测试凭据未写入仓库或日志。

## 测试模型

| 模型 | 模型包 ID | chip / channel | 包版本 | 来源 |
| --- | --- | --- | --- | --- |
| bakery | BAKERY_PACKAGE_ID | rk3576 / E2E | 3 | 已发布烘焙模型 |
| fresh | FRESH_PACKAGE_ID | rk3576 / E2E | 3 | 为测试使用原 normal 渠道 fresh v2 的同一份模型内容；新增 E2E 包记录及对应 head 文件 |

两个源包均校验了完整 pub 下载大小与 SHA-256，private head 文件均为 1 MiB 且存在。没有覆盖原模型包；新增生鲜测试包不是新训练的权重版本。

## 实际流程与结果

1. 全新安装后状态为 `NOT_ACTIVATED`；输入测试激活码，通过真实提交按钮查询列表，出现烘焙和生鲜两个授权模型。选模之前仍未激活。
2. 选择 bakery，客户端调用 `activate(activationCode, "bakery")`。状态经过 `MODEL_PREPARING` 到 `READY`，弹出“激活成功”，证书返回 bakery，序列号已保存。
3. 打开识别设置，点击“切换模型”，选择 fresh。客户端使用同一授权再次调用 `activate(activationCode, "fresh")`，经过模型准备后进入 `READY`。
4. 弹出“模型切换成功”，当前模型卡片刷新为“生鲜模型”；列表只有 fresh 标为 current。证书和后端设备、License 均为 fresh，授权到期时间保持不变（2027-01-14 14:43:47 UTC）。
5. 最终 `freshActivationThenSwitch` 一次完整运行通过（19.358 秒）；独立 `switchActivatedDevice` 也通过。首轮测试仅因错误地要求空闲 Fragment 宿主被移除而失败，修正断言为业务弹窗关闭后，重新清理并重装完成了上述最终验收。

后续反馈真机验收发现：切回烘焙后，虽然本报告的授权和状态断言仍通过，但生鲜 320 维识别库与烘焙 960 维冲突，实际检测建链失败。本报告只确认激活与状态切换，不代表不同模型识别库切换可用；复现与临时空库备份处理见 [feedback / item 真机验收](feedback-item-real-device-20261016.md)。

SDK 状态接口的 `modelVersion` 仍为空，设置页按现有规则显示 `—`；此项不代表模型未就绪。版本字段回传未在本次修改。

后续模型版本回传已完成，并通过真实客户端 AIDL、WS 和设置页验证，见 [模型版本真机验收](model-version-real-device-20261016.md)。

真实生鲜模型卡片已检查横屏与竖屏截图；竖屏使用临时 810×1440 尺寸，检查后已恢复原始 1920×1080。

## 交付

设备保留 8001 地址和已激活的 fresh 模型，方便继续测试。真实测试入口 `RealModelActivationDeviceTest` 必须显式传 `realModelTest=true` 才执行，普通设备测试默认跳过，避免意外写授权。测试激活码从私有临时文件读取，首次激活结束后删除。

日志和截图在本地 `design/qa-model-selection-20261016/real-model-final*`，包括首次选模、切换选模、切换前后卡片和完整测试日志。
