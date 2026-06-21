package com.kdu.mc.lifelogger

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.border

import androidx.compose.ui.platform.LocalContext
import com.kdu.mc.lifelogger.data.AppContainer

// ── Colour helpers ─────────────────────────────────────────────────────────────
private val Purple      = Color(0xFF6A35FF)
private val Blue        = Color(0xFF1E5BFF)
private val GreenOk     = Color(0xFF2ECC71)
private val RedFail     = Color(0xFFE53935)
private val OrangePend  = Color(0xFFFF9800)
private val SurfaceGrey = Color(0xFFF5F7FC)
private val CardWhite   = Color.White

@Composable
fun SyncCenterScreen(
    language: AppLanguage,
    onBackClick: () -> Unit,
    container: AppContainer
) {
    val context = LocalContext.current
    val viewModel: SyncViewModel = viewModel(
        factory = SyncViewModelFactory(
            application = context.applicationContext as Application,
            authRepository = container.authRepository,
            entryRepository = container.entryRepository,
            syncManager = container.syncManager
        )
    )
    val state by viewModel.uiState.collectAsState()

    // Auto-trigger sync when network becomes available and auto-sync is on
    LaunchedEffect(state.networkType, state.autoSyncEnabled) {
        if (state.autoSyncEnabled && viewModel.canSync() &&
            state.syncStatus == SyncStatus.IDLE && state.pendingEntries.isNotEmpty()
        ) {
            viewModel.startSync()
        }
    }

    Scaffold(
        topBar = {
            SyncTopBar(language = language, onBackClick = onBackClick)
        },
        containerColor = SurfaceGrey
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // ── Network Status Banner ─────────────────────────────────────────
            item {
                NetworkStatusCard(networkType = state.networkType, lastSynced = state.lastSyncedTime)
            }

            // ── Success Banner ────────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = state.showSuccess,
                    enter = fadeIn() + slideInVertically(),
                    exit  = fadeOut() + slideOutVertically()
                ) {
                    SuccessBanner(onDismiss = { viewModel.dismissSuccess() })
                }
            }

            // ── Sync Progress ─────────────────────────────────────────────────
            item {
                AnimatedVisibility(visible = state.syncStatus == SyncStatus.SYNCING) {
                    SyncProgressCard(
                        progress    = state.progress,
                        synced      = state.syncedCount,
                        total       = state.totalCount
                    )
                }
            }

            // ── Auto Sync Settings Card ───────────────────────────────────────
            item {
                AutoSyncCard(
                    autoSyncEnabled = state.autoSyncEnabled,
                    syncMode        = state.syncMode,
                    onAutoSyncToggle = { viewModel.setAutoSync(it) },
                    onModeChange     = { viewModel.setSyncMode(it) }
                )
            }

            // ── Manual Sync Button (only when online and auto-sync off) ───────
            if (!state.autoSyncEnabled && viewModel.canSync() &&
                state.syncStatus != SyncStatus.SYNCING
            ) {
                item {
                    ManualSyncButton(onClick = { viewModel.startSync() })
                }
            }

            // ── Pending Entries ───────────────────────────────────────────────
            if (state.pendingEntries.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending (${state.pendingEntries.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        color      = Color(0xFF222222),
                        modifier   = Modifier.padding(start = 4.dp)
                    )
                }
                items(state.pendingEntries, key = { it.id }) { entry ->
                    PendingEntryCard(
                        entry    = entry,
                        onRetry  = { viewModel.retryEntry(entry.id) }
                    )
                }
            } else if (state.syncStatus != SyncStatus.SYNCING) {
                item { AllSyncedCard() }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ── Top Bar ────────────────────────────────────────────────────────────────────
@Composable
fun SyncTopBar(language: AppLanguage, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(AppGradient)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter           = painterResource(id = R.drawable.back),
            contentDescription = "Back",
            modifier          = Modifier
                .size(24.dp)
                .clickable { onBackClick() },
            colorFilter       = ColorFilter.tint(Color.White)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text       = "Sync Center",
            color      = Color.White,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Network Status Card ────────────────────────────────────────────────────────
@Composable
fun NetworkStatusCard(networkType: NetworkType, lastSynced: String) {
    val (icon, label, color) = when (networkType) {
        NetworkType.WIFI        -> Triple("📶", "Connected via Wi-Fi",        GreenOk)
        NetworkType.MOBILE_DATA -> Triple("📱", "Connected via Mobile Data",  Blue)
        NetworkType.NONE        -> Triple("📵", "No Internet Connection",      RedFail)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text       = label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = color
                )
                Text(
                    text     = "Last synced: $lastSynced",
                    fontSize = 12.sp,
                    color    = Color(0xFF9E9E9E)
                )
            }
        }
    }
}

