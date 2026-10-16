package com.aladin.foreground2.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.aladin.foreground2.core.logging.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App flags kept in DataStore Preferences. */
interface SettingsRepository {
    val onboardingComplete: Flow<Boolean>

    suspend fun setOnboardingComplete()

    /** Whether this app muted media for an overlay and hasn't unmuted it yet (true after a crash mid-overlay). */
    suspend fun mediaMutedByApp(): Boolean

    suspend fun setMediaMutedByApp(muted: Boolean)
}

@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[OnboardingComplete] ?: false }

        override suspend fun setOnboardingComplete() {
            dataStore.edit { it[OnboardingComplete] = true }
            Log.i(Log.Area.DB) { "onboardingComplete=true" }
        }

        override suspend fun mediaMutedByApp(): Boolean = dataStore.data.first()[MediaMutedByApp] ?: false

        override suspend fun setMediaMutedByApp(muted: Boolean) {
            dataStore.edit { it[MediaMutedByApp] = muted }
            Log.d(Log.Area.DB) { "mediaMutedByApp=$muted" }
        }

        private companion object {
            val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
            val MediaMutedByApp = booleanPreferencesKey("media_muted_by_app")
        }
    }
