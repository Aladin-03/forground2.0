package com.aladin.foreground2.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Glance widgets and their receivers are created by the system, so they reach Hilt through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetRefresher(): WidgetRefresher
}

internal fun Context.widgetRefresher(): WidgetRefresher =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java).widgetRefresher()
