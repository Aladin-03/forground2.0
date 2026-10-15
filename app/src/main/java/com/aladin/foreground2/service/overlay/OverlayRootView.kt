package com.aladin.foreground2.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Root of the overlay window. Swallows the back key (rule 2: back does nothing) and any touch the content doesn't
 * handle, so nothing reaches the app underneath. Other keys (volume, etc.) go through as normal.
 */
internal class OverlayRootView(
    context: Context,
) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)

    @SuppressLint("ClickableViewAccessibility") // Nothing to click: the touch is deliberately dropped.
    override fun onTouchEvent(event: MotionEvent): Boolean = true
}
