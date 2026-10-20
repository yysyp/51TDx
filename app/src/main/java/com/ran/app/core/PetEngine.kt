package com.ran.app.core

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The virtual pet's behaviour model: states, decay, expressions and the shop.
 *
 * Everything here is pure so the behaviour can be unit-tested without Android.
 *
 * Why a pet belongs in a learning app at all is not decoration: it is a sink
 * that converts stars back into *caring*. Three well-established effects do the
 * heavy lifting.
 *
 * - **Endowment / IKEA effect:** once the user has fed and dressed it, the pet
 *   is worth more to them than the sum of the stars spent, so they act to
 *   protect it. That protective impulse is what pulls them back.
 * - **Zeigarnik effect:** hunger bars are deliberately *visible and
 *   unfinished*. An open loop creates mild cognitive tension until closed.
 * - **Loss aversion:** the pet does not die (cruelty would poison the loop),
 *   but it visibly wilts. Avoiding a loss motivates more reliably than chasing
 *   an equivalent gain.
 *
 * The pet is a *claim on effort already spent* — stars are minted by real work
 * elsewhere in the app, so feeding it indirectly means "I studied today".
 */
object PetEngine {

    // ---------- Decay rates ----------
    //
    // Chosen against a real child's day, not against "maximum engagement".
    // Full hunger drains in ~20h, so leaving for school and coming back after
    // dinner leaves it hungry (visible enough to pull you back) but never at
    // zero — waking to a pet that has *already bottomed out* reads as scolding,
    // not as an invitation. One or two cares a visit keep it comfortable.
    //
    // Slowing the drain was a deliberate correction: at the first attempt the
    // numbers were twice as fast (~10h) and a simulation showed that even
    // feeding morning and evening could not keep it content, which turns a
    // bonding feature into a chore. Mood drains at half the hunger rate
    // because mood is the emotional channel — it should reflect neglect over
    // days, not over one night.

    /** Milliseconds of absence per point of hunger lost. ~20h from full to empty. */
    const val HUNGER_MS_PER_POINT = 12 * 60 * 1000L

    /** Milliseconds of absence per point of mood lost. ~40h from full to empty. */
    const val MOOD_MS_PER_POINT = 24 * 60 * 1000L

    /** Beyond this absence the pet is drawn asleep rather than sad. */
    const val SLEEP_AFTER_MINUTES = 12 * 60

    // ---------- Growth ----------
    //
    // Growth runs on two axes deliberately.
    //
    // Stage is the *shape*: five visible milestones that change how the pet
    // looks, because nothing sells the first step like seeing something change.
    // Level is the *number*: it never stops.
    //
    // The second axis exists because of what "finished" does to a habit loop.
    // Originally the pet topped out at 60 care points — roughly 300 stars, two
    // or three weeks — and after that the screen had nothing left to say.
    // A completed toy does not retain attention; the day there is no next step
    // is the day the feature stops being a reason to open the app. Making the
    // level unbounded costs one line of arithmetic and removes that cliff.

    /**
     * Growth is driven by care actions, never by stars paid directly.
     *
     * Thresholds are spaced by roughly a month of comfortable caring rather
     * than evenly: the first milestone must arrive within days to establish
     * that caring does something, and the later ones may take weeks because by
     * then the pet itself is the reward.
     */
    enum class Stage(val step: Int, val label: String, val minCare: Int) {
        EGG(0, "蛋", 0),
        BABY(1, "幼崽", 1),
        CHILD(2, "小伙伴", 30),
        ADULT(3, "守护兽", 90),
        LEGEND(4, "星辰伙伴", 240);

        companion object {
            fun forCare(care: Int): Stage = entries.lastOrNull { care >= it.minCare } ?: EGG

            /** The next milestone, or null once every stage has been reached. */
            fun next(current: Stage): Stage? = entries.firstOrNull { it.step == current.step + 1 }
        }
    }

    /**
     * Care points per level.
     *
     * Fixed rather than escalating so the user can always tell how much effort
     * one more level costs — an accelerating curve hides its own price, and a
     * hidden price reads as the game moving the goalposts.
     */
    const val LEVEL_CARE_PER_LEVEL = 20

    /** Always has a next level, which is the entire point of having levels. */
    object Level {
        fun levelFor(care: Int): Int = care.coerceAtLeast(0) / LEVEL_CARE_PER_LEVEL + 1

        /** Care accumulated inside the current level, for the progress bar. */
        fun careIntoLevel(care: Int): Int = care.coerceAtLeast(0) % LEVEL_CARE_PER_LEVEL

        fun progressInLevel(care: Int): Float =
            careIntoLevel(care).toFloat() / LEVEL_CARE_PER_LEVEL.toFloat()

