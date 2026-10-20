package com.ran.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ran.app.core.RewardEngine
import com.ran.app.core.TimeUtils
import com.ran.app.data.local.PlanEntity
import com.ran.app.data.local.PlanStatus
import com.ran.app.ui.components.ConfirmDialog
import com.ran.app.ui.components.EmptyHint
import com.ran.app.ui.components.LabeledStarProgress
import com.ran.app.ui.components.SectionCard
import com.ran.app.ui.components.SectionTitle
import com.ran.app.ui.components.VerticalSpace
import com.ran.app.ui.theme.Amber
import com.ran.app.ui.theme.Coral
import com.ran.app.ui.theme.Teal

/**
 * Plan management with daily check-in and star progress.
 *
 * The check-in button stays visible and large because the whole loop depends
 * on the user taking that single action. Completed plans move to a separate
 * list rather than disappearing, which preserves the record of past success.
 */
@Composable
fun PlansScreen(
    activePlans: List<PlanEntity>,
    completedPlans: List<PlanEntity>,
    checkedInPlanIds: Set<Long>,
    modifier: Modifier = Modifier,
    onCheckIn: (PlanEntity) -> Unit,
    onSave: (PlanEntity) -> Unit,
    onDelete: (PlanEntity) -> Unit
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<PlanEntity?>(null) }
    var deleting by rememberSaveable { mutableStateOf<PlanEntity?>(null) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(
                        text = "任务计划",
                        emoji = "🎯",
                        subtitle = "${activePlans.size} 项进行中 · ${completedPlans.size} 项已完成"
                    )
                    FilledTonalButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新建计划", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新建")
                    }
                }
            }
        }

        if (activePlans.isEmpty()) {
            item { EmptyHint("还没有计划，添加一个开始今天的小目标") }
        } else {
            items(activePlans, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    alreadyCheckedIn = plan.id in checkedInPlanIds,
                    onCheckIn = { onCheckIn(plan) },
                    onEdit = { editing = plan },
                    onDelete = { deleting = plan }
                )
            }
        }

        if (completedPlans.isNotEmpty()) {
            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCompleted = !showCompleted },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle(text = "已完成计划", emoji = "🏆", subtitle = "共 ${completedPlans.size} 项")
                        Text(
                            text = if (showCompleted) "收起 ▲" else "查看 ▼",
                            style = MaterialTheme.typography.labelMedium,
                            color = Teal
                        )
                    }
                    if (showCompleted) {
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(completedPlans, key = { it.id }) { plan ->
                                CompletedPlanRow(plan)
                            }
                        }
                    }
                }
            }
        }
        item { VerticalSpace(8) }
    }

    if (creating) {
        PlanEditDialog(
            plan = null,
            onDismiss = { creating = false },
            onSave = { onSave(it); creating = false }
        )
    }
    editing?.let { plan ->
        PlanEditDialog(
            plan = plan,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null }
        )
    }
    deleting?.let { plan ->
        ConfirmDialog(
            title = "删除计划",
            message = "确定删除「${plan.title}」吗？打卡记录也会一并删除。",
            onConfirm = { onDelete(plan); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun PlanCard(
    plan: PlanEntity,
    alreadyCheckedIn: Boolean,
    onCheckIn: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = plan.emoji, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(plan.title, style = MaterialTheme.typography.titleMedium)
                    if (plan.note.isNotBlank()) {
                        Text(
                            text = plan.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { menuOpen = true }) {
                    Text(text = "⋮", style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LabeledStarProgress(current = plan.currentCount, target = plan.targetCount, label = "完成进度")

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Amber.copy(alpha = 0.2f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "完成 +${RewardEngine.clampPlanReward(plan.starReward, plan.targetCount)}⭐",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8A6B00)
                        )
                    }
                }
                Button(
                    onClick = onCheckIn,
                    enabled = !alreadyCheckedIn && plan.status == PlanStatus.ACTIVE,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (alreadyCheckedIn) Teal else Coral,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(if (alreadyCheckedIn) "今日已打卡 ✓" else "打卡 +3⭐")
                }
            }
        }
    }
}

@Composable
private fun CompletedPlanRow(plan: PlanEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = plan.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${plan.currentCount} 次 · +${RewardEngine.clampPlanReward(plan.starReward, plan.targetCount)}⭐",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (plan.completedAt != null) {
            Text(
                text = TimeUtils.formatDate(plan.completedAt!!),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanEditDialog(
    plan: PlanEntity?,
    onDismiss: () -> Unit,
    onSave: (PlanEntity) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(plan?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(plan?.note ?: "") }
    var emoji by rememberSaveable { mutableStateOf(plan?.emoji ?: "🎯") }
    var target by rememberSaveable { mutableStateOf((plan?.targetCount ?: RewardEngine.DEFAULT_PLAN_TARGET).toString()) }
    // The completion reward tracks the target count (3 stars per step by default,
    // 5 per step at most). `rewardTouched` stops a manual override from being
    // silently overwritten when the user then edits the target — but until they do
    // touch it, the default follows the target so the two never contradict.
    var rewardTouched by rememberSaveable { mutableStateOf(false) }
    val targetInt = target.toIntOrNull()?.coerceAtLeast(1) ?: RewardEngine.DEFAULT_PLAN_TARGET
    val suggestedReward = RewardEngine.defaultPlanReward(targetInt)
    val maxReward = RewardEngine.maxPlanReward(targetInt)
    val rewardDigits = maxReward.toString().length
    var reward by rememberSaveable {
        mutableStateOf(
            RewardEngine.clampPlanReward(plan?.starReward, plan?.targetCount ?: RewardEngine.DEFAULT_PLAN_TARGET)
                .toString()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) "新建计划" else "编辑计划") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("计划名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("图标") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { input ->
                            val next = input.filter { it.isDigit() }.take(4)
                            target = next
                            if (!rewardTouched) {
                                // Recomputed from the incoming text, not from
                                // suggestedReward: that binding still holds the
                                // pre-edit target for this composition.
                                val nextTarget = next.toIntOrNull()?.coerceAtLeast(1)
                                    ?: RewardEngine.DEFAULT_PLAN_TARGET
                                reward = RewardEngine.defaultPlanReward(nextTarget).toString()
                            }
                        },
                        label = { Text("目标次数") },
                        supportingText = { Text("默认 $suggestedReward · 最多 $maxReward ⭐") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = reward,
                        // Digit budget tracks the scaled cap (a 100-step plan pays
                        // 500, which needs three digits), so the field accepts every
                        // legal value instead of truncating a valid one.
                        onValueChange = { input ->
                            rewardTouched = true
                            reward = input.filter { it.isDigit() }.take(rewardDigits)
                        },
                        label = { Text("完成奖励⭐") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        (plan ?: PlanEntity(title = "")).copy(
                            title = title.trim(),
                            note = note.trim(),
                            emoji = emoji.ifBlank { "🎯" }.take(4),
                            targetCount = targetInt,
                            starReward = RewardEngine.clampPlanReward(
                                reward.toIntOrNull(),
                                targetInt
                            )
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}