package com.foxybook.app.core.tts

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class VoiceInfo(
    val id: String,
    val language: String,
    val name: String
)

data class EngineInfo(
    val packageName: String,
    val label: String
)

data class TtsState(
    val isSpeaking: Boolean = false,
    val isPaused: Boolean = false,
    val availableVoices: List<VoiceInfo> = emptyList(),
    val availableLanguages: List<String> = emptyList(),
    val sleepTimerRemainingSeconds: Long = 0L,
    val currentEngine: String? = null,
    val availableEngines: List<EngineInfo> = emptyList()
)

/**
 * Manages TTS engine lifecycle, voice loading, and sleep timer.
 * Delegates actual speech to TtsService and exposes state via StateFlow.
 * Does NOT know about book chapters or block iteration — that's ReaderViewModel's job.
 */
class TtsManager(private val application: Application) {

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var ttsService: TtsService? = null
    private var isBound = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepTimerUpdateJob: Job? = null
    private var pendingEnginePackage: String? = null

    var onBlockCompleted: (() -> Unit)? = null
    var onCommand: ((String) -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TtsService.TtsBinder
            ttsService = binder.getService()
            isBound = true
            setupServiceCallbacks()
            // Применяем сохранённый движок, если есть
            pendingEnginePackage?.let {
                switchEngine(it)
                pendingEnginePackage = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ttsService = null
            isBound = false
        }
    }

    init {
        Intent(application, TtsService::class.java).also { intent ->
            application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun setupServiceCallbacks() {
        ttsService?.onBlockCompleted = { onBlockCompleted?.invoke() }
        ttsService?.onCommand = { cmd -> onCommand?.invoke(cmd) }
        ttsService?.onInitComplete = {
            loadVoices()
            loadEngines()
        }
        loadVoices()
        loadEngines()
    }

    fun switchEngine(packageName: String?) {
        if (isBound && ttsService != null) {
            ttsService?.switchEngine(packageName)
            _state.update { it.copy(currentEngine = packageName) }
        } else {
            // Сервис ещё не подключён — сохраняем для применения после подключения
            pendingEnginePackage = packageName
            _state.update { it.copy(currentEngine = packageName) }
        }
    }

    fun getEngineList(): List<EngineInfo> {
        return ttsService?.getEngines()?.map {
            EngineInfo(packageName = it.packageName, label = it.label)
        } ?: emptyList()
    }

    private fun loadEngines() {
        val engines = ttsService?.getEngines()?.map {
            EngineInfo(packageName = it.packageName, label = it.label)
        } ?: emptyList()
        _state.update { it.copy(availableEngines = engines) }
    }

    fun speak(text: String, bookTitle: String, chapterTitle: String, rate: Float, pitch: Float, voiceName: String?) {
        ttsService?.startReading(text, bookTitle, chapterTitle, rate, pitch, voiceName)
        _state.update { it.copy(isSpeaking = true, isPaused = false) }
    }

    fun pause() {
        ttsService?.pauseReading()
        _state.update { it.copy(isSpeaking = false, isPaused = true) }
    }

    fun stop() {
        ttsService?.stopReading()
        cancelSleepTimer()
        _state.update { it.copy(isSpeaking = false, isPaused = false, sleepTimerRemainingSeconds = 0L) }
    }

    fun setSleepTimer(minutes: Int) {
        if (minutes > 0) {
            ttsService?.startSleepTimer(minutes)
            _state.update { it.copy(sleepTimerRemainingSeconds = minutes * 60L) }
            startSleepTimerUpdates()
        } else {
            cancelSleepTimer()
        }
    }

    fun cancelSleepTimer() {
        ttsService?.stopSleepTimer()
        stopSleepTimerUpdates()
        _state.update { it.copy(sleepTimerRemainingSeconds = 0L) }
    }

    private fun loadVoices() {
        val service = ttsService ?: return

        // Обновляем текущий движок
        _state.update { it.copy(currentEngine = service.getCurrentEngine()) }

        val voices = service.getVoices()
        val voiceInfos = voices.map { voice ->
            val lang = voice.locale.getDisplayLanguage(Locale("ru")).replaceFirstChar { it.uppercase() }
            VoiceInfo(
                id = voice.name,
                language = lang,
                name = if (voice.name.contains("female")) "Женский"
                       else if (voice.name.contains("male")) "Мужской"
                       else voice.name.substringAfterLast("-")
            )
        }.sortedWith(compareBy(
            { it.language != "Русский" },
            { it.language != "Английский" },
            { it.language }
        ))
        val languages = voiceInfos.map { it.language }.distinct()
        _state.update { it.copy(availableVoices = voiceInfos, availableLanguages = languages) }
    }

    private fun startSleepTimerUpdates() {
        sleepTimerUpdateJob?.cancel()
        sleepTimerUpdateJob = scope.launch {
            while (true) {
                delay(1000L)
                val remaining = ttsService?.getSleepTimerRemaining() ?: 0L
                if (remaining <= 0) {
                    _state.update { it.copy(sleepTimerRemainingSeconds = 0L, isSpeaking = false, isPaused = false) }
                    break
                }
                _state.update { it.copy(sleepTimerRemainingSeconds = remaining) }
            }
        }
    }

    private fun stopSleepTimerUpdates() {
        sleepTimerUpdateJob?.cancel()
        sleepTimerUpdateJob = null
    }

    fun destroy() {
        sleepTimerUpdateJob?.cancel()
        sleepTimerUpdateJob = null
        onBlockCompleted = null
        onCommand = null
        ttsService?.onInitComplete = null
        if (isBound) {
            application.unbindService(connection)
            isBound = false
        }
        scope.cancel()
    }
}
