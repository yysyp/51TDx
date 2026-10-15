package com.ran.app.ui.screens

import com.ran.app.R
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ran.app.data.local.FoodEntity
import com.ran.app.ui.components.ConfirmDialog
import com.ran.app.ui.components.EmptyHint
import com.ran.app.ui.components.PulsingActionLabel
import com.ran.app.ui.components.SectionCard
import com.ran.app.ui.components.SectionTitle
import com.ran.app.ui.components.VerticalSpace
import com.ran.app.ui.theme.Amber
import com.ran.app.ui.theme.Coral

/**
 * "What to eat" random picker.
 *
 * Removes the low-stakes decision fatigue of choosing a meal while preserving
 * the sense of having made the choice — the result is committed by the user
 * pressing the button, so it reads as a decision rather than an outcome
 * imposed on them.
 */
@Composable
fun FoodScreen(
    foods: List<FoodEntity>,
    pickedFood: FoodEntity?,
    isRolling: Boolean,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
    onClearPick: () -> Unit,
    onSave: (FoodEntity) -> Unit,
    onDelete: (FoodEntity) -> Unit
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<FoodEntity?>(null) }
    var deleting by rememberSaveable { mutableStateOf<FoodEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FoodPickCard(pickedFood, isRolling, onPick, onClearPick)
        }
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(text = "待抽食物", emoji = "🍽", subtitle = "共 ${foods.size} 项")
                    FilledTonalButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加食物", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加")
                    }
                }
            }
        }
        if (foods.isEmpty()) {
            item { EmptyHint("还没有食物，添加几个备选吧") }
        } else {
            items(foods, key = { it.id }) { food ->
                FoodRow(food = food, onEdit = { editing = food }, onDelete = { deleting = food })
            }
        }
        item { VerticalSpace(8) }
    }

    if (creating) {
        FoodEditDialog(food = null, onDismiss = { creating = false }, onSave = { onSave(it); creating = false })
    }
    editing?.let { food ->
        FoodEditDialog(food = food, onDismiss = { editing = null }, onSave = { onSave(it); editing = null })
    }
    deleting?.let { food ->
        ConfirmDialog(
            title = "删除食物",
            message = "确定删除「${food.name}」吗？",
            onConfirm = { onDelete(food); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun FoodPickCard(
    pickedFood: FoodEntity?,
    isRolling: Boolean,
    onPick: () -> Unit,
    onClearPick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFFF3D6), Color(0xFFFFE8D9))))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val emoji = pickedFood?.emoji ?: "🍽"
            val transition = rememberInfiniteTransition(label = "foodIdle")
            val rotation by transition.animateFloat(
                initialValue = -4f,
                targetValue = 4f,
                animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                label = "foodWobble"
            )
            val emojiScale by animateFloatAsState(
                targetValue = if (isRolling) 1.1f else 1f,
                animationSpec = tween(200),
                label = "foodScale"
            )

            Text(
                text = emoji,
                fontSize = 62.sp,
                modifier = Modifier
                    .scale(emojiScale)
                    .rotate(if (isRolling) rotation * 4f else rotation)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = when {
                    pickedFood != null -> "今天就吃：${pickedFood.name}"
                    isRolling -> "抽签中…"
                    else -> stringResource(R.string.app_name) + " 帮你决定今天吃什么"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF6B4E2E)
            )

            if (pickedFood != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${pickedFood.category} · ${pickedFood.emoji}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8A6B45)
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { if (pickedFood != null) onClearPick() else onPick() },
                enabled = !isRolling,
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(50.dp)
            ) {
                PulsingActionLabel(
                    text = when {
                        isRolling -> "抽签中…"
                        pickedFood != null -> "再抽一次"
                        else -> "开始抽签"
                    }
                )
            }
        }
    }
}

@Composable
private fun FoodRow(food: FoodEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    SectionCard(modifier = Modifier.clickable { menuOpen = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = food.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = food.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Amber.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(food.category, fontSize = 12.sp, color = Color(0xFF8A6B00))
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { menuOpen = true }) {
                Text(text = "⋮", style = MaterialTheme.typography.titleLarge)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("编辑") }, onClick = { menuOpen = false; onEdit() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

@Composable
private fun FoodEditDialog(
    food: FoodEntity?,
    onDismiss: () -> Unit,
    onSave: (FoodEntity) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(food?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(food?.category ?: "") }
    var emoji by rememberSaveable { mutableStateOf(food?.emoji ?: "🍜") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (food == null) "添加食物" else "编辑食物") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("食物名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类（可选）") },
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (food ?: FoodEntity(name = "")).copy(
                            name = name.trim(),
                            category = category.trim().ifBlank { "日常" },
                            emoji = emoji.ifBlank { "🍜" }.take(4)
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}