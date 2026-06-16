package com.foxybook.app.core.tts

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.foxybook.app.MainActivity
import kotlinx.coroutines.*

class TtsService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentBookTitle: String = ""
    private var currentChapterTitle: String = ""
    private var isPaused = false

    // TTS engine tracking
    private var currentEnginePackage: String? = null
    private var ttsReady = false

    // Sleep timer
    private var sleepTimerJob: Job? = null
    private var sleepTimerRemainingSeconds: Long = 0L

    var onBlockCompleted: (() -> Unit)? = null
    var onCommand: ((String) -> Unit)? = null
    var onInitComplete: (() -> Unit)? = null

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    private val binder = TtsBinder()

    override fun onCreate() {
        super.onCreate()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "TtsService").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { onCommand?.invoke("RESUME") }
                override fun onPause() { onCommand?.invoke("PAUSE") }
                override fun onStop() { onCommand?.invoke("STOP") }
                override fun onSkipToNext() { onCommand?.invoke("NEXT") }
                override fun onSkipToPrevious() { onCommand?.invoke("PREV") }
            })
            isActive = true
        }
    }

    fun switchEngine(enginePackage: String?) {
        currentEnginePackage = enginePackage
        Log.d("TtsService", "switchEngine: $enginePackage")
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    override fun onInit(status: Int) {
        val success = status == TextToSpeech.SUCCESS
        ttsReady = success
        if (success) {
            try {
                val m = TextToSpeech::class.java.getMethod("getCurrentEngine")
                currentEnginePackage = m.invoke(tts) as? String
            } catch (_: Exception) {}
            Log.d("TtsService", "onInit: SUCCESS, engine=$currentEnginePackage")

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    updatePlaybackState(PlaybackState.STATE_PLAYING)
                }
                override fun onDone(utteranceId: String?) {
                    Log.d("TtsService", "onDone: utteranceId=$utteranceId isPaused=$isPaused")
                    if (!isPaused) {
                        onBlockCompleted?.invoke()
                    }
                }
                override fun onError(utteranceId: String?) {
                    Log.e("TtsService", "onError: utteranceId=$utteranceId")
                    updatePlaybackState(PlaybackState.STATE_ERROR)
                }
            })

            // Если есть отложенный запуск речи — стартуем
            pendingSpeech?.let { (text, bookTitle, chapterTitle, rate, pitch, voiceName) ->
                pendingSpeech = null
                doStartReading(text, bookTitle, chapterTitle, rate, pitch, voiceName)
            }
            onInitComplete?.invoke()
        } else {
            Log.e("TtsService", "onInit: FAILED, status=$status")
        }
    }

    private var pendingSpeech: Params? = null
    private data class Params(
        val text: String, val bookTitle: String, val chapterTitle: String,
        val rate: Float, val pitch: Float, val voiceName: String?
    )

    fun getVoices() = tts?.voices?.toList() ?: emptyList()

    fun getCurrentEngine(): String? = currentEnginePackage

    data class EngineInfo(val packageName: String, val label: String)

    fun getEngines(): List<EngineInfo> {
        val engines = mutableListOf<EngineInfo>()
        try {
            val resolveInfos = packageManager.queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                PackageManager.GET_META_DATA
            )
            for (info in resolveInfos) {
                val pkg = info.serviceInfo.packageName
                val label = info.loadLabel(packageManager)?.toString() ?: pkg.substringAfterLast(".")
                engines.add(EngineInfo(pkg, label))
            }
        } catch (e: Exception) {
            Log.e("TtsService", "Failed to query TTS engines", e)
        }
        return engines
    }

    fun startReading(text: String, bookTitle: String, chapterTitle: String, rate: Float, pitch: Float, voiceName: String?) {
        Log.d("TtsService", "startReading: engine=$currentEnginePackage")

        if (tts == null) {
            Log.d("TtsService", "startReading: creating TTS")
            tts = if (currentEnginePackage != null) {
                TextToSpeech(this, this, currentEnginePackage!!)
            } else {
                TextToSpeech(this, this)
            }
        }

        // Если TTS уже готов — говорим сразу, иначе ждём onInit
        if (ttsReady) {
            doStartReading(text, bookTitle, chapterTitle, rate, pitch, voiceName)
        } else {
            pendingSpeech = Params(text, bookTitle, chapterTitle, rate, pitch, voiceName)
        }
    }

    private fun doStartReading(text: String, bookTitle: String, chapterTitle: String, rate: Float, pitch: Float, voiceName: String?) {
        Log.d("TtsService", "doStartReading | engine=$currentEnginePackage | voiceName=$voiceName")

        currentBookTitle = bookTitle
        currentChapterTitle = chapterTitle
        isPaused = false

        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)

        Log.d("TtsService", "startReading: voiceName=$voiceName")
        Log.d("TtsService", "Current TTS voice before setting: ${tts?.voice?.name}")

        if (voiceName != null) {
            val availableVoices = tts?.voices?.toList() ?: emptyList()
            Log.d("TtsService", "Available voices count: ${availableVoices.size}")

            val selectedVoice = availableVoices.find { it.name == voiceName }
            if (selectedVoice != null) {
                Log.d("TtsService", "Setting voice: ${selectedVoice.name}, locale: ${selectedVoice.locale}")
                val result = tts?.setVoice(selectedVoice)
                Log.d("TtsService", "setVoice result: $result")
                Log.d("TtsService", "Current TTS voice after setting: ${tts?.voice?.name}")
            } else {
                Log.w("TtsService", "Voice not found: $voiceName")
                Log.d("TtsService", "Available voice names: ${availableVoices.take(10).map { it.name }}")
            }
        } else {
            Log.d("TtsService", "No voice specified, using default")
        }

        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "block")

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "block")

        updateMetadata()
        updatePlaybackState(PlaybackState.STATE_PLAYING)

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun pauseReading() {
        isPaused = true
        tts?.stop()
        updatePlaybackState(PlaybackState.STATE_PAUSED)
        updateNotification()
    }

    fun stopReading() {
        isPaused = true
        tts?.stop()
        updatePlaybackState(PlaybackState.STATE_STOPPED)
        stopForeground(true)
        stopSelf()
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP
            )
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateMetadata() {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentChapterTitle)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, currentBookTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "FoxyBook")
            .build()
        mediaSession?.setMetadata(metadata)
    }

    private fun createNotification(): Notification {
        val channelId = "tts_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Озвучивание", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, channelId)
            .setContentTitle(currentChapterTitle)
            .setContentText(currentBookTitle)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession?.sessionToken))

        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Назад", createActionIntent("PREV")).build())
        if (isPaused) {
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_play, "Играть", createActionIntent("RESUME")).build())
        } else {
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Пауза", createActionIntent("PAUSE")).build())
        }
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Вперед", createActionIntent("NEXT")).build())
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Закрыть", createActionIntent("STOP")).build())

        return builder.build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, TtsReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun updateNotificationWithTimer(remainingSeconds: Long) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotificationWithTimer(remainingSeconds))
    }

    private fun createNotificationWithTimer(remainingSeconds: Long): Notification {
        val channelId = "tts_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Озвучивание", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val timerText = formatTimeRemaining(remainingSeconds)

        val builder = Notification.Builder(this, channelId)
            .setContentTitle(currentChapterTitle)
            .setContentText("$currentBookTitle - Сон: $timerText")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession?.sessionToken))

        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Назад", createActionIntent("PREV")).build())
        if (isPaused) {
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_play, "Играть", createActionIntent("RESUME")).build())
        } else {
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Пауза", createActionIntent("PAUSE")).build())
        }
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Вперед", createActionIntent("NEXT")).build())
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Закрыть", createActionIntent("STOP")).build())

        return builder.build()
    }

    private fun formatTimeRemaining(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { onCommand?.invoke(it) }
        return START_NOT_STICKY
    }

    fun startSleepTimer(minutes: Int) {
        stopSleepTimer()
        sleepTimerRemainingSeconds = minutes * 60L
        sleepTimerJob = serviceScope.launch(Dispatchers.IO) {
            while (sleepTimerRemainingSeconds > 0) {
                delay(1000L)
                sleepTimerRemainingSeconds--
                withContext(Dispatchers.Main) {
                    updateNotificationWithTimer(sleepTimerRemainingSeconds)
                }
                if (sleepTimerRemainingSeconds <= 0) {
                    withContext(Dispatchers.Main) { stopReading() }
                    break
                }
            }
        }
    }

    fun stopSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerRemainingSeconds = 0L
        updateNotification()
    }

    fun getSleepTimerRemaining(): Long {
        return if (sleepTimerJob?.isActive == true) sleepTimerRemainingSeconds else 0L
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
        mediaSession?.release()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
    }
}
