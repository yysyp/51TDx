package com.ran.app.data.local

import com.ran.app.core.SpacedRepetition
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A prize in the lottery pool. [weight] drives a weighted random draw so that
 * rare rewards stay exciting while common filler keeps the loop running.
 */
@Entity(tableName = "prizes")
data class PrizeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val emoji: String = "🎁",
    val weight: Int = 10,
    val starCost: Int = 5,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/** A draw record, kept so the user can see their reward history. */
@Entity(tableName = "draw_records", indices = [Index("createdAt")])
data class DrawRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prizeTitle: String,
    val prizeEmoji: String,
    val starCost: Int,
    val isJackpot: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

/** A repeating plan with a target count, e.g. "read 30 pages". */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val emoji: String = "🎯",
    val targetCount: Int = 21,
    val currentCount: Int = 0,
    val starReward: Int = 10,
    val status: String = PlanStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

object PlanStatus {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
}

/** One check-in event; unique per plan per day so double-tapping cannot farm stars. */
@Entity(
    tableName = "plan_check_ins",
    indices = [Index(value = ["planId", "dayKey"], unique = true), Index("dayKey")]
)
data class PlanCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val dayKey: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "todos", indices = [Index("isDone"), Index("priority")])
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val priority: Int = TodoPriority.MEDIUM,
    val isDone: Boolean = false,
    val starReward: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

object TodoPriority {
    const val HIGH = 1
    const val MEDIUM = 2
    const val LOW = 3

    fun label(priority: Int): String = when (priority) {
        HIGH -> "高"
        LOW -> "低"
        else -> "中"
    }
}

@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "日常",
    val emoji: String = "🍜",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A single learned item moving through a spaced-repetition schedule.
 *
 * [nextDueDay] is a `yyyy-MM-dd` key rather than a timestamp so that "due?" is
 * a lexicographic comparison in SQL and a day can never host two reviews.
 *
 * The scheduling state is stored rather than recomputed because SM-2 is
 * path-dependent: the same card reviewed by two children (or by the same child
 * in different moods) ends up at *different* [ease] values, and that history is
 * precisely the information the algorithm carries forward. Deriving the
 * schedule from [stage] alone would throw it away.
 *
 * [stage] is total attempts (including failures) and exists for display;
 * [repetitions] is consecutive successes and drives the schedule.
 */
@Entity(
    tableName = "review_items",
    indices = [Index("nextDueDay"), Index("createdAt")]
)
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    /** SM-2 [SpacedRepetition.Difficulty] ordinal; sets the starting ease. */
    val difficulty: Int = SpacedRepetition.Difficulty.NORMAL.level,
    val stage: Int = 0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val intervalDays: Int = 0,
    val ease: Float = SpacedRepetition.Difficulty.NORMAL.initialEase,
    val isMastered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val nextDueDay: String = SpacedRepetition.initialDueDay(createdAt)
)

/**
 * The virtual pet. Deliberately a single row (id pinned to 1) rather than a
 * table of pets: one companion keeps the bond specific. Adding pets later means
 * changing this to a list, which the rest of the code never assumed otherwise.
 *
 * Timestamps rather than counters drive everything that changes over time: the
 * pet must age while the app is closed, and only elapsed wall-clock time knows
 * that. Counters would silently freeze the pet whenever the app was off.
 */
@Entity(tableName = "pet")
data class PetEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val name: String = "团子",
    val species: Int = 0,
    /** 0-100 hunger analogue; decays with time, restored by feeding. */
    val hunger: Int = 100,
    /** 0-100 mood analogue; decays slower than hunger. */
    val mood: Int = 70,
    /** Lifetime care actions; drives growth. Never decremented. */
    val careCount: Int = 0,
    /** Anchor for time-based decay. Advanced on every state write. */
    val lastTickAt: Long = System.currentTimeMillis(),
    val lastActionAt: Long = System.currentTimeMillis(),
    /**
     * Owned accessory ids, comma separated.
     *
     * Denormalised on purpose: ownership is a small set read and written only
     * together with the pet row, so a join table would add a migration surface
     * and a query per render in exchange for nothing. Kept as ids, not emojis,
     * so the catalogue can change without touching save data.
     */
    val ownedCsv: String = "",
    val equippedHat: String = "",
    val equippedFace: String = "",
    val equippedNeck: String = "",
    val equippedBack: String = "",
    /**
     * Per-accessory drag corrections, as `id:dx:dy` triples joined by commas.
     *
     * Keyed by accessory rather than by slot: emoji glyphs carry their own
     * padding, so no shared slot anchor can line all of them up. See
     * [PetEngine.OffsetStore] for why this is one CSV instead of many columns.
     */
    val offsetCsv: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}

/**
 * Append-only history of completed reviews — one row per review, never updated.
 *
 * Deliberately separate from [StarLedgerEntity]: that ledger answers "where did
 * my stars go", while this answers "how is my learning going". Distinct days in
 * this table are what make the review streak computable, and per-item histories
 * make forgetting curves observable. Keeping one log for both questions means
 * every star-lifecycle change risks corrupting learning analytics.
 */
@Entity(
    tableName = "review_log",
    indices = [Index("reviewedDay"), Index("reviewItemId")]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reviewItemId: Long,
    /** `yyyy-MM-dd` key, so streak maths is a plain DISTINCT on a comparable key. */
    val reviewedDay: String,
    /** SM-2 quality score: 1 forgot, 3 fuzzy, 5 recalled. */
    val qualityScore: Int,
    val starsGained: Int,
    val reviewedAt: Long = System.currentTimeMillis()
)

/**
 * Append-only star ledger. The balance is derived from the sum of [delta],
 * which keeps every grant auditable and makes accidental double-spend visible.
 */
@Entity(tableName = "star_ledger", indices = [Index("createdAt")])
data class StarLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val delta: Int,
    val balanceAfter: Int,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis()
)