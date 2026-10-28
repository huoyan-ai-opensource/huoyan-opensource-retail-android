# Agents.en.md

[简体中文](AGENTS.md) | English

This is the English version of the repository's [AGENTS.md](AGENTS.md), the guide for AI coding agents working in `huoyan-opensource-retail-android`. Read it before making changes.

## Refactoring and compatibility

- When refactoring, replacing, or upgrading a feature, keep only the new implementation and delete the superseded code unless the user explicitly requests compatibility.
- Do not add or retain legacy configuration migration, API adapters, format detection, compatibility branches, fallbacks, or parallel old and new implementations without that request.
- Remove configuration, parameters, dependencies, tests, and documentation used only by the replaced implementation. Leave no unused code or redundant compatibility layers.
- Keep validation, error handling, and device reconnection required by the current implementation. These are not legacy compatibility mechanisms.
- Limit cleanup to the current task. Any user-requested compatibility applies only within the specified scope.

## Design maintenance

- `design/huoyan-retail.pen` is the sole design source. Maintain all modules and both portrait and landscape layouts in this file; do not create separate design files.
- Use the design itself as the source for module arrangement, pages, and nodes. Do not maintain a separate index. Keep the images and shaders referenced by the design alongside it.
- Read and modify `.pen` content through Pencil MCP, then save the source file.

## Project scope

Huoyan's general-purpose, open-source Android retail client provides AI recognition and checkout for **fresh produce, snacks, bakery products, and small-portion prepared dishes**.

**Package:** `cc.huoyan.retail`
**Min SDK:** 24 | **Target SDK:** 27 | **Compile SDK:** 33
**Java source and target compatibility:** 11 | **NDK:** 25.2.9519653

## Build with JBR-17

Gradle 7.5 does not support Java 21 and fails with class major version 65. Run Gradle with JBR-17. Replace the example path with your local JBR-17 Java home:

```bash
export JAVA_HOME=/path/to/jbr-17/Contents/Home
bash gradlew :app:assembleDebug        # Build the Debug APK
bash gradlew :app:testDebugUnitTest    # Run unit tests
```

Use `bash gradlew` because the wrapper may not have executable permission.

## Architecture and MVP boundaries

Pages use lightweight MVP, while the underlying singleton managers remain in place. Source packages follow business responsibilities:

```text
app/src/main/java/cc/huoyan/retail/
├── home/             # Home navigation
├── checkout/         # Checkout UI and Presenter; domain/ state and pricing; integration/ product loading and output
├── goods/            # Product management, repository, search, and filtering
├── learning/         # ProductLearningActivity / StudyActivity and sample image management
├── settings/         # Settings pages and authorization, connection, and device components
├── recognition/      # Recognition loop, result evaluation, and drawing; box and response models come from the JAR
├── pricing/          # Money in cents (long) with BigDecimal, pricing strategies, and mixed carts
├── feedback/         # Convert local product/checkout selections to JAR parameters; the JAR assembles and submits feedback
├── device/           # Camera, scale, printing, speech, and device information
├── data/             # In-memory SDK product snapshots (GoodsEntity); SharedPreferences utilities
├── sdk/              # Host configuration, thread wiring, installation UI, and image display
├── config/           # Scene / SceneDefaults / ModuleConfig
├── diagnostics/      # Logging, log pages, and display models
├── common/           # Shared utilities; ui/ contains common UI components
├── receiver/         # Boot and debug-injection broadcast entry points
└── RetailApplication.java / RetailConstants.java / ThreadAffinity.java (JNI)
```

See [Code structure](docs/code-structure.md) for detailed ownership and [SDK architecture](huoyan-sdk-client/ARCHITECTURE.md) for JAR packages and dependency rules; both references are in Chinese. Unit and device tests follow the same business packages. Read and modify product data only through the SDK shell API. The host must not maintain a product database, catalog cache, or product ID mapping.

### Checkout and pricing

- Pass recognized or manually selected products through `PricingMode.fromUnit(goods.getUnit())`. `COUNT` uses the quantity confirmation keyboard (`RecognizeCountKeyboardDialog`); `WEIGHT` uses the weighing confirmation flow.
- Call `PricingGate.check` before adding a product to the cart. If weighing is disabled, show the rejection message for a `WEIGHT` product and do not add it.
- `CheckoutRows` copies the displayed list into `SaleLine` objects. `CheckoutSession` exclusively owns session, weight, recognition lock, and print state, and publishes immutable `CheckoutState` values.
- `SalePricing` checks that products priced using the whole-scale weight share the same unit price and validates module switches. All totals go through `CartCalculator.computeTotal`. Use `BigDecimal` for business weights and `long` cents for unit prices and totals.
- `CheckoutPresenter` handles page actions and coordinates the list, product replacement, quantity/weight entry, and printing. It must not hold an Activity or controls. `MainActivity` implements `CheckoutPresenter.View` and owns controls, dialogs, devices, and lifecycle handling.
- `CheckoutGoodsLoader` handles asynchronous SDK product queries, merges results, and discards stale results. `CheckoutOutput` captures the feedback snapshot and performs printing and feedback submission. Leaving the page does not cancel output already submitted.
- `checkout.domain` may depend only on Java and `pricing`. Do not introduce Android types, database entities, `Bitmap`, or SDK singletons.

