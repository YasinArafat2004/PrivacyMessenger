
package com.yasin.privacymessenger.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserAccount::class,
        FriendRequest::class,
        Contact::class,
        Message::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userAccountDao(): UserAccountDao

    abstract fun friendRequestDao(): FriendRequestDao

    abstract fun contactDao(): ContactDao

    abstract fun messageDao(): MessageDao

    companion object {

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    """
                    ALTER TABLE messages
                    ADD COLUMN serverMessageId INTEGER DEFAULT NULL
                    """
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    """
                    ALTER TABLE messages
                    ADD COLUMN status INTEGER NOT NULL DEFAULT 1
                    """
                )

                db.execSQL(
                    """
                    ALTER TABLE messages
                    ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0
                    """
                )

                db.execSQL(
                    """
                    UPDATE messages
                    SET serverMessageId = NULL
                    WHERE serverMessageId = 0
                    """
                )

                db.execSQL(
                    """
                    DELETE FROM messages
                    WHERE serverMessageId IS NOT NULL
                    AND id NOT IN (
                        SELECT MIN(id)
                        FROM messages
                        WHERE serverMessageId IS NOT NULL
                        GROUP BY serverMessageId
                    )
                    """
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_messages_chatUsername
                    ON messages (chatUsername)
                    """
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_messages_serverMessageId
                    ON messages (serverMessageId)
                    """
                )
            }
        }

        // Version 6 -> 7: Add client-generated message UUID.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    """
                    ALTER TABLE messages
                    ADD COLUMN clientMessageId TEXT DEFAULT NULL
                    """
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_messages_clientMessageId
                    ON messages (clientMessageId)
                    """
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "privacy_messenger_database"
                )
                    .addMigrations(
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