// ── Success Banner ─────────────────────────────────────────────────────────────
@Composable
fun SuccessBanner(onDismiss: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = GreenOk.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✅", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text       = "All Synced!",
                        fontWeight = FontWeight.Bold,
                        color      = GreenOk,
                        fontSize   = 15.sp
                    )
                    Text(
                        text     = "All entries uploaded to cloud.",
                        fontSize = 12.sp,
                        color    = GreenOk.copy(alpha = 0.8f)
                    )
                }
            }
            Text(
                text     = "✕",
                fontSize = 18.sp,
                color    = GreenOk,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(4.dp)
            )
        }
    }
}

// ── Sync Progress Card ─────────────────────────────────────────────────────────
@Composable
fun SyncProgressCard(progress: Float, synced: Int, total: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue  = progress,
        animationSpec = tween(durationMillis = 500),
        label        = "progress"
    )

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Syncing…",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = Purple
                )
                Text(
                    text     = "$synced / $total",
                    fontSize = 13.sp,
                    color    = Color(0xFF9E9E9E)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress             = { animatedProgress },
                modifier             = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color                = Purple,
                trackColor           = Purple.copy(alpha = 0.15f),
                strokeCap            = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text     = "${(animatedProgress * 100).toInt()}% complete",
                fontSize = 12.sp,
                color    = Color(0xFF9E9E9E)
            )
        }
    }
}

// ── Auto Sync Settings Card ────────────────────────────────────────────────────
@Composable
fun AutoSyncCard(
    autoSyncEnabled: Boolean,
    syncMode: SyncMode,
    onAutoSyncToggle: (Boolean) -> Unit,
    onModeChange: (SyncMode) -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            // Auto-sync toggle row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = "Auto Sync",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        color      = Color(0xFF222222)
                    )
                    Text(
                        text     = "Sync automatically when connected",
                        fontSize = 12.sp,
                        color    = Color(0xFF9E9E9E)
                    )
                }
                Switch(
                    checked         = autoSyncEnabled,
                    onCheckedChange = onAutoSyncToggle,
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor  = Color.White,
                        checkedTrackColor  = Purple,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFCCCCCC)
                    )
                )
            }

            // Dropdown — only show when auto-sync is on
            AnimatedVisibility(visible = autoSyncEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFFF0F0F0))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text     = "Sync when connected via",
                        fontSize = 13.sp,
                        color    = Color(0xFF9E9E9E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Dropdown trigger
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Purple.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable { dropdownExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                text       = syncMode.label,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color      = Purple
                            )
                            Text("▾", fontSize = 14.sp, color = Purple)
                        }

                        DropdownMenu(
                            expanded        = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier        = Modifier.background(CardWhite)
                        ) {
                            SyncMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text       = mode.label,
                                            fontSize   = 14.sp,
                                            color      = if (mode == syncMode) Purple else Color(0xFF222222),
                                            fontWeight = if (mode == syncMode) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onModeChange(mode)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Manual Sync Button ─────────────────────────────────────────────────────────
@Composable
fun ManualSyncButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .background(AppGradient, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☁️", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text       = "Sync Now",
                color      = Color.White,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Pending Entry Card ─────────────────────────────────────────────────────────
@Composable
fun PendingEntryCard(entry: PendingEntry, onRetry: () -> Unit) {
    val (statusIcon, statusColor) = when (entry.syncStatus) {
        EntrySync.PENDING  -> "⏳" to OrangePend
        EntrySync.SYNCING  -> "🔄" to Blue
        EntrySync.SUCCESS  -> "✅" to GreenOk
        EntrySync.FAILED   -> "❌" to RedFail
    }

    // Pulse animation for syncing state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (entry.syncStatus == EntrySync.SYNCING) 0.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor.copy(alpha = alpha), CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Entry info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = entry.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = Color(0xFF222222)
                )
                Text(
                    text     = entry.date,
                    fontSize = 12.sp,
                    color    = Color(0xFF9E9E9E)
                )
            }

            // Status icon / retry button
            if (entry.syncStatus == EntrySync.FAILED) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RedFail.copy(alpha = 0.1f))
                        .clickable { onRetry() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text       = "Retry",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = RedFail
                    )
                }
            } else {
                Text(text = statusIcon, fontSize = 20.sp)
            }
        }
    }
}

// ── All Synced Empty State ─────────────────────────────────────────────────────
@Composable
fun AllSyncedCard() {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text("☁️", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text       = "All entries are synced",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = Color(0xFF222222)
            )
            Text(
                text     = "Nothing pending right now.",
                fontSize = 13.sp,
                color    = Color(0xFF9E9E9E)
            )
        }
    }
}
