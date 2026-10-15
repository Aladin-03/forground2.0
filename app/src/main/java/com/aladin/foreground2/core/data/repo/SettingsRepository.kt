package com.aladin.foreground2.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.aladin.foreground2.core.logging.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App flags kept in DataStore Preferences. */
interface SettingsRepository {
    val onboardingComplete: Flow<Boolean>

    suspend fun setOnboardingComplete()
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

        private companion object {
            val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
        }
    }
