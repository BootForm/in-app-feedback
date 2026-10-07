# Working in this repo

Copy-paste samples that give a mobile app a feedback screen backed by BootForm, with no server of
the app developer's own. One folder per platform; each has **one file meant to be copied** into
someone else's app, plus a demo app around it.

## Rules

- **The copied file must stand alone.** `BootFormFeedback.kt` and `bootform_feedback.dart` may only
  depend on the platform and the packages listed at their top. No helpers in other files: a reader
  copies one file, and it has to compile.
- **Ship `__YOUR_FORM_ID__`, never a real form ID.** A form ID is the whole claim credential:
  whoever claims it first receives everything sent to it. Both files refuse to send while the
  placeholder is in place. When testing against the live API, put a freshly generated UUID in
  locally and never commit it.
- **Keep the platforms in step.** The answer handling (`classify`), the field names, the screenshot
  fallback codes and the root README's tables describe one behavior. Change one, change all of them,
  including both test files.
- **The queue saves before it sends.** Never send straight from the button: feedback is often
  written exactly when the connection is bad.
- **Multipart for every report**, with `Accept: application/json`. One code path, and the JSON
  answer is what `classify` reads.

## API facts the code depends on

- `POST https://f.bootform.com/{form_id}`; `multipart/form-data`, JSON or urlencoded are accepted.
- `_replyto` and `_subject` are consumed by BootForm and not stored. Every other field is stored.
- Files are refused with `403 PLAN_LIMIT` on the Free plan and `422 FORM_NOT_CLAIMED` on an
  unclaimed form. `FILE_TOO_LARGE` (422) and `REQUEST_TOO_LARGE` (413) are the size refusals.
- An unclaimed form holds up to 10 submissions for 48 hours, then `429 CLAIM_LIMIT_EXCEEDED`.
- A native app sends no `Origin` header, so a form with an origin allowlist answers
  `403 ORIGIN_NOT_ALLOWED`. App forms must keep that list empty.

## Checks before a change is done

- Android: `./gradlew testDebugUnitTest assembleDebug` in `compose/`.
- Flutter: `flutter analyze` and `flutter test` in `flutter/`.
- A behavior change also gets one real run against the live API on an emulator: a report with a
  screenshot to an unclaimed test ID must come back as "resent without the screenshot", and a
  report written in airplane mode must arrive after the network returns.

## Adding a platform

Add one only when an issue asks for it. A new folder needs the same three pieces (the one file to
copy, a demo app, `classify` tests), a row in the root README's platform table, and a real test run.
Don't add a platform that can't be built and run on the machine doing the work: an untested sample
does more harm than a missing one.
