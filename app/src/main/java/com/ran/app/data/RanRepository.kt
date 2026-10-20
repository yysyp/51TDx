package com.ran.app.data

import com.ran.app.core.DailyCycle
import com.ran.app.core.PetEngine
import com.ran.app.core.PrizeBalancer
import com.ran.app.core.RewardEngine
import com.ran.app.core.SpacedRepetition
import com.ran.app.core.TimeUtils
import com.ran.app.data.local.DrawRecordDao
import com.ran.app.data.local.DrawRecordEntity
import com.ran.app.data.local.FoodDao
import com.ran.app.data.local.FoodEntity
import com.ran.app.data.local.PlanCheckInDao
import com.ran.app.data.local.PlanCheckInEntity
import com.ran.app.data.local.PlanDao
import com.ran.app.data.local.PlanEntity
import com.ran.app.data.local.PlanStatus
import com.ran.app.data.local.PetDao
import com.ran.app.data.local.PetEntity
import com.ran.app.data.local.PrizeDao
import com.ran.app.data.local.PrizeEntity
import com.ran.app.data.local.ReviewDao
import com.ran.app.data.local.ReviewItemEntity
import com.ran.app.data.local.ReviewLogDao
import com.ran.app.data.local.ReviewLogEntity
import com.ran.app.data.local.StarLedgerDao
import com.ran.app.data.local.StarLedgerEntity
import com.ran.app.data.local.TodoDao
import com.ran.app.data.local.TodoEntity
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Outcome of a plan check-in, surfaced to the UI for feedback messaging. */
sealed interface CheckInResult {
    data object AlreadyCheckedIn : CheckInResult
    data class Progressed(val planId: Long, val current: Int, val target: Int) : CheckInResult
    data class Completed(val planId: Long, val starsGained: Int, val finishBonus: Int) : CheckInResult
}

sealed interface DrawResult {
    data class Success(val record: DrawRecordEntity) : DrawResult
    data object NotEnoughStars : DrawResult
    data object EmptyPool : DrawResult
}

sealed interface ToggleTodoResult {
    data class Completed(val starsGained: Int) : ToggleTodoResult
    data class Reopened(val starsRefunded: Int) : ToggleTodoResult
}

/** Several cards created together from one multi-line entry. */
data class BatchReviewRecord(
    val starsGained: Int,
    val created: Int,
    val skipped: Int,
    val tooLong: Int
)

/** Outcome of one graded review. */
data class ReviewOutcome(
    val starsGained: Int,
    /** Tier bonus folded into [starsGained], reported separately for the copy. */
    val streakBonus: Int = 0,
    /** How many cards have been reviewed today, including this one. */
    val reviewsToday: Int = 0,
    val nextDueDay: String,
    val intervalDays: Int,
    val mastered: Boolean,
    /** True when the variable-ratio bonus fired; drives the celebratory copy. */
    val bonus: Boolean,
    val quality: SpacedRepetition.Quality
)

/** Result of one care interaction with the pet. */
data class PetActionResult(
    val ok: Boolean,
    val spokenLine: String,
    val banner: String,
    val starsSpent: Int,
    val hunger: Int,
    val mood: Int,
    val evolvedTo: com.ran.app.core.PetEngine.Stage?
)

/** Result of buying or equipping an accessory. */
data class PetPurchaseResult(
    val ok: Boolean,
    val message: String,
    val starsSpent: Int,
    val equippedNow: String
)

/** Today's review standing, for the header card. */
data class ReviewSummary(
    val dueCount: Int = 0,
    val doneToday: Int = 0,
    val streak: Int = 0,
    val starsToday: Int = 0,
    val totalCards: Int = 0,
    val masteredCount: Int = 0
)

/**
 * Single entry point for all persistence and reward accounting.
 *
 * Star mutations are serialised through [awardStars]/[spendStars] so the
 * running balance in the ledger stays consistent even under rapid tapping.
 */
/**
 * The passcode that authorises a star-balance reset.
 *
 * This is a guard against a curious child, not a security control: the app is
 * local-only and the string ships inside the APK, so anyone who unpacks the
 * binary can read it. Treating it as a secret would be false comfort. What it
 * actually buys is the thing it is for — a two-year-old cannot guess it, and an
 * accidental tap cannot wipe a month of progress.
 */
const val RESET_PASSPHRASE = "行微易成54321"

/** True when [input] may reset the balance. */
fun isResetAuthorised(input: String): Boolean = input == RESET_PASSPHRASE

