# In-app feedback for mobile apps, with no backend

Add a **"Send feedback" / "Report a bug" screen** to an Android or Flutter app without running a
server. Each report arrives in your [BootForm](https://bootform.com) dashboard (and, if you like, your
email, Slack or Discord) with the details you need to act on it:

- **Device info attached automatically**: app version and build, OS version, device model, locale.
- **An offline queue**: a report written on a train or in airplane mode is saved on the device and
  sent once there's a connection.
- **An optional screenshot** of the screen the user was on, shrunk before upload. If your plan
  doesn't take files, the report is still sent, just without the image.

No SDK and no package to install from us. Each platform is **one file you copy into your app**,
plus a small demo app showing it in use.

| Platform | Folder | The file to copy | Status |
|---|---|---|---|
| Android (Kotlin, Jetpack Compose) | [`compose/`](compose/) | [`BootFormFeedback.kt`](compose/app/src/main/java/com/bootform/feedback/BootFormFeedback.kt) | Tested |
| Flutter (Android and iOS) | [`flutter/`](flutter/) | [`bootform_feedback.dart`](flutter/lib/bootform_feedback.dart) | Tested on Android |
| SwiftUI (iOS) | | | Planned, once it can be tested on a real Mac |

Want React Native, .NET MAUI or another platform? [Open an issue](https://github.com/BootForm/in-app-feedback/issues).
Platforms get added when someone asks, so each one stays maintained.

---

## How it works

Your app sends one HTTP request per report. Everything else (the queue, the retries, the
screenshot fallback) is the app deciding what to do with the answer.

```
POST https://f.bootform.com/{your-form-id}
Content-Type: multipart/form-data
Accept: application/json
```

Multipart is used for every report, with or without a screenshot, so there is only one code path.
The `Accept: application/json` header makes BootForm answer with JSON instead of a redirect
(redirects are for browser forms).

### The fields each report sends

| Field | Example | Notes |
|---|---|---|
| `message` | `The export button does nothing` | What the user typed. Capped at 5,000 characters. |
| `email` | `sam@example.com` | Optional. Shown in your dashboard. |
| `_replyto` | `sam@example.com` | Same address. BootForm sets it as Reply-To on the notification email, so you can just hit Reply. Not stored as a field. |
| `_subject` | `Feedback: My App 2.4.1` | Subject line of the notification email. Not stored as a field. |
| `report_id` | `b1f0...` | Made on the device, and also sent as the `Idempotency-Key` header, so a retry never creates a second report. See [Sent once, even when retried](#sent-once-even-when-retried). |
| `platform` | `android` / `ios` | |
| `app_version` | `2.4.1` | `versionName` on Android, `version` in Flutter. |
| `app_build` | `241` | `versionCode` on Android, `buildNumber` in Flutter. |
| `os_version` | `Android 16 (API 36)` / `iOS 26.0` | |
| `device` | `Google Pixel 7` / `iPhone16,1` | iOS reports a model identifier, not the marketing name. |
| `locale` | `en-GB` | The device language, which often explains "the text is cut off" reports. |
| `screenshot` | `screenshot.jpg` | Optional file part. See [Screenshots](#screenshots-and-which-plan-you-need). |

Every field except `_replyto` and `_subject` is stored with the report, shows in your dashboard
and comes along in exports. Add your own the same way (a user ID, the current screen name, a feature
flag): just add a key to the map.

### What the app does with each answer

| BootForm answers | Code | The sample code |
|---|---|---|
| `200` | | **Sent.** Deletes the report and its screenshot from the device. |
| `200` with `"duplicate": true` | | **Sent.** BootForm already had this report from an earlier try whose answer never arrived, and kept just the one. |
| `200` with `"status": "pending_claim"` | | **Sent**, to a form nobody has claimed yet. The answer includes your `claim_url`, which the sample logs (Logcat on Android, the debug console in Flutter). |
| `403` | `PLAN_LIMIT` | **Resends without the screenshot.** Your plan doesn't accept files. |
| `422` | `FILE_TOO_LARGE` | **Resends without the screenshot.** |
| `422` | `FORM_NOT_CLAIMED` | **Resends without the screenshot.** Unclaimed forms never accept files. |
| `413` | `REQUEST_TOO_LARGE` | **Resends without the screenshot.** |
| `429` | `RATE_LIMITED`, `QUOTA_EXCEEDED`, `CLAIM_LIMIT_EXCEEDED` | **Keeps it and retries later.** |
| `5xx`, timeout, no network | | **Keeps it and retries later.** |
| `410` | `FORM_DELETED` | **Gives up.** The form is gone, so retrying can never succeed. |
| `403` | `FORM_DISABLED` | **Gives up.** |
| `422` | `INVALID_FORM_ID`, `VALIDATION_ERROR` | **Gives up.** Retrying the same report would fail the same way. |

The decision is one small pure function (`classify`) in each file, with unit tests in each
folder, so you can change the rules without a device.

---

## Setup, the short version

1. **Make your own form ID.** Generate a random UUID (BootForm's
   [UUID generator](https://bootform.com/uuidgenerator), or `uuidgen` in a terminal) and paste it
   into the file as `FORM_ID` / `formId`. The file ships with `__YOUR_FORM_ID__` and refuses to
   send until you replace it.
2. **Claim it** at `https://app.bootform.com/claim/{your-id}` (a free account). Do this **before
   you ship**: see [Why claim the form before release](#why-claim-the-form-before-release).
3. **Copy the one file** for your platform and add its dependencies. The folder README has the
   exact steps: [Android / Compose](compose/README.md), [Flutter](flutter/README.md).
4. **Call it** from your own feedback button. The demo apps show a complete, plain screen you can
   restyle or replace.

---

## The offline queue

Feedback is often written exactly when things are going wrong, which is also when the connection is
bad. So the samples never send directly from the button. They **save the report first**, then try to
send it.

- **Android** uses WorkManager. A queued report waits for a network, retries with exponential
  backoff (30 seconds, doubling, at most 5 hours apart), and is sent **even if the app is closed**.
  The one exception is a *force stop* from Android's settings: Android cancels that app's scheduled
  work until it's opened again. After about a day and a half of failed tries, the report is dropped.
- **Flutter** saves each report as a JSON file (plus the image) in the app's support directory, and
  sends the queue **when the app starts and whenever it comes back to the foreground**. It doesn't
  send while the app is closed. That keeps it to four common packages with no background
  setup on iOS. Reports older than 7 days are dropped.

Both were tested by sending a report in airplane mode, killing the app, and turning the network
back on.

### Sent once, even when retried

A queue that retries can send the same report twice: BootForm stores it, but the answer is lost
(the connection drops, the app is killed) and the next try sends it again. To stop that, each
request carries the report's `report_id` as an `Idempotency-Key` header. BootForm keeps the first
report with that key and answers any repeat within 7 days with `200` and `"duplicate": true`,
without storing it again, emailing you again or counting it against your quota. A report that was
refused (say, its screenshot was too big) doesn't use up its key, so the resend without the
screenshot goes through. More in BootForm's docs:
[Retrying safely](https://bootform.com/docs/building-your-form#retrying-safely).

---

## Screenshots, and which plan you need

The samples take the screenshot **before** the feedback screen opens, so it shows what the user was
actually looking at, and the user can see it and untick it before sending.

| | Free | Starter and up |
|---|---|---|
| Text reports with device info | Yes | Yes |
| Offline queue | Yes | Yes |
| Screenshot attached | No: the report is sent without it | Yes, up to the plan's file size (5 MB on Starter) |

Screenshots are shrunk on the device first, so a plan's limit is never close: Android sends a JPEG
with the longest side at 1,280 pixels (usually 100 to 400 KB), and Flutter sends a PNG at the same
size. Current limits are on the [pricing page](https://bootform.com/pricing).

A form that hasn't been claimed yet refuses files whatever the plan. This is deliberate, so a
stranger can't use an unclaimed ID as free file storage.

---

## Why claim the form before release

A form ID works the moment your app first uses it, even before anyone has claimed it. Until it's
claimed, BootForm **holds up to 10 reports for 48 hours** and gives each answer a claim link. The
first person to open that claim link owns the form, and everything sent to it.

Your form ID ships inside your app, where anyone who unpacks the app can read it. If it's unclaimed
when you release, a stranger could claim it before you do. Claim it while you're still
developing, and it's yours.

**Never use a form ID copied from a tutorial or a README, this one included.** Generate your own.

---

## Spam, and what an app can't do that a website can

Be clear about this before you ship:

- **There's no origin lock for apps.** A website form can be limited to requests from your own
  domain. A native app sends no `Origin` header, and a form with an origin allowlist turns it away
  (`403 ORIGIN_NOT_ALLOWED`). So a form used from an app has to leave that list empty.
- **The form ID can be extracted from your app.** Someone determined can find it and send junk
  reports straight to the API.
- **What still protects you:** per-IP and per-form rate limits, your monthly quota, and
  [Form Rules](https://bootform.com/docs) that mark matching reports as spam (for example, a
  `message` that contains `http`). A CAPTCHA widget like Turnstile is built for web pages, and a hidden honeypot
  field only catches bots filling in a web form, so neither helps here.
- **The worst case is spam, never a leak.** The form ID only lets someone *send* to your form. It
  can't be used to read anything. For feedback, that trade-off is usually fine.

Use a separate form for app feedback, so a spam wave never mixes with your website's contact form.

---

## Privacy

- Device info (model, OS, app version, locale) isn't tied to a person on its own, but mention in
  your privacy policy that feedback includes it.
- The email field is optional and labelled as such.
- A screenshot can contain personal data: whatever was on screen. That's why the samples show it to
  the user, with a checkbox to leave it out.

---

## FAQ

**How do I add a feedback form to an Android app without a backend?**
Copy [`BootFormFeedback.kt`](compose/app/src/main/java/com/bootform/feedback/BootFormFeedback.kt),
add WorkManager, set your form ID, and call `BootFormFeedback.send(context, message, email,
screenshot)` from your button. Reports land in your BootForm dashboard. See
[`compose/README.md`](compose/README.md).

**How do I add a bug report screen to a Flutter app?**
Copy [`bootform_feedback.dart`](flutter/lib/bootform_feedback.dart), add `http`, `path_provider`,
`device_info_plus` and `package_info_plus`, and call `BootFormFeedback.send(...)`. Call
`BootFormFeedback.flush()` on start and resume. See [`flutter/README.md`](flutter/README.md).

**Does it work for iOS?**
Yes, through the Flutter version, whose code is the same on iOS. It has been run on Android only so
far. A native SwiftUI version is planned.

**Can users attach a screenshot or a file?**
Yes, on BootForm's Starter plan and up. On Free, the report still goes through, without the image.

**What happens if the user is offline?**
The report is saved on the device and sent later. See [The offline queue](#the-offline-queue).

**Can I get the reports in Slack, Discord or email instead of a dashboard?**
Yes. Set that up on the form in BootForm. The app code doesn't change.

**Is this an SDK I have to keep updated?**
No. It's one file with no dependency on BootForm code, which you own once you've copied it. The
API it calls is a plain HTTP form endpoint.

**How is this different from a crash reporter?**
A crash reporter catches what the app does wrong without the user. This catches what the user
wants to tell you: confusing screens, missing features, "it's slow on my phone". They work well side
by side.

---

## License

MIT. Copy the files into closed-source apps freely.
