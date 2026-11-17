package com.wupp.meow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        MessageEditEntity::class,
        StatusEntity::class,
        PresenceLogEntity::class,
        ProfileHistoryEntity::class,
        PrivacyRuleEntity::class,
        GroupReceiptEntity::class,
        ContactEntity::class,
        MessageReactionEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class WaUiDatabase : RoomDatabase() {
    abstract fun waDao(): WaDao

    companion object {
        @Volatile
        private var INSTANCE: WaUiDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN is_community_parent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN parent_group_jid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN is_default_subgroup INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `contacts` (
                        `jid` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `about_text` TEXT NOT NULL DEFAULT '',
                        `avatar_path` TEXT,
                        `is_saved` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`jid`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN is_locked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN local_archive_override INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_reactions` (`message_id` TEXT NOT NULL, " +
                        "`actor` TEXT NOT NULL, `emoji` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`message_id`, `actor`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_message_reactions_message_id` " +
                        "ON `message_reactions` (`message_id`)"
                )
            }
        }

        fun getInstance(context: Context): WaUiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WaUiDatabase::class.java,
                    File(context.applicationContext.filesDir, "wa_ui.db").absolutePath
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            runCatching { db.query("PRAGMA busy_timeout = 10000;").use { it.moveToFirst() } }
                            runCatching { db.query("PRAGMA synchronous = NORMAL;").use { it.moveToFirst() } }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
