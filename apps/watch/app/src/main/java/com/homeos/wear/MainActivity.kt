package com.homeos.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val vm: WatchViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.start() }

    // On-device speech (Google recognizer on the watch) when the backend has no Sarvam key.
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (text != null) vm.ask(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        permissions.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION,
                "android.permission.HIGH_SAMPLING_RATE_SENSORS",
            ),
        )
        setContent {
            val ui by vm.ui.collectAsState()
            WatchScreen(
                ui = ui,
                onChip = vm::chip,
                onMic = { if (ui.serverVoice) vm.toggleRecording() else listen() },
                onPrompt = vm::answerPrompt,
                onStopPacer = vm::stopPacer,
            )
        }
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask HomeOS")
        runCatching { speech.launch(i) }
    }

    override fun onDestroy() {
        if (isFinishing) vm.stop()
        super.onDestroy()
    }
}
