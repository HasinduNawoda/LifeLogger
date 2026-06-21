package com.kdu.mc.lifelogger

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

fun dateKeyOf(year: Int, month: Int, day: Int): String =
    "%04d-%02d-%02d".format(year, month + 1, day)

fun friendlyDate(dateKey: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val out = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        out.format(sdf.parse(dateKey)!!)
    } catch (e: Exception) { dateKey }
}

fun friendlyTime(millis: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}

// ─── CalendarScreen ───────────────────────────────────────────────────────────

@Composable
fun CalendarScreen(
    language: AppLanguage,
    notes: List<CalendarNote>,
    onAddNote: (CalendarNote) -> Unit,
    onDeleteNote: (CalendarNote) -> Unit,
    onNavigateHome: () -> Unit,
    onProfileClick: () -> Unit           // ← ADDED
) {
    val context = LocalContext.current

    var isMonthlyView by remember { mutableStateOf(true) }
    val today = remember { Calendar.getInstance() }
    var displayYear by remember { mutableStateOf(today.get(Calendar.YEAR)) }
    var displayMonth by remember { mutableStateOf(today.get(Calendar.MONTH)) }
    var displayWeekStart by remember { mutableStateOf(getWeekStart(today)) }
    var selectedDateKey by remember { mutableStateOf(todayKey()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var viewingNote by remember { mutableStateOf<CalendarNote?>(null) }

    val hasNotifPermission = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPermission.value = granted }

    val exactAlarmOk = remember { mutableStateOf(canScheduleExactAlarms(context)) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission.value) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        exactAlarmOk.value = canScheduleExactAlarms(context)
    }

    Scaffold(
        topBar = {
            CalendarTopBar(
                language = language,
                isMonthlyView = isMonthlyView,
                onToggleView = { isMonthlyView = !isMonthlyView },
                onBack = onNavigateHome
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(AppGradient, CircleShape)
                    .clickable { showAddDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.add),
                    contentDescription = "Add note",
                    modifier = Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
        },
        bottomBar = {
            AppBottomBar(
                currentScreen = "calendar",
                onHomeClick = onNavigateHome,
                onCalendarClick = {},
                onProfileClick = onProfileClick,
                language = language
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Exact alarm warning banner ──
            if (!exactAlarmOk.value) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3E0))
                        .clickable {
                            openExactAlarmSettings(context)
                            exactAlarmOk.value = canScheduleExactAlarms(context)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Reminders may not work",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            "Tap here to allow exact alarms in Settings",
                            fontSize = 11.sp,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            // ── Calendar section ──
            if (isMonthlyView) {
                MonthlyCalendar(
                    year = displayYear,
                    month = displayMonth,
                    selectedDateKey = selectedDateKey,
                    notes = notes,
                    onDayClick = { key -> selectedDateKey = key },
                    onPrevMonth = {
                        if (displayMonth == 0) { displayMonth = 11; displayYear-- }
                        else displayMonth--
                    },
                    onNextMonth = {
                        if (displayMonth == 11) { displayMonth = 0; displayYear++ }
                        else displayMonth++
                    }
                )
            } else {
                WeeklyCalendar(
                    weekStart = displayWeekStart,
                    selectedDateKey = selectedDateKey,
                    notes = notes,
                    onDayClick = { key -> selectedDateKey = key },
                    onPrevWeek = { displayWeekStart = shiftWeek(displayWeekStart, -7) },
                    onNextWeek = { displayWeekStart = shiftWeek(displayWeekStart, 7) }
                )
            }

            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // ── Notes section header ──
            val dayNotes = notes.filter { it.dateKey == selectedDateKey }
            Text(
                text = "📅  ${friendlyDate(selectedDateKey)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E5BFF),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            // ── Notes list ──
            if (dayNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No notes for this day", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dayNotes) { note ->
                        NoteListItem(
                            note = note,
                            onClick = { viewingNote = note },
                            onDelete = {
                                cancelReminder(context, note.id)
                                onDeleteNote(note)
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Add note dialog ──
    if (showAddDialog) {
        AddNoteDialog(
            initialDateKey = selectedDateKey,
            language = language,
            onDismiss = { showAddDialog = false },
            onConfirm = { note ->
                onAddNote(note)
                note.reminderTimeMillis?.let {
                    scheduleReminder(context, note.id, note.title, it)
                }
                showAddDialog = false
            }
        )
    }

    // ── Note detail dialog ──
    viewingNote?.let { note ->
        NoteDetailDialog(
            note = note,
            onDismiss = { viewingNote = null },
            onDelete = {
                cancelReminder(context, note.id)
                onDeleteNote(note)
                viewingNote = null
            }
        )
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
fun CalendarTopBar(
    language: AppLanguage,
    isMonthlyView: Boolean,
    onToggleView: () -> Unit,
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
            text = textOf(language, "calendar"),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                .padding(3.dp)
        ) {
            TogglePill(label = "Monthly", selected = isMonthlyView, onClick = { if (!isMonthlyView) onToggleView() })
            TogglePill(label = "Weekly", selected = !isMonthlyView, onClick = { if (isMonthlyView) onToggleView() })
        }
    }
}

@Composable
fun TogglePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "pill")
    val textColor by animateColorAsState(if (selected) Color(0xFF6A35FF) else Color.White, label = "pillText")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Monthly Calendar ─────────────────────────────────────────────────────────

@Composable
fun MonthlyCalendar(
    year: Int,
    month: Int,
    selectedDateKey: String,
    notes: List<CalendarNote>,
    onDayClick: (String) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        .format(Calendar.getInstance().apply { set(year, month, 1) }.time)

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", fontSize = 26.sp, color = Color(0xFF6A35FF),
                modifier = Modifier
                    .clickable { onPrevMonth() }
                    .padding(8.dp))
            Text(monthName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("›", fontSize = 26.sp, color = Color(0xFF6A35FF),
                modifier = Modifier
                    .clickable { onNextMonth() }
                    .padding(8.dp))
        }

        val dayHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        Row(modifier = Modifier.fillMaxWidth()) {
            dayHeaders.forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        val cal = Calendar.getInstance().apply { set(year, month, 1) }
        val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = firstDow + daysInMonth

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
            userScrollEnabled = false
        ) {
            items(cells) { index ->
                if (index < firstDow) {
                    Box(modifier = Modifier.size(44.dp))
                } else {
                    val day = index - firstDow + 1
                    val key = dateKeyOf(year, month, day)
                    val dotColors = notes.filter { it.dateKey == key }.take(3).map { it.colorLabel.color }
                    DayCell(
                        day = day,
                        isSelected = key == selectedDateKey,
                        isToday = key == todayKey(),
                        dotColors = dotColors,
                        onClick = { onDayClick(key) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun DayCell(day: Int, isSelected: Boolean, isToday: Boolean, dotColors: List<Color>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(2.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> Color(0xFF6A35FF)
                    isToday -> Color(0xFF1E5BFF).copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$day",
            fontSize = 14.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> Color.White
                isToday -> Color(0xFF1E5BFF)
                else -> MaterialTheme.colorScheme.onBackground
            }
        )
        if (dotColors.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                dotColors.forEach { c ->
                    Box(modifier = Modifier
                        .size(5.dp)
                        .background(
                            if (isSelected) Color.White else c, CircleShape
                        ))
                }
            }
        }
    }
}

// ─── Weekly Calendar ──────────────────────────────────────────────────────────

fun getWeekStart(cal: Calendar): Calendar {
    val c = cal.clone() as Calendar
    c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    return c
}

fun shiftWeek(cal: Calendar, days: Int): Calendar {
    val c = cal.clone() as Calendar
    c.add(Calendar.DAY_OF_MONTH, days)
    return c
}

@Composable
fun WeeklyCalendar(
    weekStart: Calendar,
    selectedDateKey: String,
    notes: List<CalendarNote>,
    onDayClick: (String) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val weekLabel = run {
        val end = shiftWeek(weekStart, 6)
        val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
        val fmtY = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        "${fmt.format(weekStart.time)} – ${fmtY.format(end.time)}"
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", fontSize = 26.sp, color = Color(0xFF6A35FF),
                modifier = Modifier
                    .clickable { onPrevWeek() }
                    .padding(8.dp))
            Text(weekLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("›", fontSize = 26.sp, color = Color(0xFF6A35FF),
                modifier = Modifier
                    .clickable { onNextWeek() }
                    .padding(8.dp))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            for (i in 0..6) {
                val dayCal = shiftWeek(weekStart, i)
                val key = sdf.format(dayCal.time)
                val isSelected = key == selectedDateKey
                val isToday = key == todayKey()
                val dotColors = notes.filter { it.dateKey == key }.take(3).map { it.colorLabel.color }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isSelected -> Color(0xFF6A35FF)
                                isToday -> Color(0xFF1E5BFF).copy(alpha = 0.10f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onDayClick(key) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(dayNames[i], fontSize = 10.sp,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dayCal.get(Calendar.DAY_OF_MONTH).toString(),
                        fontSize = 15.sp,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> Color.White
                            isToday -> Color(0xFF1E5BFF)
                            else -> MaterialTheme.colorScheme.onBackground
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        dotColors.forEach { c ->
                            Box(modifier = Modifier
                                .size(5.dp)
                                .background(
                                    if (isSelected) Color.White else c, CircleShape
                                ))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Note List Item ───────────────────────────────────────────────────────────

@Composable
fun NoteListItem(note: CalendarNote, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 72.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(46.dp)
                    .background(note.colorLabel.color, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(note.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (note.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(note.description, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (note.reminderTimeMillis != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(friendlyTime(note.reminderTimeMillis),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(note.colorLabel.color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(note.colorLabel.label, fontSize = 11.sp, color = note.colorLabel.color,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter = painterResource(id = R.drawable.bin),
                contentDescription = "Delete",
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onDelete() },
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            )
        }
    }
}

// ─── Note Detail Dialog ───────────────────────────────────────────────────────

@Composable
fun NoteDetailDialog(
    note: CalendarNote,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(note.colorLabel.color, RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(note.title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📅", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(friendlyDate(note.dateKey), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (note.reminderTimeMillis != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(friendlyTime(note.reminderTimeMillis), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier
                        .size(12.dp)
                        .background(note.colorLabel.color, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(note.colorLabel.label, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (note.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(note.description, fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                    ) {
                        Text("Delete")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .background(AppGradient, RoundedCornerShape(14.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Add Note Dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNoteDialog(
    initialDateKey: String,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (CalendarNote) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(NoteColor.PURPLE) }
    var reminderTime by remember { mutableStateOf<Long?>(null) }
    val dateKey by remember { mutableStateOf(initialDateKey) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(AppGradient, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Add Note — ${friendlyDate(dateKey)}",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 3,
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Color label", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoteColor.entries.forEach { nc ->
                        val isSelected = selectedColor == nc
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(nc.color)
                                .then(
                                    if (isSelected) Modifier
                                        .border(3.dp, Color.White, CircleShape)
                                        .border(4.dp, nc.color.copy(alpha = 0.5f), CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = nc }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Reminder time", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (reminderTime != null) Color(0xFF6A35FF).copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                val parts = dateKey.split("-")
                                val yr = parts[0].toInt()
                                val mo = parts[1].toInt() - 1
                                val dy = parts[2].toInt()
                                val cal = Calendar.getInstance()
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        cal.set(yr, mo, dy, hour, minute, 0)
                                        cal.set(Calendar.MILLISECOND, 0)
                                        reminderTime = cal.timeInMillis
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (reminderTime != null) "🔔 ${friendlyTime(reminderTime!!)}" else "Tap to set a reminder",
                            color = if (reminderTime != null) Color(0xFF6A35FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = if (reminderTime != null) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    if (reminderTime != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { reminderTime = null })
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .background(
                                if (title.isNotBlank()) AppGradient
                                else androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Color.Gray, Color.Gray)
                                ),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = title.isNotBlank()) {
                                onConfirm(
                                    CalendarNote(
                                        id = System.currentTimeMillis().toInt(),
                                        dateKey = dateKey,
                                        title = title.trim(),
                                        description = description.trim(),
                                        colorLabel = selectedColor,
                                        reminderTimeMillis = reminderTime
                                    )
                                )
                            }
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Add Note", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
