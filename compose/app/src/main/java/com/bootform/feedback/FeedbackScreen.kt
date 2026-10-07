package com.bootform.feedback

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * A plain feedback form. Restyle it freely: everything BootForm-specific is in BootFormFeedback.kt.
 * [screenshot] is what the screen looked like before this form opened, or null for none.
 */
@Composable
fun FeedbackScreen(screenshot: Bitmap?, onClose: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var includeScreenshot by remember { mutableStateOf(screenshot != null) }
    var sentId by remember { mutableStateOf<UUID?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Send feedback", style = MaterialTheme.typography.headlineSmall)

        val id = sentId
        if (id != null) {
            SendStatus(id)
            Button(onClick = onClose) { Text("Done") }
            return@Column
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(5000) },
            label = { Text("What happened, or what should we change?") },
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Your email (optional, so we can reply)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (screenshot != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeScreenshot, onCheckedChange = { includeScreenshot = it })
                Text("Include a screenshot of the screen you were on")
            }
            if (includeScreenshot) {
                Image(screenshot.asImageBitmap(), contentDescription = "Screenshot to send", modifier = Modifier.height(200.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = message.isNotBlank(),
                onClick = {
                    sentId = BootFormFeedback.send(
                        context,
                        message = message.trim(),
                        email = email.trim(),
                        screenshot = screenshot.takeIf { includeScreenshot },
                    )
                },
            ) { Text("Send") }
            TextButton(onClick = onClose) { Text("Cancel") }
        }
    }
}

@Composable
private fun SendStatus(id: UUID) {
    val context = LocalContext.current
    val flow = remember(id) { runCatching { WorkManager.getInstance(context).getWorkInfoByIdFlow(id) }.getOrElse { flowOf(null) } }
    val info by flow.collectAsState(initial = null)
    val text = when (info?.state) {
        WorkInfo.State.SUCCEEDED -> "Sent. Thank you!"
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> "Sorry, this report couldn't be sent."
        WorkInfo.State.RUNNING -> "Sending…"
        else -> "Saved. It will be sent as soon as you're online, even if you close the app."
    }
    Text(text)
}
