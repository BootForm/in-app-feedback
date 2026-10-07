import 'package:flutter_test/flutter_test.dart';
import 'package:in_app_feedback/bootform_feedback.dart';

void main() {
  Outcome classify(int status, [String? code, bool hadScreenshot = false]) =>
      BootFormFeedback.classify(status, code, hadScreenshot: hadScreenshot);

  test('accepted is sent', () => expect(classify(200), Outcome.sent));
  test('free plan refusing the file drops only the screenshot',
      () => expect(classify(403, 'PLAN_LIMIT', true), Outcome.dropScreenshot));
  test('unclaimed form refusing the file drops only the screenshot',
      () => expect(classify(422, 'FORM_NOT_CLAIMED', true), Outcome.dropScreenshot));
  test('oversized file drops only the screenshot',
      () => expect(classify(422, 'FILE_TOO_LARGE', true), Outcome.dropScreenshot));
  test('plan limit without a screenshot is not retried forever',
      () => expect(classify(403, 'PLAN_LIMIT'), Outcome.rejected));
  test('rate limit is retried', () => expect(classify(429, 'RATE_LIMITED'), Outcome.retryLater));
  test('server error is retried', () => expect(classify(503), Outcome.retryLater));
  test('deleted form is given up', () => expect(classify(410, 'FORM_DELETED'), Outcome.rejected));
  test('disabled form is given up', () => expect(classify(403, 'FORM_DISABLED'), Outcome.rejected));
  test('invalid form id is given up', () => expect(classify(422, 'INVALID_FORM_ID'), Outcome.rejected));
}
