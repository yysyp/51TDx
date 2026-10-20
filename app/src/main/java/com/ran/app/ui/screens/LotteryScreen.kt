package com.ran.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ran.app.data.local.DrawRecordEntity
import com.ran.app.data.local.PrizeEntity
import com.ran.app.ui.DrawPhase
import com.ran.app.ui.DrawUiState
import com.ran.app.ui.components.ConfirmDialog
import com.ran.app.ui.components.EmptyHint
import com.ran.app.ui.components.PulsingActionLabel
import com.ran.app.ui.components.ScratchReveal
import com.ran.app.ui.components.SectionCard
import com.ran.app.ui.components.SectionTitle
import com.ran.app.ui.components.VerticalSpace
import com.ran.app.ui.theme.Amber
import com.ran.app.ui.theme.Coral
import com.ran.app.ui.theme.Teal
import com.ran.app.core.TimeUtils

/**
 * Lottery screen.
 *
 * A draw runs in three deliberate beats rather than one instant reveal:
 * press to start the roll, press again to stop it, then scratch the coating to
 * find out what landed. Each beat adds a little ceremony, and the ceremony is
 * the point: anticipation is most of what a variable-ratio reward is made of,
 * so the reveal is stretched out on purpose rather than optimised away.
 */
@Composable
fun LotteryScreen(
    prizes: List<PrizeEntity>,
    drawState: DrawUiState,
    records: List<DrawRecordEntity>,
    starBalance: Int,
    modifier: Modifier = Modifier,
    onStartRoll: () -> Unit,
    onStopRoll: () -> Unit,
    onScratched: () -> Unit,
    onScratchReminder: () -> Unit,
    onClearResult: () -> Unit,
    onSave: (PrizeEntity) -> Unit,
    onDelete: (PrizeEntity) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf<PrizeEntity?>(null) }
    var deleting by rememberSaveable { mutableStateOf<PrizeEntity?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DrawMachineCard(
                drawState = drawState,
                starBalance = starBalance,
                onStartRoll = onStartRoll,
                onStopRoll = onStopRoll,
                onScratched = onScratched,
                onScratchReminder = onScratchReminder,
                onClearResult = onClearResult
            )
        }
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(text = "奖项池", emoji = "🎁", subtitle = "共 ${prizes.size} 项")
                    FilledTonalButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加奖项", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加")
                    }
                }
            }
        }
        if (prizes.isEmpty()) {
            item { EmptyHint("还没有奖项，添加几个让抽奖更有吸引力") }
        } else {
            items(prizes, key = { it.id }) { prize ->
                PrizeRow(
                    prize = prize,
                    onEdit = { editing = prize },
                    onDelete = { deleting = prize }
                )
            }
        }
        item {
            SectionCard {
                SectionTitle(text = "中奖记录", emoji = "🧾")
                Spacer(Modifier.height(8.dp))
                if (records.isEmpty()) {
                    EmptyHint("还没有记录，先抽一次试试")
                } else {
                    // Lazy loading rather than eager forEach: draw history grows
                    // without bound, and composing every row blocks the frame.
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(records, key = { it.id }) { record ->
                            DrawRecordRow(record)
                        }
                    }
                }
            }
        }
        item { VerticalSpace(8) }
    }

    if (creating) {
        PrizeEditDialog(
            prize = null,
            onDismiss = { creating = false },
            onSave = {
                onSave(it)
                creating = false
            }
        )
    }

    editing?.let { prize ->
        PrizeEditDialog(
            prize = prize,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            }
        )
    }

    deleting?.let { prize ->
        ConfirmDialog(
            title = "删除奖项",
            message = "确定删除「${prize.title}」吗？",
            onConfirm = {
                onDelete(prize)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DrawMachineCard(
    drawState: DrawUiState,
    starBalance: Int,
    onStartRoll: () -> Unit,
    onStopRoll: () -> Unit,
    onScratched: () -> Unit,
    onScratchReminder: () -> Unit,
    onClearResult: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFFF0D6), Color(0xFFFFE0E9))))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (drawState.phase) {
                DrawPhase.SCRATCH, DrawPhase.DONE -> ScratchResultCard(
                    drawState = drawState,
                    onScratched = onScratched
                )
                else -> MachineFace(
                    drawState = drawState,
                    starBalance = starBalance
                )
            }

            Spacer(Modifier.height(16.dp))

            val label = when (drawState.phase) {
                DrawPhase.IDLE -> "开始"
                DrawPhase.ROLLING -> "停"
                DrawPhase.SCRATCH -> "👆 先刮开涂层"
                DrawPhase.DONE -> "再来一次"
            }
            val scratching = drawState.phase == DrawPhase.SCRATCH
            Button(
                onClick = {
                    when (drawState.phase) {
                        DrawPhase.IDLE -> onStartRoll()
                        DrawPhase.ROLLING -> onStopRoll()
                        // Deliberately inert: a tap here used to call
                        // onClearResult and skip the reveal the whole scratch
                        // step exists to create. Long press is the deliberate
                        // escape hatch, so a stuck card is still openable.
                        DrawPhase.SCRATCH -> onScratchReminder()
                        DrawPhase.DONE -> onClearResult()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (drawState.phase) {
                        DrawPhase.ROLLING -> Teal
                        DrawPhase.SCRATCH -> Color(0xFFB9BDC9)
                        else -> Coral
                    },
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .height(52.dp)
                    .combinedClickable(
                        onClick = {
                            when (drawState.phase) {
                                DrawPhase.IDLE -> onStartRoll()
                                DrawPhase.ROLLING -> onStopRoll()
                                DrawPhase.SCRATCH -> onScratchReminder()
                                DrawPhase.DONE -> onClearResult()
                            }
                        },
                        onLongClick = { if (scratching) onScratched() }
                    )
            ) {
                PulsingActionLabel(
                    text = label,
                    pulsing = drawState.phase == DrawPhase.ROLLING
                )
            }

            if (scratching) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "用手指把涂层刮开 · 长按按钮可直接揭晓",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A6B45)
                )
            }
            if (drawState.phase == DrawPhase.DONE) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClearResult) { Text("返回", color = Color(0xFF8A6B45)) }
            }
        }
    }
}

