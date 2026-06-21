package com.kdu.mc.lifelogger

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import coil3.util.DebugLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable

fun HomeScreen(
    entries: List<LifeEntry>,
    language: AppLanguage,
    onLanguageToggle: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    onDeleteClick: (Set<Int>) -> Unit,
    onCalendarClick: () -> Unit,
    onProfileClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onSyncClick: (String) -> Unit,
    onOffline: (String) -> Unit,
    onLogout: () -> Unit,

    ) {
    var selectedEntryIndex by remember { mutableStateOf<Int?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }   // ← exit confirmation

    val gifImageLoader = rememberGifImageLoader()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp * 0.75).dp

    val isDrawerOpen = drawerState.isOpen
    val blurAmount by animateFloatAsState(
        targetValue = if (isDrawerOpen) 200f else 0f,
        animationSpec = tween(300),
        label = "blur"
    )

    // ── Back-press handling ───────────────────────────────────────────────────
    // Priority 1: close drawer if open
    // Priority 2: exit selection mode if active
    // Priority 3: show exit confirmation dialog
    BackHandler(enabled = true) {
        when {
            isDrawerOpen -> scope.launch { drawerState.close() }
            selectionMode -> {
                selectionMode = false
                selectedItems = emptySet()
            }
            else -> showExitDialog = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            DrawerContent(
                language = language,
                drawerWidth = drawerWidth,
                drawerState = drawerState,
                scope = scope,
                onNavigate = onNavigate ,
                onSyncClick = onSyncClick,
                onOffline= onOffline,
                onLogout = onLogout
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    if (selectionMode) {
                        SelectionTopBar(
                            selectedCount = selectedItems.size,
                            allSelected = selectedItems.size == entries.size,
                            language = language,
                            onClose = {
                                selectionMode = false
                                selectedItems = emptySet()
                            },
                            onSelectAll = {
                                selectedItems = if (selectedItems.size == entries.size) emptySet() else entries.indices.toSet()
                                if (selectedItems.isEmpty()) selectionMode = false
                            },
                            onDelete = {
                                if (selectedItems.isNotEmpty()) showDeleteDialog = true
                            }
                        )
                    } else {
                        NormalTopBar(
                            language = language,
                            onLanguageToggle = onLanguageToggle,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                    }
                },
                floatingActionButton = {
                    if (!selectionMode) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .background(AppGradient, CircleShape)
                                .clickable { onAddClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.add),
                                contentDescription = "Add Entry",
                                modifier = Modifier.size(28.dp),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                    }
                },
                bottomBar = {
                    AppBottomBar(
                        currentScreen = "home",
                        onHomeClick = { },
                        onCalendarClick = onCalendarClick,
                        onProfileClick = onProfileClick,
                        language = language
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .graphicsLayer {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                renderEffect = android.graphics.RenderEffect
                                    .createBlurEffect(
                                        blurAmount,
                                        blurAmount,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                    .asComposeRenderEffect()
                            } else {
                                renderEffect = null
                            }
                        }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Text(
                        text = currentHeaderDate(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E5BFF),
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
                    )
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                    if (entries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 40.dp)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(R.drawable.home)
                                        .build(),
                                    contentDescription = textOf(language, "empty_illustration"),
                                    imageLoader = gifImageLoader,
                                    modifier = Modifier.size(300.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = textOf(language, "no_entries"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                            itemsIndexed(entries) { index, entry ->
                                EntryListItem(
                                    entry = entry,
                                    language = language,
                                    isSelectionMode = selectionMode,
                                    isSelected = index in selectedItems,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedItems = if (index in selectedItems) selectedItems - index else selectedItems + index
                                            if (selectedItems.isEmpty()) selectionMode = false
                                        } else {
                                            selectedEntryIndex = index
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
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(textOf(language, "delete_selected")) },
            text = { Text(textOf(language, "delete_message")) },
            confirmButton = {
                Button(onClick = {
                    onDeleteClick(selectedItems)
                    selectedItems = emptySet()
                    selectionMode = false
                    selectedEntryIndex = null
                    showDeleteDialog = false
                }) {
                    Text(textOf(language, "delete"))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text(textOf(language, "cancel"))
                }
            }
        )
    }

    // ── Exit confirmation dialog ──────────────────────────────────────────────
    if (showExitDialog) {
        val activity = LocalContext.current as? android.app.Activity
        ExitConfirmDialog(
            language = language,
            onDismiss = { showExitDialog = false },
            onConfirm = {
                showExitDialog = false
                activity?.finish()
            }
        )
    }

    // ── Entry detail dialog ───────────────────────────────────────────────────
    selectedEntryIndex?.let { index ->
        if (index < entries.size) {
            EntryDetailDialog(
                entry = entries[index],
                language = language,
                onDismiss = { selectedEntryIndex = null },
                onEditClick = {
                    selectedEntryIndex = null
                    onEditClick(index)
                }
            )
        }
    }
}

// ── Exit dialog needs Activity context — helper extension ─────────────────────
// The confirmButton lambda can't call LocalContext directly, so pass it in:
@Composable
fun ExitConfirmDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = textOf(language, "exit_title"),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = textOf(language, "exit_message"),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .background(AppGradient, RoundedCornerShape(12.dp))
                    .clickable { onConfirm() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(textOf(language, "exit_yes"), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text(textOf(language, "exit_no"))
            }
        }
    )
}

@Composable
fun rememberGifImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory(enforceMinimumFrameDelay = true))
                }
            }
            .logger(DebugLogger())
            .build()
    }
}

