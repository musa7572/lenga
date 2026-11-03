package app.lenga.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lenga.data.apps.LaunchableApp
import app.lenga.ui.theme.TextSecondary

/**
 * Full-screen, searchable app list. [essentials] are shown checked and can't be changed.
 * [onConfirm] receives the chosen packages, without the essentials.
 */
@Composable
fun AppPickerDialog(
    apps: List<LaunchableApp>?,
    essentials: Set<String>,
    initialSelection: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selected by remember { mutableStateOf(initialSelection) }
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Allowed apps", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                if (apps == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val visible = apps.filter { it.label.contains(query, ignoreCase = true) }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(visible, key = { it.packageName }) { app ->
                            val essential = app.packageName in essentials
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !essential) {
                                        selected = if (app.packageName in selected) {
                                            selected - app.packageName
                                        } else {
                                            selected + app.packageName
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(app.label)
                                    if (essential) {
                                        Text("Always allowed", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                                Checkbox(
                                    checked = essential || app.packageName in selected,
                                    onCheckedChange = null,
                                    enabled = !essential
                                )
                            }
                        }
                    }
                }
                val chosen = selected - essentials
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onConfirm(chosen) }) { Text("Done (${chosen.size})") }
                }
            }
        }
    }
}
