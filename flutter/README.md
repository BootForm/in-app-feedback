# Flutter (Android and iOS)

A feedback screen for a Flutter app: device info attached automatically, an offline queue saved to
disk, and an optional screenshot. Read the [root README](../README.md) first for how it works and
what to know before shipping.

## Add it to your app

1. **Add the packages**:

   ```bash
   flutter pub add http path_provider device_info_plus package_info_plus
   ```

2. **Copy** [`lib/bootform_feedback.dart`](lib/bootform_feedback.dart) into your app's `lib/`.
3. **Set your form ID**: replace `__YOUR_FORM_ID__` with a UUID you generated yourself, and claim it
   (see the root README's setup section).
4. **Send the queue on start and resume**, so reports written offline go out:

   ```dart
   @override
   void initState() {
     super.initState();
     BootFormFeedback.flush();
     _lifecycle = AppLifecycleListener(onResume: BootFormFeedback.flush);
   }
   ```

5. **For screenshots**, wrap your app in a `RepaintBoundary` with a key, and capture it before your
   feedback screen opens:

   ```dart
   final screenshotKey = GlobalKey();

   MaterialApp(
     builder: (context, child) => RepaintBoundary(key: screenshotKey, child: child),
     // ...
   );

   final png = await BootFormFeedback.captureScreenshot(screenshotKey);
   ```

6. **Send a report** when the user taps Send. It returns once the report is saved on the device:

   ```dart
   await BootFormFeedback.send(message: text, email: email, screenshotPng: png);
   ```

[`lib/main.dart`](lib/main.dart) and [`lib/feedback_screen.dart`](lib/feedback_screen.dart) show
all of this in one small working app.

## Run the demo

Put your form ID in `lib/bootform_feedback.dart`, then:

```bash
flutter test   # the answer-handling rules, no device needed
flutter run    # onto a connected device or emulator
```

While the form is still unclaimed, the debug console prints its claim link (look for
`BootFormFeedback:`).

## What's in here

| File | What it is |
|---|---|
| `lib/bootform_feedback.dart` | **The file to copy.** Device info, the on-disk queue, the HTTP request, the screenshot capture and its fallback. |
| `lib/feedback_screen.dart` | A plain Material form: message, optional email, a screenshot preview with a checkbox. Restyle or replace it. |
| `lib/main.dart` | A stand-in for your app: the `RepaintBoundary`, the flush on start and resume, and a "Send feedback" button. |
| `test/classify_test.dart` | Unit tests for what the app does with each answer from BootForm. |

## Notes

- **The queue sends while the app is open**, not in the background. If you need background
  sending, call `BootFormFeedback.flush()` from a background task (for example the `workmanager`
  package). It's safe to call from anywhere and at any time.
- **iOS**: the code has nothing platform-specific to change, but it has only been run on Android
  so far. If you run it on iOS, an issue saying how it went is very welcome.
- `device` on iOS is a model identifier such as `iPhone16,1`, not "iPhone 15 Pro". Map it in
  `deviceInfo()` if you prefer readable names.
