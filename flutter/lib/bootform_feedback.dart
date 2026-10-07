// The one file to copy into your app. It sends a feedback report to BootForm with the device
// details attached, keeps it on disk while the phone is offline, and drops the screenshot (keeping
// the report) when your plan doesn't accept files.
//
// Needs: http, path_provider, device_info_plus, package_info_plus.

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:ui' as ui;

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';

class BootFormFeedback {
  /// Your form ID. Generate your own UUID and claim it at https://app.bootform.com/claim/{your-id}
  /// before you ship. Never use an ID copied from a tutorial: whoever claims an ID first owns it.
  static const formId = '__YOUR_FORM_ID__';

  static const _endpoint = 'https://f.bootform.com/';

  /// Longest side of the screenshot. Keeps a PNG of a typical screen well under 1 MB.
  static const _screenshotMaxSide = 1280.0;

  /// A queued report older than this is given up, so a form that keeps refusing doesn't retry forever.
  static const _maxAge = Duration(days: 7);

  static Future<void>? _flushing;

  /// Saves the report to disk, then tries to send everything queued. Returns once the report is
  /// safely saved; sending happens in the background. Call [flush] when the app starts and resumes,
  /// so a report written offline goes out the next time the app is opened online.
  static Future<void> send({required String message, String? email, Uint8List? screenshotPng}) async {
    if (formId == '__YOUR_FORM_ID__') {
      throw StateError('Set BootFormFeedback.formId to your own form ID first.');
    }
    final reportId = _newId();
    final fields = <String, String>{
      'message': message,
      'report_id': reportId,
      if (email != null && email.isNotEmpty) 'email': email,
      if (email != null && email.isNotEmpty) '_replyto': email,
      ...await deviceInfo(),
    };

    final dir = await _queueDir();
    if (screenshotPng != null) {
      await File('${dir.path}/$reportId.png').writeAsBytes(screenshotPng, flush: true);
    }
    // Written last: a report only counts as queued once its JSON exists.
    await File('${dir.path}/$reportId.json').writeAsString(
      jsonEncode({'created_at': DateTime.now().toUtc().toIso8601String(), 'fields': fields}),
      flush: true,
    );
    unawaited(flush());
  }

  /// Sends every queued report. Safe to call often: concurrent calls share one run.
  static Future<void> flush() => _flushing ??= _flushQueue().whenComplete(() => _flushing = null);

  static Future<void> _flushQueue() async {
    final dir = await _queueDir();
    final reports = dir.listSync().whereType<File>().where((f) => f.path.endsWith('.json')).toList()
      ..sort((a, b) => a.path.compareTo(b.path));

    for (final report in reports) {
      final Map<String, dynamic> saved;
      try {
        saved = jsonDecode(await report.readAsString()) as Map<String, dynamic>;
      } catch (_) {
        await report.delete(); // a half-written file from a crash; nothing to recover
        continue;
      }
      final fields = (saved['fields'] as Map).cast<String, String>();
      var screenshot = File(report.path.replaceFirst(RegExp(r'\.json$'), '.png'));
      var withScreenshot = await screenshot.exists();

      var outcome = await _post(fields, withScreenshot ? screenshot : null);
      if (outcome == Outcome.dropScreenshot) {
        await screenshot.delete();
        withScreenshot = false;
        outcome = await _post(fields, null);
      }

      final createdAt = DateTime.tryParse(saved['created_at'] as String? ?? '') ?? DateTime.now();
      final tooOld = DateTime.now().toUtc().difference(createdAt) > _maxAge;
      if (outcome == Outcome.retryLater && !tooOld) {
        return; // probably offline: keep this and everything after it for the next flush
      }
      if (withScreenshot) await screenshot.delete();
      await report.delete();
    }
  }