/** The spinning dial plus the balance readout, shown while rolling or idle. */
@Composable
private fun MachineFace(drawState: DrawUiState, starBalance: Int) {
    val transition = rememberInfiniteTransition(label = "machineIdle")
    val idleScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (drawState.phase == DrawPhase.ROLLING) 1f else 1.03f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "idleScale"
    )
    val rolling = drawState.phase == DrawPhase.ROLLING

    Box(
        modifier = Modifier
            .size(130.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.75f))
            .border(3.dp, Color.White, CircleShape)
            .scale(if (rolling) 1.06f else idleScale),
        contentAlignment = Alignment.Center
    ) {
        Text(text = drawState.rollingItem?.emoji ?: "🎰", fontSize = 58.sp)
    }

    Spacer(Modifier.height(14.dp))
    Text(
        text = when {
            rolling -> "滚着呢，点「停」揭晓"
            else -> "星星 $starBalance ，点「开始」"
        },
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF6B4E2E)
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (starBalance < 0) "当前欠着 ${-starBalance} 星，抽奖不拦你" else "星星不够也能抽，会先欠着",
        style = MaterialTheme.typography.labelSmall,
        color = if (starBalance < 0) Coral else Color(0xFF8A6B45)
    )
}

/** The prize sitting under the coating, with the scratch layer on top. */
@Composable
private fun ScratchResultCard(drawState: DrawUiState, onScratched: () -> Unit) {
    val record = drawState.result ?: return
    val revealed = drawState.phase == DrawPhase.DONE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (record.isJackpot) Color(0xFFFFF1D6) else Color.White)
            .border(
                width = if (record.isJackpot) 3.dp else 1.dp,
                color = if (record.isJackpot) Coral else Color(0x33000000),
                shape = RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = record.prizeEmoji, fontSize = 46.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = record.prizeTitle,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF6B4E2E)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (record.isJackpot) "头奖！扣除 ${record.starCost} ⭐" else "扣除 ${record.starCost} ⭐",
                style = MaterialTheme.typography.labelMedium,
                color = if (record.isJackpot) Coral else Color(0xFF8A6B45)
            )
        }

        if (!revealed) {
            ScratchReveal(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp)),
                onRevealed = onScratched
            )
        }
    }
}

