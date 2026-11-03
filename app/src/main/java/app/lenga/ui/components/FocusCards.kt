package app.lenga.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusStatus
import app.lenga.ui.theme.StudyModeActive
import app.lenga.ui.theme.TextSecondary
import java.time.ZoneId

/** Dashboard banner while a focus mode is active. */
@Composable
fun FocusBanner(status: FocusStatus) {
    val zone = remember { ZoneId.systemDefault() }
    val endsAt = status.endsAt ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StudyModeActive)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Focus mode on", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                "${status.active.joinToString { it.mode.name }} · until ${FocusFormat.clock(endsAt, zone)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                "${FocusFormat.remaining(endsAt - status.now)} left",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (status.bankedMillis > 0) {
                Text(
                    "Free time paused: ${FocusFormat.remaining(status.bankedMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/** Dashboard card for starting a timed session or detox day when no mode is active. */
@Composable
fun StartFocusCard(onTimed: () -> Unit, onDetox: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Start focus", style = MaterialTheme.typography.titleMedium)
            Text(
                "Lock distracting apps for a set time. You can't end it early.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTimed) { Text("Timed session") }
                OutlinedButton(onClick = onDetox) { Text("Detox day") }
            }
        }
    }
}
