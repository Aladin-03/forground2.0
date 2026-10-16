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
import com.aladin.foreground2.core.data.repo.SettingsRepository
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Silences media while an overlay covers the screen. Main thread only. */
interface MediaPauser {
    /**
     * Mutes media and pauses what is playing, and keeps pausing anything that starts until [release]. Repeated calls
     * do nothing.
     */
    @MainThread
    fun pause()

    /**
     * Stops pausing and unmutes, if [pause] muted. Never resumes playback: what was paused stays paused. Does nothing
     * if [pause] wasn't called.
     */
    @MainThread
    fun release()

    /** Unmutes media left muted by a run that died mid-overlay. Call once on service start, before any overlay. */
    @MainThread
    suspend fun restoreAfterCrash()
}

/** The system media controls [KeyEventMediaPauser] needs. Main thread only. */
interface MediaControls {
    /** Whether any media is playing right now. */
    fun isPlaying(): Boolean

    /** Sends a pause key to the app that owns media playback. */
    fun sendPause()

    /** Whether the media stream is muted right now (by anyone). */
    fun isMuted(): Boolean

    /** Mutes the media stream, keeping its volume level. */
    fun mute()

    fun unmute()

    /** Calls [onChange] whenever playback may have changed (a player starts or stops, or a recheck is due). */
    fun watch(onChange: () -> Unit)

    fun stopWatching()
}

/** Remembers, across process death, whether this app has media muted. */
interface MuteRecord {
    /** Saves in the background; the last call wins. */
    fun save(muted: Boolean)

    suspend fun load(): Boolean
}

/**
 * Pauses with media keys rather than audio focus: on Android 15 this device denies audio focus to a background app,
 * even one running a foreground service. Media keys need no permission and reach the app that owns playback.
 *
 * While held, any player that starts (a video that begins under the breath overlay) is paused too. Release never sends
 * play: after No the user is back in the app with the video paused, and starts it again when they want.
 *
 * Media is also muted while held, because some players can't be paused from outside: YouTube Shorts has no media
 * session, so the key reaches nothing (it ignores a key sent to its window too). Muting is skipped when media is
 * already muted, so release never unmutes something the user muted. A Short keeps playing silently and is heard again
 * on release; YouTube restarts Shorts itself on return, so it couldn't stay paused anyway. The mute is recorded in
 * [MuteRecord] so a run that dies mid-overlay is undone by [restoreAfterCrash].
 */
@Singleton
class KeyEventMediaPauser
    @Inject
    constructor(
        private val controls: MediaControls,
        private val record: MuteRecord,
    ) : MediaPauser {
        private var held = false
        private var mutedHere = false

        override fun pause() {
            if (held) return
            held = true
            if (!controls.isMuted()) {
                controls.mute()
                mutedHere = true
                record.save(true)
                Log.i(Log.Area.Overlay) { "media muted" }
            }
            controls.watch(::pauseIfPlaying)
            pauseIfPlaying()
        }

        override fun release() {
            if (!held) return
            held = false
            controls.stopWatching()
            if (mutedHere) {
                mutedHere = false
                controls.unmute()
                record.save(false)
                Log.i(Log.Area.Overlay) { "media unmuted" }
            }
        }

        override suspend fun restoreAfterCrash() {
            if (held || !record.load()) return
            controls.unmute()
            record.save(false)
            Log.i(Log.Area.Overlay) { "media unmuted (left muted by an earlier run)" }
        }

        private fun pauseIfPlaying() {
            if (!held || !controls.isPlaying()) return
            controls.sendPause()
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

        override fun isMuted(): Boolean = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)

        override fun mute() = audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)

        override fun unmute() =
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)

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

/** Keeps the flag in DataStore. Writes run on the application scope, so one started on hide still completes. */
@Singleton
class SettingsMuteRecord
    @Inject
    constructor(
        private val settings: SettingsRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) : MuteRecord {
        private val writes = Mutex()

        @Volatile private var latest = false

        override fun save(muted: Boolean) {
            latest = muted
            // Each write stores the latest value, so writes finishing out of order still leave the last one saved.
            scope.launch { writes.withLock { settings.setMediaMutedByApp(latest) } }
        }

        override suspend fun load(): Boolean = settings.mediaMutedByApp()
    }
