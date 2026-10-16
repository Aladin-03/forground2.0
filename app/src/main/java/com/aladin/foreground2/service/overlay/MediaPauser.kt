package com.aladin.foreground2.service.overlay

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.MainThread
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.logging.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Pauses whatever media is playing while an overlay covers the screen. Main thread only. */
interface MediaPauser {
    /** Pauses media now, and keeps pausing anything that starts until [release]. Repeated calls do nothing. */
    @MainThread
    fun pause()

    /** Stops pausing, and resumes playback if this pauser paused it. Does nothing if [pause] wasn't called. */
    @MainThread
    fun release()
}

/** The system media controls [KeyEventMediaPauser] needs. Main thread only. */
interface MediaControls {
    /** Whether any media is playing right now. */
    fun isPlaying(): Boolean

    /** Sends a pause key to the app that owns media playback. */
    fun sendPause()

    /** Sends a play key to the app that owns media playback. */
    fun sendPlay()

    /** Calls [onChange] whenever playback may have changed (a player starts or stops, or a recheck is due). */
    fun watch(onChange: () -> Unit)

    fun stopWatching()
}

/**
 * Pauses with media keys rather than audio focus: on Android 15 this device denies audio focus to a background app,
 * even one running a foreground service. Media keys need no permission and reach the app that owns playback.
 *
 * While held, any player that starts (a video that begins under the breath overlay) is paused too. On release, play is
 * sent only if something was paused here, so media the user wasn't playing is never started.
 */
@Singleton
class KeyEventMediaPauser
    @Inject
    constructor(
        private val controls: MediaControls,
    ) : MediaPauser {
        private var held = false
        private var pausedSomething = false

        override fun pause() {
            if (held) return
            held = true
            controls.watch(::pauseIfPlaying)
            pauseIfPlaying()
        }

        override fun release() {
            if (!held) return
            held = false
            controls.stopWatching()
            if (pausedSomething) {
                pausedSomething = false
                controls.sendPlay()
                Log.i(Log.Area.Overlay) { "media resumed" }
            }
        }

        private fun pauseIfPlaying() {
            if (!held || !controls.isPlaying()) return
            controls.sendPause()
            pausedSomething = true
            Log.i(Log.Area.Overlay) { "media paused" }
        }
    }

/**
 * Watches with a playback callback plus a recheck every poll interval. The recheck covers a pause key that was lost:
 * a key goes to the session that played last, so one sent just as an app starts playing can reach the previous app,
 * and no further callback may follow (seen on the Infinix with YouTube starting cold under the breath overlay).
 */
@Singleton
class AudioManagerMediaControls
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val timing: Timing,
    ) : MediaControls {
        private val audioManager = context.getSystemService(AudioManager::class.java)
        private val mainHandler = Handler(Looper.getMainLooper())
        private var callback: AudioManager.AudioPlaybackCallback? = null
        private var recheck: Runnable? = null

        override fun isPlaying(): Boolean = audioManager.isMusicActive

        override fun sendPause() = sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)

        override fun sendPlay() = sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)

        override fun watch(onChange: () -> Unit) {
            stopWatching()
            val watcher =
                object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = onChange()
                }
            audioManager.registerAudioPlaybackCallback(watcher, mainHandler)
            callback = watcher
            val tick =
                object : Runnable {
                    override fun run() {
                        onChange()
                        mainHandler.postDelayed(this, timing.pollIntervalMillis)
                    }
                }
            mainHandler.postDelayed(tick, timing.pollIntervalMillis)
            recheck = tick
        }

        override fun stopWatching() {
            callback?.let(audioManager::unregisterAudioPlaybackCallback)
            callback = null
            recheck?.let(mainHandler::removeCallbacks)
            recheck = null
        }

        private fun sendKey(keyCode: Int) {
            val now = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }
