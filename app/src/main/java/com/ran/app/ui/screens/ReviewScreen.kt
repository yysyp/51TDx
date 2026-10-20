package com.ran.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ran.app.core.SpacedRepetition
import com.ran.app.core.TimeUtils
import com.ran.app.data.ReviewSummary
import com.ran.app.data.local.ReviewItemEntity
import com.ran.app.ui.components.ConfirmDialog
import com.ran.app.ui.components.EmptyHint
import com.ran.app.ui.components.SectionCard
import com.ran.app.ui.components.SectionTitle
import com.ran.app.ui.theme.Amber
import com.ran.app.ui.theme.AmberSoft
import com.ran.app.ui.theme.Coral
import com.ran.app.ui.theme.Teal
import com.ran.app.ui.theme.TealSoft

/**
 * Spaced-repetition queue.
 *
 * The critical interaction is **not** the checkbox. Tapping "done" is how most
 * review apps work, and it is largely worthless: recognising a sentence you
 * just looked at produces fluency, and the brain mistakes fluency for
 * knowledge — the "fluency illusion". What actually consolidates memory is the
 * effortful act of pulling the answer out *before* seeing it again. This is the
 * testing effect: Roediger & Karpicke (2006) found retrieval practice roughly
 * doubling long-term retention versus re-reading under matched time.
 *
 * So tapping an item opens a self-test card that hides the content first. Only
 * after trying to recall does the answer appear, followed by a three-level
 * self-rating that drives the SM-2 schedule. The checkbox survives in the UI
 * because it matches how people think about lists, but it now *starts* a
 * retrieval attempt instead of declaring one finished.
 */
