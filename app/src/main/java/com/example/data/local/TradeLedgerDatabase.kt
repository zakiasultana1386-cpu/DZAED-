package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PartyEntity::class, LedgerTransactionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TradeLedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: TradeLedgerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parties ADD COLUMN username TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE parties SET username = LOWER(REPLACE(TRIM(companyName), ' ', '_')) || '_' || id WHERE username = ''"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_parties_username ON parties(username)")
            }
        }

        fun getInstance(context: Context): TradeLedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TradeLedgerDatabase::class.java,
                    "trade_ledger.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