        fun careToNextLevel(care: Int): Int = LEVEL_CARE_PER_LEVEL - careIntoLevel(care)

        /** Total care needed to *reach* [level]. */
        fun totalCareForLevel(level: Int): Int = LEVEL_CARE_PER_LEVEL * (level - 1)
    }

    /** Species are cosmetic only; every stat behaves identically. */
    enum class Species(val id: Int, val label: String, val emojiSeed: String) {
        DUMPLING(0, "团子", "🥟"),
        CAT(1, "小奶猫", "🐱"),
        DRAGON(2, "小青龙", "🐲");

        companion object {
            fun of(id: Int?): Species = entries.firstOrNull { it.id == id } ?: DUMPLING
        }
    }

    // ---------- Expressions ----------

    /**
     * Six emotional states. The pet cycles through them on its own so simply
     * opening the app produces a reaction — a static pet is a decoration, and
     * decorations are ignored by week two.
     */
    enum class Expression(val label: String) {
        JOY("超开心"),
        HAPPY("很满足"),
        IDLE("发呆中"),
        HUNGRY("有点饿"),
        BEGGING("眼巴巴"),
        SAD("有点委屈"),
        SLEEPY("睡着了"),
        EATING("吃得香")
    }

    /**
     * Derives the expression from raw state.
     *
     * Ordering matters: the strongest signal (currently chewing) must win, and
     * physical need outranks mood — a starving pet does not look cheerful just
     * because you played with it ten minutes ago.
     */
    fun expression(hunger: Int, mood: Int, minutesSinceLastAction: Int, justAte: Boolean): Expression {
        if (justAte) return Expression.EATING
        if (minutesSinceLastAction >= SLEEP_AFTER_MINUTES) return Expression.SLEEPY
        if (hunger <= 15) return Expression.BEGGING
        if (hunger <= 40) return Expression.HUNGRY
        if (mood <= 25) return Expression.SAD
        if (mood >= 80 && hunger >= 70) return Expression.JOY
        if (mood >= 55) return Expression.HAPPY
        return Expression.IDLE
    }

    /** Repeated exposure to identical praise is what makes a pet feel scripted. */
    fun lineFor(expression: Expression, name: String): String {
        val pool = when (expression) {
            Expression.EATING -> listOf("好吃！还要一口～", "唔…饱饱的…", "这是今天最好吃的一口！")
            Expression.JOY -> listOf("今天和你在一起最开心啦 ⭐", "我可是最幸福的小家伙！", "再陪我玩一会儿嘛")
            Expression.HAPPY -> listOf("我现在心情很好哦", "你来了我就开心", "哼个小曲给你听～")
            Expression.IDLE -> listOf("我在想今天学了什么…", "你不在的时候我有点想你", "发呆也是一种修行")
            Expression.HUNGRY -> listOf("肚子有点咕咕叫…", "有吃的吗？一点点就好", "喂我一口我就精神了！")
            Expression.BEGGING -> listOf("我快饿扁了…给我点星星换吃的吧 🥺", "看我这么可爱，忍心吗？", "一点点就好，真的只要一点点")
            Expression.SAD -> listOf("你最近都没理我…", "有点孤单呢", "陪我玩一下好不好")
            Expression.SLEEPY -> listOf("呼…呼…", "等你回来我就精神啦", "（睡着了还在等你）")
        }
        val index = kotlin.math.abs(name.hashCode() + expression.ordinal) % pool.size
        return pool[index]
    }

    /** The nudge shown when the pet is hungry — this is the whole point of it. */
    fun begLine(hunger: Int): String = when {
        hunger <= 15 -> "快没力气了…点下面的小零食救救我 🥺"
        hunger <= 40 -> "有点饿了，喂我一口吧！"
        else -> ""
    }

    // ---------- Care actions ----------

    /**
     * Care actions — the daily places stars go.
     *
     * The spread matters more than any single price. Two cheaps crawls of the
     * cheapest actions exist deliberately (a sip of water, a tidy-up): on a day
     * when the user earned almost nothing and feels poor, there must still be
     * something they can do, because a pet you cannot afford to greet is a pet
     * you stop visiting. At the top end a full meal is expensive enough that
     * paying for it feels like spending real earnings.
     *
     * Everything costs stars. That is not incidental — without a sink, stars
     * inflate and the rewards that mint them stop meaning anything.
     */
    enum class Action(
        val emoji: String,
        val label: String,
        val cost: Int,
        val hungerDelta: Int,
        val moodDelta: Int,
        val carePoints: Int
    ) {
        DRINK("🥛", "喂口水", 3, 4, 6, 1),
        SNACK("🍪", "小零食", 4, 12, 4, 1),
        MEAL("🍚", "正餐", 10, 32, 10, 2),
        DESSERT("🍰", "小蛋糕", 7, 8, 22, 1),
        PLAY("🎾", "陪它玩", 8, -10, 25, 3),
        CLEAN("🧹", "打扫小窝", 3, 0, 14, 1),
        GROOM("🧴", "梳毛洗澡", 5, 0, 14, 1);

        companion object {
            fun all(): List<Action> = entries.toList()
        }
    }

