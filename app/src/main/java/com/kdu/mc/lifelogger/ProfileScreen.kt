package com.kdu.mc.lifelogger

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.foundation.Image

// ---------------------------------------------------------------------------
// Data class
// ---------------------------------------------------------------------------
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUri: Uri? = null,
    val joinedAt: Long = System.currentTimeMillis(),
    val usedStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 1_073_741_824L
)

// ---------------------------------------------------------------------------
// ProfileScreen
// ---------------------------------------------------------------------------
@Composable
fun ProfileScreen(
    profile: UserProfile,
    entryCount: Int,
    language: AppLanguage,
    onLanguageToggle: () -> Unit,
    onMenuClick: () -> Unit,
    onHomeClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onSaveProfile: (UserProfile) -> Unit
) {
    Scaffold(
        topBar = {
            ProfileTopBar(
                language = language,
                onBack = onHomeClick
            )
        },
        bottomBar = {
            AppBottomBar(
                currentScreen = "profile",      // "calendar" or "profile" depending on the screen
                onHomeClick = onHomeClick,
                onCalendarClick = onCalendarClick,
                onProfileClick = {  },
                language = language
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Avatar + Name + Email ─────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Gradient ring
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(AppGradient, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile.photoUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(profile.photoUri)
                                    .build(),
                                contentDescription = "Profile picture",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF1E5BFF).copy(alpha = 0.15f),
                                                Color(0xFF6A35FF).copy(alpha = 0.15f)
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (profile.name.isNotBlank())
                                        profile.name.take(1).uppercase() else "?",
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E5BFF)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (profile.name.isNotBlank()) {
                    Text(
                        text = profile.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (profile.email.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile.email,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Stats row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "📓",
                    label = "Entries",
                    value = "$entryCount"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "📅",
                    label = "Joined",
                    value = formatJoinDate(profile.joinedAt)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Bio card ──────────────────────────────────────────────────
            if (profile.bio.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "About",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E5BFF)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = profile.bio,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Info fields card ──────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    ProfileInfoRow(
                        icon = "👤",
                        label = textOf(language, "profile_name"),
                        value = profile.name.ifBlank { "—" }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                    ProfileInfoRow(
                        icon = "✉️",
                        label = textOf(language, "profile_email"),
                        value = profile.email.ifBlank { "—" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Storage card ──────────────────────────────────────────────
            StorageCard(
                usedBytes = profile.usedStorageBytes,
                totalBytes = profile.totalStorageBytes,
                language = language
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Top Bar  (matches CalendarTopBar style)
// ---------------------------------------------------------------------------
@Composable
fun ProfileTopBar(
    language: AppLanguage,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(AppGradient)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.back),
            contentDescription = "Back",
            modifier = Modifier
                .size(26.dp)
                .clickable { onBack() },
            colorFilter = ColorFilter.tint(Color.White)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "User Profile",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------------------------------------------------------------------------
// Stat Card
// ---------------------------------------------------------------------------
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: String,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E5BFF)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Info Row  (read-only)
// ---------------------------------------------------------------------------
@Composable
fun ProfileInfoRow(
    icon: String,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E5BFF)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Storage Card  (unchanged logic, kept here for self-containment)
// ---------------------------------------------------------------------------
@Composable
fun StorageCard(
    usedBytes: Long,
    totalBytes: Long,
    language: AppLanguage
) {
    val fraction = if (totalBytes > 0)
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 900),
        label = "storage"
    )
    val barColor = when {
        fraction < 0.6f  -> Color(0xFF1E5BFF)
        fraction < 0.85f -> Color(0xFF6A35FF)
        else             -> Color(0xFFE53935)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1E5BFF), Color(0xFF6A35FF))
                            ),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("☁", color = Color.White, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = textOf(language, "storage_usage"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(50)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF1E5BFF), barColor)),
                            RoundedCornerShape(50)
                        )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(fraction * 100).toInt()}% ${textOf(language, "storage_used")}",
                    fontSize = 12.sp,
                    color = barColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatBytes(totalBytes - usedBytes)} ${textOf(language, "storage_free")}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
fun formatJoinDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L     -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024L         -> String.format("%.1f KB", bytes / 1_024.0)
        else                    -> "$bytes B"
    }
}