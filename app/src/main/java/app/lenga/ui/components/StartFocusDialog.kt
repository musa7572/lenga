package app.lenga.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.lenga.data.apps.LaunchableApp
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusKind
import app.lenga.focus.FocusMode
import app.lenga.focus.ModeSchedule
import app.lenga.ui.theme.AccentRed
import java.time.LocalDate
import java.time.ZoneId

private const val CUSTOM_LIST = "custom"

/**
 * Sets up a timed session or detox day. [onStart] receives the end time, the allowed apps,
 * and a callback for showing an error in the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartFocusDialog(
    kind: FocusKind,
    savedModes: List<FocusMode>,
    apps: List<LaunchableApp>?,
    essentials: Set<String>,
    onDismiss: () -> Unit,
    onStart: (end: Long, allowed: Set<String>, onError: (String) -> Unit) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    var minutes by remember { mutableIntStateOf(60) }
    var customText by remember { mutableStateOf("") }
    var detoxLastDay by remember { mutableStateOf(LocalDate.now(zone)) }
    var allowed by remember { mutableStateOf(emptySet<String>()) }
    // Null = essentials only, a mode id = copied from that mode, CUSTOM_LIST = edited by hand
    var listSource by remember { mutableStateOf<String?>(null) }
    var pickingApps by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val listModes = savedModes.filter { it.schedule is ModeSchedule.Weekly || it.schedule is ModeSchedule.OneOff }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == FocusKind.TIMED) "Timed session" else "Detox day") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (kind == FocusKind.TIMED) {
                    Text("Length", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 120).forEach { preset ->
                            FilterChip(
                                selected = customText.isEmpty() && minutes == preset,
                                onClick = { minutes = preset; customText = "" },
                                label = { Text(FocusFormat.remaining(preset * 60_000L)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { text ->
                            customText = text.filter(Char::isDigit).take(4)
                            customText.toIntOrNull()?.let { minutes = it }
                        },
                        label = { Text("Custom length (minutes)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                } else {
                    Text("Until the end of ${FocusFormat.date(detoxLastDay)}")
                    TextButton(onClick = { pickingDate = true }) { Text("Change date") }
                }

                Text("Allowed apps", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = listSource == null,
                        onClick = { listSource = null; allowed = emptySet() },
                        label = { Text("Essentials only") }
                    )
                    listModes.forEach { mode ->
                        FilterChip(
                            selected = listSource == mode.id,
                            onClick = { listSource = mode.id; allowed = mode.allowedPackages },
                            label = { Text(mode.name) }
                        )
                    }
                }
                TextButton(onClick = { pickingApps = true }) { Text("Edit apps (${allowed.size})") }

                Text(
                    "You can't end this early. Emergency unlock (2 a day) still works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentRed
                )
                error?.let { Text(it, color = AccentRed, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (kind == FocusKind.TIMED && minutes <= 0) {
                    error = "Enter a length in minutes."
                    return@Button
                }
                val end = if (kind == FocusKind.TIMED) {
                    System.currentTimeMillis() + minutes * 60_000L
                } else {
                    detoxLastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                }
                onStart(end, allowed) { error = it }
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickingApps) {
        AppPickerDialog(
            apps = apps,
            essentials = essentials,
            initialSelection = allowed,
            onDismiss = { pickingApps = false },
            onConfirm = {
                allowed = it
                listSource = CUSTOM_LIST
                pickingApps = false
            }
        )
    }
    if (pickingDate) {
        DateDialog(
            initial = detoxLastDay,
            onDismiss = { pickingDate = false },
            onConfirm = {
                detoxLastDay = maxOf(it, LocalDate.now(zone))
                pickingDate = false
            }
        )
    }
}
