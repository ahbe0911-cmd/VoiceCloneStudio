package com.voiceclonestudio.app

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var selectedUri: Uri? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 56, 36, 36)
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        }
        fun button(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        layout.addView(TextView(this).apply {
            text = "استودیو کلون صدا"
            textSize = 24f
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply { text = "صدای مرجع را ضبط یا انتخاب کنید."; textSize = 16f }
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
        val input = EditText(this).apply {
            hint = "متن فارسی خود را وارد کنید"
            minLines = 4
            gravity = Gravity.TOP or Gravity.RIGHT
        }
        layout.addView(input)
        button("تولید صدا") {
            status.text = if (selectedUri == null && recordingFile == null)
                "ابتدا صدای مرجع انتخاب کنید."
            else if (input.text.isBlank()) "ابتدا متن فارسی را وارد کنید."
            else "موتور هوش مصنوعی هنوز متصل نشده است."
        }
        setContentView(ScrollView(this).apply { addView(layout) })
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
        recorder?.release()
        super.onDestroy()
    }
}
