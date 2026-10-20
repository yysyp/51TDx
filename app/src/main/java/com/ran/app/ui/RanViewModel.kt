package com.ran.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ran.app.RanApplication
import com.ran.app.core.DailyCycle
import com.ran.app.core.PetEngine
import com.ran.app.core.RewardEngine
import com.ran.app.core.SpacedRepetition
import com.ran.app.core.TimeUtils
import com.ran.app.data.CheckInResult
import com.ran.app.data.DrawResult
import com.ran.app.data.RanRepository
import com.ran.app.data.isResetAuthorised
import com.ran.app.data.ReviewSummary
import com.ran.app.data.ToggleTodoResult
import com.ran.app.data.local.FoodEntity
import com.ran.app.data.local.PlanEntity
import com.ran.app.data.local.PrizeEntity
import com.ran.app.data.local.ReviewItemEntity
import com.ran.app.data.local.TodoEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Transient celebration/feedback banner shown after a rewarding action. */
data class Feedback(val text: String, val starsGained: Int = 0, val isError: Boolean = false, val id: Long = 0)

/** Immutable snapshot powering the home dashboard. */
data class HomeStats(
    val stars: Int = 0,
    val starsToday: Int = 0,
    val level: Int = 1,
    val levelProgress: Float = 0f,
    val dailyRatio: Float = 0f,
    val streak: Int = 0,
    val activePlans: Int = 0,
    val openTodos: Int = 0,
    val encouragement: String = ""
)

class RanViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: RanRepository = (application as RanApplication).repository

    val prizes = repo.prizes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val drawRecords = repo.drawRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activePlans = repo.activePlans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val completedPlans = repo.completedPlans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val openTodos = repo.openTodos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val completedTodos = repo.completedTodos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val foods = repo.foods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val starEntries = repo.recentStarEntries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dueReviews = repo.dueReviews.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reviewItems = repo.reviewItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reviewSummary = repo.reviewSummary.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewSummary())
    val pet = repo.pet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Today's day key, re-emitted across midnight so nothing caches yesterday. */
    val todayKey: StateFlow<String> = repo.todayKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeUtils.todayKey())

    /**
     * Plans already stamped today, and only today.
     *
     * The check-in rows do not change at midnight — the *day* does. Deriving
     * this from [todayKey] instead of a one-shot read is what re-arms the
     * button each morning; reading it once left yesterday's ids in the set and
     * every completed plan greyed out for good.
     */
    val checkedInPlanIdsToday: StateFlow<Set<Long>> =
        combine(repo.allCheckIns, repo.todayKey) { checkIns, day ->
            DailyCycle.idsStampedOn(checkIns.map { DailyCycle.Stamp(it.planId, it.dayKey) }, day)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    // Lottery animation state
    private val _drawState = MutableStateFlow(DrawUiState())
    val drawState: StateFlow<DrawUiState> = _drawState.asStateFlow()

    private var rollJob: Job? = null

    // Food picker state
    private val _foodPick = MutableStateFlow<FoodEntity?>(null)
    val foodPick: StateFlow<FoodEntity?> = _foodPick.asStateFlow()
    private val _foodRolling = MutableStateFlow(false)
    val foodRolling: StateFlow<Boolean> = _foodRolling.asStateFlow()

    private var feedbackSeq = 0L

    val homeStats: StateFlow<HomeStats> = combine(
        repo.starBalance,
        repo.recentStarEntries,
        repo.distinctCheckInDays,
        repo.todayKey,
        combine(activePlans, openTodos) { plans, todos -> plans to todos }
    ) { balance, entries, days, day, planTodos ->
        // Derived from the live day key: a start-of-day timestamp captured once
        // would still point at yesterday after midnight, and "stars today"
        // would keep counting last night's earnings.
        val startOfToday = DailyCycle.startOfDayMillis(day)
        val starsToday = entries.filter { it.createdAt >= startOfToday && it.delta > 0 }.sumOf { it.delta }
        val ratio = RewardEngine.dailyGoalRatio(starsToday)
        val streak = computeStreakFrom(days)
        HomeStats(
            stars = balance,
            starsToday = starsToday,
            level = RewardEngine.levelFor(balance),
            levelProgress = RewardEngine.levelProgress(balance),
            dailyRatio = ratio,
            streak = streak,
            activePlans = planTodos.first.size,
            openTodos = planTodos.second.size,
            encouragement = RewardEngine.encouragement(ratio, streak)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats())

    init {    }

    // ---------- Feedback ----------

    private fun emit(text: String, stars: Int = 0, isError: Boolean = false) {
        feedbackSeq += 1
        _feedback.value = Feedback(text, stars, isError, feedbackSeq)
    }

    fun consumeFeedback() {
        _feedback.value = null
    }

    // ---------- Lottery ----------

    /**
     * Clears the star balance after a passphrase check.
     *
     * The check is here rather than in the repository so the UI can react to a
     * wrong passphrase without touching the database, and so an unauthorised
     * attempt never becomes a write.
     */
    fun resetStarBalance(passphrase: String) = viewModelScope.launch {
        if (!isResetAuthorised(passphrase)) {
            emit("口令不对，积分没有清零", isError = true)
            return@launch
        }
        val before = repo.currentBalance()
        val removed = repo.resetStarBalance()
        emit(
            when {
                removed == 0 -> "当前没有积分记录，无需清零"
                before < 0 -> "已清零，${-before} 星欠账一笔勾销"
                else -> "积分已清零（原 ${before} ⭐）"
            },
            // Negative so the balance ticker counts *down* to zero, which is what
            // happened; a positive delta would fly upwards against the change.
            stars = -before
        )
    }

    fun savePrize(prize: PrizeEntity) = viewModelScope.launch { repo.savePrize(prize) }

    fun deletePrize(prize: PrizeEntity) = viewModelScope.launch {
        repo.deletePrize(prize)
        emit("已删除「${prize.title}」")
    }

    /**
     * Stage one: start the roll. Nothing is decided yet — the draw is only
     * committed when the user stops it.
     */
    fun startRoll() {
        val state = _drawState.value
        if (state.phase != DrawPhase.IDLE) return
        val pool = prizes.value.filter { it.isActive }
        if (pool.isEmpty()) {
            emit("奖池是空的，先添加几个奖项吧", isError = true)
            return
        }
        _drawState.value = state.copy(
            phase = DrawPhase.ROLLING,
            rollingItem = pool.random(),
            result = null
        )
        rollJob?.cancel()
        rollJob = viewModelScope.launch {
            // Continuous cycle at a steady clip. The user decides when it ends,
            // so this is not a scripted deceleration: it is the machine running
            // while the hand is on the button.
            var visible = pool.random()
            while (true) {
                delay(70L)
                visible = pool.random()
                _drawState.value = _drawState.value.copy(rollingItem = visible)
            }
        }
    }

    /**
     * Stage two: stop the roll and commit the draw.
     *
     * The deceleration is short on purpose. The suspense is now the player's
     * own timing rather than a timer they wait out, so a long wind-down would be
     * dead time between deciding and finding out.
     */
    fun stopRoll() {
        if (_drawState.value.phase != DrawPhase.ROLLING) return
        rollJob?.cancel()
        rollJob = null
        viewModelScope.launch {
            var stepDelay = 70L
            repeat(6) {
                _drawState.value = _drawState.value.copy(rollingItem = prizes.value.randomOrNull())
                delay(stepDelay)
                stepDelay = (stepDelay * 1.35).toLong().coerceAtMost(300L)
            }
            when (val result = repo.drawPrize()) {
                is DrawResult.Success -> {
                    _drawState.value = _drawState.value.copy(
                        phase = DrawPhase.SCRATCH,
                        result = result.record,
                        rollingItem = null
                    )
                }
                DrawResult.EmptyPool -> {
                    _drawState.value = DrawUiState()
                    emit("奖池是空的，先添加几个奖项吧", isError = true)
                }
                // Unreachable while the draw allows debt; kept so a future change
                // to that rule fails loudly here instead of silently.
                DrawResult.NotEnoughStars -> {
                    _drawState.value = DrawUiState()
                    emit("星星不够，先去完成任务赚星星吧", isError = true)
                }
            }
        }
    }

    /** Stage three: the coating has been scratched away. */
    fun finishScratch() {
        if (_drawState.value.phase != DrawPhase.SCRATCH) return
        val record = _drawState.value.result ?: return
        _drawState.value = _drawState.value.copy(phase = DrawPhase.DONE)
        val msg = if (record.isJackpot) {
            "头奖！${record.prizeEmoji} ${record.prizeTitle}"
        } else {
            "${record.prizeEmoji} ${record.prizeTitle}"
        }
        emit(msg, stars = 0)
    }

    /**
     * A plain tap on the button while the coating is still intact.
     *
     * Used to end the draw outright, which quietly removed the entire reveal the
     * scratch step exists for -- the result was settled and discarded before the
     * user ever saw it. Now it only prompts; [finishScratch] runs when the
     * coating is actually opened.
     */
    fun remindToScratch() {
        if (_drawState.value.phase != DrawPhase.SCRATCH) return
        emit("用手指把涂层刮开才揭晓哦", isError = false)
    }

    /** Back to idle, ready for the next draw. */
    fun clearDrawResult() {
        rollJob?.cancel()
        rollJob = null
        _drawState.value = DrawUiState()
    }

    // ---------- Plans ----------

    fun savePlan(plan: PlanEntity) = viewModelScope.launch { repo.savePlan(plan) }

    fun deletePlan(plan: PlanEntity) = viewModelScope.launch {
        repo.deletePlan(plan)
        emit("已删除「${plan.title}」")
    }

    fun checkIn(plan: PlanEntity) = viewModelScope.launch {
        when (val result = repo.checkIn(plan.id)) {
            CheckInResult.AlreadyCheckedIn -> emit("今天已经打过卡了，明天继续", isError = true)
            is CheckInResult.Completed -> {
                val total = result.starsGained + result.finishBonus
                emit(
                    "计划完成！+${result.starsGained} 星" +
                        if (result.finishBonus > 0) "，另有达成奖励 +${result.finishBonus} 星" else "",
                    stars = total
                )
            }
            is CheckInResult.Progressed -> {
                val remaining = result.target - result.current
                emit("打卡成功，还差 $remaining 次", stars = RewardEngine.STAR_CHECK_IN)
            }
        }
    }

    fun checkInsForPlan(planId: Long) = repo.checkInsForPlan(planId)

    // ---------- Todos ----------

    fun saveTodo(todo: TodoEntity) = viewModelScope.launch { repo.saveTodo(todo) }

    fun deleteTodo(todo: TodoEntity) = viewModelScope.launch {
        repo.deleteTodo(todo)
        emit("已删除「${todo.title}」")
    }

    fun toggleTodo(todo: TodoEntity) = viewModelScope.launch {
        when (val result = repo.toggleTodo(todo)) {
            is ToggleTodoResult.Completed -> emit("完成！+${result.starsGained} 星", stars = result.starsGained)
            is ToggleTodoResult.Reopened -> emit("已重新打开", stars = result.starsRefunded)
            null -> Unit
        }
    }

    // ---------- Food ----------

    fun saveFood(food: FoodEntity) = viewModelScope.launch { repo.saveFood(food) }

    fun deleteFood(food: FoodEntity) = viewModelScope.launch {
        repo.deleteFood(food)
        emit("已删除「${food.name}」")
    }

    fun pickFood() {
        if (_foodRolling.value) return
        viewModelScope.launch {
            val pool = foods.value
            if (pool.isEmpty()) {
                emit("还没有待抽食物，先添加几个吧", isError = true)
                return@launch
            }
            _foodRolling.value = true
            // Deliberately much shorter than the lottery roll (~2.5s). "吃什么"
            // is answered while standing in front of an open fridge, so the
            // suspense is not the point — the decision is. Keeping only enough
            // animation to feel like a machine (~0.8s) removes the friction
            // without touching the lottery, where anticipation IS the reward.
            var stepDelay = 45L
            repeat(9) {
                _foodPick.value = pool.random()
                delay(stepDelay)
                stepDelay = (stepDelay * 1.2).toLong().coerceAtMost(150L)
            }
            val winner = repo.pickFood()
            _foodPick.value = winner ?: pool.random()
            _foodRolling.value = false
        }
    }

    fun clearFoodPick() {
        _foodPick.value = null
    }
    // ---------- Review (spaced repetition) ----------

    /**
     * Records what was learned today, split into recall-sized cards.
     *
     * The feedback line names the next contact explicitly ("明天见它第一面")
     * because an open loop is what pulls people back — the Zeigarnik effect,
     * reused here on memory rather than on tasks.
     */
    fun addReviewItem(
        question: String,
        answer: String = "",
        difficulty: SpacedRepetition.Difficulty = SpacedRepetition.Difficulty.NORMAL
    ) =
        viewModelScope.launch {
            val result = repo.recordReviews(question, answer, difficulty)
            when {
                result.created == 0 -> emit("先写点今天学到的东西吧", isError = true)
                result.created == 1 -> emit(
                    "已存进记忆库，明天见它第一面 +${result.starsGained} 星",
                    stars = result.starsGained
                )
                else -> emit(
                    "拆成了 ${result.created} 张小卡片，明天开始第一次复习 +${result.starsGained} 星",
                    stars = result.starsGained
                )
            }
        }

    /**
     * Grades one review after the user has actually tried to recall it.
     *
     * Copy differs by outcome on purpose: failure must read as *informative*
     * rather than punitive. A single lapse genuinely is when consolidation
     * happens fastest, so saying so is both kind and true.
     */
    fun gradeReview(item: ReviewItemEntity, quality: SpacedRepetition.Quality) = viewModelScope.launch {
        val result = repo.gradeReview(item, quality)
        val prefix = when {
            result.bonus -> "✨ 记忆闪光！双倍奖励 "
            result.quality == SpacedRepetition.Quality.FORGOT -> ""
            else -> ""
        }
        val tail = when (result.quality) {
            SpacedRepetition.Quality.FORGOT ->
                "忘了也没关系，现在重学的印象最深 · 明天再见它"
            SpacedRepetition.Quality.FUZZY ->
                if (result.mastered) "这项已经出师了 🎓"
                else "有点模糊，所以明天再巩固一次"
            SpacedRepetition.Quality.RECALL ->
                if (result.mastered) "已经刻进长期记忆，出师了 🎓"
                else "又想起来一次！${result.intervalDays} 天后再见它"
        }
        // Named in the copy on purpose: an unannounced bonus teaches nothing
        // except that the numbers moved. Saying "today's 5th card" makes the
        // next one worth reaching for.
        val streakNote = if (result.streakBonus > 0) {
            "（今日第 ${result.reviewsToday} 张，加成 +${result.streakBonus}）"
        } else {
            ""
        }
        emit("$prefix$tail +${result.starsGained} 星$streakNote", stars = result.starsGained)
    }

    fun deleteReviewItem(item: ReviewItemEntity) = viewModelScope.launch {
        repo.deleteReviewItem(item)
        emit("已删除「${item.content.take(20)}」")
    }

    private fun previousDay(dayKey: String): String = runCatching {
        java.time.LocalDate.parse(dayKey).minusDays(1).toString()
    }.getOrDefault(dayKey)

    // ---------- Pet ----------

    /**
     * Care actions are the app's star *sink*. They deliberately do not show a
     * "+N stars" flash like earning does: spending should feel like giving, not
     * losing, and the visible payoff is the pet's reaction rather than a number.
     */
    fun petAction(action: PetEngine.Action) = viewModelScope.launch {
        val result = repo.performPetAction(action)
        if (!result.ok) {
            emit("${result.spokenLine} · ${result.banner}", isError = true)
            return@launch
        }
        emit(
            if (result.banner.isNotBlank()) "${result.banner}｜${result.spokenLine}"
            else result.spokenLine
        )
    }

    fun buyAccessory(id: String) = viewModelScope.launch {
        val result = repo.purchaseAccessory(id)
        emit(result.message, isError = !result.ok)
    }

    fun renamePet(name: String) = viewModelScope.launch {
        repo.renamePet(name)
        val clean = name.trim().take(12)
        if (clean.isNotBlank()) emit("以后就叫「$clean」啦")
    }

    /** Commits a drag; called once per gesture end, not on every move. */
    fun moveAccessory(id: String, dx: Float, dy: Float) = viewModelScope.launch {
        repo.moveAccessoryOffset(id, dx, dy)
    }

    fun resetAccessory(id: String) = viewModelScope.launch {
        repo.resetAccessoryOffset(id)
        emit("装扮回到原来的位置啦")
    }

    fun resetAllAccessories() = viewModelScope.launch {
        repo.resetAllAccessoryOffsets()
        emit("所有装扮都摆正了")
    }

    // ---------- Helpers ----------

    private fun computeStreakFrom(days: List<String>): Int {
        if (days.isEmpty()) return 0
        val set = days.toSet()
        val today = TimeUtils.todayKey()
        val yesterday = TimeUtils.todayKey(TimeUtils.now() - 86_400_000L)
        var cursor = if (set.contains(today)) today else if (set.contains(yesterday)) yesterday else return 0
        var streak = 0
        while (set.contains(cursor)) {
            streak++
            cursor = TimeUtils.todayKey(TimeUtils.now() - (streak * 86_400_000L))
        }
        return streak
    }

    fun currentBalanceNow(callback: (Int) -> Unit) = viewModelScope.launch { callback(repo.currentBalance()) }
}

/** The three stages a draw moves through, in order. */
enum class DrawPhase { IDLE, ROLLING, SCRATCH, DONE }

data class DrawUiState(
    val phase: DrawPhase = DrawPhase.IDLE,
    val rollingItem: com.ran.app.data.local.PrizeEntity? = null,
    val result: com.ran.app.data.local.DrawRecordEntity? = null
) {
    /** Kept so callers that only care about "is the machine running" read clearly. */
    val isRolling: Boolean get() = phase == DrawPhase.ROLLING
}