### Page MVP

- Home, product management, learning image lists, logs, and asynchronous settings flows have dedicated Presenters. The code structure reference above lists their responsibilities.
- Keep each Presenter and View in the same business package. Detach View references when the view is destroyed. Background executors must not retain an Activity or Fragment through method references. Complete submitted writes according to the existing business behavior; discard stale query callbacks using generation checks.
- `SettingsPageActivity` remains the navigation container; do not add forwarding layers for simple control bindings. `StudyActivity` retains camera preview and controls. `StudyPresenter` coordinates learning transactions and feedback, while `StudyLearningSource` owns Bitmap, SDK, and file operations. Batch image learning continues to use the existing ViewModel.

### Coding conventions

- Represent money as **cents (`long`) with `BigDecimal` calculations**; never use `double`. Represent weight in kilograms with `BigDecimal`.
- Write comments in Chinese. Document the responsibility and necessary thread/lifecycle constraints of every newly extracted class, interface, method (including constructors and private methods), and field. New public methods require Chinese Javadoc.
- Emit diagnostic markers only in `BuildConfig.DEBUG`: `[Toast] <message>` at the `ToastEx` output and `[Biz] key=value` for displayed values. The platform's `wait_log`, `assert_toast`, and `assert_value` checks depend on these markers.
- Do not change the cross-application AIDL service identifier `com.huoyan.DataService`; it is part of the SDK ecosystem.

## Tests

- Unit tests live in `app/src/test/java`, use matching packages and `XxxTest` names, and run with plain JUnit without Robolectric. Android classes in the mockable JAR do not initialize fields, so keep pure logic independent of Android classes.

## Signing

Do not commit production signing keys or passwords. `.gitignore` excludes `*.jks` and `*.keystore`. The sole exception is `signing/xinhai-debug-demo.jks`, which the user explicitly requested to publish. Its password is public; use it only for noncommercial experiments and test Release builds of the open-source version, never for commercial releases.

Debug builds use the locally generated debug key. Local and CNB release builds default to the fixed experimental key described in [Signing](signing/README.md) (Chinese). For production releases, configure all four properties in the untracked `local.properties`: `signing.storeFile`, `signing.storePassword`, `signing.keyAlias`, and `signing.keyPassword`. Do not put production key paths or default passwords in build scripts.

## UI conventions and sizing

### Dialogs and operation feedback

- Business confirmation dialogs must reuse `common.ui.AlertDialog`, usually through `DialogUtil.showAlertDialog`. Do not use system or AppCompat default title, body, and button styles. A Dialog hosting custom content is not itself a default confirmation dialog.
- Use the project's `LoadingDialog` while waiting, managed by the owning page or its child FragmentManager. Close it on success, failure, or an exception. Restore it after recreation according to the operation in progress, avoiding duplicate submissions or leftover overlays.
- Report operation results once through `ToastEx`. Choose success or failure icons from the actual response, never by matching message text.
- By default, pages, cards, dialogs, and other modules must not display request status, progress, success, failure, or results inline in the module that triggered the operation.
- Display a state inline only when explicitly required, such as whether the cloud service is enabled. This exception applies only to the specified state and does not permit additional progress or result displays.
- For cloud services, perform shop login and automatic queries silently. Use `LoadingDialog` when enabling the service, synchronizing products, or synchronizing the recognition library, and report results through `ToastEx`. Keep the explicitly required cloud service state visible. Confirmation before recognition library synchronization must use the project's `AlertDialog`.

### Dimensions and colors

- For `dp_N` resources in `values-sw720dp`, `N` is the numeric suffix and `px = N × 2 × density`. For example, `dp_10` is declared as `20dp`, which renders at 30 px at density 1.5 on C50D. Other resource qualifiers use their own declared values.
- Hardcoded dp values use `px = N × density`, or `N × 1.5` at density 1.5.
- Palette: `#F2F4F8` background / `#293A51` primary text / `#3F3EED` accent blue / `#FA6400` warning orange.

## APK naming

- `app/build.gradle` defines the APK filename: `huoyan-retail-{version}-{code}-{ts}-{type}.apk`.
