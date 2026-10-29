package com.voiceclonestudio.app

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import android.view.Gravity
import android.widget.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : Activity() {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var selectedUri: Uri? = null
    private var outputFile: File? = null
    private var player: MediaPlayer? = null
    private lateinit var status: TextView
    private lateinit var generate: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 42, 32, 32)
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        }
        fun button(label: String, action: () -> Unit): Button {
            val b = Button(this).apply { text = label; setOnClickListener { action() } }
            layout.addView(b)
            return b
        }
        layout.addView(TextView(this).apply {
            text = "استودیو کلون صدا"
            textSize = 24f
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply {
            text = "صدای مرجع را ضبط یا انتخاب کنید."
            textSize = 16f
        }
        layout.addView(status)
        button("ضبط صدا") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            else startRecording()
        }
        button("پایان ضبط") { stopRecording() }
        button("انتخاب فایل صوتی") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, 101)
        }
        val textInput = EditText(this).apply {
            hint = "متن فارسی را وارد کنید"
            minLines = 3
            gravity = Gravity.TOP or Gravity.RIGHT
        }
        layout.addView(textInput)
        val endpoint = EditText(this).apply {
            hint = "https://your-server.example"
            setSingleLine(true)
            setText(getPreferences(MODE_PRIVATE).getString("server", ""))
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
        }
        layout.addView(endpoint)
        generate = button("تولید صدا") {
            val url = endpoint.text.toString().trim().trimEnd('/')
            val text = textInput.text.toString().trim()
            if (!url.startsWith("https://")) {
                status.text = "آدرس سرور باید با https:// شروع شود."
            } else if (recordingFile == null && selectedUri == null) {
                status.text = "ابتدا صدای مرجع را انتخاب کنید."
            } else if (recorder != null) {
                status.text = "ابتدا ضبط را پایان دهید."
            } else if (text.isEmpty()) {
                status.text = "ابتدا متن فارسی را وارد کنید."
            } else {
                getPreferences(MODE_PRIVATE).edit().putString("server", url).apply()
                sendRequest(url, text)
            }
        }
        button("پخش خروجی") {
            val file = outputFile
            if (file == null) status.text = "هنوز خروجی دریافت نشده است."
            else try {
                player?.release()
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                }
            } catch (e: Exception) { status.text = "خطای پخش: ${e.localizedMessage}" }
        }
        button("ذخیره خروجی") { saveOutput() }
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun sendRequest(base: String, text: String) {
        generate.isEnabled = false
        status.text = "در حال ارسال و پردازش..."
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val boundary = "VoiceClone" + UUID.randomUUID().toString()
                connection = (URL("$base/clone").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 180000
                    doOutput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("Accept", "audio/wav")
                }
                val crlf = "\r\n"
                connection.outputStream.use { out ->
                    fun write(value: String) { out.write(value.toByteArray(Charsets.UTF_8)) }
                    write("--$boundary$crlf")
                    write("Content-Disposition: form-data; name=\"text\"$crlf$crlf")
                    write(text + crlf)
                    write("--$boundary$crlf")
                    write("Content-Disposition: form-data; name=\"reference\"; filename=\"reference.audio\"$crlf")
                    write("Content-Type: application/octet-stream$crlf$crlf")
                    val uri = selectedUri
                    if (uri != null) contentResolver.openInputStream(uri)?.use { it.copyTo(out) }
                        ?: error("فایل انتخاب‌شده قابل خواندن نیست.")
                    else recordingFile?.inputStream()?.use { it.copyTo(out) }
                        ?: error("فایل ضبط‌شده وجود ندارد.")
                    write(crlf + "--$boundary--$crlf")
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val msg = connection.errorStream?.bufferedReader()?.use { it.readText().take(400) } ?: ""
                    error("سرور خطای $code برگرداند: $msg")
                }
                val mime = connection.contentType ?: ""
                if (!mime.contains("audio/")) error("پاسخ سرور فایل صوتی نیست: $mime")
                val file = File(cacheDir, "output.wav")
                connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                outputFile = file
                runOnUiThread { status.text = "صدا آماده است. پخش یا ذخیره کنید." }
            } catch (e: Exception) {
                runOnUiThread { status.text = "خطا: ${e.localizedMessage}" }
            } finally {
                connection?.disconnect()
                runOnUiThread { generate.isEnabled = true }
            }
        }.start()
    }

    private fun saveOutput() {
        val file = outputFile ?: run { status.text = "هنوز خروجی وجود ندارد."; return }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "VoiceClone-${System.currentTimeMillis()}.wav")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/VoiceCloneStudio")
            }
            val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("ساخت فایل ممکن نیست.")
            contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("ذخیره فایل ممکن نیست.")
            status.text = "فایل در پوشه Music/VoiceCloneStudio ذخیره شد."
        } catch (e: Exception) { status.text = "خطای ذخیره: ${e.localizedMessage}" }
    }

    private fun startRecording() {
        try {
            recordingFile = File(cacheDir, "reference.m4a")
            recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile!!.absolutePath)
                prepare()
                start()
            }
            selectedUri = null
            status.text = "در حال ضبط..."
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            status.text = "خطای ضبط: ${e.localizedMessage}"
        }
    }

    private fun stopRecording() {
        val active = recorder ?: return
        try {
            active.stop()
            status.text = "ضبط ذخیره شد."
        } catch (e: Exception) {
            recordingFile?.delete()
            recordingFile = null
            status.text = "ضبط نامعتبر یا بسیار کوتاه بود."
        } finally {
            active.release()
            recorder = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            selectedUri = data?.data
            recordingFile = null
            status.text = if (selectedUri != null) "فایل صوتی انتخاب شد." else "انتخاب فایل لغو شد."
        }
    }

    override fun onDestroy() {
        player?.release()
        recorder?.release()
        super.onDestroy()
    }
}
