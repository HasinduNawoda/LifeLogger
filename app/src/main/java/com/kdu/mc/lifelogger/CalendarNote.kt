package com.kdu.mc.lifelogger

import androidx.compose.ui.graphics.Color

enum class NoteColor(val color: Color, val label: String) {
    RED(Color(0xFFE53935), "Red"),
    ORANGE(Color(0xFFFB8C00), "Orange"),
    YELLOW(Color(0xFFFDD835), "Yellow"),
    GREEN(Color(0xFF43A047), "Green"),
    BLUE(Color(0xFF1E5BFF), "Blue"),
    PURPLE(Color(0xFF6A35FF), "Purple"),
    PINK(Color(0xFFD81B60), "Pink"),
}

data class CalendarNote(
    val id: Int,
    val dateKey: String,
    val title: String,
    val description: String = "",       // ← NEW
    val colorLabel: NoteColor,
    val reminderTimeMillis: Long?,
)
