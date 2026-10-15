package com.aladin.foreground2.feature.picker

import com.aladin.foreground2.core.domain.AppInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PickerItemsTest {
    private val chrome = AppInfo("com.android.chrome", "Chrome")
    private val instagram = AppInfo("com.instagram.android", "Instagram")
    private val bank = AppInfo("com.axis.mobile", "axis Mobile")
    private val youtube = AppInfo("com.google.android.youtube", "YouTube")
    private val launcher = listOf(youtube, chrome, instagram, bank)

    private fun labels(items: List<PickerItem>) = items.map { it.app.label }

    @Test
    fun `nothing watched sorts alphabetically ignoring case`() {
        val items = pickerItems(launcher, watched = emptyList(), query = "")
        assertEquals(listOf("axis Mobile", "Chrome", "Instagram", "YouTube"), labels(items))
        assertEquals(List(4) { false }, items.map { it.watched })
    }

    @Test
    fun `watched apps come first, each group alphabetical`() {
        val items = pickerItems(launcher, watched = listOf(youtube, instagram), query = "")
        assertEquals(listOf("Instagram", "YouTube", "axis Mobile", "Chrome"), labels(items))
        assertEquals(listOf(true, true, false, false), items.map { it.watched })
    }

    @Test
    fun `query matches label or package, case-insensitively and trimmed`() {
        assertEquals(listOf("Instagram"), labels(pickerItems(launcher, emptyList(), "  INSTA ")))
        assertEquals(listOf("YouTube"), labels(pickerItems(launcher, emptyList(), "google")))
        assertEquals(emptyList<String>(), labels(pickerItems(launcher, emptyList(), "zzz")))
    }

    @Test
    fun `watched app missing from launcher is still listed so it can be unwatched`() {
        val gone = AppInfo("com.uninstalled", "Gone")
        val items = pickerItems(launcher, watched = listOf(gone), query = "")
        assertEquals(PickerItem(gone, watched = true), items.first())
        assertEquals(5, items.size)
    }
}
