package com.thoughtdrop.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thoughtdrop.app.data.model.NoteEntity
import com.thoughtdrop.app.ui.theme.*
import com.thoughtdrop.app.ui.util.CalendarUtils

enum class SchedulePreset(val label: String, val subtitle: String) {
    TOMORROW_MORNING("Tomorrow Morning", "9:00 AM"),
    TODAY_EVENING("This Evening", "6:00 PM"),
    IN_TWO_DAYS("In 2 Days", "10:00 AM")
}

@Composable
fun ScheduleTaskDialog(
    taskItem: String,
    parentNote: NoteEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedPreset by remember { mutableStateOf(SchedulePreset.TOMORROW_MORNING) }
    val isNoteItself = taskItem.trim() == parentNote.title.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = AccentSky.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AccentSky.copy(alpha = 0.3f)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = AccentSky,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isNoteItself) "Schedule Note Reminder" else "Schedule Action Item",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "1-Tap Calendar or Alarm",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Item card preview
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isNoteItself) "💡 $taskItem" else "☑ $taskItem",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            lineHeight = 18.sp
                        )
                        if (parentNote.summary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = parentNote.summary,
                                fontSize = 11.5.sp,
                                color = TextMuted,
                                maxLines = 2,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "SELECT REMINDER TIME",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Presets list
                SchedulePreset.values().forEach { preset ->
                    val isSelected = selectedPreset == preset
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) StudioSurfaceHighlight else StudioSurface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AccentViolet.copy(alpha = 0.6f) else StudioBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.5.dp)
                            .clickable { selectedPreset = preset }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedPreset = preset },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentViolet,
                                    unselectedColor = TextMuted
                                ),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = preset.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = preset.subtitle,
                                fontSize = 11.5.sp,
                                color = if (isSelected) AccentSky else TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Calendar vs Alarm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Google Calendar Button
                    Button(
                        onClick = {
                            val targetMillis = when (selectedPreset) {
                                SchedulePreset.TOMORROW_MORNING -> CalendarUtils.getTomorrowMorningMillis()
                                SchedulePreset.TODAY_EVENING -> CalendarUtils.getTodayEveningMillis()
                                SchedulePreset.IN_TWO_DAYS -> CalendarUtils.getInTwoDaysMillis()
                            }
                            CalendarUtils.addToCalendar(
                                context = context,
                                taskTitle = taskItem,
                                noteTitle = parentNote.title,
                                noteSummary = parentNote.summary,
                                startTimeMillis = targetMillis
                            )
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add to Calendar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Alarm Button
                    OutlinedButton(
                        onClick = {
                            val (hour, minute) = when (selectedPreset) {
                                SchedulePreset.TOMORROW_MORNING -> Pair(9, 0)
                                SchedulePreset.TODAY_EVENING -> Pair(18, 0)
                                SchedulePreset.IN_TWO_DAYS -> Pair(10, 0)
                            }
                            CalendarUtils.setDeviceAlarm(
                                context = context,
                                taskTitle = taskItem,
                                hour = hour,
                                minute = minute
                            )
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Icon(
                            Icons.Outlined.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = AccentAmber
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Set Alarm",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextSecondary)
            }
        }
    )
}
