package com.ran.app

import com.ran.app.core.PetEngine
import com.ran.app.core.PetEngine.Action
import com.ran.app.core.PetEngine.Expression
import com.ran.app.core.PetEngine.Rarity
import com.ran.app.core.PetEngine.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pet's state machine, decay maths and shop economics.
 *
 * Decay in particular is easy to get subtly wrong: too fast and the pet nags,
 * too slow and it never asks for anything, which silently removes the feature's
 * entire purpose as a reason to earn stars.
 */
class PetEngineTest {

    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test
    fun `a pet left alone a full day is clearly hungry but never below zero`() {
        val start = 1_000_000L
        val (hunger, _) = PetEngine.snapshotted(100, 100, start, start + day)
        assertEquals(0, hunger)
        assertTrue("hunger must stay within bounds", hunger in 0..100)
    }

    @Test
    fun `hunger reaches empty in roughly twenty hours`() {
        val start = 0L
        assertEquals(0, PetEngine.snapshotted(100, 100, start, start + 20 * hour).first)
        val nineteenHours = PetEngine.snapshotted(100, 100, start, start + 19 * hour).first
        assertTrue("should still have some left after nineteen hours: $nineteenHours", nineteenHours > 0)
    }

    @Test
    fun `mood decays about half as fast as hunger`() {
        val start = 0L
        // Ten hours: hunger has lost half its bar, mood only a quarter.
        val (hunger, mood) = PetEngine.snapshotted(100, 100, start, start + 10 * hour)
        assertTrue("mood should outlast hunger: hunger=$hunger mood=$mood", mood > hunger)
        assertTrue(hunger > 0)
    }

    @Test
    fun `decay is idempotent per tick anchor and never run twice`() {
        val start = 0L
        val first = PetEngine.snapshotted(100, 100, start, start + 5 * hour).first
        // Re-reading must give the identical answer: start is the anchor.
        val repeated = PetEngine.snapshotted(100, 100, start, start + 5 * hour).first
        assertEquals(first, repeated)
    }

    @Test
    fun `negative elapsed time does not resurrect the pet`() {
        val (hunger, mood) = PetEngine.snapshotted(40, 40, 5_000L, 1_000L)
        assertEquals(40, hunger)
        assertEquals(40, mood)
    }

    @Test
    fun `expression prioritises physical need over mood`() {
        // Starving but euphoric must still read as begging: you cannot play
        // your way out of hunger.
        assertEquals(
            Expression.BEGGING,
            PetEngine.expression(hunger = 5, mood = 100, minutesSinceLastAction = 0, justAte = false)
        )
        assertEquals(
            Expression.HUNGRY,
            PetEngine.expression(hunger = 30, mood = 95, minutesSinceLastAction = 0, justAte = false)
        )
        assertEquals(
            Expression.JOY,
            PetEngine.expression(hunger = 90, mood = 90, minutesSinceLastAction = 0, justAte = false)
        )
    }

    @Test
    fun `a long absence reads as sleep rather than neglect`() {        assertEquals(
            Expression.SLEEPY,
            PetEngine.expression(hunger = 80, mood = 80, minutesSinceLastAction = PetEngine.SLEEP_AFTER_MINUTES, justAte = false)
        )
    }

    @Test
    fun `chewing overrides everything else`() {
        assertEquals(
            Expression.EATING,
            PetEngine.expression(hunger = 0, mood = 0, minutesSinceLastAction = 9999, justAte = true)
        )
    }

    @Test
    fun `every expression has several lines so praise does not feel scripted`() {
        Expression.values().forEach { expression ->
            val lines = (0 until 12).map { i -> PetEngine.lineFor(expression, "name$i") }.toSet()
            assertTrue("${expression.label} only produced one line", lines.size > 1)
        }
    }

    @Test
    fun `care actions grow the pet and none of them is free`() {
        Action.all().forEach { action ->
            assertTrue("${action.label} must cost stars", action.cost > 0)
            assertTrue("${action.label} must grant care points", action.carePoints > 0)
        }
        // Playing costs energy — you cannot sustain a pet on attention alone.
        assertTrue(Action.PLAY.hungerDelta < 0)
    }