    // ---------- Accessories ----------

    enum class Slot(val label: String) {
        HAT("头顶"),
        FACE("脸上"),
        NECK("脖子"),
        BACK("身后");

        /**
         * Default anchor as a fraction of the avatar box; (0,0) is the centre
         * and +y is down.
         *
         * These replaced a set of alignment-plus-offset rules that pushed some
         * accessories *outside* the box — a hat anchored to the top edge and
         * then nudged further up gets clipped, which is exactly "the hat is in
         * the wrong place" and no amount of patience can fix it from the UI.
         * Working from the centre keeps every anchor inside the frame.
         */
        fun anchor(): Pair<Float, Float> = when (this) {
            HAT -> 0f to -0.28f
            FACE -> 0f to 0.07f
            NECK -> 0f to 0.27f
            BACK -> -0.33f to 0.04f
        }

        /** Size of the drawn emoji, as a fraction of the avatar box. */
        fun glyphRatio(): Float = when (this) {
            HAT -> 0.34f
            FACE -> 0.30f
            NECK -> 0.26f
            BACK -> 0.24f
        }

        /** Drawn once per side, mirrored across the vertical axis. */
        fun mirrored(): Boolean = this == BACK
    }

    /**
     * Scarcity is cosmetic: three price bands, no difference in behaviour.
     *
     * It exists because a flat catalogue has no shape — with everything costing
     * roughly the same there is nothing to save up *for*, and saving toward a
     * specific legendary item sustains activity far longer than twelve equal
     * purchases would.
     */
    enum class Rarity(val label: String, val badge: String) {
        COMMON("常见", "·"),
        RARE("稀有", "✦"),
        LEGEND("传说", "★")
    }

    data class Accessory(
        val id: String,
        val name: String,
        val emoji: String,
        val price: Int,
        val slot: Slot,
        val rarity: Rarity = Rarity.COMMON
    )

    /**
     * Catalogue prices are set against realistic earnings: roughly one to three
     * days of honest check-ins per item. Cheaper than that and nothing feels
     * earned; pricier and the shop becomes a wall. Buying is the sink that gives
     * stars their value — without something to spend them on, rewards inflate
     * and stop motivating.
     *
     * Twenty-four items rather than twelve for a specific reason: collecting is
     * the one kind of content that cannot be "finished" in a bad way. Growth
     * milestones eventually run out, so each doubles as a reason to keep going,
     * and fully decking out a pet is a project measured in months.
     */
    val CATALOG: List<Accessory> = listOf(
        // Head
        Accessory("beanie", "毛线帽", "🧢", 15, Slot.HAT, Rarity.COMMON),
        Accessory("bow", "蝴蝶结", "🎀", 12, Slot.HAT, Rarity.COMMON),
        Accessory("straw", "小草帽", "👒", 22, Slot.HAT, Rarity.COMMON),
        Accessory("wizard", "巫师帽", "🧙", 40, Slot.HAT, Rarity.RARE),
        Accessory("crown", "小王冠", "👑", 50, Slot.HAT, Rarity.LEGEND),
        Accessory("halo", "小光环", "😇", 60, Slot.HAT, Rarity.LEGEND),

        // Face
        Accessory("glasses", "小眼镜", "👓", 18, Slot.FACE, Rarity.COMMON),
        Accessory("sunglasses", "墨镜", "🕶️", 25, Slot.FACE, Rarity.COMMON),
        Accessory("mask", "小口罩", "😷", 17, Slot.FACE, Rarity.COMMON),
        Accessory("nose", "小丑鼻", "🤡", 20, Slot.FACE, Rarity.COMMON),
        Accessory("spyglass", "侦探镜", "🔍", 27, Slot.FACE, Rarity.RARE),
        Accessory("goggles", "小泳镜", "🥽", 30, Slot.FACE, Rarity.RARE),

        // Neck
        Accessory("tie", "领结", "🎗️", 20, Slot.NECK, Rarity.COMMON),
        Accessory("scarf", "围巾", "🧣", 22, Slot.NECK, Rarity.COMMON),
        Accessory("bell", "小铃铛", "🔔", 23, Slot.NECK, Rarity.COMMON),
        Accessory("medal", "奖牌", "🏅", 38, Slot.NECK, Rarity.RARE),
        Accessory("beads", "小项链", "📿", 42, Slot.NECK, Rarity.RARE),
        Accessory("cape", "小披风", "🧥", 55, Slot.NECK, Rarity.LEGEND),

        // Behind
        Accessory("flower", "小花", "🌸", 13, Slot.BACK, Rarity.COMMON),
        Accessory("balloon", "气球", "🎈", 17, Slot.BACK, Rarity.COMMON),
        Accessory("leaf", "小绿叶", "🍃", 18, Slot.BACK, Rarity.COMMON),
        Accessory("cloud", "云朵", "☁️", 23, Slot.BACK, Rarity.COMMON),
        Accessory("rocket", "小火箭", "🚀", 46, Slot.BACK, Rarity.RARE),
        Accessory("wings", "翅膀", "🪽", 58, Slot.BACK, Rarity.LEGEND)
    )

