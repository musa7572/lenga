package app.lenga.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.ui.theme.*
import app.lenga.ui.components.*
import app.lenga.viewmodel.StudyViewModel
import kotlinx.coroutines.delay

@Composable
fun RewardScreen(viewModel: StudyViewModel, onTimeUp: () -> Unit) {
    // Free time is an absolute end time, so read it from the clock rather than counting down locally
    var rewardTime by remember { mutableIntStateOf(viewModel.freeTimeRemainingSeconds()) }
    val totalTime = viewModel.freeTimeTotalSeconds().coerceAtLeast(1)
    var showWarning by remember { mutableStateOf(false) }
    var warned by remember { mutableStateOf(rewardTime <= 60) }

    val minutes = rewardTime / 60
    val seconds = rewardTime % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)

    LaunchedEffect(Unit) {
        while (rewardTime > 0) {
            delay(1000)
            rewardTime = viewModel.freeTimeRemainingSeconds()
            if (rewardTime in 1..60 && !warned) {
                warned = true
                showWarning = true
            }
        }
        onTimeUp()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = listOf(SuccessGreen.copy(alpha = 0.1f), Background)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text("🎉 Free Time!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
            Text("Your apps are now unlocked", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)

            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier.size(280.dp).clip(CircleShape).background(Color.White).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = (rewardTime.toFloat() / totalTime).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxSize(),
                    color = if (rewardTime < 60) AccentRed else SuccessGreen,
                    strokeWidth = 12.dp,
                    trackColor = Color(0xFFE2E8F0)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = timeString, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = if (rewardTime < 60) AccentRed else TextPrimary)
                    Text("remaining", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickActionButton(Icons.Default.Message, "Messages") {}
                QuickActionButton(Icons.Default.Call, "Phone") {}
                QuickActionButton(Icons.Default.Camera, "Camera") {}
                QuickActionButton(Icons.Default.Apps, "All Apps") {}
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    viewModel.endFreeTime()
                    onTimeUp()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("End Free Time Early")
            }
        }

        if (showWarning) {
            AlertDialog(
                onDismissRequest = { showWarning = false },
                icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                title = { Text("1 Minute Left!") },
                text = { Text("Your free time is almost over. Apps will be locked soon.") },
                confirmButton = { Button(onClick = { showWarning = false }) { Text("Got it") } }
            )
        }
    }
}

@Composable
fun QuickActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = SuccessGreen.copy(alpha = 0.1f), contentColor = SuccessGreen)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}