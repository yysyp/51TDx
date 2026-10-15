package com.ran.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ran.app.core.PetEngine
import com.ran.app.core.PetEngine.Expression
import com.ran.app.core.PetEngine.Slot
import com.ran.app.core.PetEngine.Species
import com.ran.app.core.PetEngine.Stage
import com.ran.app.data.local.PetEntity
import kotlin.math.roundToInt

/**
 * Draws the companion.
 *
 * The face is drawn with vector primitives rather than served as emoji so that
 * expressions can vary continuously with state — an emoji swap would give six
 * disconnected pictures, while drawn eyes and mouth can exaggerate gradually.
 * Accessories are emoji overlays, which is honest about what they are: small
 * decorations with no animation attached.
 *
 * Everything scales from the passed [boxSize], so the same component works as a
 * compact shop thumbnail and as the full-width hero.
 *
 * In [editable] mode each worn accessory becomes draggable. That exists because
 * emoji glyphs are not interchangeable rectangles: each ships with its own
 * padding and baseline, so no single anchor suits them all and someone always
 * finds one sitting visibly off. Rather than guess a third time, the user sets
 * it — they can see the screen and we cannot.
 */
@Composable
fun PetAvatar(
    pet: PetEntity,
    expression: Expression,
    boxSize: Dp,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onMoveAccessory: ((id: String, dx: Float, dy: Float) -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val species = Species.of(pet.species)
    val stage = Stage.forCare(pet.careCount)
    val body = colorFor(species, dark = false)
    val inner = colorFor(species, dark = true)

    val nudges = remember(pet.offsetCsv) { PetEngine.OffsetStore.parse(pet.offsetCsv) }
    val density = LocalDensity.current
    val boxPx = remember(density, boxSize) { with(density) { boxSize.toPx() } }

    Box(modifier = modifier.size(boxSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(boxSize)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f

            if (stage == Stage.EGG) {
                drawOval(color = body, topLeft = Offset(w * 0.24f, h * 0.12f), size = Size(w * 0.52f, h * 0.82f))
                // Three small nicks read as "about to hatch" without any text.
                drawLine(
                    color = inner,
                    start = Offset(w * 0.42f, h * 0.58f),
                    end = Offset(w * 0.46f, h * 0.50f),
                    strokeWidth = w * 0.035f
                )
                drawLine(
                    color = inner,
                    start = Offset(w * 0.48f, h * 0.62f),
                    end = Offset(w * 0.54f, h * 0.52f),
                    strokeWidth = w * 0.035f
                )
                drawLine(
                    color = inner,
                    start = Offset(w * 0.54f, h * 0.60f),
                    end = Offset(w * 0.58f, h * 0.52f),
                    strokeWidth = w * 0.035f
                )
            } else {
                // Ears first so the body overlaps their base.
                drawCircle(color = body, radius = w * 0.115f, center = Offset(w * 0.245f, h * 0.255f))
                drawCircle(color = body, radius = w * 0.115f, center = Offset(w * 0.755f, h * 0.255f))
                drawCircle(color = inner, radius = w * 0.055f, center = Offset(w * 0.245f, h * 0.255f))
                drawCircle(color = inner, radius = w * 0.055f, center = Offset(w * 0.755f, h * 0.255f))

                // Shadow left intentionally flat: a highlight reads as a light
                // source, which would look wrong once the body colour changes.
                drawCircle(color = body, radius = w * 0.36f, center = Offset(cx, h * 0.60f))
                drawOval(
                    color = inner.copy(alpha = 0.25f),
                    topLeft = Offset(w * 0.30f, h * 0.60f),
                    size = Size(w * 0.40f, h * 0.30f)
                )
            }

            drawEyes(expression, w, h, cx)
            drawMouth(expression, w, h, cx)
            drawBlush(expression, w, h)
        }

        // Accessory overlays, anchored from the centre of the box so the default
        // position can never fall outside it, plus whatever correction the user
        // has dragged onto that particular item.
        wornAccessories(pet).forEach { worn ->
            val anchor = worn.slot.anchor()
            val nudge = PetEngine.OffsetStore.offsetFor(nudges, worn.id)
            // BACK draws once per side; both copies move together so the pair
            // still reads as a pair after being nudged.
            val sides = if (worn.slot.mirrored()) listOf(1f, -1f) else listOf(1f)

            sides.forEach { sign ->
                val baseXPx = (anchor.first * sign + nudge.dx).coerceIn(-1f, 1f) * boxPx
                val baseYPx = (anchor.second + nudge.dy).coerceIn(-1f, 1f) * boxPx
                DraggableGlyph(
                    emoji = worn.emoji,
                    ratio = worn.slot.glyphRatio(),
                    boxSize = boxSize,
                    baseXPx = baseXPx,
                    baseYPx = baseYPx,
                    editable = editable,
                    stateKey = worn.id to sign,
                    onCommit = { deltaXPx, deltaYPx ->
                        onMoveAccessory?.invoke(
                            worn.id,
                            nudge.dx + deltaXPx / boxPx,
                            nudge.dy + deltaYPx / boxPx
                        )
                    }
                )
            }
        }

        if (expression == Expression.SLEEPY) {
            Text(
                text = "💤",
                fontSize = emojiSize(boxSize, 0.26f),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/**
 * One accessory glyph, optionally draggable.
 *
 * The in-flight gesture lives in local pixel state and is written out only when
 * the finger lifts — otherwise a drag issues dozens of database writes per
 * second, each one restarting the very flows this avatar is drawn from.
 */
@Composable
private fun DraggableGlyph(
    emoji: String,
    ratio: Float,
    boxSize: Dp,
    baseXPx: Float,
    baseYPx: Float,
    editable: Boolean,
    stateKey: Any,
    onCommit: (deltaXPx: Float, deltaYPx: Float) -> Unit
) {
    var dragPx by remember(stateKey) { mutableStateOf(Offset.Zero) }

    // Once the persisted offset catches up, the local overlay must clear —
    // otherwise every new drag adds itself on top of the one already applied.
    LaunchedEffect(stateKey, baseXPx, baseYPx) { dragPx = Offset.Zero }

    Text(
        text = emoji,
        fontSize = emojiSize(boxSize, ratio),
        modifier = Modifier
            .offset { IntOffset((baseXPx + dragPx.x).roundToInt(), (baseYPx + dragPx.y).roundToInt()) }
            .then(
                if (editable) {
                    Modifier.pointerInput(stateKey) {
                        detectDragGestures(
                            onDragEnd = { onCommit(dragPx.x, dragPx.y) },
                            onDragCancel = { dragPx = Offset.Zero }
                        ) { _, dragAmount -> dragPx += dragAmount }
                    }
                } else {
                    Modifier
                }
            )
    )
}

/** Equipped items in draw order: behind the body first, face last. */
private fun wornAccessories(pet: PetEntity): List<Worn> = buildList {
    pet.equippedBack.takeIf { it.isNotBlank() }?.let { add(Worn(it, Slot.BACK)) }
    pet.equippedNeck.takeIf { it.isNotBlank() }?.let { add(Worn(it, Slot.NECK)) }
    pet.equippedHat.takeIf { it.isNotBlank() }?.let { add(Worn(it, Slot.HAT)) }
    pet.equippedFace.takeIf { it.isNotBlank() }?.let { add(Worn(it, Slot.FACE)) }
}.filter { it.emoji.isNotEmpty() }

private class Worn(val id: String, val slot: Slot) {
    val emoji: String get() = PetEngine.accessoryOf(id)?.emoji.orEmpty()
}

private fun emojiSize(boxSize: Dp, ratio: Float): TextUnit = (boxSize.value * ratio).sp

private fun DrawScope.drawEyes(expression: Expression, w: Float, h: Float, cx: Float) {
    val eyeY = h * 0.55f
    val dx = w * 0.115f
    val sclera = Color.White
    val pupil = Color(0xFF33261D)

    when (expression) {
        Expression.JOY, Expression.EATING -> {
            // Closed, curving upward — the "happy squint".
            listOf(cx - dx, cx + dx).forEach { x ->
                drawArc(
                    color = pupil,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(x - w * 0.075f, eyeY - w * 0.075f),
                    size = Size(w * 0.15f, w * 0.15f),
                    style = Stroke(width = w * 0.032f)
                )
            }
        }
        Expression.SLEEPY -> {
            listOf(cx - dx, cx + dx).forEach { x ->
                drawLine(
                    color = pupil,
                    start = Offset(x - w * 0.055f, eyeY),
                    end = Offset(x + w * 0.055f, eyeY),
                    strokeWidth = w * 0.032f
                )
            }
        }
        else -> {
            // Puppy-dog eyes when begging: noticeably larger, which is the whole
            // point of that state.
            val grow = if (expression == Expression.BEGGING) 1.35f else 1f
            listOf(cx - dx, cx + dx).forEach { x ->
                drawOval(
                    color = sclera,
                    topLeft = Offset(x - w * 0.062f * grow, eyeY - w * 0.075f * grow),
                    size = Size(w * 0.124f * grow, w * 0.15f * grow)
                )
                drawCircle(color = pupil, radius = w * 0.042f * grow, center = Offset(x, eyeY + w * 0.005f))
                drawCircle(
                    color = Color.White,
                    radius = w * 0.015f * grow,
                    center = Offset(x - w * 0.016f, eyeY - w * 0.022f)
                )
            }
        }
    }
}

private fun DrawScope.drawMouth(expression: Expression, w: Float, h: Float, cx: Float) {
    val mouthY = h * 0.70f
    val mouthColor = Color(0xFF33261D)

    when (expression) {
        Expression.JOY -> drawArc(
            color = mouthColor,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = true,
            topLeft = Offset(cx - w * 0.10f, mouthY - w * 0.06f),
            size = Size(w * 0.20f, w * 0.16f)
        )
        Expression.EATING -> drawOval(
            color = mouthColor,
            topLeft = Offset(cx - w * 0.07f, mouthY - w * 0.05f),
            size = Size(w * 0.14f, w * 0.11f)
        )
        Expression.SAD -> drawArc(
            color = mouthColor,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.08f, mouthY + w * 0.02f),
            size = Size(w * 0.16f, w * 0.12f),
            style = Stroke(width = w * 0.028f)
        )
        Expression.IDLE -> drawLine(
            color = mouthColor,
            start = Offset(cx - w * 0.045f, mouthY),
            end = Offset(cx + w * 0.045f, mouthY),
            strokeWidth = w * 0.026f
        )
        Expression.SLEEPY -> drawOval(
            color = mouthColor,
            topLeft = Offset(cx - w * 0.035f, mouthY - w * 0.03f),
            size = Size(w * 0.07f, w * 0.07f)
        )
        Expression.BEGGING -> {
            // A small open mouth: hopeful rather than distressed.
            drawOval(
                color = mouthColor,
                topLeft = Offset(cx - w * 0.045f, mouthY - w * 0.035f),
                size = Size(w * 0.09f, w * 0.085f)
            )
        }
        else -> drawArc(
            color = mouthColor,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.075f, mouthY - w * 0.055f),
            size = Size(w * 0.15f, w * 0.13f),
            style = Stroke(width = w * 0.028f)
        )
    }
}

private fun DrawScope.drawBlush(expression: Expression, w: Float, h: Float) {
    val showBlush = expression == Expression.JOY ||
        expression == Expression.HAPPY ||
        expression == Expression.BEGGING
    if (!showBlush) return
    val y = h * 0.645f
    val alpha = if (expression == Expression.BEGGING) 0.28f else 0.22f
    listOf(w * 0.21f, w * 0.79f).forEach { x ->
        drawCircle(color = Color(0xFFFF8A9B).copy(alpha = alpha), radius = w * 0.055f, center = Offset(x, y))
    }
}

private fun colorFor(species: Species, dark: Boolean): Color = when (species) {
    Species.DUMPLING -> if (dark) Color(0xFFF2DCB8) else Color(0xFFFFF0D6)
    Species.CAT -> if (dark) Color(0xFFE4C48F) else Color(0xFFFFE2B0)
    Species.DRAGON -> if (dark) Color(0xFFA9DCC8) else Color(0xFFD9F5E7)
}