@Composable
private fun PrizeRow(prize: PrizeEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    SectionCard(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable { menuOpen = true }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = prize.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(prize.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = prize.description.ifBlank { "中奖概率 ${com.ran.app.core.PrizeBalancer.format(prize.weight)}%" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Amber.copy(alpha = 0.22f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${prize.starCost}⭐",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF8A6B00)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { menuOpen = true }) {
                Text(text = "⋮", style = MaterialTheme.typography.titleLarge)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun DrawRecordRow(record: DrawRecordEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = record.prizeEmoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = record.prizeTitle,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            fontWeight = if (record.isJackpot) FontWeight.Bold else FontWeight.Normal,
            color = if (record.isJackpot) Coral else MaterialTheme.colorScheme.onSurface
        )
        if (record.isJackpot) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Coral.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("大奖", fontSize = 11.sp, color = Coral, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = "-${record.starCost}⭐",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = TimeUtils.formatTime(record.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Create/edit dialog for a prize, including rarity and cost controls. */
@Composable
private fun PrizeEditDialog(
    prize: PrizeEntity?,
    onDismiss: () -> Unit,
    onSave: (PrizeEntity) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(prize?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(prize?.description ?: "") }
    var emoji by rememberSaveable { mutableStateOf(prize?.emoji ?: "🎁") }
    var weight by rememberSaveable { mutableStateOf(com.ran.app.core.PrizeBalancer.format(prize?.weight ?: com.ran.app.core.PrizeBalancer.defaultWeight())) }
    var cost by rememberSaveable { mutableStateOf((prize?.starCost ?: 5).toString()) }
    var jackpot by rememberSaveable { mutableStateOf(prize?.isJackpot ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prize == null) "添加奖项" else "编辑奖项") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("奖项名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("图标 emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { input -> cost = input.filter { it.isDigit() }.take(4) },
                        label = { Text("星星消耗") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = weight,
                        // One decimal point, at most two decimals: the
                        // balancer's quantum is 0.01, so accepting more digits
                        // would only store a number nothing can ever match.
                        onValueChange = { input ->
                            val cleaned = input.filter { it.isDigit() || it == '.' }
                            val firstDot = cleaned.indexOf('.')
                            val trimmed = if (firstDot < 0) {
                                cleaned
                            } else {
                                cleaned.substring(0, firstDot + 1) +
                                    cleaned.substring(firstDot + 1).replace(".", "")
                            }
                            weight = trimmed.take(6)
                        },
                        label = { Text("中奖概率 %") },
                        supportingText = { Text("其余奖项自动按比例调整") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { jackpot = !jackpot }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = jackpot,
                        onCheckedChange = { jackpot = it },
                        colors = CheckboxDefaults.colors(checkedColor = Coral)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "标记为头奖（历史记录里高亮显示）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "填 10 就是 10% 中奖率。全部奖项合计 100%，" +
                        "改这一项时其余奖项会按比例自动让出空间。星星消耗决定扣多少。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        (prize ?: PrizeEntity(title = "")).copy(
                            title = title.trim(),
                            description = description.trim(),
                            emoji = emoji.ifBlank { "🎁" }.take(4),
                            weight = com.ran.app.core.PrizeBalancer.quantize(
                                weight.toDoubleOrNull()
                                    ?: com.ran.app.core.PrizeBalancer.defaultWeight()
                            ).coerceIn(
                                com.ran.app.core.PrizeBalancer.MIN_WEIGHT,
                                com.ran.app.core.PrizeBalancer.TOTAL
                            ),
                            starCost = cost.toIntOrNull()?.coerceIn(0, 9999) ?: 5,
                            isJackpot = jackpot
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}