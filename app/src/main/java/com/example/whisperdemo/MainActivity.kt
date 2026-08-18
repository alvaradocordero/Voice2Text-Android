package com.example.whisperdemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.whisperdemo.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Speech-to-Text Android App demonstration.
 * Uses MVVM architecture, Jetpack View Binding, StateFlows, and lifecycle-aware state handling.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerReady = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            viewModel.setStatusText(getString(R.string.status_permission_denied))
            Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdgePadding()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            viewModel.setStatusText(getString(R.string.error_no_recognition))
            binding.toggleButton.isEnabled = false
            binding.langButton.isEnabled = false
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(createRecognitionListener())
        }
        recognizerReady = true

        binding.toggleButton.setOnClickListener { onToggleClicked() }
        binding.langButton.setOnClickListener { cycleLanguage() }

        observeViewModel()
    }

    private fun setupEdgeToEdgePadding() {
        val contentPadding = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = contentPadding + bars.left,
                top = contentPadding,
                right = contentPadding + bars.right,
                bottom = contentPadding + bars.bottom
            )
            insets
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedLanguage.collect { language ->
                        binding.langButton.text = language.displayName
                    }
                }
                launch {
                    viewModel.isListening.collect { isListening ->
                        binding.toggleButton.text = if (isListening) {
                            getString(R.string.stop)
                        } else {
                            getString(R.string.start)
                        }
                    }
                }
                launch {
                    viewModel.statusText.collect { status ->
                        if (status.isNotBlank()) {
                            binding.statusText.text = status
                        } else if (!viewModel.isListening.value) {
                            binding.statusText.text = getString(R.string.status_ready)
                        }
                    }
                }
                launch {
                    viewModel.transcriptText.collect { text ->
                        binding.resultText.text = text
                        binding.resultScroller.post {
                            binding.resultScroller.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                    }
                }
            }
        }
    }

    private fun onToggleClicked() {
        if (!recognizerReady) return
        if (viewModel.isListening.value) {
            stopListening()
        } else if (hasMicPermission()) {
            startListening()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun cycleLanguage() {
        val newLanguage = viewModel.cycleLanguage()
        Toast.makeText(this, newLanguage.displayName, Toast.LENGTH_SHORT).show()
    }

    private fun startListening() {
        viewModel.prepareNewSession()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, viewModel.selectedLanguage.value.code)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
        viewModel.setListening(true)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        viewModel.setListening(false)
    }

    private fun createRecognitionListener(): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                viewModel.setStatusText(getString(R.string.status_listening))
            }

            override fun onBeginningOfSpeech() {
                viewModel.setStatusText(getString(R.string.status_speaking))
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                viewModel.setStatusText(getString(R.string.status_processing))
            }

            override fun onError(error: Int) {
                viewModel.setListening(false)
                viewModel.setStatusText(
                    getString(R.string.status_error_format, errorCodeToString(error))
                )
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    viewModel.appendFinalResult(matches[0])
                }
                viewModel.setStatusText(getString(R.string.status_tap_to_continue))
                viewModel.setListening(false)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    binding.resultText.text = viewModel.getPreviewWithPartial(partial[0])
                    binding.resultScroller.post {
                        binding.resultScroller.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

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

    override fun onStop() {
        super.onStop()
        if (viewModel.isListening.value) {
            stopListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (recognizerReady) {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
}
