# 使用与激活说明

本仓库中火眼自有的 App 和客户端 JAR 源码对所有人开放使用，可以基于源码开发和集成自己的应用。需要使用识别功能时，请联系火眼，确认设备支持并完成配套 SDK 激活。

## 源码与 SDK 的关系

| 内容 | 使用方式 |
| --- | --- |
| 本仓库火眼自有的 App 和客户端 JAR 源码 | 对所有人开放使用；开发与构建见[快速开始](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/blob/main/QUICKSTART.md)和[构建指南](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/blob/main/docs/build.md) |
| 识物 SDK 壳 APK、原生识别库和模型 | 按火眼交付的设备、包名和模型授权使用；源码仓库公开不代替 SDK 激活 |
| 门店、账号和云端服务 | 按实际交付的账号及服务权限接入；公开源码不提供任意账号或门店访问权 |
| 第三方代码和二进制 | 继续遵循对应作者或供应商的许可；清单和原文见[第三方组件说明](THIRD_PARTY_NOTICES.md) |

修改客户端包名、更换账号或部署到新设备时，先联系火眼确认所需登记和授权。客户端内置账号配置只用于约定的 SDK 业务接入，不是绕过设备授权的凭据。

## 联系激活

通过[火眼官网 www.huoyan.cc](https://www.huoyan.cc)联系火眼。可先准备设备型号、Android 版本、芯片与 ABI、应用包名、使用场景，以及当前 App、JAR、SDK 壳版本。激活码、账号等资料通过双方确认的私密渠道交接，不发布在公开 Issue 中。

完整安装、激活和模型准备步骤见[快速开始](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/blob/main/QUICKSTART.md)。激活请求返回成功后，仍需等待 SDK 确认模型就绪，才可调用识别和学习。

## 发布自己的 App

正式发布时使用自己的签名。随仓库公开的 `xinhai-debug-demo` 签名仅用于非商业开源实验和测试 Release 打包，其具体规则见[签名说明](https://github.com/huoyan-ai-opensource/huoyan-opensource-retail-android/blob/main/signing/README.md)。第三方组件已有的版权及许可声明应随相应分发保留。

本说明表达本项目的使用与激活规则，未将整个仓库指定为 MIT 或 Apache 2.0 等标准许可证；其中提到的第三方许可证只适用于对应组件。
