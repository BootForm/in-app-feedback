# Android (Jetpack Compose)

A feedback screen for an Android app: device info attached automatically, an offline queue built on
WorkManager, and an optional screenshot. Read the [root README](../README.md) first for how it
works and what to know before shipping.

## Add it to your app

1. **Copy** [`BootFormFeedback.kt`](app/src/main/java/com/bootform/feedback/BootFormFeedback.kt)
   into your app and change its `package` line to yours. It has no Compose code, so it works
   in a View-based app too.
2. **Add the dependencies** to your module's `build.gradle.kts`:

   ```kotlin
   implementation("androidx.work:work-runtime-ktx:2.10.2")
   implementation("androidx.core:core-ktx:1.16.0")
   ```

3. **Allow network access** in `AndroidManifest.xml`, if your app doesn't already:

   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```

4. **Set your form ID**: replace `__YOUR_FORM_ID__` with a UUID you generated yourself, and claim it
   (see the root README's setup section).
5. **Send a report** from your own button:

   ```kotlin
   // Take the screenshot first, before your feedback UI covers the screen.
   val screenshot = window.decorView.rootView.drawToBitmap()

   // Later, when the user taps Send. Returns at once: sending happens in the background.
   val workId = BootFormFeedback.send(context, message = text, email = email, screenshot = screenshot)
   ```

   `send` returns the WorkManager ID, so you can show progress with
   `WorkManager.getInstance(context).getWorkInfoByIdFlow(workId)`.
   [`FeedbackScreen.kt`](app/src/main/java/com/bootform/feedback/FeedbackScreen.kt) does exactly
   that.

## Run the demo

Open this `compose/` folder in Android Studio, put your form ID in `BootFormFeedback.kt`, and run.
Or from a terminal, with the Android SDK installed:

```bash
./gradlew testDebugUnitTest   # the answer-handling rules, no device needed
./gradlew installDebug        # onto a connected device or emulator
```

While the form is still unclaimed, Logcat prints its claim link (filter by the tag
`BootFormFeedback`).

## What's in here

| File | What it is |
|---|---|
| `BootFormFeedback.kt` | **The file to copy.** Device info, the queue (a WorkManager worker), the HTTP request, and the screenshot fallback. |
| `FeedbackScreen.kt` | A plain Compose form: message, optional email, a screenshot preview with a checkbox, and the send status. Restyle or replace it. |
| `MainActivity.kt` | A stand-in for your app, with a "Send feedback" button that takes the screenshot first. |
| `ClassifyTest.kt` | Unit tests for what the app does with each answer from BootForm. |

Built with Kotlin 2.2, AGP 8.11, `compileSdk` 36, `minSdk` 24.
