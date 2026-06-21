package com.kdu.mc.lifelogger

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage

// ─────────────────────────────────────────────────────────────────────────────
// DATA
// ─────────────────────────────────────────────────────────────────────────────

private data class OfflineMediaItem(
    val uri: Uri,
    val entryTitle: String,
    val entryCreatedAt: Long
)

private enum class OfflineMediaTab {
    IMAGE,
    AUDIO,
    VIDEO
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OfflineScreen(
    entries: List<LifeEntry>,
    language: AppLanguage,
    onBackClick: () -> Unit
) {

    var activeTab by remember {
        mutableStateOf(OfflineMediaTab.IMAGE)
    }

    val images = remember(entries) {
        entries.flatMap { entry ->
            entry.blocks
                .filterIsInstance<EntryBlock.ImageBlock>()
                .map {
                    OfflineMediaItem(
                        uri = it.uri,
                        entryTitle = entry.title.ifBlank { "Untitled" },
                        entryCreatedAt = entry.createdAt
                    )
                }
        }
    }

    val audios = remember(entries) {
        entries.flatMap { entry ->
            entry.blocks
                .filterIsInstance<EntryBlock.AudioBlock>()
                .map {
                    OfflineMediaItem(
                        uri = it.uri,
                        entryTitle = entry.title.ifBlank { "Untitled" },
                        entryCreatedAt = entry.createdAt
                    )
                }
        }
    }

    val videos = remember(entries) {
        entries.flatMap { entry ->
            entry.blocks
                .filterIsInstance<EntryBlock.VideoBlock>()
                .map {
                    OfflineMediaItem(
                        uri = it.uri,
                        entryTitle = entry.title.ifBlank { "Untitled" },
                        entryCreatedAt = entry.createdAt
                    )
                }
        }
    }

    Scaffold(
        topBar = {
            OfflineTopBar(
                language = language,
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {

            UploadsTabRow(
                activeTab = activeTab,
                imageCt = images.size,
                audioCt = audios.size,
                videoCt = videos.size,
                onTabSelected = {
                    activeTab = it
                }
            )

            Divider(color = MaterialTheme.colorScheme.surfaceVariant)

            when (activeTab) {

                OfflineMediaTab.IMAGE -> {
                    ImageGrid(images, language)
                }

                OfflineMediaTab.AUDIO -> {
                    AudioList(audios, language)
                }

                OfflineMediaTab.VIDEO -> {
                    VideoGrid(videos, language)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OfflineTopBar(
    language: AppLanguage,
    onBackClick: () -> Unit
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
                .clickable {
                    onBackClick()
                },
            colorFilter = ColorFilter.tint(Color.White)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = textOf(language, "Offline Media"),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UploadsTabRow(
    activeTab: OfflineMediaTab,
    imageCt: Int,
    audioCt: Int,
    videoCt: Int,
    onTabSelected: (OfflineMediaTab) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        TabPill(
            label = "Images ($imageCt)",
            selected = activeTab == OfflineMediaTab.IMAGE,
            modifier = Modifier.weight(1f),
            onClick = {
                onTabSelected(OfflineMediaTab.IMAGE)
            }
        )

        TabPill(
            label = "Audio ($audioCt)",
            selected = activeTab == OfflineMediaTab.AUDIO,
            modifier = Modifier.weight(1f),
            onClick = {
                onTabSelected(OfflineMediaTab.AUDIO)
            }
        )

        TabPill(
            label = "Video ($videoCt)",
            selected = activeTab == OfflineMediaTab.VIDEO,
            modifier = Modifier.weight(1f),
            onClick = {
                onTabSelected(OfflineMediaTab.VIDEO)
            }
        )
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    if (selected) {

        Box(
            modifier = modifier
                .height(40.dp)
                .background(
                    AppGradient,
                    RoundedCornerShape(50)
                )
                .clickable {
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

    } else {

        Box(
            modifier = modifier
                .height(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50)
                )
                .clickable {
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(message: String) {

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IMAGE GRID
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ImageGrid(
    images: List<OfflineMediaItem>,
    language: AppLanguage
) {

    if (images.isEmpty()) {
        EmptyState(textOf(language, "no_images"))
        return
    }

    var fullscreenUri by remember {
        mutableStateOf<Uri?>(null)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        items(images) { item ->

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        fullscreenUri = item.uri
                    }
            ) {

                AsyncImage(
                    model = item.uri,
                    contentDescription = item.entryTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {

                    Text(
                        text = item.entryTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    fullscreenUri?.let { uri ->

        Dialog(
            onDismissRequest = {
                fullscreenUri = null
            }
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        fullscreenUri = null
                    },
                contentAlignment = Alignment.Center
            ) {

                AsyncImage(
                    model = uri,
                    contentDescription = "Full Image",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AUDIO LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AudioList(
    audios: List<OfflineMediaItem>,
    language: AppLanguage
) {

    if (audios.isEmpty()) {
        EmptyState(textOf(language, "no_audio"))
        return
    }

    var playerUri by remember {
        mutableStateOf<Uri?>(null)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        items(audios) { item ->

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            playerUri = item.uri
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                AppGradient,
                                RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = "▶",
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = item.entryTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = formatDateTime(item.entryCreatedAt),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    playerUri?.let { uri ->

        Dialog(
            onDismissRequest = {
                playerUri = null
            }
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = textOf(language, "audio"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    AudioPlayer(uri)

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            playerUri = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {

                        Text(textOf(language, "close"))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VIDEO GRID
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoGrid(
    videos: List<OfflineMediaItem>,
    language: AppLanguage
) {

    if (videos.isEmpty()) {
        EmptyState(textOf(language, "no_video"))
        return
    }

    var playerUri by remember {
        mutableStateOf<Uri?>(null)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        items(videos) { item ->

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        playerUri = item.uri
                    }
            ) {

                VideoThumbnail(
                    uri = item.uri,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "▶",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {

                    Text(
                        text = item.entryTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    playerUri?.let { uri ->

        Dialog(
            onDismissRequest = {
                playerUri = null
            }
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                )
            ) {

                Column(
                    modifier = Modifier.padding(12.dp)
                ) {

                    VideoPlayer(uri)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            playerUri = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {

                        Text(
                            text = textOf(language, "close"),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VIDEO THUMBNAIL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current

    var bitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(uri) {

        try {

            val retriever = MediaMetadataRetriever()

            retriever.setDataSource(context, uri)

            bitmap = retriever.getFrameAtTime(0)

            retriever.release()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (bitmap != null) {

        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Video Thumbnail",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )

    } else {

        Box(
            modifier = modifier.background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = "Video",
                color = Color.White
            )
        }
    }
}