package com.example.whisperdemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Proof-of-concept: capture microphone audio and convert speech to text.
 *
 * Uses Android's built-in [SpeechRecognizer], which performs on-device (or system
 * service) speech recognition and supports selecting the recognition language.
 * Spanish ("es-ES") and English ("en-US") can be toggled at runtime.
 *
 * Flow:
 *   1. Ensure RECORD_AUDIO runtime permission.
 *   2. Build a recognition [Intent] with the chosen language.
 *   3. Stream partial results live, then append final results on completion.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var toggleButton: Button
    private lateinit var langButton: Button
    private lateinit var resultScroller: ScrollView

    private var isListening = false
    private var currentLangIndex = 0
    private var recognizerReady = false

    // Recognizer locales. Add more here (e.g. "en-GB", "es-MX") as desired.
    private val languages = arrayOf("en-US", "es-ES")
    private val languageNames = arrayOf("English (US)", "Español (ES)")

    // Accumulates finalized segments across multiple recognition sessions.
    private val transcriptBuilder = StringBuilder()

    // Register the permission request callback up front.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            statusText.text = getString(R.string.status_permission_denied)
            Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        toggleButton = findViewById(R.id.toggleButton)
        langButton = findViewById(R.id.langButton)
        resultScroller = findViewById(R.id.resultScroller)

        // Edge-to-edge: apply system-bar insets as padding so content is never
        // obscured by the status/navigation bars (mandatory for targetSdk 35+).
        val contentPadding = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<android.view.View>(R.id.rootLayout)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = contentPadding + bars.left,
                top = contentPadding,
                right = contentPadding + bars.right,
                bottom = contentPadding + bars.bottom
            )
            insets
        }

        // Verify the device actually offers speech recognition.
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = getString(R.string.error_no_recognition)
            toggleButton.isEnabled = false
            langButton.isEnabled = false
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(createRecognitionListener())
        recognizerReady = true

        langButton.text = languageNames[currentLangIndex]
        toggleButton.setOnClickListener { onToggleClicked() }
        langButton.setOnClickListener { cycleLanguage() }

        updateButtonStates()
    }

    private fun onToggleClicked() {
        if (!recognizerReady) return
        if (isListening) {
            stopListening()
        } else if (hasMicPermission()) {
            startListening()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun cycleLanguage() {
        currentLangIndex = (currentLangIndex + 1) % languages.size
        langButton.text = languageNames[currentLangIndex]
        Toast.makeText(this, languageNames[currentLangIndex], Toast.LENGTH_SHORT).show()
    }

    private fun startListening() {
        transcriptBuilder.clear()
        resultText.text = ""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // The key line for bilingual support: set the recognition language.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languages[currentLangIndex])
            // Request live partial results for a responsive UI.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.startListening(intent)
        isListening = true
        updateButtonStates()
    }

    private fun stopListening() {
        speechRecognizer.stopListening()
        isListening = false
        updateButtonStates()
    }

    private fun createRecognitionListener(): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = getString(R.string.status_listening)
            }

            override fun onBeginningOfSpeech() {
                statusText.text = getString(R.string.status_speaking)
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                statusText.text = getString(R.string.status_processing)
            }

            override fun onError(error: Int) {
                isListening = false
                updateButtonStates()
                statusText.text =
                    getString(R.string.status_error_format, errorCodeToString(error))
            }

            override fun onResults(results: Bundle?) {
                val matches =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    transcriptBuilder.append(matches[0]).append(" ")
                    resultText.text = transcriptBuilder.toString().trim()
                    resultScroller.post { resultScroller.fullScroll(ScrollView.FOCUS_DOWN) }
                }
                statusText.text = getString(R.string.status_tap_to_continue)
                isListening = false
                updateButtonStates()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    resultText.text = "${transcriptBuilder}${partial[0]}"
                    resultScroller.post { resultScroller.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

    private fun updateButtonStates() {
        toggleButton.text = if (isListening)
            getString(R.string.stop) else getString(R.string.start)
        if (!isListening && statusText.text.isBlank()) {
            statusText.text = getString(R.string.status_ready)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /** Human-readable labels for the most common recognition error codes. */
    private fun errorCodeToString(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NETWORK -> "network error"
        SpeechRecognizer.ERROR_AUDIO -> "audio error"
        SpeechRecognizer.ERROR_SERVER -> "server error"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech detected"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "insufficient permissions"
        else -> "unknown error ($error)"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (recognizerReady) {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
    }
}