class RanRepository(
    private val prizeDao: PrizeDao,
    private val drawRecordDao: DrawRecordDao,
    private val planDao: PlanDao,
    private val planCheckInDao: PlanCheckInDao,
    private val todoDao: TodoDao,
    private val foodDao: FoodDao,
    private val petDao: PetDao,
    private val reviewDao: ReviewDao,
    private val reviewLogDao: ReviewLogDao,
    private val starLedgerDao: StarLedgerDao
) {

    companion object {
        /**
         * One card must fit in working memory: roughly four chunks, ~15 words.
         * Anything longer cannot be recalled, only recognised, and recognition
         * masquerades as knowing.
         */
        const val MAX_CARD_LENGTH = 80

        /** Cap per submission so one pasted essay cannot mint a fortune. */
        const val MAX_CARDS_PER_BATCH = 12

        private val SENTENCE_SPLIT = Regex("[\\n;；。!！?？]+")
    }

    /**
     * The local day key as a stream, not a value.
     *
     * Every "today" surface used to read this once and cache it, while data
     * flows only emit when the database changes. Nothing changes at midnight,
     * so yesterday's key survived the night and a finished check-in stayed
     * greyed out until some unrelated write refreshed it. Emitting the key on
     * its own schedule decouples the day boundary from writes.
     *
     * Polling rather than sleeping exactly until midnight: the clock can also
     * jump (manual edit, timezone change, travel), and only re-reading it
     * catches that.
     */
    val todayKey: Flow<String> = flow {
        while (true) {
            emit(TimeUtils.todayKey())
            delay(DailyCycle.millisUntilNextLocalDay().coerceAtMost(30_000L))
        }
    }.distinctUntilChanged()

    // ---------- Star economy ----------

    val starBalance: Flow<Int> = starLedgerDao.observeBalance()
    val recentStarEntries: Flow<List<StarLedgerEntity>> = starLedgerDao.observeRecent()
    val activeDayCount: Flow<Int> = starLedgerDao.observeActiveDays()
    val distinctCheckInDays: Flow<List<String>> = starLedgerDao.observeDistinctDays()

    suspend fun currentBalance(): Int = starLedgerDao.balanceOnce()

    /** Grants [amount] stars and records the resulting balance. */
    suspend fun awardStars(amount: Int, reason: String): Int {
        if (amount <= 0) return currentBalance()
        val balance = currentBalance() + amount
        starLedgerDao.insert(
            StarLedgerEntity(
                delta = amount,
                balanceAfter = balance,
                reason = reason,
                createdAt = TimeUtils.now()
            )
        )
        return balance
    }

/**
 * Deducts [amount] stars. Returns false (and changes nothing) if unaffordable.
 *
 * [allowDebt] exists for the lottery only. Refusing a draw because the
 * balance is short teaches the wrong lesson -- it turns "not yet" into "not
 * allowed", and the child concludes the reward is not theirs to earn. The
 * debt is visible on the balance instead, so it stays a debt rather than
 * becoming a wall. Every other spender keeps the hard guard: spending on
 * the pet must not be able to go further into debt.
 */
suspend fun spendStars(amount: Int, reason: String, allowDebt: Boolean = false): Boolean {
        if (amount <= 0) return true
        val balance = currentBalance()
        if (!allowDebt && balance < amount) return false
        starLedgerDao.insert(
            StarLedgerEntity(
                delta = -amount,
                balanceAfter = balance - amount,
                reason = reason,
                createdAt = TimeUtils.now()
            )
        )
        return true
    }

    // ---------- Lottery ----------

    /**
     * Sets the star balance to exactly zero, whatever it was.
     *
     * Both directions are handled by clearing the ledger: the balance *is* the
     * sum of its deltas, so an empty ledger is zero regardless of whether the
     * balance was positive or negative. That also erases any accumulated debt,
     * which is the point — a reset that left the balance at -30 would not be a
     * reset.
     *
     * Check-ins, todos and review history are separate tables and are left
     * alone: the stars are what gets cleared, not the effort behind them.
     *
     * @return number of ledger entries removed
     */
    suspend fun resetStarBalance(): Int = starLedgerDao.clearAll()

    val prizes: Flow<List<PrizeEntity>> = prizeDao.observeAll()
    val drawRecords: Flow<List<DrawRecordEntity>> = drawRecordDao.observeRecent()

    /**
     * Saves a prize edit and rebalances the whole pool around it.
     *
     * Weights are percentages of a fixed 100, so changing one prize necessarily
     * changes the others -- otherwise every displayed probability drifts out of
     * step with the probabilities actually used by the draw. This lives here
     * rather than in the editor so the invariant holds no matter which screen
     * made the change.
     *
     * Written as a single transaction via [PrizeDao.replaceAll]: an interrupted
     * rebalance would otherwise leave the pool summing to 93, and the user would
     * have no way to tell that from an intentional edit.
     */
    suspend fun savePrize(prize: PrizeEntity) {
        val pool = prizeDao.all()
        if (prize.id == 0L) {
            val (granted, others) = PrizeBalancer.admit(pool.map { it.weight }, prize.weight)
            val inserted = prize.copy(weight = granted)
            val id = prizeDao.insert(inserted)
            prizeDao.replaceAll(
                pool.mapIndexed { i, existing -> existing.copy(weight = others[i]) } +
                    inserted.copy(id = id)
            )
            return
        }
        val index = pool.indexOfFirst { it.id == prize.id }
        if (index < 0) {
            prizeDao.insert(prize.copy(weight = PrizeBalancer.quantize(prize.weight)))
            return
        }
        val rebalanced = PrizeBalancer.rebalance(pool.map { it.weight }, index, prize.weight)
        // The edited prize keeps the weight that was typed and carries over every
        // other field the caller changed; the rest take their computed share so
        // the pool lands on exactly 100.
        prizeDao.replaceAll(
            pool.mapIndexed { i, existing ->
                if (i != index) {
                    existing.copy(weight = rebalanced[i])
                } else {
                    prize.copy(id = existing.id, weight = PrizeBalancer.quantize(prize.weight))
                }
            }
        )
    }

    /**
     * Deletes a prize and hands its share back to the ones that remain.
     *
     * Without the redistribution the pool simply sums to less than 100, and
     * every surviving prize quietly becomes likelier than its number claims --
     * the one change users would never have made deliberately.
     */
    suspend fun deletePrize(prize: PrizeEntity) {
        val pool = prizeDao.all()
        val remaining = pool.filter { it.id != prize.id }
        prizeDao.delete(prize)
        if (remaining.isNotEmpty()) {
            val rebalanced = PrizeBalancer.release(remaining.map { it.weight })
            prizeDao.replaceAll(
                remaining.mapIndexed { i, existing -> existing.copy(weight = rebalanced[i]) }
            )
        }
    }

    /**
     * Performs one draw.
     *
     * The prize is chosen first and the cost charged afterwards. Charging first would
     * make the balance decide the outcome; charging second means the draw is
     * always honest and the debt, if any, is simply recorded.
     *
     * Weights are percentages summing to 100, so a weight reads directly as its
     * share of the pool. See [com.ran.app.core.PrizeBalancer].
     *
     * The balance is allowed to go negative here and nowhere else (see
     * [spendStars]).
     */
    suspend fun drawPrize(): DrawResult {
        val pool = prizeDao.activeOnce()
        if (pool.isEmpty()) return DrawResult.EmptyPool

        val winner = RewardEngine.weightedPick(pool, { it.weight }) ?: return DrawResult.EmptyPool
        // Can no longer fail: the debt is permitted, so this is a plain debit.
        spendStars(winner.starCost, "抽奖：${winner.title}", allowDebt = true)

        val record = DrawRecordEntity(
            prizeTitle = winner.title,
            prizeEmoji = winner.emoji,
            starCost = winner.starCost,
            isJackpot = winner.isJackpot,
            createdAt = TimeUtils.now()
        )
        drawRecordDao.insert(record)
        return DrawResult.Success(record)
    }

    // ---------- Plans ----------

    val activePlans: Flow<List<PlanEntity>> = planDao.observeActive()
    val completedPlans: Flow<List<PlanEntity>> = planDao.observeCompleted()

    fun checkInsForPlan(planId: Long): Flow<List<PlanCheckInEntity>> =
        planCheckInDao.observeForPlan(planId)

    val allCheckIns: Flow<List<PlanCheckInEntity>> = planCheckInDao.observeAll()

    suspend fun savePlan(plan: PlanEntity): Long =
        if (plan.id == 0L) planDao.insert(plan) else { planDao.update(plan); plan.id }

    suspend fun deletePlan(plan: PlanEntity) = planDao.delete(plan)

    /**
     * Records one check-in. The unique index on (planId, dayKey) makes repeat
     * taps on the same day a no-op instead of a star farm.
     */
    suspend fun checkIn(planId: Long): CheckInResult {
        val plan = planDao.byId(planId) ?: return CheckInResult.AlreadyCheckedIn
        if (plan.status == PlanStatus.COMPLETED) return CheckInResult.AlreadyCheckedIn
        val today = TimeUtils.todayKey()
        if (planCheckInDao.countForPlanOnDay(planId, today) > 0) return CheckInResult.AlreadyCheckedIn

        planCheckInDao.insert(PlanCheckInEntity(planId = planId, dayKey = today, createdAt = TimeUtils.now()))
        val nextCount = plan.currentCount + 1
        if (nextCount >= plan.targetCount) {
            planDao.bumpCount(planId, PlanStatus.COMPLETED, TimeUtils.now())
            // The final check-in pays the plan's own completion reward instead of
            // a flat per-check-in amount. The editor calls this field "完成奖励"
            // and the list shows "完成 +N⭐"; paying a hardcoded constant made
            // both of those promises false.
            val granted = RewardEngine.clampPlanReward(plan.starReward, plan.targetCount)
            awardStars(granted, "完成计划：${plan.title}")
            // Paid on top of the per-step reward, which the check-ins already
            // handed out one at a time. Without it the endpoint of a hard plan
            // was worth exactly the same as the endpoint of a trivial one.
            awardStars(RewardEngine.STAR_PLAN_FINISH_BONUS, "达成目标：${plan.title}")
            return CheckInResult.Completed(
                planId,
                granted,
                RewardEngine.STAR_PLAN_FINISH_BONUS
            )
        }
        planDao.bumpCount(planId, PlanStatus.ACTIVE, null)
        awardStars(RewardEngine.STAR_CHECK_IN, "打卡：${plan.title}")
        return CheckInResult.Progressed(planId, nextCount, plan.targetCount)
    }

    /** Current length of the daily check-in streak, including today if present. */
    suspend fun computeStreak(days: List<String>): Int {
        if (days.isEmpty()) return 0
        val today = TimeUtils.todayKey()
        val sorted = days.sortedDescending()
        // Anchor on today when checked in, otherwise on yesterday so the streak
        // is not reported as broken before the day is actually over.
        var cursor = if (sorted.first() == today) today else TimeUtils.todayKey(TimeUtils.now() - 86_400_000L)
        var streak = 0
        for (day in sorted) {
            val gap = TimeUtils.daysBetweenKeys(day, cursor)
            if (gap == 0L) {
                streak++
                cursor = TimeUtils.todayKey(TimeUtils.now() - (streak * 86_400_000L))
            } else if (gap > 0L) {
                break
            }
        }
        return streak
    }

    // ---------- Todos ----------

    val openTodos: Flow<List<TodoEntity>> = todoDao.observeOpen()
    val completedTodos: Flow<List<TodoEntity>> = todoDao.observeCompleted()

    suspend fun saveTodo(todo: TodoEntity): Long =
        if (todo.id == 0L) todoDao.insert(todo) else { todoDao.update(todo); todo.id }

    suspend fun deleteTodo(todo: TodoEntity) = todoDao.delete(todo)

    /**
     * Marks a todo done or reopens it. Reopening refunds the stars so the
     * ledger stays a truthful record of earned reward.
     */
    suspend fun toggleTodo(todo: TodoEntity): ToggleTodoResult? {
        // Grant and refund both go through the cap, so a row written before the
        // cap existed cannot be completed for 9999 and reopened for 9999 either.
        val granted = RewardEngine.clampTodoReward(todo.starReward)
        if (!todo.isDone) {
            todoDao.update(
                todo.copy(isDone = true, completedAt = TimeUtils.now())
            )
            awardStars(granted, "完成任务：${todo.title}")
            return ToggleTodoResult.Completed(granted)
        }
        todoDao.update(todo.copy(isDone = false, completedAt = null))
        // Refund the stars originally granted. Guarded against a negative balance
        // so the ledger can never show a negative balance if data was edited.
        val balance = currentBalance()
        val refund = granted.coerceAtMost(balance)
        if (refund > 0) {
            starLedgerDao.insert(
                StarLedgerEntity(
                    delta = -refund,
                    balanceAfter = balance - refund,
                    reason = "撤销任务：${todo.title}",
                    createdAt = TimeUtils.now()
                )
            )
        }
        return ToggleTodoResult.Reopened(refund)
    }

    // ---------- Food picker ----------

    val foods: Flow<List<FoodEntity>> = foodDao.observeActive()

    suspend fun saveFood(food: FoodEntity): Long =
        if (food.id == 0L) foodDao.insert(food) else { foodDao.update(food); food.id }

    suspend fun deleteFood(food: FoodEntity) = foodDao.delete(food)

    suspend fun pickFood(): FoodEntity? {
        val pool = foodDao.activeOnce()
        return RewardEngine.weightedPick(pool)
    }
    // ---------- Pet ----------

    /**
     * The companion state as it stands right now.
     *
     * Decay is applied on read and never written back until an action occurs,
     * because it is a pure function of [PetEntity.lastTickAt]. Persisting decay
     * on every tick would both spam the database and double-charge elapsed time
     * whenever the same row was touched twice.
     */
    val pet: Flow<PetEntity?> = combine(petDao.observe(), everyThirtySeconds()) { row, _ -> row }
        .map { row ->
            row?.let {
                val now = TimeUtils.now()
                val (hunger, mood) = PetEngine.snapshotted(it.hunger, it.mood, it.lastTickAt, now)
                it.copy(hunger = hunger, mood = mood)
            }
        }

    /** Creates the companion on first launch and after any schema rebuild. */
    suspend fun ensurePet() {
        if (petDao.once() == null) petDao.upsert(PetEntity())
    }

    /**
     * Spends stars on one care action and applies its effect.
     *
     * Growth comes from care actions, never from the amount paid, so buying the
     * priciest snack repeatedly is not a shortcut to evolution — the intent is
     * that showing up regularly is the only way to raise it.
     */
    suspend fun performPetAction(action: PetEngine.Action): PetActionResult {
        val row = petDao.once() ?: PetEntity().also { petDao.upsert(it) }
        val now = TimeUtils.now()
        val (hungerBefore, moodBefore) = PetEngine.snapshotted(row.hunger, row.mood, row.lastTickAt, now)

        if (!spendStars(action.cost, "宠物·${action.label}")) {
            val begLine = PetEngine.begLine(hungerBefore)
            return PetActionResult(
                ok = false,
                spokenLine = begLine.ifBlank { "想给你买更好的东西…可是星星不够了 🥺" },
                banner = "星星不够啦，去完成任务赚星星吧",
                starsSpent = 0,
                hunger = hungerBefore,
                mood = moodBefore,
                evolvedTo = null
            )
        }

        val hunger = PetEngine.clamp(hungerBefore + action.hungerDelta)
        val mood = PetEngine.clamp(moodBefore + action.moodDelta)
        val care = row.careCount + action.carePoints
        val stageBefore = PetEngine.Stage.forCare(row.careCount)
        val stageAfter = PetEngine.Stage.forCare(care)

        petDao.upsert(
            row.copy(
                hunger = hunger,
                mood = mood,
                careCount = care,
                lastTickAt = now,
                lastActionAt = now
            )
        )

        val expression = PetEngine.expression(
            hunger = hunger,
            mood = mood,
            minutesSinceLastAction = 0,
            justAte = action.hungerDelta > 0
        )
        return PetActionResult(
            ok = true,
            spokenLine = PetEngine.lineFor(expression, row.name),
            banner = if (stageAfter != stageBefore) "${row.name} 长大了！变成${stageAfter.label} 🎉" else "",
            starsSpent = action.cost,
            hunger = hunger,
            mood = mood,
            evolvedTo = if (stageAfter != stageBefore) stageAfter else null
        )
    }

    /**
     * Buys an accessory, or equips it free if already owned.
     *
     * Owning something you have already earned and then *choosing* to wear it
     * is the part that builds attachment, so paying again is never required.
     */
    suspend fun purchaseAccessory(id: String): PetPurchaseResult {
        val row = petDao.once() ?: return PetPurchaseResult(false, "宠物还没准备好", 0, "")
        val accessory = PetEngine.accessoryOf(id)
            ?: return PetPurchaseResult(false, "没有这个装扮", 0, "")

        val owned = ownedSet(row.ownedCsv)
        if (owned.contains(id)) {
            equipAccessory(id)
            val worn = equippedIds(row)
            return PetPurchaseResult(true, "「${accessory.name}」${if (worn.contains(id)) "戴上啦" else "摘下来了"}", 0, id)
        }

        if (!spendStars(accessory.price, "宠物装扮·${accessory.name}")) {
            return PetPurchaseResult(
                false,
                "还差 ${accessory.price - currentBalance()} 颗星星，继续加油！",
                0,
                ""
            )
        }
        petDao.upsert(row.copy(ownedCsv = (owned + id).joinToString(",")))
        equipAccessory(id)
        return PetPurchaseResult(true, "「${accessory.name}」买下了，已经戴好 ✨", accessory.price, id)
    }

    /** Toggles one accessory in its slot; each slot holds at most one item. */
    suspend fun equipAccessory(id: String) {
        val row = petDao.once() ?: return
        val accessory = PetEngine.accessoryOf(id) ?: return
        val worn = equippedIds(row)
        val removing = worn.contains(id)
        petDao.upsert(
            when (accessory.slot) {
                PetEngine.Slot.HAT -> row.copy(equippedHat = if (removing) "" else id)
                PetEngine.Slot.FACE -> row.copy(equippedFace = if (removing) "" else id)
                PetEngine.Slot.NECK -> row.copy(equippedNeck = if (removing) "" else id)
                PetEngine.Slot.BACK -> row.copy(equippedBack = if (removing) "" else id)
            }
        )
    }

    suspend fun renamePet(rawName: String) {
        val row = petDao.once() ?: return
        val name = rawName.trim().take(12)
        if (name.isBlank()) return
        petDao.upsert(row.copy(name = name))
    }

    /**
     * Remembers where the user dragged one accessory to.
     *
     * The offsets are theirs, not ours: glyph padding differs per emoji, so the
     * person looking at the screen is the only one who can judge "straight".
     */
    suspend fun moveAccessoryOffset(id: String, dx: Float, dy: Float) {
        val row = petDao.once() ?: return
        val map = PetEngine.OffsetStore.with(PetEngine.OffsetStore.parse(row.offsetCsv), id, dx, dy)
        petDao.upsert(row.copy(offsetCsv = PetEngine.OffsetStore.serialize(map)))
    }

    /** Drops one accessory back to its default anchor. */
    suspend fun resetAccessoryOffset(id: String) {
        val row = petDao.once() ?: return
        val map = PetEngine.OffsetStore.without(PetEngine.OffsetStore.parse(row.offsetCsv), id)
        petDao.upsert(row.copy(offsetCsv = PetEngine.OffsetStore.serialize(map)))
    }

    /** Clears every correction at once, for when a pet has been nudged into chaos. */
    suspend fun resetAllAccessoryOffsets() {
        val row = petDao.once() ?: return
        if (row.offsetCsv.isBlank()) return
        petDao.upsert(row.copy(offsetCsv = ""))
    }

    private fun ownedSet(csv: String): Set<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun equippedIds(row: PetEntity): Set<String> =
        setOf(row.equippedHat, row.equippedFace, row.equippedNeck, row.equippedBack)
            .filter { it.isNotBlank() }
            .toSet()

    private fun everyThirtySeconds(): Flow<Long> = flow {
        while (true) {
            emit(TimeUtils.now())
            delay(30_000L)
        }
    }
    // ---------- Review (spaced repetition) ----------

    /** Every item still in rotation, oldest learning first. */
    val reviewItems: Flow<List<ReviewItemEntity>> = reviewDao.observeActive()

    /**
     * Today's queue, oldest first.
     *
     * The day comes from [todayKey], so a screen left open across midnight
     * rolls over to tomorrow's list on its own instead of showing yesterday's.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dueReviews: Flow<List<ReviewItemEntity>> =
        todayKey.flatMapLatest { day -> reviewDao.observeDue(day) }

    suspend fun countDueReviews(): Int = reviewDao.countDue(TimeUtils.todayKey())

    /**
     * Records everything the user typed, splitting it into recall-sized cards.
     *
     * Splitting is not cosmetic — it is the working-memory constraint itself.
     * A card that cannot be held in mind in one breath cannot be *retrieved*,
     * and retrieval is the only part of review that consolidates memory. A
     * 400-character block quote would be silently "reviewed" by recognising it,
     * which produces the sensation of learning without the storage.
     *
     * Newlines and sentence terminators are treated as boundaries, and each
     * piece is capped, so one paste becomes several honest little cards.
     * Answers pair with questions **by line position**. Answers are optional and
     * may be fewer than the questions: a question with no answer stays a valid
     * card, it just has nothing to reveal. Pairing on position rather than on
     * content is deliberate — content matching would silently attach the wrong
     * answer after any reordering, which is worse than no answer at all.
     */
    suspend fun recordReviews(
        rawQuestions: String,
        rawAnswers: String,
        difficulty: SpacedRepetition.Difficulty
    ): BatchReviewRecord {
        val allLines = rawQuestions.split(SENTENCE_SPLIT)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val lines = allLines.take(MAX_CARDS_PER_BATCH)
        if (lines.isEmpty()) return BatchReviewRecord(starsGained = 0, created = 0, skipped = 0, tooLong = 0)

        // Answers are split on newlines only: a fragmentary answer may legitimately
        // contain a full stop, and cutting it at the punctuation would truncate it.
        val answerPieces = rawAnswers.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val tooLong = lines.count { it.length > MAX_CARD_LENGTH }
        var stars = 0
        lines.forEachIndexed { index, question ->
            val clamped = question.take(MAX_CARD_LENGTH)
            val answer = answerPieces.getOrNull(index)?.take(MAX_CARD_LENGTH).orEmpty()
            val now = TimeUtils.now()
            reviewDao.insert(
                ReviewItemEntity(
                    content = clamped,
                    answer = answer,
                    difficulty = difficulty.level,
                    ease = difficulty.initialEase,
                    createdAt = now,
                    nextDueDay = SpacedRepetition.initialDueDay(now)
                )
            )
            val gained = SpacedRepetition.starsForRecording(difficulty)
            awardStars(gained, "记录学习内容：${clamped.take(30)}")
            stars += gained
        }
        return BatchReviewRecord(
            starsGained = stars,
            created = lines.size,
            skipped = (allLines.size - lines.size).coerceAtLeast(0),
            tooLong = tooLong
        )
    }

    /**
     * Grades one review and reschedules the card.
     *
     * Note that the card is updated *before* the star grant, and both are plain
     * sequential suspends: a double tap on the same card is stopped upstream by
     * the day-key comparison rather than by a mutex, which also survives a
     * process restart.
     */
    suspend fun gradeReview(
        item: ReviewItemEntity,
        quality: SpacedRepetition.Quality
    ): ReviewOutcome {
        val now = TimeUtils.now()
        val difficulty = SpacedRepetition.Difficulty.of(item.difficulty)
        val outcome = SpacedRepetition.applyReview(
            progress = SpacedRepetition.Progress(
                repetitions = item.repetitions,
                lapses = item.lapses,
                intervalDays = item.intervalDays,
                ease = item.ease
            ),
            quality = quality,
            difficulty = difficulty,
            reviewedAtMillis = now
        )
        val bonus = quality != SpacedRepetition.Quality.FORGOT && RewardEngine.rollReviewBonus()
        val stars = if (bonus) outcome.stars * RewardEngine.REVIEW_BONUS_MULTIPLIER else outcome.stars

        reviewDao.update(
            item.copy(
                stage = item.stage + 1,
                repetitions = outcome.repetitions,
                lapses = outcome.lapses,
                intervalDays = outcome.intervalDays,
                ease = outcome.ease,
                lastReviewedAt = now,
                nextDueDay = outcome.nextDueDay,
                isMastered = outcome.isMastered
            )
        )
        reviewLogDao.insert(
            ReviewLogEntity(
                reviewItemId = item.id,
                reviewedDay = TimeUtils.todayKey(now),
                qualityScore = quality.score,
                starsGained = stars,
                reviewedAt = now
            )
        )
        awardStars(stars, "复习：${item.content.take(30)}")

        // Tier bonus for sustained review, counted on the log rather than in
        // memory so it survives the process being killed and cannot be inflated
        // by replaying a single session.
        val day = TimeUtils.todayKey(now)
        val reviewedToday = reviewLogDao.countReviewsOnDay(day)
        val streakBonus = RewardEngine.reviewStreakBonus(reviewedToday)
        val total = if (streakBonus > 0) {
            awardStars(streakBonus, "今日第 $reviewedToday 张复习奖励")
            stars + streakBonus
        } else {
            stars
        }

        return ReviewOutcome(
            starsGained = total,
            streakBonus = streakBonus,
            reviewsToday = reviewedToday,
            nextDueDay = outcome.nextDueDay,
            intervalDays = outcome.intervalDays,
            mastered = outcome.isMastered,
            bonus = bonus,
            quality = quality
        )
    }

    /**
     * Aggregate displayed on the review screen's header.
     *
     * The streak is derived from distinct review days rather than a stored
     * counter, so a missed day is discovered rather than silently carried.
     */
    val reviewSummary: Flow<ReviewSummary> = combine(
        dueReviews,
        reviewLogDao.observeReviewDays()
    ) { due, days ->
        val today = TimeUtils.todayKey()
        ReviewSummary(
            dueCount = due.size,
            doneToday = reviewLogDao.countReviewsOnDay(today),
            streak = computeStreak(days),
            starsToday = reviewLogDao.starsOnDay(today),
            totalCards = reviewDao.countActive(),
            masteredCount = reviewDao.countMastered()
        )
    }

    // The streak maths already exists above for check-ins and follows exactly
    // the same rule (anchored on today, falling back to yesterday), so it is
    // reused rather than duplicated — two slowly diverging streak definitions
    // would eventually report different numbers for the same day.
    suspend fun deleteReviewItem(item: ReviewItemEntity) = reviewDao.delete(item)

    // ---------- Seeding ----------

    /**
     * Seeds the prize catalogue on first launch, and after a migration that
     * clears it.
     *
     * **The weight column IS the percentage.** Weights always sum to
     * [com.ran.app.core.PrizeBalancer.TOTAL], so "16" reads as "16% chance" and
     * the column needs no explanation to the person editing it.
     *
     * The shape: the top three carry 46% between them, so a child is rarely
     * handed an empty screen — an empty screen is what actually ends the habit,
     * not the absence of a big win. The middle band keeps a gradient between
     * "trivial" and "rare" so the ladder does not collapse into two steps. The
     * three dearest carry the jackpot flag, which marks them in the history
     * list; it does not change how they are drawn.
     */
    suspend fun seedIfEmpty() {
        if (prizeDao.count() == 0) {
            listOf(
                PrizeEntity(title = "1元", description = "零花钱起步", emoji = "🪙", weight = 16.0, starCost = 1),
                PrizeEntity(title = "15分钟屏幕", description = "先做完正事", emoji = "⏱", weight = 16.0, starCost = 2),
                PrizeEntity(title = "免一次家务", description = "这次爸妈来做", emoji = "🧹", weight = 14.0, starCost = 3),
                PrizeEntity(title = "2元", description = "自己存着也行", emoji = "💰", weight = 6.0, starCost = 4),
                PrizeEntity(title = "5元", description = "买点想吃的", emoji = "💵", weight = 6.0, starCost = 5),
                PrizeEntity(title = "普通水果券", description = "当季水果，任选", emoji = "🍎", weight = 5.0, starCost = 6),
                PrizeEntity(title = "10元", description = "零花钱升级", emoji = "💴", weight = 5.0, starCost = 8),
                PrizeEntity(title = "大餐：牛排或羊排或海鲜", description = "全家一起吃好的", emoji = "🥩", weight = 4.0, starCost = 10),
                PrizeEntity(title = "饮料券", description = "想喝什么自己选", emoji = "🥤", weight = 4.0, starCost = 12),
                PrizeEntity(title = "15元", description = "可以计划一下", emoji = "💶", weight = 4.0, starCost = 14),
                PrizeEntity(title = "看一场电影", description = "选片一起看", emoji = "🎬", weight = 3.0, starCost = 16),
                PrizeEntity(title = "周末半日游", description = "去哪儿你定", emoji = "🚗", weight = 5.0, starCost = 18),
                PrizeEntity(title = "贵重水果：车厘子，榴莲等", description = "当季最贵的那种", emoji = "🍒", weight = 4.0, starCost = 20),
                PrizeEntity(title = "冰激凌券", description = "饭后吃", emoji = "🍦", weight = 2.0, starCost = 22),
                PrizeEntity(title = "20元", description = "大额，好好计划", emoji = "🧧", weight = 2.0, starCost = 24),
                PrizeEntity(
                    title = "买书券", description = "看完聊聊", emoji = "📚",
                    weight = 1.0, starCost = 26, isJackpot = true
                ),
                PrizeEntity(
                    title = "周末晚睡15分钟特权1次", description = "仅限周末", emoji = "🌙",
                    weight = 1.0, starCost = 28, isJackpot = true
                ),
                PrizeEntity(
                    title = "30元", description = "头奖中的头奖", emoji = "🧨",
                    weight = 2.0, starCost = 30, isJackpot = true
                )
            ).forEach { prizeDao.insert(it) }
        }
        if (foodDao.count() == 0) {
            listOf(
                // 蛋与豆制品
                FoodEntity(name = "西红柿炒鸡蛋", category = "6-8月", emoji = "🍅"),
                FoodEntity(name = "韭菜炒鸡蛋", category = "3-5月", emoji = "🌿"),
                FoodEntity(name = "丝瓜炒蛋", category = "6-9月", emoji = "🥒"),
                FoodEntity(name = "蒸水蛋", category = "全年（鸡蛋）", emoji = "🥚"),
                FoodEntity(name = "麻婆豆腐", category = "全年（豆腐、豆瓣酱）", emoji = "🌶"),
                FoodEntity(name = "家常豆腐", category = "全年（豆腐）", emoji = "🧈"),
                FoodEntity(name = "香干炒肉", category = "全年（香干/豆腐干）", emoji = "🍖"),
                FoodEntity(name = "虾仁豆腐蒸蛋", category = "全年（鸡蛋、豆腐、虾）", emoji = "🦐"),
                FoodEntity(name = "西葫芦炒鸡蛋", category = "4-7月（春夏）", emoji = "🥒"),
                // 猪肉牛肉
                FoodEntity(name = "青椒炒肉", category = "6-9月", emoji = "🫑"),
                FoodEntity(name = "回锅肉", category = "全年（蒜苗冬春季3-4月）", emoji = "🥓"),
                FoodEntity(name = "鱼香肉丝", category = "全年", emoji = "🥢"),
                FoodEntity(name = "红烧肉", category = "全年", emoji = "🍖"),
                FoodEntity(name = "土豆炖牛腩", category = "全年（土豆耐储，6-10月）", emoji = "🥔"),
                FoodEntity(name = "胡萝卜炖牛腩", category = "9-11月", emoji = "🥕"),
                FoodEntity(name = "蒜苔炒肉", category = "4-5月", emoji = "🥬"),
                FoodEntity(name = "芹菜炒肉", category = "10-2月", emoji = "🥬"),
                FoodEntity(name = "木耳炒肉", category = "全年（干货木耳全年）", emoji = "🍄"),
                FoodEntity(name = "四季豆炒肉", category = "5-10月", emoji = "🫛"),
                FoodEntity(name = "肉末豆角", category = "7-10月", emoji = "🫛"),
                FoodEntity(name = "肉末烧扁豆", category = "7-11月", emoji = "🫛"),
                FoodEntity(name = "莴笋炒肉丝", category = "9-11月", emoji = "🥬"),
                FoodEntity(name = "腊肉炒红菜苔", category = "10-2月", emoji = "🥓"),
                FoodEntity(name = "冬笋炒腊肉", category = "10-2月", emoji = "🎍"),
                FoodEntity(name = "腐竹烧木耳", category = "全年", emoji = "🍄"),
                // 鸡鸭
                FoodEntity(name = "可乐鸡翅", category = "全年", emoji = "🍗"),
                FoodEntity(name = "辣子鸡", category = "全年（辣椒6-9月）", emoji = "🌶"),
                FoodEntity(name = "口水鸡", category = "全年", emoji = "🍗"),
                // 汤
                FoodEntity(name = "香菇鸡汤", category = "全年（香菇干货全年）", emoji = "🍄"),
                FoodEntity(name = "西红柿鸡蛋汤", category = "6-8月", emoji = "🍲"),
                FoodEntity(name = "紫菜蛋花汤", category = "全年（紫菜干货）", emoji = "🍜"),
                FoodEntity(name = "冬瓜排骨汤", category = "6-9月", emoji = "🍲"),
                FoodEntity(name = "玉米排骨汤", category = "7-9月（嫩玉米7-8月）", emoji = "🌽"),
                FoodEntity(name = "萝卜牛腩汤", category = "9-12月（秋冬白萝卜）", emoji = "🥕"),
                FoodEntity(name = "丝瓜蛋汤", category = "6-9月", emoji = "🥒"),
                // 河海鲜
                FoodEntity(name = "清蒸鱼", category = "全年", emoji = "🐟"),
                FoodEntity(name = "清蒸鲈鱼", category = "12-2月", emoji = "🐠"),
                FoodEntity(name = "白灼虾", category = "全年", emoji = "🦐"),
                FoodEntity(name = "小龙虾", category = "6-8月", emoji = "🦞"),
                FoodEntity(name = "对虾/基围虾", category = "8-10月", emoji = "🦐"),
                FoodEntity(name = "大闸蟹", category = "9-12月", emoji = "🦀"),
                FoodEntity(name = "皮皮虾", category = "3-5月", emoji = "🦐"),
                FoodEntity(name = "扇贝", category = "9-11月", emoji = "🐚"),
                FoodEntity(name = "鲍鱼", category = "7-8月", emoji = "🐚"),
                FoodEntity(name = "生蚝/牡蛎", category = "12-3月", emoji = "🦪"),
                FoodEntity(name = "花蛤", category = "6-8月", emoji = "🐚"),
                // 素菜
                FoodEntity(name = "酸辣土豆丝", category = "全年（土豆耐储）", emoji = "🥔"),
                FoodEntity(name = "手撕包菜", category = "全年（卷心菜冬春为主）", emoji = "🥬"),
                FoodEntity(name = "蒜蓉西兰花", category = "9-11月", emoji = "🥦"),
                FoodEntity(name = "地三鲜", category = "6-8月（茄子、青椒、土豆）", emoji = "🍆"),
                FoodEntity(name = "干煸豆角", category = "6-9月", emoji = "🫛"),
                FoodEntity(name = "茄子豆角", category = "6-9月", emoji = "🍆"),
                FoodEntity(name = "红烧茄子", category = "6-8月", emoji = "🍆"),
                FoodEntity(name = "凉拌黄瓜", category = "5-8月", emoji = "🥒"),
                FoodEntity(name = "凉拌西红柿", category = "6-8月", emoji = "🍅"),
                FoodEntity(name = "蚝油生菜", category = "全年", emoji = "🥬"),
                FoodEntity(name = "蒜蓉油麦菜", category = "4-6和9-10月", emoji = "🥬"),
                // 主食与硬菜
                FoodEntity(name = "蛋炒饭", category = "全年", emoji = "🍚"),
                FoodEntity(name = "西红柿鸡蛋面", category = "6-8月", emoji = "🍜"),
                FoodEntity(name = "煎牛排", category = "全年", emoji = "🥩"),
                FoodEntity(name = "烤羊排", category = "全年", emoji = "🍖")
            ).forEach { foodDao.insert(it) }
        }
    }
}