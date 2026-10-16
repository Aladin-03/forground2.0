package com.aladin.foreground2.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.logging.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Shows at most one full-screen overlay over whatever app is in front. Main thread only. */
interface OverlayHost {
    /** Shows [content], replacing the overlay already showing, if any. [name] is for logs. */
    @MainThread
    fun show(
        name: String,
        content: @Composable () -> Unit,
    )

    /** Removes the overlay, if one is showing. */
    @MainThread
    fun hide()
}

/**
 * [OverlayHost] backed by a TYPE_APPLICATION_OVERLAY window holding a ComposeView. Each [show] adds a fresh window
 * with its own [OverlayOwner]; the previous window is removed only after the new one is added, so the app below
 * never flashes through. Removing a window destroys its owner, which disposes its composition and ViewModels.
 * Media is paused through [MediaPauser] from the first overlay shown until [hide], across replacements.
 */
@Singleton
class WindowOverlayHost
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val mediaPauser: MediaPauser,
    ) : OverlayHost {
        private class Shown(
            val name: String,
            val root: View,
            val owner: OverlayOwner,
        )

        private val windowManager = context.getSystemService(WindowManager::class.java)
        private var current: Shown? = null

        override fun show(
            name: String,
            content: @Composable () -> Unit,
        ) {
            val owner = OverlayOwner()
            val root = OverlayRootView(context)
            root.addView(
                ComposeView(context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
                    setContent {
                        ForegroundTheme {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                content()
                            }
                        }
                    }
                },
            )
            owner.attachTo(root)
            owner.resume()
            try {
                windowManager.addView(root, layoutParams())
            } catch (e: WindowManager.BadTokenException) {
                // Overlay permission revoked; the existing overlay (if any) stays as it was.
                Log.e(Log.Area.Overlay, e) { "show $name failed" }
                owner.destroy()
                return
            }
            val previous = current
            current = Shown(name, root, owner)
            Log.i(Log.Area.Overlay) { "show $name" + previous?.let { " (replaces ${it.name})" }.orEmpty() }
            previous?.let(::remove)
            mediaPauser.pause()
        }

        override fun hide() {
            val shown = current ?: return
            current = null
            remove(shown)
            Log.i(Log.Area.Overlay) { "hide ${shown.name}" }
            mediaPauser.release()
        }

        private fun remove(shown: Shown) {
            shown.owner.destroy()
            try {
                windowManager.removeViewImmediate(shown.root)
            } catch (e: IllegalArgumentException) {
                Log.w(Log.Area.Overlay, e) { "${shown.name} was not attached" }
            }
        }

        /** Full screen, edge to edge, focusable (so it gets the back key) and touchable (so it eats every touch). */
        private fun layoutParams() =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    title = "FG2 overlay"
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
                }
    }
