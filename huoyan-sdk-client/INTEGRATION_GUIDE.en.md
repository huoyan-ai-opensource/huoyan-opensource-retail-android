# Huoyan SDK JAR Integration Guide

[简体中文](INTEGRATION_GUIDE.md) | English

**JAR version: 2.0.0 | Android API 24+ | Java 11**

The Huoyan SDK is installed separately. Client applications call the SDK through the JAR's Java methods. The retail application source, provided separately, is called the “reference client.”

[Import the JAR](#import-jar) · [Connect and activate](#connect-sdk) · [Business workflows](#business-flows) · [Error handling](#error-handling) · [Reference client](#reference-client) · [Client API reference](#client-api) · [Verification and troubleshooting](#verification) · [Helper APIs](#helper-api)

## 1. Choose an integration approach

| Approach | When to use it | Suggested path |
| --- | --- | --- |
| Use the JAR in an existing application | You already have product, camera, or checkout functionality and need recognition | Import the JAR → connect and activate → submit an image → handle recognition results |
| Customize the reference client | You want to reuse the existing screens and retail workflows | [Run the reference client](#reference-client) → try learning and checkout → customize products, devices, and screens |

Before integrating, obtain a matching SDK installer and activation instructions from your integration contact, confirm device support, and register the client application's package name. The account is embedded in the delivered JAR; changing accounts requires a JAR for the new account.

Start with a fixed image to verify the workflow. If the device has already learned products, you can recognize them directly. On a new device, first photograph and learn a product as described in section 4.1, then recognize it.

“Learning” means submitting product images to the SDK. Each successfully learned image record is a “sample.”

The following Java examples are included with the JAR.

| Example | Purpose |
| --- | --- |
| [RecognitionIntegrationExample.java](samples/RecognitionIntegrationExample.java) | Connection, detection/recognition, learning, samples, feedback, and error handling |
| [ActivationStatusExample.java](samples/ActivationStatusExample.java) | Connect an unactivated device and submit activation once, alongside status and license queries |
| [SdkExample.java](samples/SdkExample.java) | Minimal connection and calls |
| [SessionIntegrationExample.java](samples/SessionIntegrationExample.java) | Optional shared connection and status queries |
| [FeedbackIntegrationExample.java](samples/FeedbackIntegrationExample.java) | Composed recognition feedback calls and file retention |

<a id="import-jar"></a>

## 2. Import the JAR

### 2.1 Dependencies and build configuration

Extract the delivery ZIP and copy all JARs from `libs/` into your application's `app/libs/`. Example configuration:

```groovy
android {
    compileSdk 33
    defaultConfig { minSdk 24 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
}
```

To import only the main JAR and let Gradle manage the other dependencies, use:

```groovy
dependencies {
    implementation files('libs/huoyan-sdk-client-2.0.0.jar')
    implementation 'org.java-websocket:Java-WebSocket:1.5.2'
    implementation 'com.alibaba:fastjson:1.2.47'
    implementation 'com.squareup.okhttp3:okhttp:4.10.0'
    implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20'
}
```

Choose one dependency approach. The main JAR embeds neither third-party classes nor Gradle dependency metadata. The full ZIP includes runtime dependencies; when using Maven artifacts, Gradle resolves them.

Preserve `raw/config`, the Java resource that supplies the account. Do not exclude it through `packagingOptions` or add a host resource with the same path.

The JAR includes obfuscation rules. To add them manually, place `consumer-rules.pro` in the app directory and add `proguardFiles 'consumer-rules.pro'` to each build type that enables obfuscation.

Java-WebSocket handles WS communication, FastJSON handles protocol JSON, and OkHttp downloads SDK APKs. Declare Kotlin JDK8 explicitly to align the OkHttp/Okio runtime.

Gradle includes transitive dependencies without direct declarations: Java-WebSocket brings in SLF4J API 1.7.25; OkHttp brings in Okio / Okio JVM 3.0.0; Kotlin JDK8 brings in JDK7, stdlib, stdlib-common, and JetBrains annotations.

### 2.2 Android configuration

Declare the following under the application's `<manifest>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="cc.huoyan.permission.SDK" />
<queries>
    <package android:name="cc.huoyan" />
</queries>
```

Register the third-party application's package name to obtain access permission. If you change the reference client's package name, register the new name as well. Configure camera, USB, and installation permissions as required by your application.

Applications targeting API 28+ must allow access to the relevant hosts when using cleartext WS or HTTP image URLs. The following network security configuration covers only local addresses; add the actual hosts for remote connections:

```xml
<!-- application attribute -->
<application android:networkSecurityConfig="@xml/huoyan_network_security">
    <!-- Application components -->
</application>
```

```xml
<!-- res/xml/huoyan_network_security.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

<a id="connect-sdk"></a>

## 3. Connect, activate, and prepare for recognition

### 3.1 Choose a connection configuration

AIDL communicates on the same device. WebSocket (WS) connects over a network.

| Huoyan SDK location | JAR configuration |
| --- | --- |
| Same device, Android API 27+ | `SdkConfig.aidl()` |
| Same device, Android API 24+ | `SdkConfig.websocket("ws://127.0.0.1:18086/ws")` |
| Another reachable device | `SdkConfig.websocket("ws://192.168.1.115:18086/ws")`; replace the IP address |

Use WebSocket on API 24–26. Remote mode does not require the Huoyan SDK to be installed on the client device. WS endpoints currently support `ws://host:port/path`; the port is required, and an omitted path defaults to `/ws`. `wss://`, URL credentials, query parameters, and fragments are not supported.

To discover devices, use `SdkDiscovery` in section 10.1.

### 3.2 Recognize an image on an activated device

For a device that has not been activated, [complete activation](#activation) first. The example below performs one recognition request; reuse the connection for continuous recognition.

Add [RecognitionIntegrationExample.java](samples/RecognitionIntegrationExample.java) to your project and run it on a background thread. Supply a correctly oriented image as `bitmap`:

```java
SdkConfig config = android.os.Build.VERSION.SDK_INT >= 27
        ? SdkConfig.aidl()
        : SdkConfig.websocket("ws://127.0.0.1:18086/ws");

try (HuoyanSdkClient client = RecognitionIntegrationExample.openReady(context, config)) {
    SdkImage input = client.image(bitmap);
    List<RecognitionItem> items = RecognitionIntegrationExample.recognize(client, input);
    // Look up your product by item.getClazz(), then display it or continue the workflow.
}
```

Import `cc.huoyan.sdk.client.*`, `cc.huoyan.sdk.client.model.RecognitionItem`, `java.util.List`, and `example.RecognitionIntegrationExample`. The caller must handle `IOException`, `InterruptedException`, and local argument errors, restoring the thread's interrupt flag when interrupted.

The sample helper `openReady()` follows the sequence below, checking recognition capability and model status. It throws on timeout or an unready model. The sample helper `recognize()` unwraps the JAR's `RecognizeResult` into an item list.

```text
HuoyanSdkClient.create(context, config)
  → connect() asynchronously
  → isConnected() confirms the connection
  → initialize() and check the result
  → getStatus() queries model status
  → SdkStatus.modelReady(status) returns true
  → recognize() / learn() / batchLearn()
```

`isConnected()` confirms communication; `initialize()` does not confirm activation; `READY` / `OFFLINE` confirms SDK startup only. **Use `SdkStatus.modelReady(status)` to gate recognition and learning.** See section 3.4 for offline conditions.

`initialize()` reads the host APK's `versionName` automatically; no manual entry is needed, and no version is fabricated if package information is unavailable. First initialization, initialization after reconnection, and explicit retries all send it through AIDL `init(String appVersion)` or WS `params.app_version`. The SDK stores it and reports it in subsequent heartbeats; the host needs no separate reporting request. Use a matching SDK that supports this signature.

After automatic reconnection, the host must still call `initialize()` and recheck model status; see section 6.1.

<a id="activation"></a>

### 3.3 Activate and switch models

For first-time activation, select a model using the activation code. To switch models on an activated device, query the current device's licenses.

| Step | Calls and handling |
| --- | --- |
| Connect | `create()` → `connect()` → wait for `isConnected()`; the activation screen requires a communication connection, not a ready model |
| Initialize and query | Call `initialize()` and check code/reason, then read `getStatus()` and `getCertInfo()`; use the sdkState rules below to decide whether to continue activation |
| Obtain an activation code | Let the user enter a code or extract the serial field (activation code) from a QR code |
| Select a model | For normal activation, call `queryLicenseModels(activationCode)` in the background; for switching, call `queryDeviceModels()`. Display the returned names in their returned order and retain the selected item's `modelCode` |
| Submit activation | Call `activate(activationCode, modelCode)` once on a background thread; do not submit if selection is canceled |
| Show preparation progress | Query `getDownloadProgress()` and `getStatus()` on another background thread while activation runs |
| Complete | Check the activation result; allow recognition/learning after a successful status query confirms modelReady=true, then refresh license information |

On the activation screen, use `openConnected(context, config)` from [ActivationStatusExample.java](samples/ActivationStatusExample.java). The `openReady()` example in section 3.2 closes the connection and throws if the model is not ready, so it is unsuitable for first-time activation.

After a successful status query, guide the user to activate when sdkState is `NOT_ACTIVATED`. For `REGISTERED`, also check license information. Handle other errors using code/reason. If the result is unknown, verify it as described in section 6.2 instead of resubmitting.

In the following example, `client` is connected, `activationCode` is the user-confirmed input, and `modelCode` is the selected item's `getModelCode()`:

```java
BaseResult result = ActivationStatusExample.activateOnce(client, activationCode, modelCode);
if (result == null || result.getCode() == HuoyanSdkClient.RESULT_UNKNOWN) {
    // Activation result unknown: refresh status and license information; do not auto-resubmit.
} else if (result.getCode() != 0) {
    // Show result.getCode()/getReason(), handle the error, then decide the next step.
} else {
    // Activation call succeeded; keep checking model status before enabling recognition.
}
```

The example formats the activation code with `activation.ActivationCode.normalize(input)` before calling `client.activate()`. Codes that contain 16 characters after removing hyphens are grouped into blocks of four. Scanned serial values of other lengths are passed through unchanged. `validManual(input)` checks only the length of manually entered codes; it neither validates the license nor rejects scanned serial values of other lengths. For JSON QR codes, extract serial before submission.

The two model-query APIs correspond to SDK shell 0.1.3 (AIDL api_version=3). `LicenseModels.code/reason` describes the query result. Each entry in `models` contains `modelCode/modelName/chip/current`; the number, names, and order of models are not fixed. `current=true` identifies the current model.

To switch models, call `activate()` with the serial returned by `getCertInfo().getActivationSerial()` on the same client and the selected modelCode. If the serial is missing, ask the user to enter or scan it after model selection, retaining the selected model for submission. Selecting the current model or canceling performs no write. Model switching is independent of saving local configuration.

`activate()` returns `BaseResult` (code/reason); query model status and certificate information separately. `getDownloadProgress()` returns model download progress:

| `DownloadProgress` method | Type/unit | Meaning |
| --- | --- | --- |
| `getCode()` / `getReason()` | int / String | Whether this progress query succeeded, and the reason |
| `getDownloaded()` | long, bytes | Downloaded size |
| `getTotal()` | long, bytes | Total size; 0 means unknown |

The reference client waits 2 seconds after each progress query before querying again. If a query fails, continue checking the activation result and status. Even when download progress reaches 100%, wait for modelReady=true.

### 3.4 Query Huoyan SDK runtime status

`client.getStatus()` returns `StatusSnapshot` for readiness checks and troubleshooting. **Check that the response is non-null and code=0 before reading status fields.**

| Method | Type | Meaning and usage |
| --- | --- | --- |
| `getCode()` | int | Result code for this query; 0 means success |
| `getReason()` | String | Reason for query failure; distinct from the SDK's current recentError |
| `getSdkState()` | String | Current runtime state; see the table below |
| `getModelReady()` | Boolean | Whether the model is ready; use `SdkStatus.modelReady()` to also check offline license and key facts before recognition/learning |
| `getDeviceId()` | String | Huoyan SDK device identifier for device information and troubleshooting; not a client-defined shop ID |
| `getModelVersion()` | String | Current model version, not the Huoyan SDK application version |
| `getCertificateValid()` | Boolean | Whether the license certificate is valid; must explicitly be true in OFFLINE mode to allow local recognition/learning |
| `getCertificateValidUntil()` | String | Certificate expiry text; use CertInfo timestamps for date calculations |
| `getTrustLevel()` | String | Device trust level reported by the Huoyan SDK; include it when troubleshooting |
| `getKeyLost()` | Boolean | Whether the key required for device authorization is lost; if true, investigate together with sdkState |
| `getBackendConnected()` | Boolean | Whether the Huoyan SDK can reach its server; use isConnected() to check the client's connection to the SDK |
| `getBusy()` | Boolean | Whether an operation is running; use it to avoid duplicate tasks |
| `getBusyOperation()` | String | Current operation name, possibly empty; for diagnostics, not text matching to determine behavior |
| `getStateUpdatedAt()` | Long, timestamp in milliseconds | When the status was updated, not when this query started |
| `getRecentError()` | String | Most recent unresolved error, possibly empty; for troubleshooting |

A null Boolean/Long means information is unavailable; do not interpret it as false/0. If a status query fails, pause new requests that depend on readiness instead of reusing a previous “ready” result. busy does not replace modelReady. See section 6.1 for operation sequencing.

`getStatus()` is a one-time query. For ongoing observation, poll from the host application or use `SdkStartupController` in section 10.2.

#### sdkState values

The SDK manages trust levels, keys, and license updates. Use these states to decide the next action; do not display internal diagnostic information directly to users.

| Value | Meaning | Suggested handling |
| --- | --- | --- |
| `NOT_INIT` | Not initialized | Initialize once and check the result |
| `INITIALIZING` | Initialization in progress | Wait and query status; avoid repeated initialization |
| `REGISTERED` | Registered; activation/preparation is incomplete | Query license information and guide activation or wait according to the actual state |
| `NOT_ACTIVATED` | Not activated | Prompt for an activation code |
| `READY` | Huoyan SDK has started | Also confirm modelReady=true |
| `OFFLINE` | Local functionality is available; device identity has not yet been reconciled with the cloud | `SdkStatus.modelReady(status)` requires modelReady=true, certificateValid=true, and keyLost not true; recognition/learning can then proceed without waiting for internet connectivity |
| `MODEL_PREPARING` | Model preparation in progress | Show download progress or a preparation prompt and continue querying |
| `MODEL_NOT_READY` | Model is not ready | Pause recognition/learning and investigate recentError |
| `STOPPED` | Huoyan SDK has stopped | Recheck status after restoring the connection/service |
| `CLEANUP_PENDING` | Data cleanup is incomplete | Wait for cleanup; initialization success alone does not allow business operations to resume |
| `KEY_ROTATION_PENDING` | Device authorization update awaiting approval | Wait for approval and contact your integration contact |
| `KEY_ROTATION_RESTORING` | Device authorization update approved; restoration in progress | Wait for restoration and recheck readiness |
| `KEY_ROTATION_REJECTED` | Device authorization update rejected | Contact your integration contact; do not submit activation in a loop |
| `KEY_ROTATION_RATE_LIMITED` | Device authorization updates requested too frequently | Wait for the limit to clear; avoid frequent retries |
| `CERTIFICATE_EXPIRED` | License certificate expired | Check license and renewal information; contact your integration contact |
| `CERTIFICATE_INVALID` | License certificate invalid | Check device and license configuration; contact your integration contact |
| `BACKEND_UNAVAILABLE` | Huoyan SDK backend temporarily unavailable | Check network and service availability, then query again after recovery |
| `STARTUP_FAILED` | Startup failed | Investigate recentError and recheck status after resolving it |
| `DISABLED` | Huoyan SDK disabled | Contact your integration contact about licensing or usage policy |
| null / an unrecognized new value | Status is unconfirmed or newly introduced | Retain the value for diagnostics; do not assume READY; check modelReady |

`SdkStatus.shellStarted()` recognizes both `READY` and `OFFLINE`. While offline, keep querying status; the SDK reconciles device identity when online and returns to READY without repeated host initialization. `OFFLINE` does not mean client–SDK disconnection or certificate expiry. Activation, model switching, feedback, uploads, synchronization, and product writes after cloud services are enabled still follow their cloud results. Do not automatically disable cloud services or turn failures into local successes because the SDK is offline.

### 3.5 Query license information and the SDK version

Read the SDK application version from the `Capability` returned by `client.getCapabilities()`. This does not depend on activation or model readiness. Handle version and license queries independently; an unavailable certificate must not prevent version display.

| `Capability` method | Type | Meaning and unavailable values |
| --- | --- | --- |
| `getSdkVersionName()` | String | SDK application version name; `unknown` means unavailable |
| `getSdkVersionCode()` | int | SDK application version code; `-1` means unavailable |

Show a placeholder if the response or version is missing; do not substitute modelVersion. Error responses may still carry version information. Read capability flags only when code=0; see section 3.6.

`client.getCertInfo()` returns `CertInfo`, including license type, activation time, and expiration time.

| Method | Type/unit | Meaning and unavailable values |
| --- | --- | --- |
| `getCode()` / `getReason()` | int / String | Query result; read certificate fields only after success |
| `getActivationSerial()` | String | Code used for activation; may be empty before activation |
| `getLicenseType()` | String | License type as returned; may be empty, and must not be assumed to come from a fixed set |
| `getModelCode()` / `getModelChannel()` | String / String | Licensed model code and channel |
| `getActivatedAt()` | long, timestamp in seconds | Activation time; 0 means no data |
| `getIssuedAt()` | long, timestamp in seconds | Certificate issuance time; 0 means no data |
| `hasRenewedAt()` / `getRenewedAt()` | boolean / long, seconds | Check hasRenewedAt before using the most recent renewal time |
| `hasNextRenewAt()` / `getNextRenewAt()` | boolean / long, seconds | Check hasNextRenewAt before using the next renewal time |
| `getExpiresAt()` | long, timestamp in seconds | Expiry time; 0 may mean no data or a perpetual license. Display “Expiration time not provided,” not “Valid forever” |

CertInfo times use **seconds**; multiply by `1000L` for Java Date and format in the client's time zone. StatusSnapshot's stateUpdatedAt uses **milliseconds**. The SDK handles license renewal; the JAR only queries renewal information.

### 3.6 Query supported capabilities

When `Capability.code=0`, use `isDetect()`, `isRecognize()`, `isItemLearn()`, `isItemForget()`, and `isItemQuery()` to check the corresponding capabilities. `getApiVersion()` returns the API version. `isSharedMemoryImage()` indicates image transport support, normally handled by the JAR. Also check model status before executing business operations.

### 3.7 Prepare input images

| Available image | Call |
| --- | --- |
| Correctly oriented Bitmap | `client.image(bitmap)` or directly `client.recognize(bitmap)` |
| RGB888 bytes | `SdkImage.rgb888(bytes, width, height)` |
| JPEG/PNG bytes | `SdkImage.encoded(bytes, width, height, format)` with `FORMAT_JPEG` / `FORMAT_PNG` |
| JPEG/PNG with unknown dimensions | `SdkImage.encoded(bytes, 0, 0, format)` |

RGB888 uses three bytes per pixel in red, green, blue order, without row padding. Total length must equal `width × height × 3`; convert NV21/YUV first. The host application handles camera capture and region-of-interest cropping.

The image limit is 32 MiB. Learning APIs decode JPEG/PNG into RGB888, and the decoded image must also fit within the limit. If AIDL cannot establish large-image transport, its fallback limits are 1 MiB for RGB and 256 KiB for JPEG/PNG; exceeding them returns `IMAGE_UNAVAILABLE`. Use only the image region and resolution actually required.

`SdkImage` copies input bytes. Do not overwrite or recycle a Bitmap before conversion finishes; the JAR does not recycle caller-owned Bitmaps. `client.image(bitmap)` converts to RGB888 for AIDL and JPEG at quality 90 for WS. Perform conversion in the background as well.

Prefer rotating the image to the correct orientation before submitting it with `rotate=0`. `recognize(image, rotate)` accepts only 0/90/180/270. With a nonzero rotation, verify returned box positions against the preview image.

<a id="business-flows"></a>

## 4. Compose business workflows

### 4.1 Photograph and learn a new product

Learning, recognition, queries, and forgetting all use the product's original PLU string as `clazz`. It must contain 1–50 ASCII letters or digits. Preserve case and leading zeros: `A001`, `a001`, and `001` are different classes. Do not convert it to a long or map it to a product ID. Sample UIDs remain long values.

```java
String clazz = "A001"; // Original product PLU.
ItemLearnBatch result = RecognitionIntegrationExample.learnCategory(client, input, clazz);
List<Long> uids = RecognitionIntegrationExample.successfulUids(result);
// Save successful UIDs and retain each box result to report partial success or failure reasons.
```

| Method | Suitable images | Successful result |
| --- | --- | --- |
| `learn(image, clazz)` | A single object; the Huoyan SDK learns the box with the highest detection score | `LearnResult` with code=0 and uid>0 |
| `batchLearn(image, clazz)` | Multiple objects of the same class in one image; all boxes are learned into that class | `ItemLearnBatch`; check code=0 and uid>0 for each box |
| `detect(image)` | A box preview is needed before learning | Boxes in `DetectResult`; the learning response determines actual learning results |

Batch learning can partially succeed. Overall code=0 with an empty box list means no objects were detected. If only 2 of 3 boxes succeed, retain those 2 UIDs and report the remaining failure reason; do not automatically relearn the whole image.

Learned samples are immediately available for recognition; no SDK save call is needed. Record successful UIDs in the client so you can undo learning with `forgetSample(uid)`.

### 4.2 Checkout recognition and product matching

Match recognition classes to business products. After the user confirms or changes the product, calculate the price by count or weight.

| Recognition field | Client usage |
| --- | --- |
| `getClazz()` | Primary class code string; use it to find the corresponding product |
| `getCandidates()` | Candidate classes for manual selection |
| `getLeft/Top/Right/Bottom()` | Pixel box in the input image for positioning, drawing, or cropping |
| `getDistance()` | Recognition distance, not an accuracy percentage; retain it during initial integration rather than guessing thresholds |
| `getRank()` / `getSquare()` | Additional recognition values; ignore them unless needed |

`DetectResult.getBoxes()` uses `x/y/w/h`; each detection box also has a `score`. Recognition boxes use `left/top/right/bottom`. An empty recognition result is valid. Multiple boxes may contain the same product class; do not remove items just because their clazz values match. If no matching product is found, retain the code and allow manual selection.

| Identifier | Meaning | Usage |
| --- | --- | --- |
| `clazz` | Recognition class | Use the original product PLU string for learning, recognition, queries, and forgetting; see section 4.1 |
| `uid` | One learned sample | A positive long returned by the Huoyan SDK for locating and deleting samples |
| Business product code / PLU | The client's product identifier | Use directly as clazz, preserving case and leading zeros |
| `goodsId` | Huoyan SDK product ID | Used by optional product APIs or feedback; distinct from clazz, uid, and the local database primary key |

After switching shops or SDK devices, match recognition results against the current products' PLUs.

For continuous recognition, take the latest camera image after the previous image has finished processing to avoid a queue. The host application controls result acceptance, pausing during manual confirmation, and pricing. When changing a product, retain both the original result and the final choice for feedback.

### 4.3 View and delete samples

| Requirement | Call |
| --- | --- |
| View learned classes | `listClasses(page, size)` |
| View samples for one class | `listImages(clazz, page, size)` |
| Delete one incorrect sample | `forgetSample(uid)` |
| Remove all samples from a class | `forgetClass(clazz)` |

In class pages, `clazz[]` and `uidCount[]` correspond by index, as does a nonempty `thumbnails[]`. Pages start at 1. Check the returned page number and total page count. Retain a sample's UID even if its image is missing so the sample can still be deleted.

Load sample images using the complete returned `imageUrl`; do not construct a port yourself. Show a placeholder when `imageAvailable=false`. URLs may change after the Huoyan SDK restarts, so query them again.

`LearnedClazzPages`, `LearnedImagePages`, and `SampleImages` in section 10.5 provide pagination, image lookup by UID, and URL caching, including cancellation and cache invalidation behavior.

### 4.4 Submit recognition feedback

Each recognized object corresponds to one `FeedbackItem`. `detect` preserves that box's original match and candidates; manual actions must not overwrite it. `result` holds the final product and its source, `sdk` or `manual`. When an object is deleted, submit `{}` as `result`, retaining the original recognition and detection box even if the product had previously been changed.

```java
List<FeedbackItem> items = FeedBackUploadHelper.prepareDetectFeedback(
        detections, imageWidth, imageHeight);
FeedbackSubmission submission = FeedbackSubmission.fromFile(photo,
        Feedback.FEEDBACK_TYPE_NORMAL, items,
        FeedbackSubmission.PhotoRetention.KEEP);
FeedBackUploadHelper.submit(client, submission, worker, completion);
```

`detections` is a `List<FeedbackDetection>` containing the original product, final product, deletion flag, original match distance, SDK candidates for the same box, and coordinates in the original uploaded image. Use `FeedbackProduct(goodsId, name, plu)` for products, identified by PLU. If no SDK product ID is available, goodsId may be null; do not supply the host database's primary key.

`prepareDetectFeedback()` places the original match and its distance first in detect, then appends same-box candidates and their distances in first-occurrence order, deduplicating by the exact clazz string. Product names and distances are not deduplication keys. Do not mix candidates from other boxes. Keep the original match even if there are no candidates. Preserve distance to three decimal places; do not substitute the detection score.

| User action | item_type | result |
| --- | --- | --- |
| Confirm the original product | 0 | Original SDK product |
| Change product A to B | 1 | Final selection B |
| Delete an object, including after changing its product | 2 | Empty object `{}` |

The constructor's `feedbackType` accepts only 0/1. The JAR calculates the submitted value from items: 0 when all are unchanged, 1 when any item is modified or deleted. Therefore, passing `FEEDBACK_TYPE_NORMAL` as in the example still supports product changes and deletions.

The SDK automatically uploads learned sample images and registers them in the cloud. The client must not submit learning feedback or call sample-image HTTP endpoints directly.

Feedback images must be JPEG/PNG and no larger than 8 MiB. `KEEP` retains the source file. `DELETE_ON_SUCCESS` deletes a dedicated temporary image only after successful submission and only if the file has not changed. Keep the file on failure or an unknown result. Reuse the same `FeedbackSubmission` on retry to retain its requestId and upload URL; calls after success do not resubmit. Do not automatically retry unknown results; see section 6.2.

### 4.5 Match image and box coordinates

Draw boxes using coordinates from the recognition input image. Feedback boxes must use coordinates in the original uploaded image. When the reference client recognizes a crop but uploads the original, restore the crop offset:

```text
Original image: 1920×1080; crop starts at (200,100)
Box in recognition input: (100,80)–(340,260)
Box in original image: (300,180)–(540,360)
Feedback: x=300, y=180, width=240, height=180
```

Feedback width/height describe the box, not the whole image. If scaling, rotation, or mirroring is involved, retain and reverse the image transform. Preview rendering must also account for view scaling and padding. Do not apply boxes from one frame to another image.

## 5. Shops, products, and other features

### 5.1 Shop binding

Bind the device to a shop as required by your project when associating a device with a shop, synchronizing products, or submitting feedback. If the Huoyan SDK returns a not-bound error, complete binding before continuing the affected operation.

```java
BindingResult binding = client.bindDevice("erp-shop-007");
// Optional shop details: client.bindDevice("erp-shop-007", "Shop One", null)
```

Supply a stable `shopId` that does not collide with another shop. Letters, leading zeros, and UUIDs are supported. The delivered JAR supplies the account; callers cannot override it. After successful binding, store these distinct values:

| Field | Meaning |
| --- | --- |
| `binding.getRequest().getShopId()` | External shop identifier supplied by the client |
| `binding.getShopId()` | Shop ID returned by the Huoyan SDK |
| `binding.getAccountId()` | Account ID actually used for binding |
| `binding.getShopAddress()` | Shop address from the backend's `address` |
| `binding.getShopPhone()` | Shop telephone from the backend's `shop_phone` |

Missing address and phone values are returned as empty strings. Save them together with binding ownership after success; empty values must also overwrite the previous shop's details. Do not substitute a contact's mobile number for the shop telephone.

Retain the existing shop information if binding fails. When several systems share one account, use different prefixes for shop IDs to avoid collisions. Reconnection does not automatically rebind the shop.

### 5.2 Product data and synchronization

If you already have a product system, you can continue using your own database and use each product's original PLU directly as clazz. The reference client queries and modifies products through the Huoyan SDK product APIs, without a separate product database or persistent product mapping. See section 8.4 for creation, query, update, and synchronization methods.

`GoodsSave` constructor argument order:

```java
new GoodsSave(name, plu, image, ingredientsList, shelfLife, pinyin,
        category, categoryName, unit, saleMode, tareWeight, price, sort, status);
```

name/plu are required for creation. For updates, null optional fields mean “leave unchanged”; numeric 0 is an actual value. `price` is a Long in integer cents, and `tareWeight` is a Long in grams. saleMode is 0 for sale by count and 1 for sale by weight. status is 1 for on sale and 2 for off sale. Lower sort values appear first. Agree on business fields such as category for your integration project.

For integrations with their own product database, `listGoods(syncTimestamp, current, size)` queries server products. Pass syncTimestamp=0 for all products. Save the returned syncTimestamp only after storing all data from that synchronization; use it next time to query subsequent changes. `downloadGoods(size)` synchronizes products into the Huoyan SDK's local data. It neither writes to the client's database nor means the images have been learned.

For batch updates or deletions, check each item's result even when the overall code is 0. For product synchronization, check the final phase, failure reasons, and counts of additions, deletions, and updates. Most product methods use String IDs; `downloadImagesAndLearnByGoodsId` uses an array of positive long values. Check the range before converting.

### 5.3 Download and learn images; back up and restore data

| Requirement | Method sequence |
| --- | --- |
| Download and learn existing cloud product images | `downloadImagesAndLearn()` → query download/learning progress independently |
| Update image samples for selected products | `downloadImagesAndLearnByGoodsId(goodsIds)`; replaces old samples in the affected classes |
| View failed images | `getFailedImages()`; only code=0 with an empty list means no failure records |
| Back up recognition data | `backupRecognitionData()` / `getRecognitionDataInfo()` |
| Restore recognition data | `restoreRecognitionData()` → recheck model status and refresh samples |

Image download and learning waits for final statistics. Query progress on another background thread. Check `ImageFlowProgress.outcome()` and also show download failure, learning failure, and skipped counts. code=0 alone does not mean every image was learned successfully. IDLE does not mean successful completion.

Pause recognition and learning before restoring data. After a successful restore, confirm model availability again. A third-party client's own product database is not included in recognition data backups and must be managed by that client.

### 5.4 Share a connection across screens

Use `SdkSession` to share a connection and `SdkStartupController` to observe startup status. The host supplies background and callback Executors. See [SessionIntegrationExample.java](samples/SessionIntegrationExample.java) for usage and section 10.2 for connection recreation and stopping observation.

### 5.5 Download and install the Huoyan SDK from the client

Use `SdkConfig.requiresLocalShell()` to determine whether a local installation is required, `SdkShell.installedVersion()` to query the installed version, and `SdkApkDownloader` to download the matching installer. See section 10.8 for callbacks, cancellation, and file cleanup. Confirm the device version and download URL with your integration contact.

<a id="error-handling"></a>

## 6. Threads, state, and error handling

### 6.1 Threads and lifecycle

SDK requests block while waiting for a result and must run in the background, as must image conversion. Switch to the main thread for UI updates, and discard stale results for closed screens or products that are no longer selected.

Serialize operations that modify samples for the same product or switch shops. Pause recognition during batch learning or data restoration. Run long operations and progress queries on different background threads.

Reuse one connection for continuous recognition. Stop submitting new images when leaving the screen. Other screens may continue using a shared connection; call `client.close()` when it is truly no longer needed. A closed client instance cannot reconnect; create a new one. Submitted learning, binding, deletion, and feedback operations are not automatically undone by leaving the screen, interrupting a thread, or closing the connection.

Automatic reconnection restores communication only; it does not call `initialize()`. After confirming reconnection, the host must initialize in the background and recheck model status before resuming recognition. Alternatively, restart `SdkStartupController` to perform this workflow. Its observation ends once the model is ready, so call stop before start to observe again.

### 6.2 Results and handling rules

Check for a non-null result and code=0 before reading state, lists, or per-item results. SDK version information is an exception: it may be read from error responses (section 3.5). Retain error code/reason; add logging with `config.withLog((tag, message) -> ...)`.

| Result | Handling |
| --- | --- |
| code=0 | Request succeeded; still check modelReady, sample UID, each batch item's result, or workflow statistics |
| null / `RESULT_UNKNOWN` (40009) | Success is not yet known; verify learning, deletion, activation, and similar operations before retrying; do not automatically resend |
| `LOCAL_DISCONNECTED` (-2147483648) | This request was not sent; wait for a connection. Previously sent requests may still have executed |
| `NOT_EXECUTED` (40010) | This request was not executed; retry after a delay with a bounded retry count |
| `IMAGE_UNAVAILABLE` (-2147483647) | This image request was not sent; adjust the image or check the connection mode |
| -1001 / -1002 | Not bound / operation not allowed; check shop binding or investigate configuration using reason |
| -1003 / -1004 | Storage failure / configuration save failure; check device storage and Huoyan SDK status |
| Other nonzero values or argument errors | Retain the reason, correct the input, or investigate the affected feature |

After a learning timeout, query samples to verify the outcome before relearning. Ordinary failed queries can be repeated. A subsequent SDK upload failure does not invalidate an already returned successful UID.

See section 3.4 for the full runtime state list and handling. You can also use `SdkStatus.issue(initCode, status)` to classify an issue and display your own wording.

### 6.3 Wait limits

| WebSocket operation | Current wait limit |
| --- | --- |
| Ordinary queries except the model queries below; recognition, detection, single-object learning, forgetting, product operations/synchronization | 35 seconds |
| Model queries `queryLicenseModels()` / `queryDeviceModels()` | 125 seconds |
| Initialization, activation, batch learning, recognition data backup/restore | 125 seconds |
| Image upload, feedback | 65 seconds |
| Shop binding | 60 seconds |
| Downloading and learning images for all or selected products | 30 minutes |

AIDL calls have no timeout that can forcibly terminate them. A wait ending does not mean the operation was not executed. If the total is unknown, show only a processing indicator; calculate a percentage only when total>0.

<a id="reference-client"></a>

## 7. Run and customize the reference client

The reference client covers fresh produce, snacks, bakery products, and small-portion prepared dishes. First run through “add product → photograph and learn → recognize at checkout → manually confirm,” then customize screens or extract JAR calls.

The reference client already includes JAR dependencies; do not import them again using section 2. Set the Gradle JDK to JBR-17 and configure the Android SDK. Run `bash gradlew :app:assembleDebug` from the source root; the APK is generated under `app/build/outputs/apk/`. Choose local or remote SDK deployment as described in section 3.1, install the client, and confirm its connection mode in Settings. Confirm device selection and camera, scale, and printer wiring for your project.

The app build uses `app/src/main/libs/huoyan-sdk-client-2.0.0.jar` and does not depend on the SDK module. After modifying SDK source, first run `bash gradlew :huoyan-sdk-client:updateAppSdkJar` to replace this JAR, including its embedded account configuration, then build the app.

The Java paths below are relative to `app/src/main/java/cc/huoyan/retail/`.

| Feature | Source location (relative to the prefix above) | Contents |
| --- | --- | --- |
| Activation and license panel | `settings/PanelFragment.java`, `PanelPresenter.java`, `PanelSdkSource.java` (same directory) | Manual/QR activation, progress, status, and certificate display |
| Connection configuration and home status | `sdk/SdkChannelManager.java`, `home/HomeActivity.java` | Save connection configuration, share the JAR client, and show recognition availability |
| Local or remote connection selection | `settings/SdkConnectionFragment.java`, `SdkConnectionPresenter.java` (same directory) | Select connection mode, discover and switch devices |
| Product-to-class matching | `goods/SdkClassMapping.java`, `goods/` | Validate PLUs and use them unchanged as clazz; read products through SDK APIs |
| Photograph and learn new products | `learning/StudyActivity.java`, `StudyPresenter.java`, `StudyLearningSource.java` (same directory) | Image preparation, learning calls, successful UIDs, and sample display |
| Product learning list | `learning/ProductLearningActivity.java`, `ProductLearningPresenter.java` (same directory) | Select products, start learning, and view samples |
| Checkout recognition | `checkout/MainActivity.java`, `recognition/RecognitionLoop.java` | Camera input, call timing, product matching, and manual confirmation |
| Feedback after manual correction | `feedback/FeedbackInputs.java`, `checkout/integration/CheckoutOutput.java` | Snapshot the user's product selections at submission, then submit feedback |
| Sample image display | `sdk/SdkGoodsImages.java` | Use returned URLs and caching; query again after images change |
| Camera, scale, and printer devices | `device/` | Replace device integration to match actual hardware |

The reference client uses LoadingDialog for waiting and ToastEx for operation results; screens do not add internal diagnostic status displays.

<a id="client-api"></a>

## 8. HuoyanSdkClient API reference

The tables below list the public methods of `HuoyanSdkClient`. Types are in `cc.huoyan.sdk.client` and its `model` package. Run methods that request the Huoyan SDK on a background thread. See section 10 for public helper APIs.

### 8.1 Connection, status, and binding

| Method | Returns | Key points |
| --- | --- | --- |
| `HuoyanSdkClient.create(Context, SdkConfig)` | `HuoyanSdkClient` | Create a connection object using an application Context |
| `connect()` | void | Connect asynchronously; repeated calls do not duplicate connections; supports automatic reconnection |
| `isConnected()` | boolean | Communication status only |
| `close()` | void | Create a new instance before using the SDK again |
| `initialize()` | `BaseResult` | Automatically includes the host APK's versionName; readiness must still be queried |
| `getCapabilities()` | `Capability` | Query supported features and the SDK application version; version is independent of activation and defaults to unknown/-1 if unavailable |
| `getStatus()` | `StatusSnapshot` | code, sdkState, modelReady, busy, recentError, etc. |
| `getCertInfo()` | `CertInfo` | License and certificate information |
| `getDownloadProgress()` | `DownloadProgress` | Model download progress in bytes; do not calculate a percentage when total=0 |
| `queryLicenseModels(activationCode)` | `LicenseModels` | Query using the entered activation code for normal activation; an empty code returns an error |
| `queryDeviceModels()` | `LicenseModels` | Query models supported by the current device without arguments |
| `activate(String activationCode, String modelCode)` | `BaseResult` | Continue checking model status after activation |
| `bindDevice(String shopId)` | `BindingResult` | External shop identifier; account is embedded in the JAR |
| `bindDevice(String shopId, String shopName, String shopNo)` | `BindingResult` | Include optional shop details |

### 8.2 Images, recognition, learning, and samples

| Method | Returns | Key points |
| --- | --- | --- |
| `image(byte[] rgb, int width, int height)` | `SdkImage` | Copy RGB888 bytes for this image |
| `image(Bitmap bitmap)` | `SdkImage` | RGB888 for AIDL; JPEG90 for WS |
| `jpegImage(Bitmap bitmap)` | `SdkImage` | JPEG90 |
| `photoImage(byte[] bytes, int format)` | `SdkImage` | JPEG/PNG with unknown dimensions |
| `detect(SdkImage)` / `detect(Bitmap)` | `DetectResult` | Detection boxes only; no class recognition |
| `recognize(SdkImage)` / `recognize(Bitmap)` | `RecognizeResult` | Correctly oriented image |
| `recognize(SdkImage, int rotate)` | `RecognizeResult` | rotate accepts only 0/90/180/270 |
| `learn(SdkImage, String clazz)` / `learn(Bitmap, String clazz)` | `LearnResult` | One sample UID; clazz is a 1–50-character ASCII letter/digit string |
| `batchLearn(SdkImage, String clazz)` | `ItemLearnBatch` | Learn all objects into the same class; check code/uid individually |
| `listClasses(int page, int size)` | `ItemClazzList` | 1-based; 0 uses defaults of 1/100 |
| `listImages(String clazz, int page, int size)` | `ItemClazzImagePage` | UID, image availability, and URL |
| `forgetSample(long uid)` | `BaseResult` | Delete one sample |
| `forgetClass(String clazz)` | `BaseResult` | Delete all samples in a class |

### 8.3 Feedback, cloud learning, and maintenance

| Method | Returns | Key points |
| --- | --- | --- |
| `uploadPhoto(SdkImage)` | `PhotoUploadResult` | Upload JPEG/PNG and obtain photoFileUrl |
| `submitFeedback(Feedback)` | `BaseResult` | Includes the uploaded URL and feedback items |
| `getCloudStatus()` | `CloudStatus` | Query whether cloud services are enabled |
| `setCloudStatus(boolean enabled)` | `CloudStatus` | The current Huoyan SDK cannot disable cloud services once enabled; check the returned result |
| `setBaseUrl(String url)` | `BaseResult` | Server address used by the Huoyan SDK for uploads, not the WS endpoint; configure for the actual deployment |
| `downloadImagesAndLearn()` | `ImageFlowProgress` | Waits for the final result |
| `downloadImagesAndLearnByGoodsId(long[] goodsIds)` | `ImageFlowProgress` | Huoyan SDK product IDs; relearning replaces affected old samples |
| `getImageDownloadProgress()` / `getImageLearnProgress()` | `ImageFlowProgress` | Query on a separate thread |
| `getFailedImages()` | `FailedImagesResult` | Only code=0 with empty items means no failure records |
| `backupRecognitionData()` | `BaseResult` | Back up the recognition data managed by the Huoyan SDK |
| `getRecognitionDataInfo()` | `RecognitionDataInfo` | Backup information |
| `restoreRecognitionData()` | `BaseResult` | Recheck status after restoring |

### 8.4 Product data and synchronization

| Method | Returns | Key points |
| --- | --- | --- |
| `createGoods(GoodsSave req)` | `GoodsResult` | After creation, save the Huoyan SDK product ID from `getRecord().getGoodsId()` |
| `updateGoods(String goodsId, GoodsSave req)` | `GoodsResult` | Update only supplied fields |
| `deleteGoods(String goodsId)` | `BaseResult` | Delete one product; does not modify the client's local products |
| `getGoods(String goodsId)` | `GoodsResult` | Query a local Huoyan SDK product; returns 404 if absent |
| `queryGoods(String keyword, String name, String plu, int isDeleted, int current, int size, String sortBy, String sortOrder)` | `GoodsQueryResult` | Query the Huoyan SDK local database; `isDeleted=-1` defaults to non-deleted products, and page/page size 0 uses SDK defaults |
| `getGoodsCount(String keyword, String name, String plu, int isDeleted)` | `GoodsCountResult` | Get count using the same filters |
| `batchUpdateGoods(String[] goodsIds, GoodsSave changes)` | `GoodsBatchResult` | Check each item's code/reason even when the overall code is 0 |
| `batchDeleteGoods(String[] goodsIds)` | `GoodsBatchResult` | Batch soft deletion; handle failed items individually without resending successful ones |
| `listGoods(long syncTimestamp, int current, int size)` | `GoodsListResult` | Query server products by page; timestamp 0 queries all products. Returns records, pagination, syncMode, and syncTimestamp |
| `downloadGoods(int size)` | `GoodsDownloadResult` | Synchronize cloud products into the Huoyan SDK local database and wait for final statistics; independent of image download/learning |
| `getDownloadGoodsProgress()` | `GoodsDownloadProgress` | Query product synchronization progress on another background thread |

<a id="verification"></a>

## 9. Verification and troubleshooting

Verify the following workflows as applicable to your integration.

1. Confirm that the JAR matches the Huoyan SDK and the application's package name is registered. Connect and confirm `SdkStatus.modelReady(status)` returns true.
2. Verify recognition using a fixed image of a learned product, checking the class-to-product match. Cover empty detections and unmatched products. If integrating learning, first learn a class and save successful UIDs, then recognize it.
3. If integrating sample management, verify pagination, image display, and single-sample deletion. Show a sample as deleted only after confirmed success.
4. After integrating the camera, verify cropping, rotation, and box placement. Ensure stale results from a closed screen cannot update a new screen.
5. If using feedback, product synchronization, or restoration, verify success, failure, partial success, and unknown-result handling for each.

| Problem | Check first |
| --- | --- |
| Cannot connect / caller not allowed | Huoyan SDK deployment, package-name registration, permissions, IP/port, and connection mode |
| Connected but cannot recognize | modelReady, activation status, model preparation, and recentError |
| Class returned but no matching product | Mapping between clazz and business product code; whether the product belongs to the current shop or device |
| Learning succeeded but the image cannot load | Retained UID, imageAvailable, complete URL, network policy, and URL refresh |
| Drawn or feedback boxes are offset | Crop offset, scaling, rotation, and original upload coordinates for this image |
| Progress does not refresh during a long operation | Whether the operation and progress queries share a single background thread |

<a id="helper-api"></a>

## 10. Public helper APIs

The types below are in the `cc.huoyan.sdk.client` root package or the stated subpackage; result models are in `model`. Methods described as “background” perform blocking work on the calling thread, so the host must schedule them on a background thread. Pure validation, result interpretation, and geometry calculations do not issue SDK requests. `BooleanSupplier`, `Supplier`, `Function`, and `Consumer` come from `java.util.function`; `Executor` comes from `java.util.concurrent`.

### 10.1 Connection configuration and device discovery

These types are in the root package.

| Method | Returns | Usage and constraints |
| --- | --- | --- |
| `SdkConfig.aidl()` / `SdkConfig.websocket(String url)` | `SdkConfig` | Create a connection configuration; see section 3.1 for platform requirements and endpoint rules |
| `SdkConfig.supportedChannel(SdkConfig.Channel channel, int apiLevel)` | `Channel` | Return WS below API 27, otherwise the supplied channel; does not create a connection |
| `SdkConfig.Channel.value()` / `SdkConfig.Channel.fromValue(String value)` | `String` / `Channel` | Persisted values are `aidl` / `ws`; only `ws` parses as WS, all other values as AIDL. Platform support must still be checked |
| `SdkConfig.requiresLocalShell()` | `boolean` | AIDL or local WS requires a local SDK installation; remote WS does not |
| `SdkConfig.withLog(SdkLog log)` | `SdkConfig` | Return a configuration with a new log sink; log must not be null |
| `SdkConfig.getChannel()` / `getWsEndpoint()` / `getLog()` | `Channel` / `String` / `SdkLog` | Read configuration; the WS endpoint is null for AIDL |
| `SdkLog.log(String tag, String message)` | `void` | Host-implemented logging interface; not guaranteed to run on the main thread, so do not manipulate views directly |
| `SdkDiscovery.endpointForIp(String input)` | `String` | Validate and normalize IPv4 into `ws://IP:18086/ws`; invalid input throws `IllegalArgumentException` |
| `SdkDiscovery.ipFromEndpoint(String endpoint)` | `String` | Extract IPv4; return `127.0.0.1` if parsing fails. This does not establish endpoint validity or successful probing |
| `SdkDiscovery.probe(String endpoint)` | `boolean` | Background verification of the SDK status response; true means the service responded, not that the model is ready |
| `SdkDiscovery.discover(String preferredIp)` | `List<String>` | Background scan of local wlan/eth IPv4 subnets and the specified address; return sorted responding IPs. The argument must be valid IPv4; reading network interfaces may throw `SocketException` |

The default endpoint constant is `SdkConfig.DEFAULT_WS_ENDPOINT`. Discovery provides `SdkDiscovery.DEFAULT_PORT` (18086) and `DEFAULT_PATH` (`/ws`).

### 10.2 Connection waiting and shared sessions

These types are in `session`.

| Method or constructor | Returns | Usage and constraints |
| --- | --- | --- |
| `SdkConnection.await(HuoyanSdkClient client, long timeoutMillis, long pollMillis, BooleanSupplier active)` | `boolean` | Call connect and wait in the background; true on connection, false on timeout, cancellation, or interruption. The helper preserves the interrupt flag and leaves the client open; timeoutMillis≥0, pollMillis>0 |
| `new SdkSession(SdkConfig config, Function<SdkConfig, HuoyanSdkClient> factory)` | `SdkSession` | Lazily create the client; the factory must not retain a screen and may use an application Context |
| `SdkSession.getConfig()` | `SdkConfig` | Return current configuration without initiating a connection |
| `SdkSession.getClient()` | `HuoyanSdkClient` | Create through the factory on first use, then reuse; does not automatically connect |
| `SdkSession.configure(SdkConfig next)` | `void` | Close the old client if channel or endpoint changes; changing only logging does not recreate an existing client. Does not transfer or resend old requests |
| `SdkSession.close()` | `void` | Close the current client; a later getClient creates a new instance |
| `new SdkStartupController(Supplier<HuoyanSdkClient> clients, Executor worker, Executor callbacks, SdkLog log)` | `SdkStartupController` | Inject the client supplier, background executor, callback executor, and logger; default polling interval is 200ms |
| `new SdkStartupController(Supplier<HuoyanSdkClient> clients, Executor worker, Executor callbacks, SdkLog log, long pollMillis)` | `SdkStartupController` | Specify a positive polling interval; worker runs blocking queries, and callbacks determines the callback thread |
| `SdkStartupController.start(SdkStartupController.Listener listener)` | `void` | Start connection, initialization, and status observation; repeated calls in the same run have no effect. Polling ends when the model is ready |
| `SdkStartupController.stop()` | `void` | Stop observing and discard callbacks from the previous run; does not close the connection or undo submitted operations. Call stop before start to observe again |
| `SdkStartupController.Listener.onStatus(int initCode, StatusSnapshot status, boolean modelReady)` | `void` | Invoked on the callbacks executor; status may be null, and initCode is the initialization result |

`active` is read in the background. Ensure cross-thread visibility for a cancellation flag modified by another thread. A timeout or cancellation of connection waiting does not cancel an SDK business operation.

### 10.3 Image snapshots and input validation

`SdkImage` is in the root package, `SdkInputs` in `operation`, and `ActivationCode` in `activation`.

| Method | Returns | Usage and constraints |
| --- | --- | --- |
| `SdkImage.rgb888(byte[] bytes, int width, int height)` | `SdkImage` | Copy packed RGB888; byte count must equal width×height×3, and dimensions must be positive |
| `SdkImage.rgb888(Bitmap bitmap)` / `SdkImage.jpeg(Bitmap bitmap)` | `SdkImage` | Extract RGB888 or encode JPEG90 in the background; does not crop, rotate, or recycle the caller's Bitmap |
| `SdkImage.encoded(byte[] bytes, int width, int height, int format)` | `SdkImage` | Copy nonempty JPEG/PNG bytes; pass 0 for unknown dimensions and use `FORMAT_JPEG` / `FORMAT_PNG` |
| `SdkImage.getBytes()` | `byte[]` | Return a separate copy each time; modifying it does not affect the snapshot. Copy large images in the background |
| `SdkImage.getWidth()` / `getHeight()` / `getFormat()` / `getSize()` | `int` | Pixel width, height, format, and byte count; encoded image dimensions may be 0 |
| `SdkInputs.positiveId(long id)` | `long` | Return a positive value unchanged; otherwise throw `IllegalArgumentException`. Used for numeric IDs such as sample UIDs |
| `SdkInputs.clazz(String value)` | `String` | Validate 1–50 ASCII letters/digits and return unchanged; invalid input throws `IllegalArgumentException` |
| `SdkInputs.isClazz(String value)` | `boolean` | Boolean check using the same class rules; null returns false, and the original value is not modified |
| `ActivationCode.normalize(String input)` | `String` | Format an activation code; see section 3.3 for rules |
| `ActivationCode.validManual(String input)` | `boolean` | Check manually entered code length, not license validity; do not use to reject scanned serial values of other lengths |

Image format constants are `FORMAT_RGB888=1`, `FORMAT_JPEG=2`, and `FORMAT_PNG=3`. `MAX_TRANSPORT_BYTES` is 32 MiB. Feedback images have a separate 8 MiB limit.

### 10.4 Batch learning, long operations, and result interpretation

These operation helpers are in `operation`; `ImageFlowProgress` is in `model`.

| Method | Returns | Usage and constraints |
| --- | --- | --- |
| `BatchLearning.learnedBoxesOf(ItemLearnBatch batch)` | `List<ItemLearnBox>` | If overall code=0, extract boxes with code=0 and uid>0, deduplicating by UID while preserving order. A null response or overall failure returns an empty list; this alone does not mean “no objects” |
| `BatchLearning.boxDetailOf(ItemLearnBatch batch)` | `String` | Produce diagnostic text with each box's UID/failure code; not used to determine success |
| `SdkOperations.learnImages(HuoyanSdkClient client)` | `ImageFlowProgress` | Submit one full-library image download/learning operation in the background; disconnected returns LOCAL_DISCONNECTED, and missing responses or runtime exceptions become RESULT_UNKNOWN |
| `SdkOperations.imageProgress(HuoyanSdkClient client, boolean download)` | `ImageFlowProgress` | Query progress in the background: true for downloads, false for learning. Runtime exceptions become RESULT_UNKNOWN; a missing underlying response may still return null |
| `SdkOperations.activate(HuoyanSdkClient client, String code, String modelCode)` | `BaseResult` | Normalize the activation code and submit once in the background; disconnected returns LOCAL_DISCONNECTED, and missing responses or runtime exceptions become RESULT_UNKNOWN |
| `SdkOperations.activationProgress(HuoyanSdkClient client)` | `DownloadProgress` | Query model download progress in the background; return null on disconnection, exception, or missing response |
| `OperationOutcome.fromCode(int code)` | `OperationOutcome` | 0→SUCCESS; LOCAL_DISCONNECTED / NOT_EXECUTED→NOT_EXECUTED; RESULT_UNKNOWN→UNKNOWN; other codes→FAILURE |
| `ImageFlowProgress.phase()` | `ImageFlowProgress.Phase` | Convert the phase to IDLE, DOWNLOADING, LEARNING, SUCCESS, FAILED, or UNKNOWN; missing/unrecognized values become UNKNOWN, with the raw value retained by getPhase |
| `ImageFlowProgress.learningStarted()` | `boolean` | True when code=0 and the phase is learning/success or current>0; use to choose which progress to observe, not as final success |
| `ImageFlowProgress.outcome()` | `ImageFlowProgress.Outcome` | Interpret the final response as SUCCESS, PARTIAL_SUCCESS, FAILURE, NOT_EXECUTED, or UNKNOWN; download or learning failure counts>0 may yield partial success |

`SdkOperations` does not automatically retry. Progress polling does not replace the final operation response. `outcome()` does not classify skippedCount alone as partial success, so the host must still read skipped counts and reasons. Retain the raw result codes described in section 6.2. For example, `IMAGE_UNAVAILABLE` means an image request was not sent; generic outcome categories alone cannot establish whether every failed request was sent.

### 10.5 Sample pagination and image URL caching

These types are in `samples`. Run pagination methods in the background. A returned URL does not mean the image has been downloaded.

| Method | Returns | Usage and constraints |
| --- | --- | --- |
| `LearnedClazzPages.loadAll(HuoyanSdkClient client)` | `ItemClazzList` | Aggregate all classes using 100 entries per page |
| `LearnedClazzPages.loadAll(HuoyanSdkClient client, int pageSize)` | `ItemClazzList` | Specify a positive page size; if any page fails, return the failure response or null, not a partial list. No cancellation predicate argument |
| `LearnedImagePages.loadPage(HuoyanSdkClient client, String clazz, int page, int size, BooleanSupplier isCurrent)` | `ItemClazzImagePage` | Query one page and validate its response and page number; pages start at 1 |
| `LearnedImagePages.loadNewestPage(HuoyanSdkClient client, String clazz, int size, BooleanSupplier isCurrent)` | `ItemClazzImagePage` | Read total pages, then fetch the last page; relocate at most once if concurrent deletion shrinks the page count |
| `LearnedImagePages.loadAll(HuoyanSdkClient client, String clazz)` | `List<ItemClazzImage>` | Read all samples using 100 entries per page, retaining samples without images |
| `LearnedImagePages.loadAll(HuoyanSdkClient client, String clazz, BooleanSupplier isCurrent)` | `List<ItemClazzImage>` | Also check screen/product validity to avoid continuing queries for a previously selected product |
| `LearnedImagePages.findUrls(HuoyanSdkClient client, String clazz, long[] uids, int size, BooleanSupplier isCurrent)` | `Map<Long, String>` | Search backward from the newest page for positive UIDs and return valid image URLs found; omit UIDs that are absent or lack images |
| `LearnedImagePages.latestUrls(List<ItemClazzImage> images, int limit)` | `List<String>` | Locally extract up to limit unique HTTP(S) URLs by descending UID; skip missing images and issue no requests |
| `LearnedImagePages.isHttpUrl(String url)` | `boolean` | Check HTTP(S) scheme and host; does not verify reachability |

`LearnedImagePages` checks `isCurrent` and thread interruption before and after each page request. It throws `CancellationException` when no longer current or interrupted, and `IllegalStateException` for query or pagination failures. Cancellation checks do not forcibly abort requests already sent; the host must still verify that a result belongs to the current request generation before updating the screen.

| Method or constructor | Returns | Usage and constraints |
| --- | --- | --- |
| `new SampleImages(long ttlMillis, int previewLimit)` | `SampleImages` | Set URL cache lifetime in milliseconds and the preview count limit; both must be nonnegative. Creates no threads |
| `SampleImages.update(ItemClazzList result, HuoyanSdkClient client)` | `void` | Update thumbnails, client ownership, and cache version from a successful full class-list response; failed responses do not update the cache. Issues no requests |
| `SampleImages.refreshAddresses(HuoyanSdkClient client)` | `void` | Refresh expired URLs when called by the host in the background; no scheduled refresh. Switching clients invalidates the old cache; results invalidated during querying are not cached |
| `SampleImages.previews(String clazz, HuoyanSdkClient client)` | `List<String>` | Obtain the latest valid preview URLs in the background, possibly querying pages; return an unmodifiable list |
| `SampleImages.thumbnail(String clazz, HuoyanSdkClient client)` | `String` | Obtain a cover image in the background; query samples if no valid thumbnail exists, then return null if still unavailable |
| `SampleImages.invalidate()` / `SampleImages.version()` | `void` / `long` | Clear the URL cache and increment its version / read the current version; the host may use this version as an image-cache signature |

Call `invalidate()` after learning, deletion, restoration, or switching shops. This cache manages URLs only; the host loads the images. Callers must still handle pagination exceptions from `previews()` and `thumbnail()`.

### 10.6 Feedback sessions

These types are in `feedback`; feedback item and product models are in `model`.

| Method or constructor | Returns | Usage and constraints |
| --- | --- | --- |
| `new FeedbackSubmission(byte[] bytes, int feedbackType, List<FeedbackItem> items)` | `FeedbackSubmission` | Copy image bytes and the item list without issuing a request; feedbackType accepts only 0/1, and the image is validated on submission |
| `FeedbackSubmission.fromFile(File photo, int feedbackType, List<FeedbackItem> items, FeedbackSubmission.PhotoRetention retention)` | `FeedbackSubmission` | Retain the file reference and read it only on the first submit; null retention uses KEEP |
| `FeedbackSubmission.submit(HuoyanSdkClient client)` | `BaseResult` | Synchronously upload and submit feedback in the background; reuse requestId and any acquired URL. Calls after success do not resend; direct calls may propagate runtime exceptions |
| `FeedbackSubmission.getStage()` | `FeedbackSubmission.Stage` | UPLOAD means upload success is not yet confirmed; SUBMIT means a URL is available; COMPLETE means a successful feedback response was received |
| `FeedbackSubmission.getRequestId()` | `String` | Return this instance's fixed request identifier |
| `FeedbackSubmission.getUploadedUrl()` | `String` | Return the confirmed upload URL, or null before upload succeeds |
| `FeedBackUploadHelper.prepareDetectFeedback(List<FeedbackDetection> detections, int imageWidth, int imageHeight)` | `List<FeedbackItem>` | Locally build an unmodifiable item list; supply dimensions and coordinates from the original uploaded image. See section 4.4 for field rules |
| `FeedBackUploadHelper.submit(HuoyanSdkClient client, FeedbackSubmission submission, Executor executor, Consumer<BaseResult> completion)` | `void` | Submit on the host's background executor; completion runs on the same worker thread. Convert submission runtime exceptions to RESULT_UNKNOWN; no automatic retry |

`submit()`, `getStage()`, and `getUploadedUrl()` use the same instance lock, so reading the stage or URL during submission may block. Do not poll these getters on the main thread. Stages record only confirmed local steps; they cannot tell whether a request with an unknown result has executed on the backend. See section 4.4 for `KEEP` / `DELETE_ON_SUCCESS` and the 8 MiB limit, whose constant is `FeedbackSubmission.MAX_PHOTO_BYTES`.

### 10.7 Geometry and status helpers

Geometry helpers are in `geometry`; status helpers are in `status`.

| Method | Returns | Usage and constraints |
| --- | --- | --- |
| `DetectBoxParser.parse(float[] points, DetectGeometry geometry)` | `DetectBoxParser.DetectRect` | BBOX reads the first 4 values as `[left,top,right,bottom]`; OBB reads the first 8 values as four vertices and computes the bounding rectangle. Null or insufficient input returns null; format is not guessed |
| `DetectBoxParser.DetectRect.of(float x1, float y1, float x2, float y2)` | `DetectRect` | Normalize two opposite corners using min/max; read the result through public read-only left/top/right/bottom fields |
| `FeedbackBounds.fromPoints(float[] points, int imageWidth, int imageHeight)` | `int[]` | Compute and round a bounding box from vertices, returning `[x,y,width,height]`; clip to image bounds when dimensions are positive. Null, fewer than two points, an odd number of coordinates, or non-finite coordinates returns an all-zero box |
| `SdkStatus.shellStarted(StatusSnapshot status)` | `boolean` | True only for a valid response with code=0 and sdkState=READY or OFFLINE; does not confirm model availability |
| `SdkStatus.modelReady(StatusSnapshot status)` | `boolean` | Requires a valid response with code=0 and modelReady=true; OFFLINE additionally requires certificateValid=true and keyLost not true |
| `SdkStatus.issue(int initCode, StatusSnapshot status)` | `SdkIssue` | Classify issues using initialization code and status, prioritizing disconnection; return NONE if the model is ready |
| `SdkError.fromCode(int code)` | `SdkError` | LOCAL_DISCONNECTED / NOT_EXECUTED→NOT_CONNECTED, RESULT_UNKNOWN→RESULT_UNKNOWN, -3→NOT_INITIALIZED, -42/-50→NOT_ACTIVATED, others→OTHER; not a success check |
| `SdkError.defaultMessage()` / `SdkIssue.defaultMessage()` | `String` | Return default Chinese descriptions; retain original code/reason. The host may map them to localized text |

Geometry helpers only convert explicitly defined coordinates. The host handles crop offsets, rotation, and preview scaling.

### 10.8 SDK downloads and callbacks

These types are in `deployment`.

| Method or constructor | Returns | Usage and constraints |
| --- | --- | --- |
| `SdkShell.installedVersion(PackageManager packageManager)` | `PackageInfo` | Query the locally installed SDK version; return null if not installed |
| `new SdkApkDownloader(String url, File directory)` | `SdkApkDownloader` | Create a single-use downloader; supply a writable temporary directory and confirm the URL for the actual deployment |
| `SdkApkDownloader.start(SdkApkDownloader.Listener listener)` | `void` | Download asynchronously; only one start is allowed per instance |
| `SdkApkDownloader.cancel()` | `void` | Cancel network requests and reads, then clean up undelivered files in the background; does not delete already delivered files |
| `SdkApkDownloader.Listener.onProgress(long downloaded, long total)` | `void` | Background callback reporting bytes; total<0 means unknown. Calculate a percentage only when total>0 |
| `SdkApkDownloader.Listener.onComplete(File apk)` | `void` | Deliver a complete file in the background; the host validates, installs, and later cleans it up |
| `SdkApkDownloader.Listener.onFailure(IOException error)` | `void` | Report download failure in the background; explicit cancellation does not trigger this callback |

When canceling a download, the host must dismiss its waiting indicator and verify that the screen is still valid before callbacks update the UI. `SdkShell.PACKAGE_NAME` and `DEFAULT_APK_URL` provide the SDK package name and default distribution URL. The host still manages installation permissions and FileProvider.
