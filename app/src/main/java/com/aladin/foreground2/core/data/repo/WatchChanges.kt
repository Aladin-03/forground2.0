package com.aladin.foreground2.core.data.repo

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.watch.PendingChange
import com.aladin.foreground2.core.domain.watch.WatchDecision
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.core.domain.watch.WatchRequest
import com.aladin.foreground2.core.domain.watch.WatchRules
import com.aladin.foreground2.core.domain.watch.WatchedApp
import com.aladin.foreground2.core.logging.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place watching is changed from the UI, applying [WatchRules] against the stored mode and locks: enabling
 * and tightening are written at once, loosening and disabling come back as [WatchDecision.NeedsCountdown] and are
 * only written by [complete] after the sit-through countdown, and both are refused while the app is locked. Also
 * tells the UI how long each mode's window is.
 */
@Singleton
class WatchChanges
    @Inject
    constructor(
        private val watched: WatchedAppsRepository,
        private val locks: LockRepository,
        private val timing: Timing,
    ) {
        /** Every watched app with its mode, re-emitted on each change. */
        fun observeWatched(): Flow<List<WatchedApp>> = watched.observeWatched()

        /** How long [mode]'s allowed window lasts, for showing it next to the mode's name. */
        fun windowMillis(mode: WatchMode): Long = timing.windowMillis(mode)

        /** Decides [request] for [app] and applies it if it may happen now. */
        suspend fun request(
            app: AppInfo,
            request: WatchRequest,
        ): WatchDecision {
            val current = currentMode(app.packageName)
            val locked = isLocked(app.packageName)
            val decision = WatchRules.decide(app.packageName, current, request, locked)
            Log.i(Log.Area.Mode) {
                "request ${app.label} (${app.packageName}) $request current=${current?.key} locked=$locked -> $decision"
            }
            if (decision == WatchDecision.ApplyNow) {
                when (request) {
                    is WatchRequest.Enable -> {
                        watched.watch(app, request.mode)
                        Log.i(Log.Area.Mode) { "enable ${app.packageName} mode=${request.mode.key}" }
                    }
                    is WatchRequest.ChangeMode -> {
                        watched.setMode(app.packageName, request.mode)
                        Log.i(Log.Area.Mode) {
                            "tighten ${app.packageName} ${current?.key} -> ${request.mode.key} (from the next window)"
                        }
                    }
                    WatchRequest.Disable -> error("disabling is never applied without a countdown")
                }
            }
            return decision
        }

        /** The mode [packageName] is watched in, or null if it isn't watched. */
        suspend fun currentMode(packageName: String): WatchMode? =
            watched
                .observeWatched()
                .first()
                .find { it.packageName == packageName }
                ?.mode

        suspend fun isLocked(packageName: String): Boolean = packageName in locks.activeLocks()

        /**
         * Applies [change] once its countdown has completed, if [WatchRules.canApply] still allows it. Returns
         * whether it was applied.
         */
        suspend fun complete(change: PendingChange): Boolean {
            val current = currentMode(change.packageName)
            val locked = isLocked(change.packageName)
            if (!WatchRules.canApply(change, current, locked)) {
                Log.w(Log.Area.Mode) { "not applying $change: now mode=${current?.key} locked=$locked" }
                return false
            }
            when (change) {
                is PendingChange.Loosen -> {
                    watched.setMode(change.packageName, change.to)
                    Log.i(Log.Area.Mode) {
                        "loosen ${change.packageName} ${change.from.key} -> ${change.to.key} (from the next window)"
                    }
                }
                is PendingChange.Disable -> {
                    watched.unwatch(change.packageName)
                    Log.i(Log.Area.Mode) { "disable ${change.packageName} (was ${change.mode.key})" }
                }
            }
            return true
        }
    }
