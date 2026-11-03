package app.lenga.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.focus.ActiveMode
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusMode
import app.lenga.focus.FocusPolicy
import app.lenga.focus.ModeSchedule
import app.lenga.ui.theme.AccentRed
import app.lenga.ui.theme.TextSecondary
import app.lenga.viewmodel.StudyViewModel
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModesScreen(
    viewModel: StudyViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit
) {
    val load by viewModel.modesLoad.collectAsStateWithLifecycle()
    val status by viewModel.focusStatus.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val now = status?.now ?: System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Add mode") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (load?.corrupt == true) {
                item { Text("Saved modes couldn't be read and were reset.", color = AccentRed) }
            }
            val modes = load?.modes.orEmpty()
            if (load != null && modes.isEmpty()) {
                item {
                    Text("No modes yet. Add a weekly schedule or a one-off block.", color = TextSecondary)
                }
            }
            items(modes, key = { it.id }) { mode ->
                val active = status?.active?.find { it.mode.id == mode.id }
                val editable = active == null &&
                    (mode.schedule is ModeSchedule.Weekly || mode.schedule is ModeSchedule.OneOff)
                Card(modifier = Modifier.fillMaxWidth().clickable(enabled = editable) { onEdit(mode.id) }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                FocusFormat.schedule(mode.schedule, zone),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(statusLine(mode, active, now, zone), style = MaterialTheme.typography.bodySmall)
                        }
                        if (active != null) Icon(Icons.Default.Lock, contentDescription = "Running")
                    }
                }
            }
        }
    }
}

private fun statusLine(mode: FocusMode, active: ActiveMode?, now: Long, zone: ZoneId): String = when {
    active != null -> "Running until ${FocusFormat.clock(active.end, zone)}"
    else -> FocusPolicy.nextStart(mode.schedule, now, zone)
        ?.let { "Next: ${FocusFormat.dayTime(it, zone)}" }
        ?: "Ended"
}
