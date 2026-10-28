# huoyan-opensource-retail-android

[简体中文](README.md) | English

Huoyan's open-source Android retail client provides AI recognition and checkout for **fresh produce, snacks, bakery products, and small-portion prepared dishes**.

Official website: [www.huoyan.cc](https://www.huoyan.cc)

Application package: `cc.huoyan.retail`; SDK JAR package: `cc.huoyan.sdk.client`.

## SDK JAR integration

[English Integration Guide](huoyan-sdk-client/INTEGRATION_GUIDE.en.md) · [Chinese Integration Guide](huoyan-sdk-client/INTEGRATION_GUIDE.md) · [Java example](huoyan-sdk-client/samples/RecognitionIntegrationExample.java)

Use the JAR in an existing application or customize this client. The JAR handles communication with the Huoyan SDK.

The app uses `app/src/main/libs/huoyan-sdk-client-2.0.0.jar`; runtime dependencies are declared in `app/build.gradle`. The `huoyan-sdk-client` module is packaged and tested independently, and app builds do not update the JAR automatically. After changing SDK source, run `bash gradlew :huoyan-sdk-client:updateAppSdkJar` with JBR-17 before building the app. `:huoyan-sdk-client:clientDistribution` generates the full delivery ZIP.

The SDK's `OFFLINE` state supports local checkout and learning when the certificate is valid, the model is ready, and the key is not lost. There is no need to wait for cloud reconciliation to return the SDK to `READY`. Cloud operations still use the SDK's actual results; failures are not treated as local successes while offline.

## Features

- AI recognition (USB camera + Huoyan SDK) → product matching → pricing by count or weight
- Mixed-cart checkout through `CartCalculator`, combining count and weight pricing with per-line HALF_UP rounding
- Weighing and printing switches stored in local SharedPreferences, with defaults for each shop scenario
- Pricing validation that blocks weight-based products when weighing is disabled

## Code structure

See [Code structure](docs/code-structure.md) (Chinese) for business packages, device integration, and test locations.

## Build

Use JBR-17; Gradle 7.5 does not support Java 21. Replace `JAVA_HOME` below with your local path.

```bash
export JAVA_HOME=/path/to/jbr-17/Contents/Home
bash gradlew :app:assembleDebug        # Debug APK (huoyan-retail-{version}-*.apk)
bash gradlew :app:assembleRelease      # Release APK (open-source experimental signing by default)
bash gradlew :app:testDebugUnitTest    # Unit tests
```

Debug builds use the debug signing key generated locally by Android build tools. Local and CNB release builds use the repository's [`xinhai-debug-demo` signing key](signing/README.md) by default, with debugging disabled. **This key is only for noncommercial experiments and test Release builds of the open-source version. Do not use it for commercial releases; its test private key and password are public.**

For production releases, configure all four properties in the untracked `local.properties`: `signing.storeFile` (an absolute path is recommended), `signing.storePassword`, `signing.keyAlias`, and `signing.keyPassword`. Release builds will then use that signing configuration. Do not commit production signing keys or passwords.

## Tests

- Unit tests: `app/src/test/java` (plain JUnit tests for money, pricing, and checkout logic)

See [Agents.en.md](Agents.en.md) for coding conventions.
