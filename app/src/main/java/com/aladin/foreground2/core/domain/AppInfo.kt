package com.aladin.foreground2.core.domain

/** An app as the user sees it: its package and launcher label. */
data class AppInfo(
    val packageName: String,
    val label: String,
)