    fun accessoryOf(id: String?): Accessory? = id?.let { wanted -> CATALOG.firstOrNull { it.id == wanted } }

    /**
     * A user-made position correction for one specific accessory, in fractions
     * of the avatar box. Stored relative to the slot's anchor, so changing a
     * default anchor later never invalidates anyone's adjustment.
     */
    data class Nudge(val dx: Float = 0f, val dy: Float = 0f)

    /**
     * Nudges are remembered *per accessory*, not per slot.
     *
     * That distinction is the whole feature: every emoji ships with its own
     * internal padding and baseline, so no single anchor can align a top hat and
     * a halo at once. A per-slot fix just moves which one looks wrong.
     *
     * Serialised into one CSV column rather than spread across fields — it is a
     * sparse map that is always read and written together with the pet row, and
     * one column keeps future changes out of the migration path.
     */
    object OffsetStore {
        /** Cap so a stray fling can never throw a hat out of frame. */
        const val MAX = 0.5f

        fun parse(csv: String): Map<String, Nudge> {
            if (csv.isBlank()) return emptyMap()
            return csv.split(',').mapNotNull { token ->
                val parts = token.split(':')
                if (parts.size != 3) return@mapNotNull null
                val dx = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val dy = parts[2].toFloatOrNull() ?: return@mapNotNull null
                parts[0] to Nudge(dx.coerceIn(-MAX, MAX), dy.coerceIn(-MAX, MAX))
            }.toMap()
        }

        fun serialize(map: Map<String, Nudge>): String =
            map.entries.joinToString(",") { (id, nudge) -> "$id:${round(nudge.dx)}:${round(nudge.dy)}" }

        fun with(map: Map<String, Nudge>, id: String, dx: Float, dy: Float): Map<String, Nudge> =
            map + (id to Nudge(dx.coerceIn(-MAX, MAX), dy.coerceIn(-MAX, MAX)))

        fun without(map: Map<String, Nudge>, id: String): Map<String, Nudge> = map - id

        fun offsetFor(map: Map<String, Nudge>, id: String): Nudge = map[id] ?: Nudge()

        /** Applies a stored nudge to its slot anchor, clamped to the frame. */
        fun combined(base: Pair<Float, Float>, nudge: Nudge): Pair<Float, Float> =
            (base.first + nudge.dx).coerceIn(-1f, 1f) to (base.second + nudge.dy).coerceIn(-1f, 1f)

        private fun round(value: Float): Float = kotlin.math.round(value * 1000f) / 1000f
    }

    // ---------- Pure state helpers ----------

    /** Applies absence decay to a stored value. Idempotent w.r.t. [lastTickAt]. */
    fun decay(value: Int, elapsedMillis: Long, millisPerPoint: Long): Int =
        (value - (elapsedMillis / millisPerPoint).toInt()).coerceIn(0, 100)

    /**
     * Returns the pet as it looks *now*, given how long ago it was last stored.
     *
     * Read-only by design: the row keeps the authoritative [lastTickAt], so
     * re-reading never double-charges time even across process restarts or
     * clock changes.
     */
    fun snapshotted(hunger: Int, mood: Int, lastTickAt: Long, now: Long): Pair<Int, Int> {
        val elapsed = (now - lastTickAt).coerceAtLeast(0L)
        return decay(hunger, elapsed, HUNGER_MS_PER_POINT) to decay(mood, elapsed, MOOD_MS_PER_POINT)
    }

    /** Minutes since the last interaction, for the asleep threshold. */
    fun minutesSince(lastTickAt: Long, now: Long): Int =
        ((now - lastTickAt).coerceAtLeast(0L) / 60_000L).toInt()

    fun clamp(value: Int): Int = value.coerceIn(0, 100)

    /** Equipment slot that [accessory] occupies, for one-tap replacing. */
    fun slotOf(id: String): Slot? = accessoryOf(id)?.slot
}
