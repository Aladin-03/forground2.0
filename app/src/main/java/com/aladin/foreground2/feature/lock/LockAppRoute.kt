package com.aladin.foreground2.feature.lock

import kotlinx.serialization.Serializable

/** Locks [packageName], or shows its running lock. */
@Serializable
data class LockAppRoute(
    val packageName: String,
) {
    companion object {
        /** Navigation stores the argument in the SavedStateHandle under the property's name. */
        const val ARG_PACKAGE_NAME = "packageName"
    }
}
