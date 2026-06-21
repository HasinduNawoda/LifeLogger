package com.kdu.mc.lifelogger

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kdu.mc.lifelogger.data.AppContainer
import kotlinx.coroutines.launch

// ── App-wide gradient (used in TopBar, FAB, auth screens, etc.) ───────────────
val AppGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF1E5BFF),
        Color(0xFF6A35FF),
        Color(0xFF9C27E8)
    )
)

// ── Entry block types ─────────────────────────────────────────────────────────
sealed class EntryBlock {
    data class TextBlock(val text: String) : EntryBlock()
    data class ImageBlock(val uri: Uri) : EntryBlock()
    data class AudioBlock(val uri: Uri) : EntryBlock()
    data class VideoBlock(val uri: Uri) : EntryBlock()
}

// ── Data models ───────────────────────────────────────────────────────────────
data class LifeEntry(
    val title: String,
    val category: String,
    val blocks: List<EntryBlock>,
    val createdAt: Long,
    val localId: Long = 0L
)

enum class AppLanguage {
    ENGLISH,
    SINHALA
}

// ── Color scheme ──────────────────────────────────────────────────────────────
private val LifeLoggerColorScheme = lightColorScheme(
    primary            = Color(0xFF90CAF9),
    onPrimary          = Color(0xFF102027),
    secondary          = Color(0xFF5800B6),
    onSecondary        = Color.White,
    tertiary           = Color(0xFF420281),
    onTertiary         = Color.White,
    background         = Color(0xFFF5F7FC),
    onBackground       = Color(0xFF17212B),
    surface            = Color.White,
    onSurface          = Color(0xFF17212B),
    surfaceVariant     = Color(0xFFF1F1F1),
    onSurfaceVariant   = Color(0xFF607080),
    error              = Color(0xFFE53935),
    onError            = Color.White
)

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        setContent {
            val container = remember { AppContainer(applicationContext) }
            MaterialTheme(colorScheme = LifeLoggerColorScheme) {
                AppRoot(container)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ROOT — Auth gate → Main app
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppRoot(container: AppContainer) {
    // Seed isLoggedIn from current Firebase Auth state so a user who was
    // already signed-in skips the login screen on re-launch.
    var isLoggedIn by remember { mutableStateOf(container.authRepository.isLoggedIn) }

    if (!isLoggedIn) {
        AuthNavHost(
            authRepository = container.authRepository,
            onAuthComplete = {
                isLoggedIn = true
            }
        )
    } else {
        LifeLoggerApp(container, onLogout = { isLoggedIn = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN APP  — repository-backed
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun LifeLoggerApp(container: AppContainer, onLogout: () -> Unit) {
    val uid = container.authRepository.currentUid ?: return
    val scope = rememberCoroutineScope()

    // ── Repository-backed state ──────────────────────────────────────────────
    val entries        by container.entryRepository.observeEntries(uid)
                              .collectAsState(initial = emptyList())
    val calendarNotes  by container.calendarNoteRepository.observeNotes(uid)
                              .collectAsState(initial = emptyList())
    val trashedRaw     by container.entryRepository.observeTrashedEntries(uid)
                              .collectAsState(initial = emptyList())
    val userProfile    by container.profileRepository.observeProfile(uid)
                              .collectAsState(initial = UserProfile())

    // Map trashed entries to the UI model (TrashedEntry wraps LifeEntry + timestamp).
    // We use updatedAt as deletedAt since that's what Room stamps on soft-delete.
    val trashedEntries = trashedRaw.map { it.toTrashedEntry() }

    // ── Navigation / editing state ───────────────────────────────────────────
    var screen       by remember { mutableStateOf("home") }
    // editingLocalId holds the Room localId of the entry being edited (null = new entry).
    var editingLocalId by remember { mutableStateOf<Long?>(null) }
    var language     by remember { mutableStateOf(AppLanguage.ENGLISH) }

    // Derive the LifeEntry currently being edited (for pre-filling AddEntryScreen).
    val editingEntry = editingLocalId?.let { id ->
        entries.find { it.localId == id }
    }

    val onLanguageToggle = {
        language = if (language == AppLanguage.ENGLISH) AppLanguage.SINHALA else AppLanguage.ENGLISH
    }

    // Pull remote data once right after the app opens (no-op if already synced).
    LaunchedEffect(uid) {
        scope.launch {
            runCatching {
                container.syncManager.pullEntries(uid)
                container.syncManager.pullCalendarNotes(uid)
            }
        }
    }

    // System back button: always return to home unless already there.
    BackHandler(enabled = screen != "home") {
        editingLocalId = null
        screen = "home"
    }

    when (screen) {

        "home" -> {
            HomeScreen(
                entries          = entries,
                language         = language,
                onLanguageToggle = onLanguageToggle,
                onAddClick       = { editingLocalId = null; screen = "add" },
                onEditClick      = { index ->
                    editingLocalId = entries[index].localId
                    screen = "add"
                },
                onDeleteClick    = { selectedIndexes ->
                    scope.launch {
                        val idsToTrash = selectedIndexes.map { entries[it].localId }
                        container.entryRepository.moveToTrash(idsToTrash)
                    }
                },
                onCalendarClick  = { screen = "calendar" },
                onProfileClick   = { screen = "profile" },
                onNavigate       = { route -> screen = route },
                onSyncClick      = { _ -> screen = "sync" },
                onOffline        = { _ -> screen = "offline" },
                onLogout         = {
                    container.authRepository.logout()
                    onLogout()
                }
            )
        }

        "add" -> {
            AddEntryScreen(
                existingEntry = editingEntry,
                language      = language,
                onSaveClick   = { entry ->
                    scope.launch {
                        if (editingLocalId == null) {
                            // New entry
                            container.entryRepository.addEntry(uid, entry)
                        } else {
                            container.entryRepository.updateEntry(uid, editingLocalId!!, entry)
                        }
                    }
                    editingLocalId = null
                    screen = "home"
                },
                onBackClick = { editingLocalId = null; screen = "home" }
            )
        }

        "calendar" -> {
            CalendarScreen(
                language       = language,
                notes          = calendarNotes,
                onAddNote      = { note ->
                    scope.launch {
                        container.calendarNoteRepository.upsertNote(uid, note)
                    }
                },
                onDeleteNote   = { note ->
                    // CalendarNote carries a local id (id field from Room via toCalendarNote())
                    scope.launch {
                        container.calendarNoteRepository.deleteNote(note.id.toLong())
                    }
                },
                onNavigateHome = { screen = "home" },
                onProfileClick = { screen = "profile" }
            )
        }

        "uploads" -> {
            UploadsScreen(
                entries     = entries,
                language    = language,
                onBackClick = { screen = "home" }
            )
        }

        "offline" -> {
            OfflineScreen(
                entries     = entries,
                language    = language,
                onBackClick = { screen = "home" }
            )
        }

        "sync" -> {
            SyncCenterScreen(
                language    = language,
                onBackClick = { screen = "home" },
                container   = container
            )
        }

        "trash" -> {
            TrashScreen(
                trashedEntries      = trashedEntries,
                language            = language,
                onRestoreEntries    = { selectedIndexes ->
                    scope.launch {
                        val toRestore = trashedRaw
                            .filterIndexed { i, _ -> i in selectedIndexes }
                            .map { it.localId }
                        container.entryRepository.restoreFromTrash(toRestore)
                    }
                },
                onDeletePermanently = { selectedIndexes ->
                    scope.launch {
                        val toDelete = trashedRaw
                            .filterIndexed { i, _ -> i in selectedIndexes }
                            .map { it.localId }
                        container.entryRepository.deletePermanently(toDelete)
                    }
                },
                onBackClick = { screen = "home" }
            )
        }

        "profile" -> {
            ProfileScreen(
                profile          = userProfile,
                entryCount       = entries.size,
                language         = language,
                onLanguageToggle = onLanguageToggle,
                onMenuClick      = { },
                onHomeClick      = { screen = "home" },
                onCalendarClick  = { screen = "calendar" },
                onSaveProfile    = { updated ->
                    scope.launch {
                        container.profileRepository.saveProfile(uid, updated)
                    }
                    screen = "home"
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STRING RESOURCES
// ─────────────────────────────────────────────────────────────────────────────
fun textOf(language: AppLanguage, key: String): String {
    return when (language) {
        AppLanguage.ENGLISH -> when (key) {
            "app_name"                  -> "Life Logger"
            "home"                      -> "Home"
            "calendar"                  -> "Calendar"
            "profile"                   -> "Profile"
            "no_entries"                -> "No entries yet"
            "add_new_entry"             -> "Add New Entry"
            "edit_entry"                -> "Edit Entry"
            "title"                     -> "Title"
            "add_text"                  -> "Add Text"
            "write_text"                -> "Write text"
            "add"                       -> "Add"
            "cancel"                    -> "Cancel"
            "add_image"                 -> "Add Image"
            "add_audio"                 -> "Add Audio"
            "add_video"                 -> "Add Video"
            "select_storage"            -> "Select from storage"
            "open_camera"               -> "Open camera"
            "record_audio"              -> "Record audio"
            "record_video"              -> "Record video"
            "custom_category"           -> "Enter custom category"
            "content_placeholder"       -> "Your entry content will appear here"
            "text"                      -> "Text"
            "image"                     -> "Image"
            "audio"                     -> "Audio"
            "video"                     -> "Video"
            "save_entry"                -> "Save Entry"
            "update_entry"              -> "Update Entry"
            "back"                      -> "Back"
            "recording"                 -> "Recording"
            "stop"                      -> "Stop"
            "up"                        -> "Up"
            "down"                      -> "Down"
            "delete"                    -> "Delete"
            "selected"                  -> "selected"
            "select_all"                -> "Select all"
            "unselect_all"              -> "Unselect all"
            "delete_selected"           -> "Delete selected entries?"
            "delete_message"            -> "This will permanently delete selected entry/entries."
            "no_title"                  -> "No title provided"
            "no_description"            -> "No description provided"
            "no_content"                -> "No content provided"
            "close"                     -> "Close"
            "edit"                      -> "Edit"
            "untitled"                  -> "Untitled Entry"
            "empty_illustration"        -> "No entries illustration"
            "exit_title"                -> "Exit App?"
            "exit_message"              -> "Are you sure you want to exit?"
            "exit_yes"                  -> "Exit"
            "exit_no"                   -> "Stay"
            "profile_name"              -> "Name"
            "profile_name_hint"         -> "Your name"
            "profile_email"             -> "Email"
            "profile_email_hint"        -> "your@email.com"
            "profile_bio"               -> "Bio"
            "profile_bio_hint"          -> "Tell us about yourself…"
            "storage_usage"             -> "Storage"
            "storage_used"              -> "used"
            "storage_free"              -> "free"
            "save_profile"              -> "Save Profile"
            "change_photo"              -> "Profile Photo"
            "choose_gallery"            -> "Choose from gallery"
            "take_photo"                -> "Take a photo"
            "remove_photo"              -> "Remove photo"
            "trash_title"               -> "Trash"
            "trash_empty"               -> "Trash is empty"
            "trash_empty_sub"           -> "Deleted entries will appear here"
            "trash_empty_all"           -> "Empty Trash"
            "trash_restore"             -> "Restore"
            "trash_restore_title"       -> "Restore entries?"
            "trash_restore_message"     -> "Selected entries will be moved back to your home feed."
            "trash_restore_confirm"     -> "Restore"
            "trash_delete_title"        -> "Delete permanently?"
            "trash_delete_message"      -> "This cannot be undone. Selected entries will be gone forever."
            "trash_delete_confirm"      -> "Delete Forever"
            "trash_empty_trash_title"   -> "Empty Trash?"
            "trash_empty_trash_message" -> "All entries will be permanently deleted. This cannot be undone."
            "trash_empty_trash_confirm" -> "Empty"
            "trash_days_ago"            -> "days ago"
            else -> key
        }

        AppLanguage.SINHALA -> when (key) {
            "app_name"                  -> "Life Logger"
            "home"                      -> "මුල් පිටුව"
            "calendar"                  -> "දින දර්ශනය"
            "profile"                   -> "පැතිකඩ"
            "no_entries"                -> "තවම සටහන් නැත"
            "add_new_entry"             -> "නව සටහනක් එක් කරන්න"
            "edit_entry"                -> "සටහන සංස්කරණය කරන්න"
            "title"                     -> "මාතෘකාව"
            "add_text"                  -> "පෙළ එක් කරන්න"
            "write_text"                -> "පෙළ ලියන්න"
            "add"                       -> "එක් කරන්න"
            "cancel"                    -> "අවලංගු කරන්න"
            "add_image"                 -> "රූපයක් එක් කරන්න"
            "add_audio"                 -> "ශ්‍රව්‍ය එක් කරන්න"
            "add_video"                 -> "වීඩියෝ එක් කරන්න"
            "select_storage"            -> "ගබඩාවෙන් තෝරන්න"
            "open_camera"               -> "කැමරාව විවෘත කරන්න"
            "record_audio"              -> "ශ්‍රව්‍ය පටිගත කරන්න"
            "record_video"              -> "වීඩියෝ පටිගත කරන්න"
            "custom_category"           -> "අභිරුචි වර්ගය ඇතුළත් කරන්න"
            "content_placeholder"       -> "ඔබගේ සටහන් අන්තර්ගතය මෙහි පෙන්වයි"
            "text"                      -> "පෙළ"
            "image"                     -> "රූප"
            "audio"                     -> "ශ්‍රව්‍ය"
            "video"                     -> "වීඩියෝ"
            "save_entry"                -> "සටහන සුරකින්න"
            "update_entry"              -> "සටහන යාවත්කාලීන කරන්න"
            "back"                      -> "ආපසු"
            "recording"                 -> "පටිගත වෙමින්"
            "stop"                      -> "නවත්වන්න"
            "up"                        -> "ඉහළට"
            "down"                      -> "පහළට"
            "delete"                    -> "මකන්න"
            "selected"                  -> "ඇත"
            "select_all"                -> "සියල්ල"
            "unselect_all"              -> "සියල්ල ඉවත්කරන්න"
            "delete_selected"           -> "තෝරාගත් සටහන් මකන්නද?"
            "delete_message"            -> "තෝරාගත් සටහන් ස්ථිරව මකා දමනු ලැබේ."
            "no_title"                  -> "මාතෘකාවක් නැත"
            "no_description"            -> "විස්තරයක් නැත"
            "no_content"                -> "අන්තර්ගතයක් නැත"
            "close"                     -> "වසන්න"
            "edit"                      -> "සංස්කරණය"
            "untitled"                  -> "නම් නොකළ සටහන"
            "empty_illustration"        -> "සටහන් නැත"
            "exit_title"                -> "යෙදුම් වසන්නද?"
            "exit_message"              -> "ඔබට ඉවත් වීමට අවශ්‍යද?"
            "exit_yes"                  -> "ඉවත් වන්න"
            "exit_no"                   -> "රැඳෙන්න"
            "profile_name"              -> "නාමය"
            "profile_name_hint"         -> "ඔබේ නම"
            "profile_email"             -> "විද්‍යුත් තැපෑල"
            "profile_email_hint"        -> "your@email.com"
            "profile_bio"               -> "හැඳින්වීම"
            "profile_bio_hint"          -> "ඔබ ගැන කෙටි හැඳින්වීමක්..."
            "storage_usage"             -> "ගබඩාව"
            "storage_used"              -> "භාවිත"
            "storage_free"              -> "නිදහස්"
            "save_profile"              -> "සුරකින්න"
            "change_photo"              -> "පැතිකඩ ඡායාරූපය"
            "choose_gallery"            -> "ගැලරියෙන් තෝරන්න"
            "take_photo"                -> "ඡායාරූපයක් ගන්න"
            "remove_photo"              -> "ඡායාරූපය ඉවත් කරන්න"
            "trash_title"               -> "කුණු බඳුන"
            "trash_empty"               -> "කුණු බඳුන හිස්ය"
            "trash_empty_sub"           -> "මකාදැමූ සටහන් මෙහි පෙන්වයි"
            "trash_empty_all"           -> "හිස් කරන්න"
            "trash_restore"             -> "නැවත ගන්න"
            "trash_restore_title"       -> "සටහන් ගලවා ගන්නද?"
            "trash_restore_message"     -> "තෝරාගත් සටහන් ගෙදර ලැයිස්තුවට යවනු ලැබේ."
            "trash_restore_confirm"     -> "ගලවා ගන්න"
            "trash_delete_title"        -> "ස්ථිරව මකන්නද?"
            "trash_delete_message"      -> "මෙය නොකළ හැක. සටහන් සදහටම ඉවත් වේ."
            "trash_delete_confirm"      -> "සදහටම මකන්න"
            "trash_empty_trash_title"   -> "කුණු බඳුන හිස් කරන්නද?"
            "trash_empty_trash_message" -> "සියලුම සටහන් ස්ථිරව මකා දමනු ලැබේ."
            "trash_empty_trash_confirm" -> "හිස් කරන්න"
            "trash_days_ago"            -> "දිනකට පෙර"
            else -> key
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UTILITY FUNCTIONS
// ─────────────────────────────────────────────────────────────────────────────
fun categoryLabel(category: String, language: AppLanguage): String {
    if (language == AppLanguage.ENGLISH) return category
    return when (category.lowercase()) {
        "study"      -> "අධ්‍යයනය"
        "workout"    -> "ව්‍යායාම"
        "event"      -> "සිදුවීම"
        "reflection" -> "සිතුවිලි"
        "other"      -> "වෙනත්"
        else         -> category
    }
}

fun formatTime(milliseconds: Int): String {
    val totalSeconds = milliseconds / 1000
    val minutes      = totalSeconds / 60
    val seconds      = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun formatDateTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