    @Test
    fun `stages follow care count monotonically`() {
        assertEquals(Stage.EGG, Stage.forCare(0))
        assertEquals(Stage.BABY, Stage.forCare(1))
        assertEquals(Stage.CHILD, Stage.forCare(30))
        assertEquals(Stage.ADULT, Stage.forCare(90))
        assertEquals(Stage.LEGEND, Stage.forCare(240))
        assertEquals(Stage.LEGEND, Stage.forCare(10_000))
    }

    /**
     * The regression that motivated the level axis: the old model topped out at
     * 60 care points and afterwards the pet had nothing left to offer. Growth
     * has to keep producing a next mark however long someone plays.
     */
    @Test
    fun `levels never stop even after the last stage`() {
        val afterLegend = 10_000
        assertEquals(Stage.LEGEND, Stage.forCare(afterLegend))
        assertTrue(PetEngine.Level.levelFor(afterLegend) > PetEngine.Level.levelFor(240))
        assertTrue(PetEngine.Level.levelFor(afterLegend + 20) > PetEngine.Level.levelFor(afterLegend))
    }

    @Test
    fun `level maths is self consistent`() {
        assertEquals(1, PetEngine.Level.levelFor(0))
        assertEquals(1, PetEngine.Level.levelFor(PetEngine.LEVEL_CARE_PER_LEVEL - 1))
        assertEquals(2, PetEngine.Level.levelFor(PetEngine.LEVEL_CARE_PER_LEVEL))
        assertEquals(PetEngine.LEVEL_CARE_PER_LEVEL, PetEngine.Level.totalCareForLevel(2))
        // Negative can only arrive from corrupt data; it must not divide negative.
        assertEquals(1, PetEngine.Level.levelFor(-5))

        assertEquals(0f, PetEngine.Level.progressInLevel(0))
        assertEquals(PetEngine.LEVEL_CARE_PER_LEVEL, PetEngine.Level.careToNextLevel(0))
        assertEquals(19, PetEngine.Level.careToNextLevel(1))

        // Every level boundary is reached exactly once per range.
        for (care in 0 until PetEngine.LEVEL_CARE_PER_LEVEL * 4) {
            val level = PetEngine.Level.levelFor(care)
            assertTrue(
                "care=$care reported level=$level",
                care >= PetEngine.Level.totalCareForLevel(level) &&
                    care < PetEngine.Level.totalCareForLevel(level + 1)
            )
        }
    }

    @Test
    fun `there is always a cheap way to say hello on a poor day`() {
        // A day with almost no earnings still has to be visitable, or the pet
        // becomes a debt collector rather than a companion.
        assertTrue(Action.all().minOf { it.cost } <= 5)
        assertTrue("actions must span a real price range", Action.all().maxOf { it.cost } >= 10)
    }

    @Test
    fun `catalogue ids are unique and prices are within reach`() {
        val ids = PetEngine.CATALOG.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        PetEngine.CATALOG.forEach { accessory ->
            assertTrue("${accessory.name} priced ${accessory.price}", accessory.price in 10..100)
        }
        // Every slot must be purchasable, or that slot is dead content.
        PetEngine.accessoryOf("crown")?.let { assertEquals(PetEngine.Slot.HAT, it.slot) }
        assertEquals(null, PetEngine.accessoryOf("does-not-exist"))
    }

    @Test
    fun `state values are clamped inside the valid range`() {
        assertEquals(100, PetEngine.clamp(500))
        assertEquals(0, PetEngine.clamp(-40))
        assertEquals(40, PetEngine.clamp(40))
    }

    @Test
    fun `begging copy only appears when it is actually hungry`() {
        assertTrue(PetEngine.begLine(0).isNotBlank())
        assertTrue(PetEngine.begLine(30).isNotBlank())
        assertEquals("", PetEngine.begLine(80))
        assertNotEquals(PetEngine.begLine(5), PetEngine.begLine(35))
    }

