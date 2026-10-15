package com.ran.app.ui.screens

import com.ran.app.R

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ran.app.core.RewardEngine
import com.ran.app.ui.HomeStats
import com.ran.app.ui.components.CountCircle
import com.ran.app.ui.components.SectionCard
import com.ran.app.ui.components.SectionTitle
import com.ran.app.ui.components.StarProgressBar
import com.ran.app.ui.components.StreakBadge
import com.ran.app.ui.components.VerticalSpace
import com.ran.app.ui.theme.Amber
import com.ran.app.ui.theme.Coral
import com.ran.app.ui.theme.Teal

/**
 * Home dashboard.
 *
 * Layout order is deliberate: identity and progress first (level, stars, daily
 * goal), then today's counts, then the shortest path to the next reward. It
 * answers "where am I" before "what can I do", which keeps the user oriented
 * rather than overwhelmed.
 */
@Composable
fun HomeScreen(
    stats: HomeStats,
    activePlanTitles: List<String>,
    modifier: Modifier = Modifier,
    onNavigatePlans: () -> Unit = {},
    onNavigateLottery: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LevelCard(stats)
        }
        item {
            DailyGoalCard(stats, onNavigateLottery)
        }
        item {
            TodayCountsCard(stats)
        }
        // Zeigarnik effect: unfinished plans stay visible so they keep mild
        // attentional tension instead of being forgotten.
        if (activePlanTitles.isNotEmpty()) {
            item {
                OpenPlansCard(activePlanTitles, onNavigatePlans)
            }
        }
        item { VerticalSpace(8) }
    }
}

@Composable
private fun LevelCard(stats: HomeStats) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFFFF8A5C), Color(0xFFFF6B4E), Color(0xFFF0523A)))
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Lv.${stats.level}",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "⭐ ${stats.stars}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                    if (stats.streak > 0) {
                        Spacer(Modifier.height(8.dp))
                        StreakBadge(stats.streak)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(stats.levelProgress.coerceIn(0f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFFD166))
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                // Competence-framed copy: describes progress, never failure.
                text = stats.encouragement,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DailyGoalCard(stats: HomeStats, onNavigateLottery: () -> Unit) {
    SectionCard {
        Column {
            SectionTitle(
                text = "今日目标",
                emoji = "🎯",
                subtitle = "已获得 ${stats.starsToday} 星"
            )
            Spacer(Modifier.height(12.dp))
            StarProgressBar(progress = stats.dailyRatio, height = 16)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (stats.dailyRatio >= 1f) "今日目标已完成 🎉" else "今日进度 ${(stats.dailyRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stats.dailyRatio >= 1f) Teal else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "去抽奖 →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Coral
                )
            }
        }
    }
    // The whole card is not clickable here; navigation is driven by the bottom bar.
}

@Composable
private fun TodayCountsCard(stats: HomeStats) {
    SectionCard {
        Column {
            SectionTitle(text = "今日概览", emoji = "📊")
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CountCircle("${stats.starsToday}", "今日星星", Amber, Modifier.weight(1f))
                CountCircle("${stats.openTodos}", "待办", Coral, Modifier.weight(1f))
                CountCircle("${stats.activePlans}", "进行中", Teal, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OpenPlansCard(titles: List<String>, onNavigatePlans: () -> Unit) {
    SectionCard {
        Column {
            SectionTitle(text = "进行中的计划", emoji = "📌")
            Spacer(Modifier.height(10.dp))
            titles.take(3).forEach { title ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Coral)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (titles.size > 3) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "还有 ${titles.size - 3} 项…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Level-up celebration copy shown when a level threshold is crossed. */
fun levelUpCopy(stats: HomeStats, previousLevel: Int): String? =
    if (stats.level > previousLevel) RewardEngine.levelUpMessage(stats.level) else null