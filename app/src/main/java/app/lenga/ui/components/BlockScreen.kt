package app.lenga.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lenga.data.model.EmergencyUnlockPolicy
import app.lenga.data.repository.EmergencyUnlockResult
import app.lenga.data.repository.EmergencyUnlocker
import app.lenga.focus.BlockText
import app.lenga.ui.theme.AccentRed
import app.lenga.ui.theme.PrimaryBlue
import app.lenga.ui.theme.PrimaryDark
import app.lenga.ui.theme.TextSecondary

private enum class BlockPage { LOCK, PIN }

/**
 * The "Stay focused" overlay over a blocked app (PRD section 35). [pinSet] and [unlocksLeft]
 * are null until loaded. [onUnlock] checks a PIN and reports the result.
 */
@Composable
fun BlockScreen(
    text: BlockText,
    pinSet: Boolean?,
    unlocksLeft: Int?,
    onClose: () -> Unit,
    onOpenLenga: () -> Unit,
    onUnlock: (pin: String, onResult: (EmergencyUnlockResult) -> Unit) -> Unit
) {
    // A different blocked app starts again on the lock page
    var page by remember(text.appLabel) { mutableStateOf(BlockPage.LOCK) }
    val backToLock = { page = BlockPage.LOCK }

    when (page) {
        BlockPage.LOCK -> LockPage(text, onClose, onOpenLenga, onEmergency = { page = BlockPage.PIN })
        BlockPage.PIN -> Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
                when {
                    pinSet == null || unlocksLeft == null -> CircularProgressIndicator()
                    !pinSet -> MessagePage(
                        title = "No emergency PIN set",
                        body = "Create one in Lenga to use emergency unlock.",
                        primaryLabel = "Open Lenga",
                        onPrimary = onOpenLenga,
                        onBack = backToLock
                    )
                    unlocksLeft == 0 -> MessagePage(
                        title = "Daily limit reached",
                        body = "You've used all ${EmergencyUnlockPolicy.DAILY_LIMIT} emergency unlocks today. " +
                            "Your phone and messaging apps still work.",
                        primaryLabel = null,
                        onPrimary = {},
                        onBack = backToLock
                    )
                    else -> UnlockPage(unlocksLeft, onUnlock, onBack = backToLock)
                }
            }
        }
    }
}

@Composable
private fun LockPage(text: BlockText, onClose: () -> Unit, onOpenLenga: () -> Unit, onEmergency: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PrimaryDark, PrimaryBlue)))
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Text("Stay focused", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "${text.appLabel} is locked",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Text(
                text.reason,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            text.remaining?.let {
                Text(it, style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PrimaryBlue)
            ) { Text("Close app") }
            OutlinedButton(
                onClick = onOpenLenga,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text("Open Lenga") }
            TextButton(
                onClick = onEmergency,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
            ) { Text("Emergency unlock") }
        }
    }
}

@Composable
private fun UnlockPage(
    unlocksLeft: Int,
    onUnlock: (pin: String, onResult: (EmergencyUnlockResult) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }

    PinPad(
        title = "Emergency unlock",
        subtitle = "Enter your emergency PIN to unlock apps for ${EmergencyUnlocker.MINUTES} minutes. " +
            "$unlocksLeft of ${EmergencyUnlockPolicy.DAILY_LIMIT} emergency unlocks left today.",
        pin = pin,
        errorText = if (attempts > 0) "Incorrect PIN (${EmergencyUnlocker.MAX_ATTEMPTS - attempts} attempts remaining)" else null,
        confirmLabel = "Unlock",
        confirmEnabled = pin.length == PIN_LENGTH && !checking,
        onPinChange = { pin = it },
        onConfirm = {
            checking = true
            onUnlock(pin) { result ->
                checking = false
                pin = ""
                // UNLOCKED hides the overlay; LIMIT_REACHED shows the limit page once the count updates
                if (result == EmergencyUnlockResult.WRONG_PIN) {
                    attempts++
                    if (attempts >= EmergencyUnlocker.MAX_ATTEMPTS) onBack()
                }
            }
        },
        onCancel = onBack
    )
}

@Composable
private fun MessagePage(title: String, body: String, primaryLabel: String?, onPrimary: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = AccentRed)
        Spacer(modifier = Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = AccentRed, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        primaryLabel?.let { Button(onClick = onPrimary) { Text(it) } }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
