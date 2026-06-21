package com.kdu.mc.lifelogger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AddEntryScreen(
    existingEntry: LifeEntry?,
    language: AppLanguage,
    onSaveClick: (LifeEntry) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(existingEntry?.title ?: "") }
    var selectedCategory by remember { mutableStateOf(existingEntry?.category ?: "Study") }
    var customCategory by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    var blocks by remember { mutableStateOf(existingEntry?.blocks ?: emptyList()) }

    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var showImageDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showVideoDialog by remember { mutableStateOf(false) }

    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempVideoUri by remember { mutableStateOf<Uri?>(null) }

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0) }
    var waveHeights by remember { mutableStateOf(List(32) { 8 }) }
    var currentAudioFile by remember { mutableStateOf<File?>(null) }

    val contentScrollState = rememberScrollState()
    val categories = listOf("Study", "Workout", "Event", "Reflection", "Other")

    // ── Validation ────────────────────────────────────────────────────────────
    val canSave = title.isNotBlank() && blocks.isNotEmpty()

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { blocks = blocks + EntryBlock.ImageBlock(it) }
    }
    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempImageUri?.let { blocks = blocks + EntryBlock.ImageBlock(it) }
    }
    val pickAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { blocks = blocks + EntryBlock.AudioBlock(it) }
    }
    val pickVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { blocks = blocks + EntryBlock.VideoBlock(it) }
    }
    val recordVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) tempVideoUri?.let { blocks = blocks + EntryBlock.VideoBlock(it) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createFileUri(context, "photo", ".jpg")
            tempImageUri = uri
            takePhotoLauncher.launch(uri)
        }
    }
    val videoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createFileUri(context, "video", ".mp4")
            tempVideoUri = uri
            recordVideoLauncher.launch(uri)
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startAudioRecording(context = context, onRecorderReady = { newRecorder, file ->
                recorder = newRecorder
                currentAudioFile = file
                isRecording = true
                recordingTime = 0
                waveHeights = List(32) { 8 }
            })
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recordingTime += 1000
        }
    }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            val amplitude = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
            val height = ((amplitude / 32767f) * 48).toInt().coerceIn(6, 48)
            waveHeights = waveHeights.drop(1) + height
            delay(120)
        }
    }
    LaunchedEffect(blocks.size, isRecording) {
        delay(200)
        contentScrollState.animateScrollTo(contentScrollState.maxValue)
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false; textInput = "" },
            title = { Text(textOf(language, "add_text")) },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text(textOf(language, "write_text")) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (textInput.isNotBlank()) blocks = blocks + EntryBlock.TextBlock(textInput)
                    textInput = ""
                    showTextDialog = false
                }) { Text(textOf(language, "add")) }
            },
            dismissButton = {
                OutlinedButton(onClick = { textInput = ""; showTextDialog = false }) {
                    Text(textOf(language, "cancel"))
                }
            }
        )
    }

    if (showImageDialog) {
        OptionDialog(
            title = textOf(language, "add_image"),
            firstOption = textOf(language, "select_storage"),
            secondOption = textOf(language, "open_camera"),
            cancelText = textOf(language, "cancel"),
            onFirstClick = { showImageDialog = false; pickImageLauncher.launch("image/*") },
            onSecondClick = {
                showImageDialog = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    val uri = createFileUri(context, "photo", ".jpg"); tempImageUri = uri; takePhotoLauncher.launch(uri)
                } else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showImageDialog = false }
        )
    }

    if (showAudioDialog) {
        OptionDialog(
            title = textOf(language, "add_audio"),
            firstOption = textOf(language, "select_storage"),
            secondOption = textOf(language, "record_audio"),
            cancelText = textOf(language, "cancel"),
            onFirstClick = { showAudioDialog = false; pickAudioLauncher.launch("audio/*") },
            onSecondClick = {
                showAudioDialog = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startAudioRecording(context = context, onRecorderReady = { newRecorder, file ->
                        recorder = newRecorder; currentAudioFile = file; isRecording = true; recordingTime = 0; waveHeights = List(32) { 8 }
                    })
                } else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { showAudioDialog = false }
        )
    }

    if (showVideoDialog) {
        OptionDialog(
            title = textOf(language, "add_video"),
            firstOption = textOf(language, "select_storage"),
            secondOption = textOf(language, "record_video"),
            cancelText = textOf(language, "cancel"),
            onFirstClick = { showVideoDialog = false; pickVideoLauncher.launch("video/*") },
            onSecondClick = {
                showVideoDialog = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    val uri = createFileUri(context, "video", ".mp4"); tempVideoUri = uri; recordVideoLauncher.launch(uri)
                } else videoPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showVideoDialog = false }
        )
    }

    // ── Screen Layout ─────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp)
    ) {
        Text(
            text = if (existingEntry == null) textOf(language, "add_new_entry") else textOf(language, "edit_entry"),
            fontSize = 28.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(textOf(language, "title")) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Box {
                Text(
                    text = categoryLabel(selectedCategory, language),
                    modifier = Modifier
                        .background(brush = AppGradient, shape = RoundedCornerShape(50))
                        .clickable { expanded = true }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(categoryLabel(category, language)) },
                            onClick = { selectedCategory = category; expanded = false }
                        )
                    }
                }
            }
        }

        if (selectedCategory == "Other") {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = customCategory, onValueChange = { customCategory = it },
                label = { Text(textOf(language, "custom_category")) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = RoundedCornerShape(28.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(contentScrollState)) {
                if (blocks.isEmpty() && !isRecording) {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = textOf(language, "content_placeholder"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Hint shown only when title is also missing
                            if (title.isBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))

                            }
                        }
                    }
                }

                blocks.forEachIndexed { index, block ->
                    EditableBlockPreview(
                        block = block, index = index, total = blocks.size, language = language,
                        onDelete = { blocks = blocks.filterIndexed { i, _ -> i != index } },
                        onMoveUp = {
                            if (index > 0) blocks = blocks.toMutableList().also {
                                val temp = it[index]; it[index] = it[index - 1]; it[index - 1] = temp
                            }
                        },
                        onMoveDown = {
                            if (index < blocks.lastIndex) blocks = blocks.toMutableList().also {
                                val temp = it[index]; it[index] = it[index + 1]; it[index + 1] = temp
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (isRecording) {
                    RecordingWaveCard(
                        recordingTime = recordingTime, waveHeights = waveHeights, language = language,
                        onStop = {
                            try {
                                recorder?.stop(); recorder?.release()
                                currentAudioFile?.let { file -> blocks = blocks + EntryBlock.AudioBlock(Uri.fromFile(file)) }
                            } catch (e: Exception) { e.printStackTrace() }
                            recorder = null; currentAudioFile = null; isRecording = false
                            recordingTime = 0; waveHeights = List(32) { 8 }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AddOptionButton(textOf(language, "text")) { showTextDialog = true }
            AddOptionButton(textOf(language, "image")) { showImageDialog = true }
            AddOptionButton(textOf(language, "audio")) { showAudioDialog = true }
            AddOptionButton(textOf(language, "video")) { showVideoDialog = true }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Save button — greyed out when canSave is false ────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    brush = if (canSave) AppGradient
                            else androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFFBDBDBD), Color(0xFFBDBDBD))
                            ),
                    shape = RoundedCornerShape(18.dp)
                )
                .then(
                    if (canSave) Modifier.clickable {
                        val finalCategory = if (selectedCategory == "Other") customCategory.ifBlank { "Other" } else selectedCategory
                        onSaveClick(LifeEntry(
                            title = title.trim(),
                            category = finalCategory,
                            blocks = blocks,
                            createdAt = System.currentTimeMillis()
                        ))
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (existingEntry == null) textOf(language, "save_entry") else textOf(language, "update_entry"),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                // Inline hint under the button text when disabled
                if (!canSave) {
                    Text(
                        text = when {
                            title.isBlank() && blocks.isEmpty() -> "Add a title and content"
                            title.isBlank() -> "Add a title"
                            else -> "Add at least one content block"
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(18.dp)
        ) { Text(textOf(language, "back")) }
    }
}

@Composable
fun RecordingWaveCard(recordingTime: Int, waveHeights: List<Int>, language: AppLanguage, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", fontSize = 22.sp, color = Color(0xFFE53935))
                Spacer(modifier = Modifier.width(8.dp))
                Text("${textOf(language, "recording")} ${formatTime(recordingTime)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.background(AppGradient, RoundedCornerShape(14.dp)).clickable { onStop() }.padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(textOf(language, "stop"), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                waveHeights.forEach { height ->
                    Box(modifier = Modifier.width(5.dp).height(height.dp).background(color = Color(0xFF1E5BFF), shape = RoundedCornerShape(50)))
                }
            }
        }
    }
}

@Composable
fun AddOptionButton(text: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(modifier = Modifier.size(58.dp).background(brush = AppGradient, shape = CircleShape), contentAlignment = Alignment.Center) {
            Text(text.take(1), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun OptionDialog(title: String, firstOption: String, secondOption: String, cancelText: String, onFirstClick: () -> Unit, onSecondClick: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(48.dp).background(AppGradient, RoundedCornerShape(16.dp)).clickable { onFirstClick() }, contentAlignment = Alignment.Center) {
                    Text(firstOption, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(48.dp).background(AppGradient, RoundedCornerShape(16.dp)).clickable { onSecondClick() }, contentAlignment = Alignment.Center) {
                    Text(secondOption, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {},
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(cancelText) } }
    )
}

@Composable
fun EditableBlockPreview(block: EntryBlock, index: Int, total: Int, language: AppLanguage, onDelete: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (block) {
                is EntryBlock.TextBlock -> Text(block.text, fontSize = 15.sp)
                is EntryBlock.ImageBlock -> Image(
                    painter = rememberAsyncImagePainter(block.uri), contentDescription = "Selected Image",
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
                is EntryBlock.AudioBlock -> AudioPlayer(block.uri)
                is EntryBlock.VideoBlock -> VideoPlayer(block.uri)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.weight(1f).height(54.dp)) {
                    Image(painterResource(id = R.drawable.up_arrow), contentDescription = textOf(language, "up"), modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.weight(1f).height(54.dp)) {
                    Image(painterResource(id = R.drawable.down_arrow), contentDescription = textOf(language, "down"), modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.weight(1f).height(54.dp)) {
                    Image(painterResource(id = R.drawable.bin), contentDescription = textOf(language, "delete"), modifier = Modifier.size(38.dp))
                }
            }
        }
    }
}

fun createFileUri(context: Context, prefix: String, extension: String): Uri {
    val file = File.createTempFile(prefix, extension, context.cacheDir)
    return FileProvider.getUriForFile(context, "com.kdu.mc.lifelogger.fileprovider", file)
}

fun startAudioRecording(context: Context, onRecorderReady: (MediaRecorder, File) -> Unit) {
    try {
        val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp4")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        newRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare(); start()
        }
        onRecorderReady(newRecorder, file)
    } catch (e: Exception) { e.printStackTrace() }
}
