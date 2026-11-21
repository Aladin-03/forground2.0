package com.aladin.foreground2.core.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private const val DATABASE = "migration-test.db"
private const val YOUTUBE = "com.google.android.youtube"
private const val INSTAGRAM = "com.instagram.android"

/**
 * Runs the real migrations on Android's SQLite (under Robolectric). Each test builds an old database exactly as the
 * exported schema in app/schemas describes it (tables, plus Room's identity hash), fills it, then opens it the way
 * di/DataModule does. Room runs the migrations and then checks every table against the current entities, failing on
 * any difference, so a passing open means the migrated schema is exactly what version 5 expects.
 *
 * A JUnit 4 test (run by the vintage engine), since Robolectric needs its own runner. A plain Application: the real
 * one starts Hilt and WorkManager, which this test has no use for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: ForegroundDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(DATABASE)
    }

    @After
    fun close() {
        db?.close()
        context.deleteDatabase(DATABASE)
    }

    @Test
    fun `4 to 5 adds mode as MINDFUL and keeps watched apps, locks and history`() {
        createDatabase(version = 4) {
            execSQL("INSERT INTO watched_apps (packageName, label) VALUES ('$YOUTUBE', 'YouTube')")
            execSQL("INSERT INTO watched_apps (packageName, label) VALUES ('$INSTAGRAM', 'Instagram')")
            execSQL("INSERT INTO locks (packageName, unlockAtMillis) VALUES ('$YOUTUBE', 1800000000000)")
            execSQL(
                "INSERT INTO daily_stats (date, unlocks, totalScreenMs, unlocksByHour) " +
                    "VALUES ('2026-11-20', 42, 7200000, '0,1,2')",
            )
            execSQL(
                "INSERT INTO daily_app_usage (date, packageName, foregroundMs) " +
                    "VALUES ('2026-11-20', '$YOUTUBE', 3600000)",
            )
        }

        val migrated = open()
        runBlocking {
            assertEquals(
                listOf(
                    WatchedAppEntity(YOUTUBE, "YouTube", "MINDFUL"),
                    WatchedAppEntity(INSTAGRAM, "Instagram", "MINDFUL"),
                ),
                migrated
                    .watchedAppDao()
                    .observeAll()
                    .first()
                    .sortedBy { it.packageName },
            )
            assertEquals(listOf(LockEntity(YOUTUBE, 1_800_000_000_000L)), migrated.lockDao().observeAll().first())
        }
        migrated
            .query(
                "SELECT unlocks, totalScreenMs, unlocksByHour FROM daily_stats WHERE date = '2026-11-20'",
                null,
            ).use {
                it.moveToNext()
                assertEquals(42, it.getInt(0))
                assertEquals(7_200_000L, it.getLong(1))
                assertEquals("0,1,2", it.getString(2))
            }
        migrated.query("SELECT foregroundMs FROM daily_app_usage WHERE packageName = '$YOUTUBE'", null).use {
            it.moveToNext()
            assertEquals(3_600_000L, it.getLong(0))
        }
        assertEquals(5, migrated.openHelper.readableDatabase.version)
    }

    @Test
    fun `new rows get the mode they are given, after migrating`() {
        createDatabase(version = 4) {}
        val migrated = open()
        runBlocking {
            migrated.watchedAppDao().insert(WatchedAppEntity(YOUTUBE, "YouTube", "STRICT"))
            assertEquals(1, migrated.watchedAppDao().setMode(YOUTUBE, "FOCUSED"))
            assertEquals(
                "FOCUSED",
                migrated
                    .watchedAppDao()
                    .observeAll()
                    .first()
                    .single()
                    .mode,
            )
        }
    }

    @Test
    fun `a version 1 database migrates all the way to 5`() {
        createDatabase(version = 1) { execSQL("INSERT INTO watched_apps (packageName) VALUES ('$YOUTUBE')") }
        val migrated = open()
        runBlocking {
            assertEquals(
                listOf(WatchedAppEntity(YOUTUBE, "", "MINDFUL")),
                migrated.watchedAppDao().observeAll().first(),
            )
        }
    }

    /** Opens the test database like di/DataModule: hand-written migrations, no destructive fallback. */
    private fun open(): ForegroundDatabase =
        Room
            .databaseBuilder(context, ForegroundDatabase::class.java, DATABASE)
            .addMigrations(ForegroundDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    /** Creates the test database at [version] from its exported schema, then runs [fill] on it. */
    private fun createDatabase(
        version: Int,
        fill: SQLiteDatabase.() -> Unit,
    ) {
        val schema =
            Json
                .parseToJsonElement(schemaFile(version).readText())
                .jsonObject
                .getValue("database")
                .jsonObject
        val creates =
            schema.getValue("entities").jsonArray.map {
                val entity = it.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                entity
                    .getValue("createSql")
                    .jsonPrimitive.content
                    .replace("\${TABLE_NAME}", table)
            }
        val setup = schema.getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content }
        SQLiteDatabase
            .openOrCreateDatabase(
                context.getDatabasePath(DATABASE).apply { parentFile?.mkdirs() },
                null,
            ).use {
                (creates + setup).forEach(it::execSQL)
                it.version = version
                it.fill()
            }
    }

    /** Unit tests run in the app module's directory. */
    private fun schemaFile(version: Int) = File("schemas/${ForegroundDatabase::class.java.name}/$version.json")
}
