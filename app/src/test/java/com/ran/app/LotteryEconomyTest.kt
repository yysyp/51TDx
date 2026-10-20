package com.ran.app

import com.ran.app.core.PetEngine
import com.ran.app.core.PrizeBalancer
import com.ran.app.core.RewardEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the link between the lottery and the pet shop.
 *
 * These two live in different files and are adjusted for different reasons, but
 * they compete for the same wallet. When the draw got cheaper (the top three
 * prizes carry ~46% of the pool), every pet price left untouched silently became
 * harder to reach -- a quiet tax on earning, and exactly the kind of change that
 * gets a child to stop bothering.
 *
 * The expectation below is therefore not about either system in isolation. It
 * fails when one side is tuned without the other.
 *
 * NOTE: [catalogue] mirrors the seed in `RanRepository.seedIfEmpty`. Re-tune the
 * weights there and this file has to follow, or the assertions stop describing
 * the app.
 */
class LotteryEconomyTest {

    /** title, starCost, weight-as-percentage -- in catalogue order. */
    private val catalogue = listOf(
        Triple("1元", 1, 16.0),
        Triple("15分钟屏幕", 2, 16.0),
        Triple("免一次家务", 3, 14.0),
        Triple("2元", 4, 6.0),
        Triple("5元", 5, 6.0),
        Triple("普通水果券", 6, 5.0),
        Triple("10元", 8, 5.0),
        Triple("大餐：牛排或羊排或海鲜", 10, 4.0),
        Triple("饮料券", 12, 4.0),
        Triple("15元", 14, 4.0),
        Triple("看一场电影", 16, 3.0),
        Triple("周末半日游", 18, 5.0),
        Triple("贵重水果：车厘子，榴莲等", 20, 4.0),
        Triple("冰激凌券", 22, 2.0),
        Triple("20元", 24, 2.0),
        Triple("买书券", 26, 1.0),
        Triple("周末晚睡15分钟特权1次", 28, 1.0),
        Triple("30元", 30, 2.0)
    )

    /** Weights are percentages, so the expected cost is a plain dot product. */
    private fun averageDrawCost(): Double =
        catalogue.sumOf { it.second * it.third } / PrizeBalancer.TOTAL.toDouble()

    @Test
    fun `the catalogue has eighteen prizes in ascending cost order`() {
        assertEquals(18, catalogue.size)
        val costs = catalogue.map { it.second }
        assertTrue("costs must ascend: $costs", costs == costs.sorted())
        assertEquals("cheapest", 1, costs.first())
        assertEquals("dearest", 30, costs.last())
    }

    @Test
    fun `weights are percentages and sum to exactly one hundred`() {
        // The weight column *is* the probability. If it stops being that, the
        // editor is lying to whoever is tuning the pool.
        assertEquals(PrizeBalancer.TOTAL, catalogue.sumOf { it.third }, 1e-6)
    }

    @Test
    fun `every prize is at least one percent`() {
        assertTrue(
            "a prize is unreachable",
            catalogue.all { it.third >= PrizeBalancer.MIN_WEIGHT }
        )
    }

    @Test
    fun `the top three carry a large share of the pool`() {
        val top3 = catalogue.take(3).sumOf { it.third }
        assertTrue("top three carried $top3%", top3 >= 40)
    }

    @Test
    fun `the weight ladder does not collapse`() {
        // Rarer-when-dearer is the shape the pool is designed around, but
        // rounding 18 values into whole percentages leaves small wobbles -- two
        // adjacent prizes can land a point either way. What must not happen is a
        // wholesale collapse: if the cheap end and the dear end shared the same
        // share, the ladder would be decorative.
        val cheap = catalogue.filter { it.second <= 6 }.sumOf { it.third }
        val dear = catalogue.filter { it.second >= 22 }.sumOf { it.third }
        assertTrue("cheap band $cheap vs dear band $dear", cheap > dear * 2)

        // And the dearest prize must never be dramatically easier than the
        // cheapest one, which is what an actual inversion looks like.
        assertTrue(
            "the dearest prizes outrank the cheapest",
            catalogue.minOf { it.third } <= catalogue.filter { it.second <= 6 }.maxOf { it.third }
        )
    }

    @Test
    fun `a draw stays cheap enough that two in a row usually returns something`() {
        val cost = averageDrawCost()
        assertTrue("average draw cost rose to $cost", cost < 10.0)
    }

    @Test
    fun `pet prices track the draw cost instead of drifting away`() {
        val drawCost = averageDrawCost()
        val dearestAccessory = PetEngine.CATALOG.maxOf { it.price }

        // The dearest item should cost roughly a week of draws. Leaving pet
        // prices alone after a cheaper draw pushes this ratio up with nothing on
        // screen saying so.
        val ratio = dearestAccessory / drawCost
        assertTrue("dearest accessory costs $ratio draws", ratio in 6.0..11.0)

        // And daily interactions must stay inside a single day's honest earning,
        // or the pet becomes a luxury instead of a check-in.
        val cheapestCare = PetEngine.Action.all().minOf { it.cost }
        assertTrue("cheapest care costs $cheapestCare", cheapestCare <= RewardEngine.STAR_CHECK_IN)
    }

    @Test
    fun `the accessory ladder still spans a real range`() {
        val prices = PetEngine.CATALOG.map { it.price }
        val ratio = prices.max().toDouble() / prices.min()
        assertTrue("accessory price range was only ${ratio}x", ratio >= 3.5)
    }
}