@Composable
fun ReviewScreen(
    dueItems: List<ReviewItemEntity>,
    allItems: List<ReviewItemEntity>,
    summary: ReviewSummary,
    modifier: Modifier = Modifier,
    onAdd: (String, String, SpacedRepetition.Difficulty) -> Unit,
    onGrade: (ReviewItemEntity, SpacedRepetition.Quality) -> Unit,
    onDelete: (ReviewItemEntity) -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var draftAnswer by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf(SpacedRepetition.Difficulty.NORMAL) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<ReviewItemEntity?>(null) }
    var testing by rememberSaveable { mutableStateOf<ReviewItemEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ProgressHeader(summary = summary) }

        item {
            SectionCard {
                SectionTitle(
                    text = "今天学了什么",
                    emoji = "🧠",
                    subtitle = "一句话一张卡，越短越记得住"
                )
                Spacer(Modifier.height(10.dp))
                DifficultyPicker(selected = difficulty, onSelect = { difficulty = it })
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("问题 / 要记住的内容") },
                    placeholder = { Text("比如：光合作用需要哪些条件？") },
                    supportingText = { Text(text = draftHint(draft), style = MaterialTheme.typography.labelSmall) },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draftAnswer,
                    onValueChange = { draftAnswer = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("答案（可留空）") },
                    placeholder = { Text("比如：光、水、二氧化碳") },
                    supportingText = {
                        Text(
                            text = "留空则复习时只回看问题 · 多行答案按行对应多行问题",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "换行或句号自动拆卡 · 明天第一次复习",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (draft.isNotBlank()) {
                                onAdd(draft, draftAnswer, difficulty)
                                draft = ""
                                draftAnswer = ""
                            }
                        },
                        enabled = draft.isNotBlank()
                    ) {
                        Text("存起来 +${SpacedRepetition.starsForRecording(difficulty)} ⭐")
                    }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle(
                    text = "今日待复习",
                    emoji = "📆",
                    subtitle = if (dueItems.isEmpty()) "今天没有到期的卡片"
                    else "${dueItems.size} 张到期 · 先考自己，再看答案"
                )
            }
        }

        if (dueItems.isEmpty()) {
            item {
                EmptyHint(
                    if (allItems.isEmpty()) "还没有记录，先写下今天学到的东西吧"
                    else "今天的复习都完成了，大脑又结实了一点 🎉"
                )
            }
        } else {
            items(dueItems, key = { it.id }) { item ->
                DueReviewRow(item = item, onStartTest = { testing = item })
            }
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAll = !showAll },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(
                        text = "全部内容",
                        emoji = "📚",
                        subtitle = "在练 ${summary.totalCards} 张 · 已出师 ${summary.masteredCount} 张"
                    )
                    Text(
                        text = if (showAll) "收起 ▲" else "查看 ▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = Teal
                    )
                }
                if (showAll) {
                    Spacer(Modifier.height(10.dp))
                    if (allItems.isEmpty()) {
                        EmptyHint("还没有任何记录")
                    } else {
                        // Plain Column inside the card: two LazyColumns sharing a
                        // scroll direction is illegal, and this list stays short.
                        allItems.forEach { item ->
                            AllReviewRow(item = item, onDelete = { deleting = item })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    testing?.let { target ->
        SelfTestCard(
            item = target,
            onGrade = { quality ->
                onGrade(target, quality)
                testing = null
            },
            onDismiss = { testing = null }
        )
    }

    deleting?.let { target ->
        ConfirmDialog(
            title = "删除这张卡片？",
            message = "「${target.content.take(30)}」将被移除，已获得的星星不会收回。",
            onConfirm = {
                onDelete(target)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

/** Header: streak plus today's progress against a small daily goal. */
@Composable
private fun ProgressHeader(summary: ReviewSummary) {
    val target = 5
    val ratio = (summary.doneToday.toFloat() / target).coerceIn(0f, 1f)

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatBlock(value = "${summary.streak}", label = "连续天数", tint = Coral)
            VerticalDivider()
            StatBlock(value = "${summary.doneToday}/$target", label = "今日复习", tint = Teal)
            VerticalDivider()
            StatBlock(value = "${summary.dueCount}", label = "待复习", tint = Amber)
            VerticalDivider()
            StatBlock(value = "${summary.starsToday}", label = "今日得星", tint = Teal)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = if (ratio >= 1f) Teal else Coral
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (summary.doneToday >= target) "今日复习目标完成，可以安心去玩了 🎉"
            else "再复习 ${target - summary.doneToday} 张就完成今天的目标",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** RowScope receiver: [Modifier.weight] is only available inside a Row. */
@Composable
private fun RowScope.StatBlock(value: String, label: String, tint: Color) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .height(36.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * Difficulty changes the *schedule*, not just the payout, so it is framed as
 * "how much help will this need". Paying more for hard items makes honest
 * labelling the profitable choice.
 */
@Composable
private fun DifficultyPicker(
    selected: SpacedRepetition.Difficulty,
    onSelect: (SpacedRepetition.Difficulty) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        SpacedRepetition.Difficulty.entries.forEachIndexed { index, option ->
            val active = option == selected
            val shape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (index < SpacedRepetition.Difficulty.entries.lastIndex) 6.dp else 0.dp)
                    .clip(shape)
                    .then(
                        if (active) Modifier.background(TealSoft)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${option.label} +${SpacedRepetition.starsForRecording(option)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Teal else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Two phases: **recall** (content hidden) then **grade** (content shown).
 *
 * There is deliberately no shortcut straight to "done": that shortcut is
 * exactly what the testing effect depends on removing.
 */
@Composable
private fun SelfTestCard(
    item: ReviewItemEntity,
    onGrade: (SpacedRepetition.Quality) -> Unit,
    onDismiss: () -> Unit
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val difficulty = SpacedRepetition.Difficulty.of(item.difficulty)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (!revealed) {
                    SectionTitle(
                        text = "先别急着看答案",
                        emoji = "🧠",
                        subtitle = "${difficulty.label} · ${roundLabel(item)}"
                    )
                    Spacer(Modifier.height(14.dp))
                    // The question must be visible during recall. Showing only
                    // "close your eyes and say it" gave the user nothing to
                    // retrieve from, which is not a test at all.
                    PromptBox(text = item.content, containerColor = AmberSoft)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "看着问题，先自己说出答案 👄",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.lastReviewedAt?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "上次见它：${TimeUtils.formatDate(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("我想起来了，揭晓答案")
                    }
                } else {
                    SectionTitle(
                        text = "自己核对一下",
                        emoji = "👀",
                        subtitle = "诚实评分，间隔才算得准"
                    )
                    Spacer(Modifier.height(14.dp))
                    PromptBox(
                        label = "问题",
                        text = item.content,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PromptBox(
                        label = "答案",
                        text = item.answer.ifBlank { "（这条没有填答案，只回看问题）" },
                        containerColor = AmberSoft
                    )
                    Spacer(Modifier.height(16.dp))
                    SpacedRepetition.Quality.entries.reversed().forEach { quality ->
                        val stars = SpacedRepetition.starsForReview(difficulty, quality)
                        GradeButton(
                            label = "${quality.emoji}  ${quality.label}   +$stars ⭐",
                            highlighted = quality == SpacedRepetition.Quality.RECALL,
                            onClick = { onGrade(quality) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptBox(text: String, containerColor: Color, label: String? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GradeButton(label: String, highlighted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(if (highlighted) TealSoft else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlighted) Teal else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A due item: tapping anywhere starts the retrieval test. */
@Composable
private fun DueReviewRow(item: ReviewItemEntity, onStartTest: () -> Unit) {
    val overdueDays = -SpacedRepetition.daysUntilDue(item.nextDueDay)
    val strength = SpacedRepetition.memoryStrength(item.intervalDays)
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onStartTest),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = false,
                // Checking starts the recall test; unchecking stays a no-op
                // because grading immediately reschedules the card off today.
                onCheckedChange = { if (it) onStartTest() },
                colors = CheckboxDefaults.colors(checkedColor = Teal)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.content, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dueMeta(item, overdueDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdueDays > 0) Amber else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { strength },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = if (strength >= 1f) Teal else Coral
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "自测", fontSize = 12.sp, color = Teal)
                Text(
                    text = "${(strength * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AllReviewRow(item: ReviewItemEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (item.isMastered) TextDecoration.LineThrough else TextDecoration.None
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = allMeta(item),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun draftHint(draft: String): String {
    val trimmed = draft.trim()
    if (trimmed.isEmpty()) return "每条不超过 80 字；换行或句号自动拆卡"
    val likely = trimmed.split(Regex("[\\n;；。!！?？]+")).count { it.isNotBlank() }
    return when {
        trimmed.length > 80 -> "这条有点长，建议拆成几张小卡（80 字以内最好记）"
        likely > 1 -> "将拆成约 $likely 张卡片"
        else -> "很好，短句最容易想起来"
    }
}

private fun roundLabel(item: ReviewItemEntity): String = when {
    item.stage == 0 -> "第一次复习"
    item.lapses > 0 -> "第 ${item.stage + 1} 次 · 曾遗忘 ${item.lapses} 次"
    else -> "第 ${item.stage + 1} 次复习"
}

private fun dueMeta(item: ReviewItemEntity, overdueDays: Long): String = buildString {
    append(roundLabel(item))
    when {
        overdueDays > 0 -> append(" · 已逾期 $overdueDays 天")
        else -> append(" · 今天到期")
    }
}

private fun allMeta(item: ReviewItemEntity): String = when {
    item.isMastered -> "已出师 🎓 · ${item.intervalDays} 天后再巩固"
    else -> "${roundLabel(item)} · ${TimeUtils.formatDate(dayKeyToMillis(item.nextDueDay))} 到期"
}

/** Day keys are `yyyy-MM-dd`; `LocalDate.parse` handles them directly. */
private fun dayKeyToMillis(dayKey: String): Long = runCatching {
    java.time.LocalDate.parse(dayKey)
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrDefault(System.currentTimeMillis())
