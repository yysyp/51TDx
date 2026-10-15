package com.ran.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ran.app.core.PetEngine
import com.ran.app.core.PetEngine.Slot
import com.ran.app.core.PetEngine.Stage
import com.ran.app.data.local.PetEntity
import com.ran.app.ui.components.EmptyHint
import com.ran.app.ui.components.PetAvatar
import com.ran.app.ui.components.SectionCard
import com.ran.app.ui.components.SectionTitle
import com.ran.app.ui.theme.Amber
import com.ran.app.ui.theme.Coral
import com.ran.app.ui.theme.Teal
import com.ran.app.ui.theme.TealSoft

/**
 * The companion screen: the place stars go.
 *
 * Layout puts the pet and its needs above everything else for a reason. Every
 * additional screen the user must cross to see a need is another place to drop
 * the habit, and this screen's job is to be opened daily.
 */
@Composable
fun PetScreen(
    pet: PetEntity?,
    stars: Int,
    modifier: Modifier = Modifier,
    onAction: (PetEngine.Action) -> Unit,
    onBuy: (String) -> Unit,
    onRename: (String) -> Unit,
    onMoveAccessory: (String, Float, Float) -> Unit,
    onResetAccessory: (String) -> Unit,
    onResetAllAccessories: () -> Unit
) {
    if (pet == null) {
        LazyColumn(modifier = modifier.fillMaxWidth()) {
            item { EmptyHint("正在唤醒你的小伙伴…") }
        }
        return
    }

    val now = System.currentTimeMillis()
    val minutesAway = PetEngine.minutesSince(pet.lastActionAt, now)
    val expression = PetEngine.expression(pet.hunger, pet.mood, minutesAway, justAte = false)
    val stage = Stage.forCare(pet.careCount)
    val level = PetEngine.Level.levelFor(pet.careCount)
    val owned = pet.ownedCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val equipped = setOf(pet.equippedHat, pet.equippedFace, pet.equippedNeck, pet.equippedBack)
        .filter { it.isNotBlank() }.toSet()
    // Nothing to adjust until something is worn — offering an empty editing
    // mode would just be a button that does nothing.
    val canAdjust = equipped.isNotEmpty()
    var adjusting by rememberSaveable { mutableStateOf(false) }
    val isAdjusting = adjusting && canAdjust

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pet.name} · ${stage.label} · Lv.$level",
                        style = MaterialTheme.typography.titleLarge
                    )
                    RenameChip(name = pet.name, onRename = onRename)
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PetAvatar(
                        pet = pet,
                        expression = expression,
                        boxSize = 168.dp,
                        editable = isAdjusting,
                        onMoveAccessory = { id, dx, dy -> onMoveAccessory(id, dx, dy) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isAdjusting) {
                        TextButton(onClick = onResetAllAccessories) { Text("全部复位") }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (canAdjust) {
                        TextButton(onClick = { adjusting = !adjusting }) {
                            Text(if (isAdjusting) "完成" else "摆正装扮")
                        }
                    }
                }
                if (isAdjusting) {
                    AdjustHint(worn = PetEngine.CATALOG.filter { it.id in equipped })
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PetEngine.CATALOG.filter { it.id in equipped }.forEach { accessory ->
                            ResetChip(
                                emoji = accessory.emoji,
                                name = accessory.name,
                                modifier = Modifier.weight(1f),
                                onReset = { onResetAccessory(accessory.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    SpeechBubble(text = PetEngine.lineFor(expression, pet.name))
                }
                Spacer(Modifier.height(12.dp))
                NeedBar(label = "饱腹", value = pet.hunger, tint = Coral)
                Spacer(Modifier.height(8.dp))
                NeedBar(label = "心情", value = pet.mood, tint = Teal)
                Spacer(Modifier.height(10.dp))
                LevelProgress(stage = stage, care = pet.careCount)
            }
        }

        item {
            SectionCard {
                SectionTitle(
                    text = "照顾它",
                    emoji = "🫶",
                    subtitle = "你有 $stars ⭐ · 照顾它就是在兑换你今天赚的星星"
                )
                Spacer(Modifier.height(10.dp))
                PetEngine.Action.all().forEach { action ->
                    ActionRow(
                        action = action,
                        affordable = stars >= action.cost,
                        onClick = { onAction(action) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        item {
            SectionCard {
                SectionTitle(
                    text = "装扮商店",
                    emoji = "👗",
                    subtitle = "买下之后随时可以换，不用再付星星"
                )
                Spacer(Modifier.height(10.dp))
                Slot.values().forEach { slot ->
                    val items = PetEngine.CATALOG.filter { it.slot == slot }
                    if (items.isNotEmpty()) {
                        Text(
                            text = slot.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        items.chunked(3).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEach { accessory ->
                                    ShopTile(
                                        emoji = accessory.emoji,
                                        title = accessory.name,
                                        rarity = accessory.rarity,
                                        price = accessory.price,
                                        owned = accessory.id in owned,
                                        equipped = accessory.id in equipped,
                                        affordable = stars >= accessory.price,
                                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                                        onClick = { onBuy(accessory.id) }
                                    )
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle(text = "小记", emoji = "📖", subtitle = "这些都是你照顾出来的")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatPill("等级", "Lv.${PetEngine.Level.levelFor(pet.careCount)}", Modifier.weight(1f))
                    StatPill("照顾次数", "${pet.careCount}", Modifier.weight(1f))
                    StatPill("当前阶段", stage.label, Modifier.weight(1f))
                    StatPill(
                        "装扮拥有",
                        "${owned.size}/${PetEngine.CATALOG.size}",
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeechBubble(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "「$text」",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NeedBar(label: String, value: Int, tint: Color) {
    val ratio = (value / 100f).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$value%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = if (ratio <= 0.25f) Coral else tint
        )
    }
}

/**
 * Growth bar.
 *
 * The bar tracks *level*, not stage. Stages are the five milestones that change
 * the drawing, but they eventually run out, and a bar pinned at 100% forever is
 * worse than no bar at all: it is a daily reminder that nothing is left. Level
 * has no ceiling, so there is always a next mark even for someone who has cared
 * for hundreds of days.
 */
@Composable
private fun LevelProgress(stage: Stage, care: Int) {
    val level = PetEngine.Level.levelFor(care)
    val nextStage = Stage.next(stage)
    val headline = "Lv.$level · 再照顾 ${PetEngine.Level.careToNextLevel(care)} 次升级"
    val footnote = nextStage?.let { next ->
        "再照顾 ${(next.minCare - care).coerceAtLeast(1)} 次进化为${next.label}"
    } ?: "已经是最终形态，等级还可以一直涨"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "成长", style = MaterialTheme.typography.labelMedium)
            Text(
                text = headline,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { PetEngine.Level.progressInLevel(care) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = Amber
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = footnote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdjustHint(worn: List<PetEngine.Accessory>) {
    if (worn.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Amber.copy(alpha = 0.16f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "按住装扮拖到你觉得顺眼的位置，松手就记住啦",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ResetChip(
    emoji: String,
    name: String,
    modifier: Modifier = Modifier,
    onReset: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "复位",
                style = MaterialTheme.typography.labelSmall,
                color = Teal
            )
        }
    }
}

@Composable
private fun ActionRow(
    action: PetEngine.Action,
    affordable: Boolean,
    onClick: () -> Unit
) {
    val container = if (affordable) TealSoft else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(enabled = affordable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = action.emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = action.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = effectSummary(action),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${action.cost} ⭐",
            style = MaterialTheme.typography.bodyMedium,
            color = if (affordable) Teal else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShopTile(
    emoji: String,
    title: String,
    rarity: PetEngine.Rarity,
    price: Int,
    owned: Boolean,
    equipped: Boolean,
    affordable: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (equipped) TealSoft else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (equipped) 1.5.dp else 0.dp,
                color = if (equipped) Teal else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = emoji, fontSize = 26.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                append(rarity.badge)
                append(' ')
                if (equipped) append("已戴上")
                else if (owned) append("点击戴上")
                else append("$price ⭐")
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                equipped -> Teal
                !owned -> when (rarity) {
                    // Nothing being sold is hidden, but the expensive things are
                    // lit up so there is something visible to save toward.
                    PetEngine.Rarity.LEGEND -> Amber
                    PetEngine.Rarity.RARE -> MaterialTheme.colorScheme.primary
                    PetEngine.Rarity.COMMON -> MaterialTheme.colorScheme.onSurfaceVariant
                }.let { if (affordable) it else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) }
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = Teal)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RenameChip(name: String, onRename: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(name) }
    TextButton(onClick = {
        draft = name
        open = true
    }) {
        Text("改名字", style = MaterialTheme.typography.labelMedium, color = Teal)
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionTitle(text = "给它起个名字", emoji = "✏️", subtitle = "12 个字以内")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(12) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { open = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            onRename(draft)
                            open = false
                        }) { Text("就叫这个") }
                    }
                }
            }
        }
    }
}

private fun effectSummary(action: PetEngine.Action): String = buildString {
    if (action.hungerDelta > 0) append("饱腹 +${action.hungerDelta}  ")
    if (action.hungerDelta < 0) append("饱腹 ${action.hungerDelta}  ")
    append("心情 +${action.moodDelta}")
    append(" · 成长 +${action.carePoints}")
}
