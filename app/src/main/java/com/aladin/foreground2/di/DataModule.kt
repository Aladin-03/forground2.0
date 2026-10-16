package com.aladin.foreground2.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.aladin.foreground2.core.data.db.DailyStatsDao
import com.aladin.foreground2.core.data.db.ForegroundDatabase
import com.aladin.foreground2.core.data.db.LockDao
import com.aladin.foreground2.core.data.db.WatchedAppDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): ForegroundDatabase =
        Room
            .databaseBuilder(context, ForegroundDatabase::class.java, ForegroundDatabase.NAME)
            .build()

    @Provides
    fun provideWatchedAppDao(db: ForegroundDatabase): WatchedAppDao = db.watchedAppDao()

    @Provides
    fun provideLockDao(db: ForegroundDatabase): LockDao = db.lockDao()

    @Provides
    fun provideDailyStatsDao(db: ForegroundDatabase): DailyStatsDao = db.dailyStatsDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore
}
