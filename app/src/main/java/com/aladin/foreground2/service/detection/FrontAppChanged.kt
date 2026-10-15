package com.aladin.foreground2.service.detection

/** [packageName] became the front app at [at] (epoch ms). */
data class FrontAppChanged(
    val packageName: String,
    val at: Long,
)
