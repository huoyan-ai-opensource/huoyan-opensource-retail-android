# 门店地址与电话接入验收

> 开源版本已脱敏设备、账号及部署标识；大写标识和 example.test 地址均为占位符。

2026-10-19，零售客户端与识物 SDK 壳已安装到 C50D（`DEVICE_SERIAL`），连接开发后端 `http://backend.example.test:8001`。后端真实签名绑定、AIDL/WS 传递、壳及客户端持久化和页面显示均通过。

## 实现

`POST /api/v2/devices/bind-shop/` 返回 `shop_address`、`shop_phone`，分别读取门店 `address`、`shop_phone`，未填写为空字符串。公共 AAR 的 `ShopBindResp`、`DeviceBinding`、`AppPrefs`，识物壳 Parcelable/WS，以及客户端 JAR 的 `BindingResult` 均传递两字段。Parcelable 两端同步升级，不提供旧线格式分支。

零售的云端服务登录与设置页绑定统一调用 `SPManager.saveBinding`，同次保存账号、门店 ID、店名、编号、地址、电话和绑定代次；空资料覆盖原值，失败不保存。店铺信息和小票沿用原有本地资料读取位置；云端弹窗空地址、电话显示“未填写”。横竖屏设计稿已保存。

这是绑定成功时的资料快照。后端修改资料后需再次绑定获取，本次未新增独立门店查询接口。

## 验证

| 检查 | 结果 |
| --- | --- |
| 后端绑定测试 | 16 项通过；开发服务已重启加载新字段 |
| 公共 AAR 单元测试 | 726 项通过 |
| 识物壳绑定、Parcel、双通道一致性回归 | 32 项通过 |
| 客户端 JAR 单元测试 | 237 项通过 |
| 零售 app 单元测试 | 452 项通过 |
| AIDL 真实绑定，横屏显示 | 通过 |
| WS 真实绑定，竖屏窗口显示 | 通过 |
| 客户端独立进程重启后读取资料 | 横竖屏均通过 |
| SDK 壳重启后绑定记录 | 保留，已核对壳偏好文件 |
| 后端恢复空地址、电话后重新绑定 | 覆盖旧资料；重启后仍显示“未填写” |
| 空白绑定请求 | 拒绝，原资料保留 |

真机用例：`app/src/androidTest/java/cc/huoyan/retail/home/ShopDetailsDeviceTest.java`，必须显式设置 `realShopDetailsTest=true`，并传入已核对的当前门店 ID。用例通过云端登录协调器发起绑定；未用模拟回执，未操作云服务开启或商品同步。

C50D 实际横屏分辨率为 1920×1080、density 240。ROM 未响应用户旋转设置和 Activity 竖屏请求；竖屏用 `adb shell wm size 1080x1920` 临时窗口验证，非物理旋转验收。结束后已 `wm size reset`，并恢复 `user_rotation=0`。

复用设备原测试门店 `SHOP_ID`（外部标识 `EXTERNAL_SHOP_ID`），没有切换绑定维度。非空测试资料为“真机联调测试路 1019 号 / 010-88886666”；验收后后端已恢复原来的 `address=null`、`shop_phone=""`，再次真实绑定后壳和前台均为空值。截图中的非空值为临时验收资料。

## 证据

本地验收产物（不随源码提交）：日志、截图和已安装构建的 SHA-256 位于 `artifacts/shop-details-20261019/`：

- `aidl-land-populated.txt`、`ws-port-populated.txt`：两通道真实绑定。
- `aidl-land-restart.txt`、`ws-port-restart.txt`：独立进程读取持久化资料。
- `aidl-land-empty.txt`、`aidl-land-empty-restart.txt`：空值覆盖及重启。
- `landscape-populated.png`、`portrait-populated.png`、`landscape-empty.png`：实机画面。
- `backend-tests.txt`、`core-shell-tests.txt`、`retail-build-tests.txt`、`SHA256SUMS.txt`：测试与产物记录。

后端仅更新 8001 开发服务，未部署生产。
