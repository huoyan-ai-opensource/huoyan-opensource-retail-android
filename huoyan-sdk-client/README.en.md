# Huoyan SDK JAR

[简体中文](README.md) | English

Official website: [www.huoyan.cc](https://www.huoyan.cc)

Call the Huoyan SDK through the JAR's Java methods. See the [English Integration Guide](INTEGRATION_GUIDE.en.md) / [Chinese Integration Guide](INTEGRATION_GUIDE.md) for setup. The reference client source is also provided; use it for calling examples or customize its screens directly.

**Requirements: JAR 2.0.0, Android API 24+, Java 11.** Install the Huoyan SDK separately on the device providing recognition services, using a version that matches the JAR. When connecting to another device, the client device does not need a local Huoyan SDK installation. Register the client application's package name and obtain the required authorization before integrating. AIDL requires API 27+; use WebSocket on API 24–26.

The Java resource `raw/config` inside the JAR supplies the account. Preserve it when packaging the APK; do not add a resource with the same path in the host or exclude it through `packagingOptions`.

| Delivery item | Purpose |
| --- | --- |
| `libs/` | Main JAR and other required runtime JARs |
| `INTEGRATION_GUIDE.md` | Integration steps, parameters, and result handling in Chinese |
| `INTEGRATION_GUIDE.en.md` | Integration steps, parameters, and result handling in English |
| `README.md` / `README.en.md` | Delivery notes in Chinese and English |
| `consumer-rules.pro` | Obfuscation configuration |
| `samples/` | Java calling examples |
| Reference client source (provided separately) | Run complete workflows or customize the existing client |
