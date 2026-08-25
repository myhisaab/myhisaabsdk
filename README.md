# MyHisaab Android SDK

Embed the **MyHisaab** web app inside any Android app with a single Composable (or
Activity / Fragment). The SDK hosts the web app in a hardened `WebView`, handles
camera & gallery uploads natively, and forwards push tokens to the web layer —
mirroring the MyHisaab iOS SDK so one web integration serves both platforms.

```kotlin
MyHisaabView(customerId = "your-customer-id")
```

loads `https://simosa.myhisaab.pk/login?customerId=<customerId>`.

The web app captures `customerId`, retains it through login and onboarding, and
sends it to the backend after OTP verification — the SDK's only job is to put it
in the URL.

---

## Contents

- [Requirements](#requirements)
- [Install](#install)
- [Quick start](#quick-start)
- [Passing an FCM push token](#passing-an-fcm-push-token)
- [Lifecycle callbacks](#lifecycle-callbacks)
- [View-based apps (Activity / Fragment)](#view-based-apps-activity--fragment)
- [`MyHisaabView` API reference](#myhisaabview-api-reference)
- [Permissions](#permissions)
- [Web-side integration contract](#web-side-integration-contract)
- [Environments](#environments)
- [Troubleshooting](#troubleshooting)

---

## Requirements

| | |
|---|---|
| Min SDK | **24** (Android 7.0) |
| UI | Jetpack Compose (Activity & Fragment wrappers provided for View-based apps) |
| Dependencies forced on your app | **None** — no Hilt, no Firebase, no `google-services.json` required |

---

## Install

**1. Add the JitPack repository** to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**2. Add the dependency** in your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.myhisaab:myhisaabsdk:1.0.0")
}
```

Replace `1.0.0` with the released git tag. Publishing steps are in
[`JITPACK.md`](JITPACK.md).

---

## Quick start

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pk.myhisaab.sdk.ui.MyHisaabView

@Composable
fun MyHisaabScreen() {
    MyHisaabView(
        customerId = "your-customer-id",
        modifier = Modifier.fillMaxSize(),
    )
}
```

`MyHisaabView` fills its parent, renders edge-to-edge, and manages its own
loading, error/retry, back-navigation, camera and gallery flows.

---

## Passing an FCM push token

Your app owns push notifications. Obtain the token from your own Firebase setup
and pass it in — the SDK injects it into the web app and **re-injects on every
reload and token rotation** (navigations wipe `window`-scoped values):

```kotlin
MyHisaabView(
    customerId = "your-customer-id",
    fcmToken = fcmToken,   // String? — pass null until you have one
    modifier = Modifier.fillMaxSize(),
)
```

---

## Lifecycle callbacks

All optional. The most useful is `onPageFinished`, for detecting login completion
(a redirect away from `/login`) so you can dismiss the SDK:

```kotlin
MyHisaabView(
    customerId = "your-customer-id",
    onPageStarted = { url -> /* a page began loading */ },
    onPageFinished = { url ->
        if (!url.contains("/login")) closeMyHisaab()   // logged in
    },
    onError = { description -> analytics.log("myhisaab_error", description) },
    modifier = Modifier.fillMaxSize(),
)
```

---

## View-based apps (Activity / Fragment)

For apps not yet on Compose. These take an optional `fcmToken` but **not** the
lifecycle callbacks (an Activity/Fragment can't carry lambdas through its
arguments) — embed `MyHisaabView` directly if you need those hooks.

**Launch a full-screen Activity** (already declared in the SDK manifest):

```kotlin
import pk.myhisaab.sdk.ui.MyHisaabActivity

MyHisaabActivity.launch(context = this, customerId = "your-customer-id")
```

**Embed as a Fragment:**

```kotlin
import pk.myhisaab.sdk.ui.MyHisaabFragment

val fragment = MyHisaabFragment.newInstance(customerId = "your-customer-id")

supportFragmentManager.beginTransaction()
    .replace(R.id.fragment_container, fragment)
    .commit()
```

---

## `MyHisaabView` API reference

```kotlin
@Composable
fun MyHisaabView(
    customerId: String,
    modifier: Modifier = Modifier,
    environment: MyHisaabEnvironment = MyHisaabEnvironment.PRODUCTION,
    fcmToken: String? = null,
    onPageStarted: ((url: String) -> Unit)? = null,
    onPageFinished: ((url: String) -> Unit)? = null,
    onError: ((description: String) -> Unit)? = null,
)
```

| Parameter        | Type                  | Default      | Description                                                              |
|------------------|-----------------------|--------------|--------------------------------------------------------------------------|
| `customerId`     | `String`              | — (required) | Customer id appended as the `customerId` query parameter.                |
| `modifier`       | `Modifier`            | `Modifier`   | Standard Compose modifier; usually `Modifier.fillMaxSize()`.             |
| `environment`    | `MyHisaabEnvironment` | `PRODUCTION` | Which MyHisaab host to load.                                             |
| `fcmToken`       | `String?`             | `null`       | FCM registration token to hand to the web app; update to re-inject.      |
| `onPageStarted`  | `((String) -> Unit)?` | `null`       | Called (main thread) when a page begins loading, with its URL.           |
| `onPageFinished` | `((String) -> Unit)?` | `null`       | Called (main thread) when a page finishes loading, with its URL.         |
| `onError`        | `((String) -> Unit)?` | `null`       | Called when the main frame fails to load. The retry overlay still shows. |

---

## Permissions

The SDK's manifest **merges these into your app** automatically:

```
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.CAMERA
android.permission.RECORD_AUDIO
android.permission.MODIFY_AUDIO_SETTINGS
```

Runtime permissions (camera, microphone) are requested by the SDK on demand.
`POST_NOTIFICATIONS` (Android 13+) is **not** handled by the SDK — that belongs to
your app's own push setup.

> The SDK registers a `FileProvider` under `${applicationId}.myhisaab.fileprovider`
> (a unique suffix) so it never clashes with a `FileProvider` your app defines.

---

## Web-side integration contract

The same JavaScript contract works on Android and iOS.

**Native camera bridge:**

```js
if (window.AndroidBridge && window.AndroidBridge.isCameraAvailable()) {
    window.AndroidBridge.openCamera();
}

window.receiveCameraImage = function (dataUrl) {
    // dataUrl = "data:image/jpeg;base64,...."
};
```

Standard `<input type="file" accept="image/*">` also works — it presents a native
Camera / Gallery chooser.

**FCM token:**

```js
const info = window.MYHISAAB_FCM_TOKEN;         // { token, platform: "android" }
window.addEventListener("myhisaab:fcm-token", (e) => {
    const { token, platform } = e.detail;
});
```

---

## Environments

```kotlin
enum class MyHisaabEnvironment { PRODUCTION }
```

Only `PRODUCTION` (`https://simosa.myhisaab.pk/login`) exists today. A `STAGING`
entry can be added later without changing the public API.

---

## Troubleshooting

**Dependency not found.** Ensure the JitPack repo is in `settings.gradle.kts`, the
tag exists on GitHub, and the repo is `github.com/myhisaab/myhisaabsdk`.

**JitPack build fails on the Android SDK.** `jitpack.yml` installs the compile SDK,
but if JitPack's image can't fetch that API level, lower `compileSdk` in
`build.gradle.kts` and re-tag. Log: `https://jitpack.io/#myhisaab/myhisaabsdk`.

**Camera returns nothing to the web app.** The web app must define
`window.receiveCameraImage(dataUrl)` or use a standard `<input type="file">`.

**Version bumps.** Keep `version` in `build.gradle.kts` equal to the git tag.
