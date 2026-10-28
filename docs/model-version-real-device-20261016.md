# 模型版本回传真机验收

> 开源版本已脱敏设备、账号及部署标识；大写标识和 example.test 地址均为占位符。

2026-10-16 23:37，C50D（`DEVICE_SERIAL`）覆盖安装本次模型版本 SDK 测试包，保留应用数据、授权和模型。客户端生产代码及 JAR 沿用现有版本。

`RealModelVersionDeviceTest.currentModelVersionReachesClient` 通过（4.7 秒）：

- 真实 JAR 经 AIDL 查询 `getStatus()`，`modelVersion=1.0.2`。
- 真实 JAR 经 WebSocket 查询 `getStatus`，`model_version=1.0.2`。
- 打开现有识别设置页，当前模型为 `bakery 模型`，版本文字为 `模型版本：1.0.2`。

测试只查询状态与显示结果，不激活、不切换模型、不修改商品。入口须显式传入 `realModelVersionTest=true`，避免普通测试要求设备预先激活。

```sh
adb -s DEVICE_SERIAL shell am instrument -w -r \
  -e class cc.huoyan.retail.settings.RealModelVersionDeviceTest \
  -e realModelVersionTest true \
  cc.huoyan.retail.test/androidx.test.runner.AndroidJUnitRunner
```

测试 APK 由 `:app:assembleDebugAndroidTest` 构建。日志和截图位于 SDK 工程的 `artifacts/model-version-20261016/device-test/`；配套 SDK 包为该目录上层的 `huoyan-sdk-model-version-debug.apk`。
