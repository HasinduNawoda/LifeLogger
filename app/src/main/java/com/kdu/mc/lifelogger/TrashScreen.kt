package com.kdu.mc.lifelogger

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

data class TrashedEntry(
    val entry: LifeEntry,
    val deletedAt: Long = System.currentTimeMillis()
)

/** How many full days ago was this entry deleted. */
fun daysSinceDeletion(trashedEntry: TrashedEntry): Long =
    TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - trashedEntry.deletedAt)
        .coerceAtLeast(0)

// ─────────────────────────────────────────────────────────────────────────────
// TrashScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrashScreen(
    trashedEntries: List<TrashedEntry>,
    language: AppLanguage,
    onRestoreEntries: (Set<Int>) -> Unit,
    onDeletePermanently: (Set<Int>) -> Unit,
    onBackClick: () -> Unit
) {
    var selectionMode     by remember { mutableStateOf(false) }
    var selectedItems     by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showEmptyDialog   by remember { mutableStateOf(false) }
    var previewEntry      by remember { mutableStateOf<TrashedEntry?>(null) }

    BackHandler(enabled = true) {
        when {
            selectionMode -> {
                selectionMode = false
                selectedItems = emptySet()
            }
            else -> onBackClick()
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TrashSelectionTopBar(
                    selectedCount = selectedItems.size,
                    allSelected   = selectedItems.size == trashedEntries.size,
                    language      = language,
                    onClose = {
                        selectionMode = false
                        selectedItems = emptySet()
                    },
                    onSelectAll = {
                        selectedItems = if (selectedItems.size == trashedEntries.size)
                            emptySet()
                        else
                            trashedEntries.indices.toSet()
                        if (selectedItems.isEmpty()) selectionMode = false
                    },
                    onRestore = { if (selectedItems.isNotEmpty()) showRestoreDialog = true },
                    onDelete  = { if (selectedItems.isNotEmpty()) showDeleteDialog  = true }
                )
            } else {
                TrashNormalTopBar(
                    language     = language,
                    hasEntries   = trashedEntries.isNotEmpty(),
                    onBackClick  = onBackClick,
                    onEmptyTrash = { showEmptyDialog = true }
                )
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (trashedEntries.isEmpty()) {

                // ── Empty state ──────────────────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(40.dp)
                    ) {
                        Text(text = "🗑️", fontSize = 80.sp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = textOf(language, "trash_empty"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = textOf(language, "trash_empty_sub"),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            } else {

                // ── Entry list ───────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(trashedEntries) { index, trashedEntry ->
                        TrashEntryListItem(
                            trashedEntry    = trashedEntry,
                            language        = language,
                            isSelectionMode = selectionMode,
                            isSelected      = index in selectedItems,
                            onClick = {
                                if (selectionMode) {
                                    selectedItems = if (index in selectedItems)
                                        selectedItems - index
                                    else
                                        selectedItems + index
                                    if (selectedItems.isEmpty()) selectionMode = false
                                } else {
                                    // tap outside selection → open read-only preview
                                    previewEntry = trashedEntry
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selectedItems = selectedItems + index
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // ── Read-only detail preview ─────────────────────────────────────────────
    previewEntry?.let { te ->
        TrashEntryDetailDialog(
            entry    = te.entry,
            language = language,
            onDismiss = { previewEntry = null }
        )
    }

    // ── Restore confirmation ─────────────────────────────────────────────────
    if (showRestoreDialog) {
        TrashAlertDialog(
            title        = textOf(language, "trash_restore_title"),
            body         = textOf(language, "trash_restore_message"),
            confirmLabel = textOf(language, "trash_restore_confirm"),
            confirmBrush = AppGradient,
            cancelLabel  = textOf(language, "cancel"),
            onConfirm = {
                onRestoreEntries(selectedItems)
                selectedItems = emptySet()
                selectionMode = false
                showRestoreDialog = false
            },
            onDismiss = { showRestoreDialog = false }
        )
    }

    // ── Permanent delete confirmation ────────────────────────────────────────
    if (showDeleteDialog) {
        TrashAlertDialog(
            title        = textOf(language, "trash_delete_title"),
            body         = textOf(language, "trash_delete_message"),
            confirmLabel = textOf(language, "trash_delete_confirm"),
            confirmBrush = Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFB71C1C))),
            cancelLabel  = textOf(language, "cancel"),
            onConfirm = {
                onDeletePermanently(selectedItems)
                selectedItems = emptySet()
                selectionMode = false
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // ── Empty trash confirmation ─────────────────────────────────────────────
    if (showEmptyDialog) {
        TrashAlertDialog(
            title        = textOf(language, "trash_empty_trash_title"),
            body         = textOf(language, "trash_empty_trash_message"),
            confirmLabel = textOf(language, "trash_empty_trash_confirm"),
            confirmBrush = Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFB71C1C))),
            cancelLabel  = textOf(language, "cancel"),
            onConfirm = {
                onDeletePermanently(trashedEntries.indices.toSet())
                showEmptyDialog = false
            },
            onDismiss = { showEmptyDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Read-only detail dialog  (no Edit button — trash context)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrashEntryDetailDialog(
    entry: LifeEntry,
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                ) {
                    Text(
                        text = entry.title.ifBlank { textOf(language, "no_title") },
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${categoryLabel(entry.category, language)} • ${formatDateTime(entry.createdAt)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (entry.blocks.isEmpty()) {
                        Text(textOf(language, "no_content"))
                    } else {
                        entry.blocks.forEach { block ->
                            when (block) {
                                is EntryBlock.TextBlock  -> Text(
                                    text = block.text.ifBlank { textOf(language, "no_description") },
                                    fontSize = 15.sp
                                )
                                is EntryBlock.ImageBlock -> DisplayImage(block.uri)
                                is EntryBlock.AudioBlock -> AudioPlayer(block.uri)
                                is EntryBlock.VideoBlock -> VideoPlayer(block.uri)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Only Close — no Edit in trash
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .background(AppGradient, RoundedCornerShape(14.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = textOf(language, "close"),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable confirm dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrashAlertDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmBrush: Brush,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Text(text = body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .background(confirmBrush, RoundedCornerShape(12.dp))
                    .clickable { onConfirm() }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(confirmLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text(cancelLabel)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bars
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrashNormalTopBar(
    language: AppLanguage,
    hasEntries: Boolean,
    onBackClick: () -> Unit,
    onEmptyTrash: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(AppGradient)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.back),
            contentDescription = "Back",
            modifier = Modifier
                .size(24.dp)
                .clickable { onBackClick() },
            colorFilter = ColorFilter.tint(Color.White)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = textOf(language, "trash_title"),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        if (hasEntries) {
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .clickable { onEmptyTrash() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = textOf(language, "trash_empty_all"),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TrashSelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    language: AppLanguage,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(AppGradient)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✕",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier
                .clickable { onClose() }
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$selectedCount ${textOf(language, "selected")}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (allSelected) textOf(language, "unselect_all") else textOf(language, "select_all"),
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable { onSelectAll() }
                .padding(horizontal = 6.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        // Restore pill
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                .clickable { onRestore() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = textOf(language, "trash_restore"),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        // Delete — danger colour signal
        Text(
            text = textOf(language, "delete"),
            color = Color(0xFFFFCDD2),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onDelete() }
                .padding(8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashEntryListItem(
    trashedEntry: TrashedEntry,
    language: AppLanguage,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val entry      = trashedEntry.entry
    val firstImage = entry.blocks.filterIsInstance<EntryBlock.ImageBlock>().firstOrNull()
    val daysAgo    = daysSinceDeletion(trashedEntry)

    val cardBackground by animateColorAsState(
        targetValue    = if (isSelected) Color(0xFF1E5BFF).copy(alpha = 0.15f)
        else            MaterialTheme.colorScheme.surface,
        animationSpec  = tween(200),
        label          = "cardBg"
    )

    Surface(
        modifier      = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        shape         = RoundedCornerShape(22.dp),
        color         = cardBackground,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .clip(RoundedCornerShape(22.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection circle
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                color = if (isSelected) Color(0xFF6A35FF)
                                else            MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text      = if (isSelected) "✓" else "",
                            color     = Color.White,
                            fontSize  = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Category icon
                Image(
                    painter            = painterResource(id = categoryIcon(entry.category)),
                    contentDescription = entry.category,
                    modifier           = Modifier.size(70.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))

                // Title + meta + deleted-ago badge
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = entry.title.ifBlank { textOf(language, "no_title") },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text     = "${categoryLabel(entry.category, language)} • ${formatOnlyTime(entry.createdAt)}",
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // "X days ago" badge
                    val badgeLabel = if (daysAgo == 0L) "Today"
                    else "$daysAgo ${textOf(language, "trash_days_ago")}"
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFF1E5BFF).copy(alpha = 0.09f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text       = badgeLabel,
                            fontSize   = 10.sp,
                            color      = Color(0xFF1E5BFF),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Thumbnail or placeholder
                if (firstImage != null) {
                    AsyncImage(
                        model              = firstImage.uri,
                        contentDescription = "Entry Image",
                        modifier           = Modifier
                            .width(82.dp)
                            .height(68.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )  }
            }
        }
    }
}