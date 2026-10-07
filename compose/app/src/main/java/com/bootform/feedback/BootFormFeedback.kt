package com.bootform.feedback

// The one file to copy into your app. It sends a feedback report to BootForm with the device
// details attached, queues it while the phone is offline, and drops the screenshot (keeping the
// report) when your plan doesn't accept files.
//
// Needs: androidx.work:work-runtime-ktx, androidx.core:core-ktx, and the INTERNET permission.

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.graphics.scale
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object BootFormFeedback {

    /**
     * Your form ID. Generate your own UUID and claim it at https://app.bootform.com/claim/{your-id}
     * before you ship. Never use an ID copied from a tutorial: whoever claims an ID first owns it.
     */
    const val FORM_ID = "__YOUR_FORM_ID__"

    internal const val ENDPOINT = "https://f.bootform.com/"

    /** Longest side of the screenshot after downscaling. Keeps a JPEG around 100 to 400 KB. */
    private const val SCREENSHOT_MAX_SIDE = 1280

    private const val FIELD_PREFIX = "field."
    private const val KEY_SCREENSHOT = "screenshot_path"
    private const val TAG = "BootFormFeedback"

    /**
     * Queues a report and returns its WorkManager id, which you can observe for its status. Returns
     * immediately: the report is sent when the phone has a connection, even if the app is closed.
     */
    fun send(context: Context, message: String, email: String? = null, screenshot: Bitmap? = null): UUID {
        check(FORM_ID != "__YOUR_FORM_ID__") { "Set BootFormFeedback.FORM_ID to your own form ID first." }

        val reportId = UUID.randomUUID().toString()
        val fields = LinkedHashMap<String, String>()
        fields["message"] = message
        fields["report_id"] = reportId
        if (!email.isNullOrBlank()) {
            fields["email"] = email
            fields["_replyto"] = email
        }
        fields += deviceInfo(context)

        val data = Data.Builder()
        fields.forEach { (name, value) -> data.putString(FIELD_PREFIX + name, value) }
        screenshot?.let { data.putString(KEY_SCREENSHOT, saveScreenshot(context, reportId, it).path) }

        val request = OneTimeWorkRequestBuilder<FeedbackWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(data.build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    /** What makes a bug report useful. Sent as ordinary fields, so they show in your dashboard. */
    fun deviceInfo(context: Context): Map<String, String> {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        val build = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        return linkedMapOf(
            "platform" to "android",
            "app_version" to (pkg.versionName ?: "unknown"),
            "app_build" to build.toString(),
            "os_version" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "locale" to Locale.getDefault().toLanguageTag(),
            "_subject" to "Feedback: $appName ${pkg.versionName ?: ""}".trim(),
        )
    }

    private fun saveScreenshot(context: Context, reportId: String, bitmap: Bitmap): File {
        val ratio = SCREENSHOT_MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (ratio < 1f) bitmap.scale((bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt()) else bitmap
        val dir = File(context.noBackupFilesDir, "bootform-feedback").apply { mkdirs() }
        val file = File(dir, "$reportId.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 70, it) }
        return file
    }

    internal enum class Outcome { Sent, DropScreenshot, RetryLater, Rejected }

    /** Error codes that mean "the report is fine, the file isn't". The report is resent without it. */
    private val SCREENSHOT_REFUSED = setOf("PLAN_LIMIT", "FILE_TOO_LARGE", "FORM_NOT_CLAIMED", "REQUEST_TOO_LARGE")

    /** Pure, so it's unit tested without a device. See the root README for the reasoning per code. */
    internal fun classify(status: Int, code: String?, hadScreenshot: Boolean): Outcome = when {
        status in 200..299 -> Outcome.Sent
        hadScreenshot && code in SCREENSHOT_REFUSED -> Outcome.DropScreenshot
        status == 408 || status == 429 || status >= 500 -> Outcome.RetryLater
        else -> Outcome.Rejected
    }

    internal fun post(fields: Map<String, String>, screenshot: File?): Outcome {
        val boundary = "bootform-" + UUID.randomUUID()
        val connection = URL(ENDPOINT + FORM_ID).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "application/json")

            connection.outputStream.buffered().use { out ->
                fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
                for ((name, value) in fields) {
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                if (screenshot != null) {
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"screenshot\"; filename=\"screenshot.jpg\"\r\n")
                    write("Content-Type: image/jpeg\r\n\r\n")
                    screenshot.inputStream().use { it.copyTo(out) }
                    write("\r\n")
                }
                write("--$boundary--\r\n")
            }

            val status = connection.responseCode
            val stream = if (status < 400) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()

            // An unclaimed form still accepts reports (it holds up to 10 for 48 hours) and answers
            // with its claim link. Logged so you can find it in Logcat while setting up.
            json?.optString("claim_url")?.takeIf { it.isNotEmpty() }?.let { Log.i(TAG, "Form not claimed yet. Claim it at $it") }
            if (status >= 400) Log.w(TAG, "BootForm answered $status: $body")

            classify(status, json?.optString("code")?.takeIf { it.isNotEmpty() }, screenshot != null)
        } catch (e: IOException) {
            Log.w(TAG, "Network error, will retry", e)
            Outcome.RetryLater
        } finally {
            connection.disconnect()
        }
    }

    class FeedbackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val fields = inputData.keyValueMap
                .filterKeys { it.startsWith(FIELD_PREFIX) }
                .map { (key, value) -> key.removePrefix(FIELD_PREFIX) to value.toString() }
                .toMap()
            var screenshot = inputData.getString(KEY_SCREENSHOT)?.let(::File)?.takeIf { it.exists() }

            var outcome = post(fields, screenshot)
            if (outcome == Outcome.DropScreenshot) {
                screenshot?.delete()
                screenshot = null
                outcome = post(fields, null)
            }

            when {
                outcome == Outcome.Sent -> { screenshot?.delete(); Result.success() }
                outcome == Outcome.RetryLater && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                else -> { screenshot?.delete(); Result.failure() }
            }
        }

        private companion object {
            // Exponential backoff from 30 seconds, capped by WorkManager at 5 hours between tries:
            // about a day and a half of trying while online, then the report is given up.
            const val MAX_ATTEMPTS = 15
        }
    }
}
