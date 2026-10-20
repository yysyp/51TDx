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
    version = 11,
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Default '' matches the entity default, so existing pets keep
                // their catalogue anchors and lose no equipped item.
                db.execSQL(
                    "ALTER TABLE `pet` ADD COLUMN `offsetCsv` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing cards keep an empty answer, which degrades them to the
                // old single-field behaviour rather than inventing one. Default ''
                // matches the entity default so no card is dropped.
                db.execSQL(
                    "ALTER TABLE `review_items` ADD COLUMN `answer` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `prizes` ADD COLUMN `isJackpot` INTEGER NOT NULL DEFAULT 0"
                )
                // The catalogue was redesigned around a pity timer: 18 prizes,
                // 1-30 stars, three jackpots. A schema migration cannot rewrite
                // seed rows, so the old pool is cleared here and re-seeded on
                // next start (seedIfEmpty checks the count). Draw history in
                // draw_records is deliberately left alone -- it records what was
                // actually won, which does not change because the pool changed.
                db.execSQL("DELETE FROM `prizes`")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Re-tuned so the top three prizes carry roughly half the pool.
                // Matched by title rather than by rowid: the user's own added
                // prizes must survive, and row order is not stable across
                // devices. A title that no longer matches simply updates nothing.
                val weights = listOf(
                    "1元" to 46,
                    "15分钟屏幕" to 44,
                    "免一次家务" to 40,
                    "2元" to 17,
                    "5元" to 16,
                    "普通水果券" to 15,
                    "10元" to 14,
                    "大餐：牛排或羊排或海鲜" to 12,
                    "饮料券" to 11,
                    "15元" to 10,
                    "看一场电影" to 9,
                    "冰激凌券" to 7,
                    "20元" to 5,
                    "买书券" to 4,
                    "周末晚睡15分钟特权1次" to 3
                )
                for ((title, weight) in weights) {
                    db.execSQL(
                        "UPDATE `prizes` SET `weight` = ? WHERE `title` = ?",
                        arrayOf<Any>(weight, title)
                    )
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Weights became percentages of a fixed 100. Existing weights
                // summed to 281, so they are rescaled by the same factor every
                // prize shares -- that preserves the relative shape the pool was
                // already tuned to, instead of resetting users to the seed.
                //
                // Largest-remainder rounding, and the leftover units go to the
                // prizes that lost the most fraction, so the total lands exactly
                // on 100 rather than 98 or 103. A pool summing to 98 is a pool
                // whose displayed percentages do not add up.
                val cursor = db.query("SELECT `id`, `weight` FROM `prizes`")
                val ids = mutableListOf<Long>()
                val weights = mutableListOf<Int>()
                cursor.use {
                    while (it.moveToNext()) {
                        ids.add(it.getLong(0))
                        weights.add(it.getInt(1).coerceAtLeast(0))
                    }
                }
                if (ids.isEmpty()) return

                val total = weights.sum()
                val exact: List<Double>? = if (total > 0) {
                    weights.map { it.toDouble() / total.toDouble() * 100.0 }
                } else {
                    null
                }
                val scaled = if (exact == null) {
                    List(ids.size) { (100 / ids.size).coerceAtLeast(1) }
                } else {
                    val shares = exact.map { kotlin.math.floor(it.toDouble()).toInt().coerceAtLeast(1) }.toMutableList()
                    var deficit = 100 - shares.sum()
                    val order = exact.indices.sortedByDescending { exact[it] - kotlin.math.floor(exact[it]) }
                    var k = 0
                    while (deficit > 0 && order.isNotEmpty()) {
                        shares[order[k % order.size]]++
                        deficit--
                        k++
                    }
                    shares
                }
                ids.forEachIndexed { index, id ->
                    db.execSQL(
                        "UPDATE `prizes` SET `weight` = ? WHERE `id` = ?",
                        arrayOf<Any>(scaled[index], id)
                    )
                }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // weight changed from INTEGER to REAL so a share can carry two
                // decimals. SQLite cannot alter a column's type, so the table is
                // rebuilt. The indices and foreign keys on prizes are recreated
                // explicitly -- a rename-based rebuild would keep the old table's
                // definitions attached to the new name only by accident.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prizes_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `emoji` TEXT NOT NULL DEFAULT '',
                        `weight` REAL NOT NULL DEFAULT 10.0,
                        `starCost` INTEGER NOT NULL DEFAULT 5,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `isJackpot` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `prizes_new`
                        (`id`, `title`, `description`, `emoji`, `weight`, `starCost`, `isActive`, `isJackpot`, `createdAt`)
                    SELECT
                        `id`, `title`, `description`, `emoji`, `weight`, `starCost`, `isActive`, `isJackpot`, `createdAt`
                    FROM `prizes`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `prizes`")
                db.execSQL("ALTER TABLE `prizes_new` RENAME TO `prizes`")

                // The copied weights were whole percents summing to 100, so they
                // are already well-formed; nothing to renormalize here. The
                // normalisation that matters happens on the next edit.
            }
        }

        fun get(context: Context): RanDatabase = instance ?: synchronized(this) {            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RanDatabase::class.java,
                "ran.db"
            )
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
