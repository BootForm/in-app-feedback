package com.bootform.feedback

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap

/** A stand-in for your app: one screen with a "Send feedback" button. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // safeDrawingPadding keeps content clear of the status bar, navigation bar and keyboard,
                // which Android 15 and later draw over the app by default.
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    // The screenshot is taken before the feedback form opens, so it shows the screen
                    // the user was actually looking at, not the form itself.
                    var feedbackScreenshot by remember { mutableStateOf<Bitmap?>(null) }
                    var showFeedback by remember { mutableStateOf(false) }

                    if (showFeedback) {
                        FeedbackScreen(screenshot = feedbackScreenshot, onClose = { showFeedback = false })
                    } else {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Your app goes here", style = MaterialTheme.typography.headlineSmall)
                            Text("Something broken or missing? Tell us, and we'll see the app version and device too.")
                            Button(onClick = {
                                feedbackScreenshot = runCatching { window.decorView.rootView.drawToBitmap() }.getOrNull()
                                showFeedback = true
                            }) { Text("Send feedback") }
                        }
                    }
                }
            }
        }
    }
}
