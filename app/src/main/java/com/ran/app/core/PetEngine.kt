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

    /** Growth is driven by care actions, never by stars paid directly. */
    enum class Stage(val step: Int, val label: String, val minCare: Int) {
        EGG(0, "蛋", 0),
        BABY(1, "幼崽", 1),
        CHILD(2, "小伙伴", 20),
        ADULT(3, "守护兽", 60);

        companion object {
            fun forCare(care: Int): Stage = entries.lastOrNull { care >= it.minCare } ?: EGG
        }
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

    enum class Action(
        val emoji: String,
        val label: String,
        val cost: Int,
        val hungerDelta: Int,
        val moodDelta: Int,
        val carePoints: Int
    ) {
        SNACK("🍪", "小零食", 5, 12, 4, 1),
        MEAL("🍚", "正餐", 12, 32, 10, 2),
        DESSERT("🍰", "小蛋糕", 8, 8, 22, 1),
        PLAY("🎾", "陪它玩", 10, -10, 25, 3),
        GROOM("🧴", "梳毛洗澡", 6, 0, 14, 1);

        companion object {
            fun all(): List<Action> = entries.toList()
        }
    }

    // ---------- Accessories ----------

    enum class Slot(val label: String) { HAT("头顶"), FACE("脸上"), NECK("脖子"), BACK("身后") }

    data class Accessory(
        val id: String,
        val name: String,
        val emoji: String,
        val price: Int,
        val slot: Slot
    )

    /**
     * Catalogue prices are set against realistic earnings: roughly one to three
     * days of honest check-ins per item. Cheaper than that and nothing feels
     * earned; pricier and the shop becomes a wall. Buying is the sink that gives
     * stars their value — without something to spend them on, rewards inflate
     * and stop motivating.
     */
    val CATALOG: List<Accessory> = listOf(
        Accessory("beanie", "毛线帽", "🧢", 18, Slot.HAT),
        Accessory("bow", "蝴蝶结", "🎀", 15, Slot.HAT),
        Accessory("crown", "小王冠", "👑", 60, Slot.HAT),
        Accessory("wizard", "巫师帽", "🧙", 48, Slot.HAT),
        Accessory("glasses", "小眼镜", "👓", 22, Slot.FACE),
        Accessory("sunglasses", "墨镜", "🕶️", 30, Slot.FACE),
        Accessory("mask", "小口罩", "😷", 20, Slot.FACE),
        Accessory("scarf", "围巾", "🧣", 26, Slot.NECK),
        Accessory("tie", "领结", "🎗️", 24, Slot.NECK),
        Accessory("medal", "奖牌", "🏅", 45, Slot.NECK),
        Accessory("wings", "翅膀", "🪽", 70, Slot.BACK),
        Accessory("flower", "小花", "🌸", 16, Slot.BACK)
    )

    fun accessoryOf(id: String?): Accessory? = CATALOG.firstOrNull { it.id == id }

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
