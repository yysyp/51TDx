package com.ran.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrizeDao {
    @Query("SELECT * FROM prizes WHERE isActive = 1 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<PrizeEntity>>

    @Query("SELECT * FROM prizes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PrizeEntity>>

    @Query("SELECT * FROM prizes WHERE isActive = 1")
    suspend fun activeOnce(): List<PrizeEntity>

    @Query("SELECT COUNT(*) FROM prizes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(prize: PrizeEntity): Long

    @Update
    suspend fun update(prize: PrizeEntity)

    @Delete
    suspend fun delete(prize: PrizeEntity)

    /**
     * Whole pool for the rebalancing pass.
     *
     * Explicitly ordered: the caller pairs this list positionally against the
     * weights it computes, and SQLite makes no promise about row order without
     * an ORDER BY.
     */
    @Query("SELECT * FROM prizes ORDER BY createdAt DESC, id ASC")
    suspend fun all(): List<PrizeEntity>

    /**
     * Writes a whole rebalanced pool atomically.
     *
     * Individual updates are not enough. A rebalance touches up to 18 rows and
     * the intermediate state sums to less than 100, so an interrupt part-way
     * through would leave the user with a pool whose displayed percentages do
     * not match the draw -- and nothing on screen would reveal it.
     */
    @Transaction
    suspend fun replaceAll(prizes: List<PrizeEntity>) {
        prizes.forEach { update(it) }
    }
}

@Dao
interface DrawRecordDao {
    @Query("SELECT * FROM draw_records ORDER BY createdAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<DrawRecordEntity>>

    @Insert
    suspend fun insert(record: DrawRecordEntity)

    @Query("DELETE FROM draw_records")
    suspend fun clear()

}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun byId(id: Long): PlanEntity?

    @Insert
    suspend fun insert(plan: PlanEntity): Long

    @Update
    suspend fun update(plan: PlanEntity)

    @Delete
    suspend fun delete(plan: PlanEntity)

    @Query("UPDATE plans SET currentCount = currentCount + 1, completedAt = :completedAt, status = :status WHERE id = :id")
    suspend fun bumpCount(id: Long, status: String, completedAt: Long?)

    @Query("SELECT COUNT(*) FROM plan_check_ins WHERE dayKey = :dayKey")
    suspend fun checkInCountForDay(dayKey: String): Int
}

@Dao
interface PlanCheckInDao {
    @Query("SELECT * FROM plan_check_ins ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlanCheckInEntity>>

    @Query("SELECT * FROM plan_check_ins WHERE planId = :planId ORDER BY createdAt DESC")
    fun observeForPlan(planId: Long): Flow<List<PlanCheckInEntity>>

    @Insert
    suspend fun insert(checkIn: PlanCheckInEntity): Long

    @Query("SELECT COUNT(*) FROM plan_check_ins WHERE planId = :planId AND dayKey = :dayKey")
    suspend fun countForPlanOnDay(planId: Long, dayKey: String): Int
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE isDone = 0 ORDER BY priority ASC, createdAt ASC")
    fun observeOpen(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE isDone = 1 ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<TodoEntity>>

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods WHERE isActive = 1 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE isActive = 1")
    suspend fun activeOnce(): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Insert
    suspend fun insert(food: FoodEntity): Long

    @Update
    suspend fun update(food: FoodEntity)

    @Delete
    suspend fun delete(food: FoodEntity)
}

@Dao
interface StarLedgerDao {
    @Query("SELECT COALESCE(SUM(delta), 0) FROM star_ledger")
    fun observeBalance(): Flow<Int>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM star_ledger")
    suspend fun balanceOnce(): Int

    @Query("SELECT * FROM star_ledger ORDER BY createdAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<StarLedgerEntity>>

    @Query("SELECT COUNT(DISTINCT dayKey) FROM plan_check_ins")
    fun observeActiveDays(): Flow<Int>

    @Query("SELECT dayKey FROM plan_check_ins GROUP BY dayKey ORDER BY dayKey DESC")
    fun observeDistinctDays(): Flow<List<String>>

    @Insert
    suspend fun insert(entry: StarLedgerEntity): Long

    /**
     * Wipes the ledger, driving the balance to exactly zero.
     *
     * @return how many entries were removed, so the caller can report honestly
     *   rather than claiming a reset that touched nothing
     */
    @Query("DELETE FROM star_ledger")
    suspend fun clearAll(): Int
}

@Dao
interface PetDao {
    @Query("SELECT * FROM pet WHERE id = 1")
    fun observe(): Flow<PetEntity?>

    @Query("SELECT * FROM pet WHERE id = 1")
    suspend fun once(): PetEntity?

    /** Upsert via REPLACE: there is exactly one row, keyed by its fixed id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pet: PetEntity)
}

@Dao
interface ReviewDao {
    /** Everything still in rotation, oldest first — newest learning sits at the end. */
    @Query("SELECT * FROM review_items WHERE isMastered = 0 ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<ReviewItemEntity>>

    /**
     * Today's queue. Day keys are ISO strings, so `<=` is a correct date
     * comparison; anything overdue is carried rather than dropped.
     * Ordered oldest-first: the earliest learning is the most at risk.
     */
    @Query(
        "SELECT * FROM review_items WHERE isMastered = 0 AND nextDueDay <= :today " +
            "ORDER BY createdAt ASC"
    )
    fun observeDue(today: String): Flow<List<ReviewItemEntity>>

    @Query("SELECT COUNT(*) FROM review_items WHERE isMastered = 0 AND nextDueDay <= :today")
    suspend fun countDue(today: String): Int

    @Insert
    suspend fun insert(item: ReviewItemEntity): Long

    @Update
    suspend fun update(item: ReviewItemEntity)

    @Delete
    suspend fun delete(item: ReviewItemEntity)

    @Query("SELECT * FROM review_items WHERE id = :id")
    suspend fun getOnce(id: Long): ReviewItemEntity?

    @Query("SELECT COUNT(*) FROM review_items WHERE isMastered = 1")
    suspend fun countMastered(): Int

    @Query("SELECT COUNT(*) FROM review_items WHERE isMastered = 0")
    suspend fun countActive(): Int
}

@Dao
interface ReviewLogDao {

    @Insert
    suspend fun insert(entry: ReviewLogEntity): Long

    /**
     * Distinct days on which a review happened, newest first.
     *
     * The streak is derived from these keys rather than from a counter column:
     * a stored counter is one missed write away from lying, while a set of days
     * can be re-derived from truth at any time.
     */
    @Query("SELECT reviewedDay FROM review_log GROUP BY reviewedDay ORDER BY reviewedDay DESC")
    fun observeReviewDays(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM review_log WHERE reviewedDay = :day")
    suspend fun countReviewsOnDay(day: String): Int

    @Query("SELECT COALESCE(SUM(starsGained), 0) FROM review_log WHERE reviewedDay = :day")
    suspend fun starsOnDay(day: String): Int
}