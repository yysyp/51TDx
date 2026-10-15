package com.ran.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ran.app.core.RewardEngine
import com.ran.app.ui.theme.Amber

/**
 * Star progress bar with goal-gradient easing.
 *
 * [eased] applies the goal-gradient effect: the fill advances faster near the
 * target so the last stretch of progress feels quick and motivating.
 */
@Composable
fun StarProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Int = 14,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    gradient: Brush = Brush.horizontalGradient(
        listOf(Amber, Color(0xFFFF9F45), Color(0xFFFF6B4E))
    )
) {
    val ratio = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = 700),
        label = "starProgress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height.dp)
                .clip(RoundedCornerShape(height.dp))
                .background(gradient)
        )
    }
}

/** Compact variant used on cards: bar plus a numeric `current/target` readout. */
@Composable
fun LabeledStarProgress(
    current: Int,
    target: Int,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val ratio = RewardEngine.progressRatio(current, target)
    val eased = RewardEngine.easedProgress(current, target)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label ?: "进度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$current / $target",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(6.dp))
        StarProgressBar(progress = eased, height = 12)
        if (ratio < 1f && ratio > 0f) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "已完成 ${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Pill showing the star balance, the app's primary reward currency. */
@Composable
fun StarBadge(
    stars: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "⭐", fontSize = if (compact) 13.sp else 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = stars.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 13.sp else 16.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/** Streak indicator; loss aversion makes continuity worth protecting. */
@Composable
fun StreakBadge(days: Int, modifier: Modifier = Modifier) {
    if (days <= 0) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🔥", fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$days 天",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Circular avatar-style counter used for levels and counts. */
@Composable
fun CountCircle(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = color
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}