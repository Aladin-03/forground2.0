package com.aladin.foreground2.feature.sitthrough

import kotlinx.serialization.Serializable

/**
 * The sit-through countdown before [packageName] is unwatched ([modeKey] null) or loosened to the WatchMode with
 * that key. The mode travels as its stable key, not the enum, which R8 may rename.
 */
@Serializable
data class SitThroughRoute(
    val packageName: String,
    val modeKey: String? = null,
) {
    companion object {
        /** Navigation stores the arguments in the SavedStateHandle under the properties' names. */
        const val ARG_PACKAGE_NAME = "packageName"
        const val ARG_MODE_KEY = "modeKey"
    }
}
