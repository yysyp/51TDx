package com.ran.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Scratch-to-reveal coating.
 *
 * The reveal is gated behind a physical gesture on purpose. A result that simply
 * appears has already been delivered by the time attention lands on it; making
 * the user rub the coating off costs a few seconds of involvement with the
 * outcome, and that involvement is most of what makes a win feel like a win
 * rather than a notification.
 *
 * Strokes are erased with [BlendMode.Clear] on an offscreen layer, which cuts a
 * real hole through the coating instead of painting a lighter overlay that only
 * pretends to be one.
 *
 * The card also opens on a second condition: grid coverage, so someone who
 * scratches erratically across the whole thing still gets the result without
 * having to be pixel-precise. A gesture that never completes is a prize the user
 * cannot open, which is a bug rather than a puzzle.
 *
 * @param strokeWidth brush width in pixels
 * @param revealRatio fraction of the grid that counts as opened
 * @param onRevealed fired once, when enough has been scratched away
 */
@Composable
fun ScratchReveal(
    modifier: Modifier = Modifier,
    strokeWidth: Float = 40f,
    gridCols: Int = 7,
    gridRows: Int = 5,
    revealRatio: Float = 0.45f,
    coatingColor: Color = Color(0xFFC9CCD6),
    onRevealed: () -> Unit
) {
    val strokes = remember { mutableStateListOf<Path>() }
    // Fixed length, index = grid position. A sparse set would need insertion
    // that reorders indices; here a cell is simply flipped false -> true.
    val cells = remember(gridCols, gridRows) {
        mutableStateListOf<Boolean>().apply { repeat(gridCols * gridRows) { add(false) } }
    }
    var last by remember { mutableStateOf(Offset.Unspecified) }
    var progress by remember { mutableFloatStateOf(0f) }
    var fired by remember { mutableStateOf(false) }

    val totalCells = gridCols * gridRows

    Box(
        modifier = modifier.graphicsLayer {
            // Clear only cuts against a composited layer; without offscreen the
            // stroke would composite as transparent black over the content.
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start -> last = start },
                        onDrag = { change, _ ->
                            val canvasSize = size
                            if (canvasSize.width <= 0f || canvasSize.height <= 0f) return@detectDragGestures
                            val position = change.position

                            // The segment from the previous sample to this one,
                            // so a fast swipe scratches a continuous line rather
                            // than a dotted trail.
                            if (last != Offset.Unspecified) {
                                strokes.add(
                                    Path().apply {
                                        moveTo(last.x, last.y)
                                        lineTo(position.x, position.y)
                                    }
                                )
                            }
                            last = position

                            // Coverage is tracked per grid cell so completion does
                            // not depend on stroke geometry being pixel-exact.
                            val col = ((position.x / canvasSize.width) * gridCols)
                                .toInt().coerceIn(0, gridCols - 1)
                            val row = ((position.y / canvasSize.height) * gridRows)
                                .toInt().coerceIn(0, gridRows - 1)
                            val index = row * gridCols + col
                            if (index in cells.indices && !cells[index]) {
                                cells[index] = true
                                progress = (cells.count { it }.toFloat() / totalCells).coerceIn(0f, 1f)
                                if (!fired && progress >= revealRatio) {
                                    fired = true
                                    onRevealed()
                                }
                            }
                        },
                        onDragEnd = { last = Offset.Unspecified }
                    )
                }
        ) {
            drawRect(color = coatingColor)
            val strokeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            strokes.forEach { path ->
                drawPath(path = path, color = Color.Transparent, blendMode = BlendMode.Clear, style = strokeStyle)
            }
        }
    }
}