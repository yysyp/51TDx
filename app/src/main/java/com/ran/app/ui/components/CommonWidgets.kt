package com.ran.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ran.app.ui.Feedback
import kotlinx.coroutines.delay

/**
 * Elevated content card with the app's warm surface treatment.
 *
 * Content is hosted in a [Column], not a [Box]. A Box stacks multiple children
 * on top of each other, which made every list rendered inside a card (headers,
 * rows, spacers) overlap into an unreadable pile. Column stacks them vertically,
 * which is what callers expect.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** Section title with an emoji, used to keep screens scannable. */
@Composable
fun SectionTitle(text: String, emoji: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Friendly empty state; copy invites action instead of reporting absence. */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Pulsing glow used to draw attention to the primary action without shouting. */
@Composable
fun PulsingActionLabel(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulseScale"
    )
    Text(
        text = text,
        modifier = modifier.scale(scale),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    )
}

/**
 * Transient reward banner.
 *
 * It self-dismisses so the reward stays visible long enough to register as a
 * reward, but not so long that it blocks the next action.
 */
@Composable
fun FeedbackBanner(
    feedback: Feedback?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(feedback?.id) {
        if (feedback != null) {
            delay(2200)
            onDismiss()
        }
    }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            val current = feedback ?: return@AnimatedVisibility
            val bg = if (current.isError) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
            val fg = if (current.isError) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .background(bg, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (current.isError) "💡" else "🎉",
                    fontSize = 15.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = current.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fg
                )
            }
        }
    }
}

/** Reusable single-field text entry dialog. */
@Composable
fun SimpleInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmText: String = "保存",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable(initialValue) {
        mutableStateOf(initialValue)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** Confirmation dialog for destructive actions. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** Full-screen scrim used behind modals. */
@Composable
fun ModalScrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
    )
}

/** Vertical rhythm helper for list spacing. */
@Composable
fun VerticalSpace(height: Int = 12) {
    Spacer(Modifier.height(height.dp))
}

/** Row layout helper with even spacing. */
@Composable
fun EvenRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: @Composable () -> Unit
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = horizontalArrangement) {
        content()
    }
}