    /**
     * A plausibility check rather than a maths check: simulate two visits a day
     * (a meal and a play each) and assert it never bottom out.
     *
     * This test is why the decay constants are what they are — at the original
     * ~10h drain even morning-and-evening care could not keep it comfortable,
     * which would have turned a bonding loop into unpaid maintenance.
     */
    @Test
    fun `two visits a day keep the pet from bottoming out`() {
        val start = 0L
        var hunger = 100
        var mood = 70
        var tick = start

        repeat(3) { round ->
            tick += 12 * hour
            val decayed = PetEngine.snapshotted(hunger, mood, tick - 12 * hour, tick)
            hunger = PetEngine.clamp(decayed.first + Action.MEAL.hungerDelta + Action.PLAY.hungerDelta)
            mood = PetEngine.clamp(decayed.second + Action.MEAL.moodDelta + Action.PLAY.moodDelta)
            assertTrue("round $round hunger bottomed out: $hunger", hunger > 20)
            assertTrue("round $round mood collapsed: $mood", mood >= 50)
        }
    }

    @Test
    fun `every slot stocks a cheap first buy and something to save up for`() {
        PetEngine.Slot.values().forEach { slot ->
            val items = PetEngine.CATALOG.filter { it.slot == slot }
            assertTrue("$slot stocks too little to choose between", items.size >= 4)
            assertTrue("$slot has nothing a beginner can afford", items.any { it.price <= 30 })
            assertTrue("$slot is dead content: nothing rare to aim at", items.any { it.rarity != Rarity.COMMON })
        }
    }

    /**
     * Guards the actual bug behind "the accessory is in the wrong place": the
     * old anchors were expressed as edge alignments with extra offsets, which
     * pushed several items outside the box where no amount of patience could
     * line them up. Every default must sit fully inside the frame.
     */
    @Test
    fun `every default anchor keeps its glyph inside the avatar box`() {
        PetEngine.Slot.values().forEach { slot ->
            val (x, y) = slot.anchor()
            val reach = slot.glyphRatio() / 2f
            assertTrue(
                "$slot spills horizontally: x=$x reach=$reach",
                kotlin.math.abs(x) + reach <= 0.5f
            )
            assertTrue(
                "$slot spills vertically: y=$y reach=$reach",
                kotlin.math.abs(y) + reach <= 0.5f
            )
        }
    }

    @Test
    fun `nudges are remembered per accessory, not per slot`() {
        val store = PetEngine.OffsetStore
        var map = store.parse("")
        assertTrue(map.isEmpty())

        map = store.with(map, "crown", 0.12f, -0.08f)
        map = store.with(map, "halo", -0.2f, 0.3f)

        val reloaded = store.parse(store.serialize(map))
        assertEquals(0.12f, store.offsetFor(reloaded, "crown").dx)
        assertEquals(-0.08f, store.offsetFor(reloaded, "crown").dy)
        // Aligning one item must leave its neighbour where it was.
        assertEquals(0f, store.offsetFor(reloaded, "bow").dx)
        assertEquals(-0.2f, store.offsetFor(reloaded, "halo").dx)
    }

    @Test
    fun `resetting one accessory leaves the others aligned`() {
        val store = PetEngine.OffsetStore
        var map = store.parse("")
        map = store.with(map, "crown", 0.1f, 0.1f)
        map = store.with(map, "halo", 0.2f, 0.2f)
        val afterReset = store.without(map, "crown")
        assertEquals(0f, store.offsetFor(afterReset, "crown").dx)
        assertEquals(0.2f, store.offsetFor(afterReset, "halo").dx)
    }

    @Test
    fun `a wild fling cannot throw an accessory out of frame`() {
        val store = PetEngine.OffsetStore
        val wild = store.with(store.parse(""), "crown", 99f, -99f)
        assertEquals(store.MAX, store.offsetFor(wild, "crown").dx)
        assertEquals(-store.MAX, store.offsetFor(wild, "crown").dy)
    }

    @Test
    fun `corrupt offset data is skipped instead of crashing`() {
        val store = PetEngine.OffsetStore
        val parsed = store.parse("crown:0.1,halo:bad:0.2,bow:0.1:0.2")
        assertEquals(0f, store.offsetFor(parsed, "crown").dx)
        assertEquals(0f, store.offsetFor(parsed, "halo").dy)
        assertEquals(0.1f, store.offsetFor(parsed, "bow").dx)
        assertTrue(store.parse("").isEmpty())
    }
}
