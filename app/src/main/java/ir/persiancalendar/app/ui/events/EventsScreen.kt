package ir.persiancalendar.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.data.db.EventEntity
import ir.persiancalendar.app.data.model.RepeatType
import ir.persiancalendar.app.ui.theme.EventDotColors
import java.time.LocalDate

/**
 * فهرست همه رویدادهای کاربر (گروه‌بندی‌شده بر اساس تاریخ).
 */
@Composable
fun EventsScreen(
    events: List<EventEntity>,
    persianDigits: Boolean,
    onEdit: (EventEntity) -> Unit,
    onAdd: () -> Unit,
    onDelete: (EventEntity) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<EventEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "رویداد جدید")
            }
        }
    ) { padding ->
        if (events.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("هنوز رویدادی ندارید", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "با دکمه + اولین رویداد، تولد یا قرار خود را اضافه کنید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val sorted = events.sortedWith(
                compareBy({ it.gy }, { it.gm }, { it.gd }, { it.hour })
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                items(sorted, key = { it.id }) { e ->
                    EventRow(
                        event = e,
                        persianDigits = persianDigits,
                        onEdit = { onEdit(e) },
                        onDelete = { pendingDelete = e }
                    )
                }
            }
        }
    }

    pendingDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف رویداد") },
            text = { Text("«${e.title}» حذف شود؟") },
            confirmButton = {
                TextButton(onClick = { onDelete(e); pendingDelete = null }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun EventRow(
    event: EventEntity,
    persianDigits: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val today = LocalDate.now()
    val eventDate = LocalDate.of(event.gy, event.gm, event.gd)
    val past = eventDate.isBefore(today)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    EventDotColors[event.colorIndex % EventDotColors.size].copy(alpha = 0.16f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (persianDigits) PersianText.toPersianDigits(event.jd.toString()) else event.jd.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = EventDotColors[event.colorIndex % EventDotColors.size]
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append(PersianText.jalaliDateShort(
                        CalendarConverter.gregorianToJalali(event.gy, event.gm, event.gd),
                        persianDigits
                    ))
                    if (event.hour >= 0) {
                        append(" - ")
                        append(PersianText.timeHM(event.hour, event.minute, persianDigits))
                    }
                    val rep = RepeatType.fromOrdinal(event.repeatType)
                    if (rep != RepeatType.NONE) append(" (${rep.label})")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
        }
    }
}
