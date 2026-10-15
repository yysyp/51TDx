package com.ran.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PrizeEntity::class,
        DrawRecordEntity::class,
        PlanEntity::class,
        PlanCheckInEntity::class,
        TodoEntity::class,
        FoodEntity::class,
        ReviewItemEntity::class,
        ReviewLogEntity::class,
        PetEntity::class,
        StarLedgerEntity::class
    ],
    // v2 dropped the pomodoro_sessions table along with the pomodoro feature.
    // v3 adds review_items (spaced repetition) and migrates in place, so an
    // upgrade keeps existing stars, plans and streak history.
    // v4 upgrades review_items from a fixed interval ladder to SM-2 per-item
    // scheduling and adds review_log. Existing cards keep their day one
    // schedule; they simply begin adapting from the next review.
    // v5 adds the virtual pet. Existing stars are untouched, so upgrading users
    // can immediately feed a pet with the balance they already earned.
    version = 5,
    exportSchema = false
)
abstract class RanDatabase : RoomDatabase() {

    abstract fun prizeDao(): PrizeDao
    abstract fun drawRecordDao(): DrawRecordDao
    abstract fun planDao(): PlanDao
    abstract fun planCheckInDao(): PlanCheckInDao
    abstract fun todoDao(): TodoDao
    abstract fun foodDao(): FoodDao
    abstract fun reviewDao(): ReviewDao
    abstract fun petDao(): PetDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun starLedgerDao(): StarLedgerDao

    companion object {
        @Volatile
        private var instance: RanDatabase? = null

        /**
         * v2 -> v3: add the review table. Written by hand rather than generated
         * (exportSchema is off), so the column order, types and index names must
         * stay exactly in sync with [ReviewItemEntity] or Room's validation fails.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `review_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`stage` INTEGER NOT NULL, " +
                        "`isMastered` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`lastReviewedAt` INTEGER, " +
                        "`nextDueDay` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_review_items_nextDueDay` " +
                        "ON `review_items` (`nextDueDay`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_review_items_createdAt` " +
                        "ON `review_items` (`createdAt`)"
                )
            }
        }

        /**
         * v3 -> v4: SM-2 scheduling fields plus the review log.
         *
         * Hand-written again (exportSchema is off), so column names, types,
         * NOT NULL constraints and defaults must match [ReviewItemEntity] and
         * [ReviewLogEntity] exactly or Room's validation will refuse to run.
         *
         * Existing rows receive NORMAL difficulty and the default ease, which
         * means they are neither advantaged nor punished by the upgrade.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `review_items` ADD COLUMN `difficulty` INTEGER NOT NULL DEFAULT 2"
                )
                db.execSQL(
                    "ALTER TABLE `review_items` ADD COLUMN `repetitions` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `review_items` ADD COLUMN `lapses` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `review_items` ADD COLUMN `intervalDays` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `review_items` ADD COLUMN `ease` REAL NOT NULL DEFAULT 2.35"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `review_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`reviewItemId` INTEGER NOT NULL, " +
                        "`reviewedDay` TEXT NOT NULL, " +
                        "`qualityScore` INTEGER NOT NULL, " +
                        "`starsGained` INTEGER NOT NULL, " +
                        "`reviewedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_review_log_reviewedDay` " +
                        "ON `review_log` (`reviewedDay`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_review_log_reviewItemId` " +
                        "ON `review_log` (`reviewItemId`)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pet` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`species` INTEGER NOT NULL, " +
                        "`hunger` INTEGER NOT NULL, " +
                        "`mood` INTEGER NOT NULL, " +
                        "`careCount` INTEGER NOT NULL, " +
                        "`lastTickAt` INTEGER NOT NULL, " +
                        "`lastActionAt` INTEGER NOT NULL, " +
                        "`ownedCsv` TEXT NOT NULL, " +
                        "`equippedHat` TEXT NOT NULL, " +
                        "`equippedFace` TEXT NOT NULL, " +
                        "`equippedNeck` TEXT NOT NULL, " +
                        "`equippedBack` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        fun get(context: Context): RanDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RanDatabase::class.java,
                "ran.db"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