@Composable
fun NormalTopBar(
    language: AppLanguage,
    onLanguageToggle: () -> Unit,
    onMenuClick: () -> Unit
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
            painter = painterResource(id = R.drawable.menu),
            contentDescription = "Menu",
            modifier = Modifier
                .size(24.dp)
                .clickable { onMenuClick() },
            colorFilter = ColorFilter.tint(Color.White)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = textOf(language, "app_name"),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                .clickable { onLanguageToggle() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (language == AppLanguage.ENGLISH) "සිං" else "EN",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text("⌕", color = Color.White, fontSize = 24.sp)
    }
}

@Composable
fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    language: AppLanguage,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
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
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (allSelected) textOf(language, "unselect_all") else textOf(language, "select_all"),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable { onSelectAll() }
                .padding(8.dp)
        )
        Text(
            text = textOf(language, "delete"),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable { onDelete() }
                .padding(8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryListItem(
    entry: LifeEntry,
    language: AppLanguage,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val firstImage = entry.blocks.filterIsInstance<EntryBlock.ImageBlock>().firstOrNull()
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) Color(0xFF1E5BFF).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
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
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                color = if (isSelected) Color(0xFF6A35FF) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isSelected) "✓" else "", color = Color.White, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Image(
                    painter = painterResource(id = categoryIcon(entry.category)),
                    contentDescription = entry.category,
                    modifier = Modifier.size(70.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title.ifBlank { textOf(language, "no_title") },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${categoryLabel(entry.category, language)} • ${formatOnlyTime(entry.createdAt)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                if (firstImage != null) {
                    AsyncImage(
                        model = firstImage.uri,
                        contentDescription = "Entry Image",
                        modifier = Modifier
                            .width(82.dp)
                            .height(68.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(82.dp)
                            .height(68.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun EntryDetailDialog(
    entry: LifeEntry,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                        .verticalScroll(rememberScrollState())
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
                                is EntryBlock.TextBlock -> {
                                    Text(text = block.text.ifBlank { textOf(language, "no_description") }, fontSize = 15.sp)
                                }
                                is EntryBlock.ImageBlock -> DisplayImage(block.uri)
                                is EntryBlock.AudioBlock -> AudioPlayer(block.uri)
                                is EntryBlock.VideoBlock -> VideoPlayer(block.uri)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                        Text(textOf(language, "close"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .background(AppGradient, RoundedCornerShape(14.dp))
                            .clickable { onEditClick() }
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(textOf(language, "edit"), color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

fun categoryIcon(category: String): Int {
    return when (category.lowercase()) {
        "study" -> R.drawable.study
        "workout" -> R.drawable.workout
        "event" -> R.drawable.event
        "reflection" -> R.drawable.reflection
        else -> R.drawable.others
    }
}

fun currentHeaderDate(): String {
    val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
    return sdf.format(Date())
}

fun formatOnlyTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun DisplayImage(uri: Uri) {
    var showFullImage by remember { mutableStateOf(false) }
    AsyncImage(
        model = uri,
        contentDescription = "Entry Image",
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { showFullImage = true },
        contentScale = ContentScale.Crop
    )
    if (showFullImage) {
        Dialog(onDismissRequest = { showFullImage = false }) {
            Box(modifier = Modifier
                .fillMaxSize()
                .clickable { showFullImage = false }, contentAlignment = Alignment.Center) {
                AsyncImage(model = uri, contentDescription = "Full Image", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun AudioPlayer(audioUri: Uri) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }

    DisposableEffect(audioUri) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, audioUri)
            prepare()
            duration = this.duration
            setOnCompletionListener {
                isPlaying = false
                currentPosition = 0
                seekTo(0)
            }
        }
        onDispose { mediaPlayer?.release() }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { currentPosition = it.currentPosition }
            delay(500)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(AppGradient, RoundedCornerShape(50))
                        .clickable {
                            mediaPlayer?.let {
                                if (isPlaying) {
                                    it.pause()
                                    isPlaying = false
                                } else {
                                    it.start()
                                    isPlaying = true
                                }
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isPlaying) "Pause" else "Play", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "${formatTime(currentPosition)} / ${formatTime(duration)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { value ->
                    currentPosition = value.toInt()
                    mediaPlayer?.seekTo(currentPosition)
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF1E5BFF),
                    activeTrackColor = Color(0xFF1E5BFF),
                    inactiveTrackColor = Color(0xFF1E5BFF).copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun VideoPlayer(videoUri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }
    DisposableEffect(videoUri) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(18.dp))
    )
}

// ---------------------------------------------------------------------------
// String keys to add to your textOf() map for exit dialog:
// ---------------------------------------------------------------------------
// "exit_title"   -> "Exit App?" / "යෙදුම් වසන්නද?"
// "exit_message" -> "Are you sure you want to exit?" / "ඔබට ඉවත් වීමට අවශ්‍යද?"
// "exit_yes"     -> "Exit" / "ඉවත් වන්න"
// "exit_no"      -> "Stay" / "රැඳෙන්න"
