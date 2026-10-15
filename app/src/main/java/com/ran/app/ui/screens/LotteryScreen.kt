package com.ran.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ran.app.ui.DrawUiState
import com.ran.app.ui.components.ConfirmDialog
import com.ran.app.ui.components.EmptyHint
import com.ran.app.ui.components.PulsingActionLabel
import com.ran.app.ui.components.SectionCard
import com.ran.app.ui.components.SectionTitle
import com.ran.app.ui.components.VerticalSpace
import com.ran.app.ui.theme.Amber
import com.ran.app.ui.theme.Coral
import com.ran.app.core.TimeUtils

/**
 * Lottery screen.
 *
 * The roll uses a decelerating sequence rather than an instant result. This is
 * a variable-ratio schedule: because rewards are unpredictable and the reveal
 * takes effort, the behaviour resists extinction far better than a fixed,
 * immediate payout would.
 */
@Composable
fun LotteryScreen(
    prizes: List<PrizeEntity>,
    drawState: DrawUiState,
    records: List<DrawRecordEntity>,
    starBalance: Int,
    modifier: Modifier = Modifier,
    onDraw: () -> Unit,
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
            DrawMachineCard(drawState, starBalance, onDraw, onClearResult)
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

@Composable
private fun DrawMachineCard(
    drawState: DrawUiState,
    starBalance: Int,
    onDraw: () -> Unit,
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
            // Idle breathing motion invites tapping without demanding attention.
            val transition = rememberInfiniteTransition(label = "machineIdle")
            val idleScale by transition.animateFloat(
                initialValue = 1f,
                targetValue = if (drawState.isRolling) 1f else 1.03f,
                animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
                label = "idleScale"
            )

            val displayEmoji = drawState.result?.prizeEmoji
                ?: drawState.rollingItem?.emoji
                ?: "🎰"

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.75f))
                    .border(3.dp, Color.White, CircleShape)
                    .scale(idleScale),
                contentAlignment = Alignment.Center
            ) {
                Text(text = displayEmoji, fontSize = 58.sp)
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = when {
                    drawState.result != null -> "恭喜获得：${drawState.result.prizeTitle}"
                    drawState.isRolling -> "抽签中…"
                    else -> "点击下方按钮抽一个奖励"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF6B4E2E)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "当前星星：$starBalance",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8A6B45)
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (drawState.result != null) onClearResult() else onDraw()
                },
                enabled = !drawState.isRolling,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Coral,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(50.dp)
            ) {
                PulsingActionLabel(
                    text = when {
                        drawState.isRolling -> "抽签中…"
                        drawState.result != null -> "再抽一次"
                        else -> "开始抽奖"
                    }
                )
            }

            if (drawState.result != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClearResult) { Text("返回", color = Color(0xFF8A6B45)) }
            }
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
                    text = prize.description.ifBlank { "权重 ${prize.weight}" },
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
    var weight by rememberSaveable { mutableStateOf((prize?.weight ?: 10).toString()) }
    var cost by rememberSaveable { mutableStateOf((prize?.starCost ?: 5).toString()) }

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
                        onValueChange = { input -> weight = input.filter { it.isDigit() }.take(3) },
                        label = { Text("权重") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "权重越高越容易抽中；星星消耗可让大奖更有分量",
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
                            weight = weight.toIntOrNull()?.coerceIn(1, 999) ?: 10,
                            starCost = cost.toIntOrNull()?.coerceIn(0, 9999) ?: 5
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}