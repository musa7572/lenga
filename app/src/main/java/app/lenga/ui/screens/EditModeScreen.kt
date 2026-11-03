package app.lenga.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusMode
import app.lenga.focus.ModeSchedule
import app.lenga.ui.components.AppPickerDialog
import app.lenga.ui.components.DateDialog
import app.lenga.ui.components.TimeDialog
import app.lenga.ui.components.rememberNotificationPermissionRequest
import app.lenga.ui.theme.AccentRed
import app.lenga.ui.theme.TextSecondary
import app.lenga.viewmodel.StudyViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private enum class EditDialog { FROM, TO, START_DATE, START_TIME, END_DATE, END_TIME, APPS }

/** Creates a weekly or one-off mode, or edits one when [modeId] is set. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditModeScreen(modeId: String?, viewModel: StudyViewModel, onDone: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val existing = remember(modeId) { modeId?.let { viewModel.mode(it) } }
    val apps by viewModel.launchableApps.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadLaunchableApps() }
    val requestNotifications = rememberNotificationPermissionRequest()

    val initialWeekly = existing?.schedule as? ModeSchedule.Weekly
    val initialOneOff = existing?.schedule as? ModeSchedule.OneOff
    val defaultStart = remember { LocalDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0) }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var weekly by remember { mutableStateOf(initialOneOff == null) }
    var days by remember { mutableStateOf(initialWeekly?.days ?: setOf(1, 2, 3, 4, 5)) }
    var fromMinute by remember { mutableIntStateOf(initialWeekly?.fromMinute ?: (8 * 60)) }
    var toMinute by remember { mutableIntStateOf(initialWeekly?.toMinute ?: (16 * 60)) }
    var start by remember { mutableStateOf(initialOneOff?.start?.let { toLocal(it, zone) } ?: defaultStart) }
    var end by remember { mutableStateOf(initialOneOff?.end?.let { toLocal(it, zone) } ?: defaultStart.plusHours(2)) }
    var allowed by remember { mutableStateOf(existing?.allowedPackages ?: emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<EditDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New mode" else "Edit mode") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Weekly") })
                FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("One-off") })
            }
            if (weekly) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.values().forEach { day ->
                        FilterChip(
                            selected = day.value in days,
                            onClick = { days = if (day.value in days) days - day.value else days + day.value },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dialog = EditDialog.FROM }) { Text("From ${FocusFormat.minuteOfDay(fromMinute)}") }
                    OutlinedButton(onClick = { dialog = EditDialog.TO }) { Text("To ${FocusFormat.minuteOfDay(toMinute)}") }
                }
                if (toMinute <= fromMinute) {
                    Text("Ends the next day", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            } else {
                DateTimeRow("Start", start, onDate = { dialog = EditDialog.START_DATE }, onTime = { dialog = EditDialog.START_TIME })
                DateTimeRow("End", end, onDate = { dialog = EditDialog.END_DATE }, onTime = { dialog = EditDialog.END_TIME })
            }
            OutlinedButton(onClick = { dialog = EditDialog.APPS }) { Text("Allowed apps (${allowed.size})") }
            error?.let { Text(it, color = AccentRed) }
            Button(
                onClick = {
                    val schedule = if (weekly) {
                        ModeSchedule.Weekly(days, fromMinute, toMinute)
                    } else {
                        ModeSchedule.OneOff(
                            start.atZone(zone).toInstant().toEpochMilli(),
                            end.atZone(zone).toInstant().toEpochMilli()
                        )
                    }
                    val mode = FocusMode(existing?.id ?: UUID.randomUUID().toString(), name.trim(), schedule, allowed)
                    viewModel.saveMode(mode) { saveError ->
                        if (saveError == null) {
                            requestNotifications()
                            onDone()
                        } else {
                            error = saveError
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
            if (existing != null) {
                OutlinedButton(
                    onClick = {
                        viewModel.deleteMode(existing.id) { deleteError ->
                            if (deleteError == null) onDone() else error = deleteError
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
                ) { Text("Delete") }
            }
        }
    }

    when (dialog) {
        EditDialog.FROM -> TimeDialog(fromMinute, onDismiss = { dialog = null }) { fromMinute = it; dialog = null }
        EditDialog.TO -> TimeDialog(toMinute, onDismiss = { dialog = null }) { toMinute = it; dialog = null }
        EditDialog.START_DATE -> DateDialog(start.toLocalDate(), onDismiss = { dialog = null }) {
            start = it.atTime(start.toLocalTime()); dialog = null
        }
        EditDialog.START_TIME -> TimeDialog(start.hour * 60 + start.minute, onDismiss = { dialog = null }) {
            start = start.toLocalDate().atTime(it / 60, it % 60); dialog = null
        }
        EditDialog.END_DATE -> DateDialog(end.toLocalDate(), onDismiss = { dialog = null }) {
            end = it.atTime(end.toLocalTime()); dialog = null
        }
        EditDialog.END_TIME -> TimeDialog(end.hour * 60 + end.minute, onDismiss = { dialog = null }) {
            end = end.toLocalDate().atTime(it / 60, it % 60); dialog = null
        }
        EditDialog.APPS -> AppPickerDialog(
            apps = apps,
            essentials = viewModel.essentialPackages,
            initialSelection = allowed,
            onDismiss = { dialog = null },
            onConfirm = { allowed = it; dialog = null }
        )
        null -> Unit
    }
}

@Composable
private fun DateTimeRow(label: String, value: LocalDateTime, onDate: () -> Unit, onTime: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(48.dp))
        OutlinedButton(onClick = onDate) { Text(FocusFormat.date(value.toLocalDate())) }
        OutlinedButton(onClick = onTime) { Text(FocusFormat.minuteOfDay(value.hour * 60 + value.minute)) }
    }
}

private fun toLocal(epochMillis: Long, zone: ZoneId): LocalDateTime =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