  /// What makes a bug report useful. Sent as ordinary fields, so they show in your dashboard.
  static Future<Map<String, String>> deviceInfo() async {
    final package = await PackageInfo.fromPlatform();
    final plugin = DeviceInfoPlugin();
    var os = Platform.operatingSystem;
    var device = 'unknown';
    if (Platform.isAndroid) {
      final android = await plugin.androidInfo;
      os = 'Android ${android.version.release} (API ${android.version.sdkInt})';
      device = '${android.manufacturer} ${android.model}';
    } else if (Platform.isIOS) {
      final ios = await plugin.iosInfo;
      os = '${ios.systemName} ${ios.systemVersion}';
      device = ios.utsname.machine; // e.g. iPhone16,1; map it to a marketing name if you like
    }
    return {
      'platform': Platform.operatingSystem,
      'app_version': package.version,
      'app_build': package.buildNumber,
      'os_version': os,
      'device': device,
      'locale': ui.PlatformDispatcher.instance.locale.toLanguageTag(),
      '_subject': 'Feedback: ${package.appName} ${package.version}',
    };
  }

  /// Captures whatever is inside the RepaintBoundary with [boundaryKey] as PNG bytes, downscaled.
  /// Take it before opening the feedback screen, so it shows what the user was looking at.
  static Future<Uint8List?> captureScreenshot(GlobalKey boundaryKey) async {
    final boundary = boundaryKey.currentContext?.findRenderObject() as RenderRepaintBoundary?;
    if (boundary == null) return null;
    final size = boundary.size;
    final view = ui.PlatformDispatcher.instance.views.first;
    final pixelRatio = min(view.devicePixelRatio, _screenshotMaxSide / max(size.width, size.height));
    final image = await boundary.toImage(pixelRatio: pixelRatio);
    final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
    image.dispose();
    return bytes?.buffer.asUint8List();
  }

  /// Error codes that mean "the report is fine, the file isn't". The report is resent without it.
  static const _screenshotRefused = {'PLAN_LIMIT', 'FILE_TOO_LARGE', 'FORM_NOT_CLAIMED', 'REQUEST_TOO_LARGE'};

  /// Pure, so it's unit tested without a device. See the root README for the reasoning per code.
  @visibleForTesting
  static Outcome classify(int status, String? code, {required bool hadScreenshot}) {
    if (status >= 200 && status < 300) return Outcome.sent;
    if (hadScreenshot && _screenshotRefused.contains(code)) return Outcome.dropScreenshot;
    if (status == 408 || status == 429 || status >= 500) return Outcome.retryLater;
    return Outcome.rejected;
  }

  static Future<Outcome> _post(Map<String, String> fields, File? screenshot) async {
    final request = http.MultipartRequest('POST', Uri.parse('$_endpoint$formId'))
      ..headers['Accept'] = 'application/json'
      ..fields.addAll(fields);
    if (screenshot != null) {
      request.files.add(await http.MultipartFile.fromPath('screenshot', screenshot.path, filename: 'screenshot.png'));
    }
    try {
      final response = await http.Response.fromStream(await request.send().timeout(const Duration(seconds: 30)));
      Map<String, dynamic>? json;
      try {
        json = jsonDecode(response.body) as Map<String, dynamic>;
      } catch (_) {}

      // An unclaimed form still accepts reports (it holds up to 10 for 48 hours) and answers with
      // its claim link. Printed so you can find it in the console while setting up.
      final claimUrl = json?['claim_url'];
      if (claimUrl is String) debugPrint('BootFormFeedback: form not claimed yet. Claim it at $claimUrl');
      if (response.statusCode >= 400) debugPrint('BootFormFeedback: BootForm answered ${response.statusCode}: ${response.body}');

      return classify(response.statusCode, json?['code'] as String?, hadScreenshot: screenshot != null);
    } on Exception catch (e) {
      debugPrint('BootFormFeedback: network error, will retry: $e');
      return Outcome.retryLater;
    }
  }

  static Future<Directory> _queueDir() async {
    final dir = Directory('${(await getApplicationSupportDirectory()).path}/bootform-feedback');
    return dir.create(recursive: true);
  }

  /// Time-ordered, so the queue sends oldest first. Unique enough for one device's reports.
  static String _newId() {
    final random = Random.secure();
    final suffix = List.generate(8, (_) => random.nextInt(16).toRadixString(16)).join();
    return '${DateTime.now().toUtc().microsecondsSinceEpoch}-$suffix';
  }
}

enum Outcome { sent, dropScreenshot, retryLater, rejected }
