import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'bootform_feedback.dart';

/// A plain feedback form. Restyle it freely: everything BootForm-specific is in
/// bootform_feedback.dart. [screenshot] is what the screen looked like before this form opened.
class FeedbackScreen extends StatefulWidget {
  const FeedbackScreen({super.key, this.screenshot});

  final Uint8List? screenshot;

  @override
  State<FeedbackScreen> createState() => _FeedbackScreenState();
}

class _FeedbackScreenState extends State<FeedbackScreen> {
  final _message = TextEditingController();
  final _email = TextEditingController();
  late bool _includeScreenshot = widget.screenshot != null;
  bool _saving = false;
  bool _saved = false;

  @override
  void dispose() {
    _message.dispose();
    _email.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    setState(() => _saving = true);
    await BootFormFeedback.send(
      message: _message.text.trim(),
      email: _email.text.trim(),
      screenshotPng: _includeScreenshot ? widget.screenshot : null,
    );
    if (mounted) setState(() => _saved = true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Send feedback')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: _saved
              ? [
                  const Text("Thank you! It's saved, and it will be sent as soon as you're online."),
                  const SizedBox(height: 16),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: FilledButton(onPressed: () => Navigator.pop(context), child: const Text('Done')),
                  ),
                ]
              : [
                  TextField(
                    controller: _message,
                    maxLines: 6,
                    maxLength: 5000,
                    onChanged: (_) => setState(() {}),
                    decoration: const InputDecoration(
                      labelText: 'What happened, or what should we change?',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _email,
                    keyboardType: TextInputType.emailAddress,
                    decoration: const InputDecoration(
                      labelText: 'Your email (optional, so we can reply)',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  if (widget.screenshot != null) ...[
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      controlAffinity: ListTileControlAffinity.leading,
                      value: _includeScreenshot,
                      onChanged: (value) => setState(() => _includeScreenshot = value ?? false),
                      title: const Text('Include a screenshot of the screen you were on'),
                    ),
                    if (_includeScreenshot)
                      Align(alignment: Alignment.centerLeft, child: Image.memory(widget.screenshot!, height: 200)),
                  ],
                  const SizedBox(height: 16),
                  Row(children: [
                    FilledButton(
                      onPressed: _message.text.trim().isEmpty || _saving ? null : _send,
                      child: Text(_saving ? 'Saving…' : 'Send'),
                    ),
                    const SizedBox(width: 8),
                    TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
                  ]),
                ],
        ),
      ),
    );
  }
}
