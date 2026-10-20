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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ran.app.core.RewardEngine
import com.ran.app.core.TimeUtils
import com.ran.app.data.local.TodoEntity
import com.ran.app.data.local.TodoPriority
import com.ran.app.ui.components.ConfirmDialog
import com.ran.app.ui.components.EmptyHint
import com.ran.app.ui.components.SectionCard
import com.ran.app.ui.components.SectionTitle
import com.ran.app.ui.components.VerticalSpace
import com.ran.app.ui.theme.Amber
import com.ran.app.ui.theme.Coral
import com.ran.app.ui.theme.Teal

/**
 * TODO list with priority ordering and strikethrough completion.
 *
 * Completion is deliberately low-friction (a single checkbox tap) because
 * friction on the smallest achievable unit is the main cause of stalled
 * habits.
 */
@Composable
fun TodosScreen(
    openTodos: List<TodoEntity>,
    completedTodos: List<TodoEntity>,
    modifier: Modifier = Modifier,
    onToggle: (TodoEntity) -> Unit,
    onSave: (TodoEntity) -> Unit,
    onDelete: (TodoEntity) -> Unit
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<TodoEntity?>(null) }
    var deleting by rememberSaveable { mutableStateOf<TodoEntity?>(null) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }

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
                    SectionTitle(
                        text = "待办清单",
                        emoji = "✅",
                        subtitle = "${openTodos.size} 项待完成 · 已完成 ${completedTodos.size} 项"
                    )
                    FilledTonalButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新建待办", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新建")
                    }
                }
            }
        }

        if (openTodos.isEmpty()) {
            item { EmptyHint("暂无待办，先记下一件小事吧") }
        } else {
            items(openTodos, key = { it.id }) { todo ->
                TodoRow(todo = todo, onToggle = { onToggle(todo) }, onEdit = { editing = todo }, onDelete = { deleting = todo })
            }
        }

        if (completedTodos.isNotEmpty()) {
            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCompleted = !showCompleted },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle(text = "已完成", emoji = "🎉", subtitle = "共 ${completedTodos.size} 项")
                        Text(
                            text = if (showCompleted) "收起 ▲" else "查看 ▼",
                            style = MaterialTheme.typography.labelMedium,
                            color = Teal
                        )
                    }
                    if (showCompleted) {
                        Spacer(Modifier.height(10.dp))
                        // LazyColumn inside a bounded box: nested scroll views are
                        // illegal in the same direction, so the height is capped and
                        // the list scrolls within the card. Keeps long histories
                        // responsive instead of composing every row eagerly.
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(completedTodos, key = { it.id }) { todo ->
                                CompletedTodoRow(
                                    todo = todo,
                                    onToggle = { onToggle(todo) },
                                    onDelete = { deleting = todo }
                                )
                            }
                        }
                    }
                }
            }
        }
        item { VerticalSpace(8) }
    }

    if (creating) {
        TodoEditDialog(todo = null, onDismiss = { creating = false }, onSave = { onSave(it); creating = false })
    }
    editing?.let { todo ->
        TodoEditDialog(todo = todo, onDismiss = { editing = null }, onSave = { onSave(it); editing = null })
    }
    deleting?.let { todo ->
        ConfirmDialog(
            title = "删除待办",
            message = "确定删除「${todo.title}」吗？",
            onConfirm = { onDelete(todo); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun TodoRow(
    todo: TodoEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val priorityColor = when (todo.priority) {
        TodoPriority.HIGH -> Coral
        TodoPriority.LOW -> Teal
        else -> Amber
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = false,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Teal)
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(todo.title, style = MaterialTheme.typography.bodyLarge)
                if (todo.note.isNotBlank()) {
                    Text(
                        text = todo.note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityChip(todo.priority, priorityColor)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "+${RewardEngine.clampTodoReward(todo.starReward)}⭐",
                        fontSize = 11.sp,
                        color = Color(0xFF8A6B00)
                    )
                }
            }
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
private fun CompletedTodoRow(todo: TodoEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = true,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Teal)
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (todo.completedAt != null) {
                Text(
                    text = TimeUtils.formatDateTime(todo.completedAt!!),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { menuOpen = true }) {
            Text(text = "⋮", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("重新打开") }, onClick = { menuOpen = false; onToggle() })
            DropdownMenuItem(text = { Text("删除") }, onClick = { menuOpen = false; onDelete() })
        }
    }
}

@Composable
private fun PriorityChip(priority: Int, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = TodoPriority.label(priority),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun TodoEditDialog(
    todo: TodoEntity?,
    onDismiss: () -> Unit,
    onSave: (TodoEntity) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(todo?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(todo?.note ?: "") }
    var priority by rememberSaveable { mutableStateOf(todo?.priority ?: TodoPriority.MEDIUM) }
    var reward by rememberSaveable {
        mutableStateOf(RewardEngine.clampTodoReward(todo?.starReward).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (todo == null) "新建待办" else "编辑待办") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("待办内容") },
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
                Spacer(Modifier.height(12.dp))
                Text("优先级", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        TodoPriority.HIGH to "高",
                        TodoPriority.MEDIUM to "中",
                        TodoPriority.LOW to "低"
                    ).forEach { (value, label) ->
                        val selected = priority == value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (selected) 1.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { priority = value }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = reward,
                    onValueChange = { input -> reward = input.filter { it.isDigit() }.take(2) },
                    label = { Text("完成奖励⭐") },
                    supportingText = { Text("最多 ${RewardEngine.MAX_TODO_REWARD} ⭐") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        (todo ?: TodoEntity(title = "")).copy(
                            title = title.trim(),
                            note = note.trim(),
                            priority = priority,
                            starReward = RewardEngine.clampTodoReward(reward.toIntOrNull())
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}