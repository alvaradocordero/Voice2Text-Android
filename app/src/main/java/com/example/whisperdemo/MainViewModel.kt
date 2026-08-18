package com.example.whisperdemo

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel to manage speech recognition UI state and retain transcripts across configuration changes.
 */
class MainViewModel : ViewModel() {

    val supportedLanguages = listOf(
        LanguageOption("en-US", "English (US)"),
        LanguageOption("es-ES", "Español (ES)")
    )

    private var currentLangIndex = 0

    private val _selectedLanguage = MutableStateFlow(supportedLanguages[currentLangIndex])
    val selectedLanguage: StateFlow<LanguageOption> = _selectedLanguage.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _transcriptText = MutableStateFlow("")
    val transcriptText: StateFlow<String> = _transcriptText.asStateFlow()

    private val transcriptBuilder = StringBuilder()

    fun cycleLanguage(): LanguageOption {
        currentLangIndex = (currentLangIndex + 1) % supportedLanguages.size
        val newLang = supportedLanguages[currentLangIndex]
        _selectedLanguage.value = newLang
        return newLang
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun setStatusText(status: String) {
        _statusText.value = status
    }

    fun prepareNewSession() {
        transcriptBuilder.clear()
        _transcriptText.value = ""
    }

    fun appendFinalResult(match: String) {
        if (match.isNotBlank()) {
            transcriptBuilder.append(match).append(" ")
            _transcriptText.value = transcriptBuilder.toString().trim()
        }
    }

    fun getPreviewWithPartial(partial: String): String {
        return if (partial.isNotBlank()) {
            "${transcriptBuilder}${partial}"
        } else {
            _transcriptText.value
        }
    }
}
