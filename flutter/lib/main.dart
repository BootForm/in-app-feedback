import 'package:flutter/material.dart';

import 'bootform_feedback.dart';
import 'feedback_screen.dart';

void main() => runApp(const DemoApp());

/// A stand-in for your app: one screen with a "Send feedback" button.
class DemoApp extends StatefulWidget {
  const DemoApp({super.key});

  @override
  State<DemoApp> createState() => _DemoAppState();
}

class _DemoAppState extends State<DemoApp> {
  /// Wraps the whole app, so a screenshot shows exactly what the user saw.
  final _screenshotKey = GlobalKey();
  final _navigatorKey = GlobalKey<NavigatorState>();
  late final AppLifecycleListener _lifecycle;

  @override
  void initState() {
    super.initState();
    // Send anything written while offline: once now, and every time the app comes back to the front.
    BootFormFeedback.flush();
    _lifecycle = AppLifecycleListener(onResume: BootFormFeedback.flush);
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    super.dispose();
  }

  Future<void> _openFeedback() async {
    final screenshot = await BootFormFeedback.captureScreenshot(_screenshotKey);
    await _navigatorKey.currentState!.push(
      MaterialPageRoute<void>(builder: (_) => FeedbackScreen(screenshot: screenshot)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Feedback demo',
      navigatorKey: _navigatorKey,
      builder: (context, child) => RepaintBoundary(key: _screenshotKey, child: child),
      home: Scaffold(
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Your app goes here', style: Theme.of(context).textTheme.headlineSmall),
                const SizedBox(height: 16),
                const Text("Something broken or missing? Tell us, and we'll see the app version and device too."),
                const SizedBox(height: 16),
                FilledButton(onPressed: _openFeedback, child: const Text('Send feedback')),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
