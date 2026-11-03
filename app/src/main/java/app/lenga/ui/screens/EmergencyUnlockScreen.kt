package app.lenga.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.data.model.EmergencyUnlockPolicy
import app.lenga.ui.theme.AccentRed
import app.lenga.ui.theme.TextSecondary
import app.lenga.data.repository.EmergencyUnlockResult
import app.lenga.data.repository.EmergencyUnlocker
import app.lenga.ui.components.PIN_LENGTH
import app.lenga.ui.components.PinPad
import app.lenga.viewmodel.StudyViewModel


@Composable
fun EmergencyUnlockScreen(viewModel: StudyViewModel, onUnlock: () -> Unit, onCancel: () -> Unit) {
    val isPinSet by viewModel.isPinSet.collectAsStateWithLifecycle()
    val unlocksLeft by viewModel.emergencyUnlocksLeftToday.collectAsStateWithLifecycle()

    when {
        isPinSet == null || unlocksLeft == null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        isPinSet == false -> CreatePinContent(onPinCreated = { viewModel.setEmergencyPin(it) }, onCancel = onCancel)
        unlocksLeft == 0 -> LimitReachedContent(onCancel = onCancel)
        else -> UnlockContent(viewModel = viewModel, unlocksLeft = unlocksLeft!!, onUnlock = onUnlock, onCancel = onCancel)
    }
}

@Composable
private fun UnlockContent(viewModel: StudyViewModel, unlocksLeft: Int, onUnlock: () -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var attempts by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }

    PinPad(
        title = "Emergency Unlock",
        subtitle = "Enter your emergency PIN to unlock apps for ${StudyViewModel.EMERGENCY_UNLOCK_MINUTES} minutes. " +
            "$unlocksLeft of ${EmergencyUnlockPolicy.DAILY_LIMIT} emergency unlocks left today.",
        pin = pin,
        errorText = if (error) "Incorrect PIN (${EmergencyUnlocker.MAX_ATTEMPTS - attempts} attempts remaining)" else null,
        confirmLabel = "Unlock",
        confirmEnabled = pin.length == PIN_LENGTH && !checking,
        onPinChange = { pin = it; error = false },
        onConfirm = {
            checking = true
            viewModel.emergencyUnlock(pin) { result ->
                checking = false
                when (result) {
                    EmergencyUnlockResult.UNLOCKED -> onUnlock()
                    // The limit screen takes over as soon as the count updates
                    EmergencyUnlockResult.LIMIT_REACHED -> pin = ""
                    EmergencyUnlockResult.WRONG_PIN -> {
                        error = true
                        attempts++
                        pin = ""
                        if (attempts >= EmergencyUnlocker.MAX_ATTEMPTS) onCancel()
                    }
                }
            }
        },
        onCancel = onCancel
    )
}

@Composable
private fun LimitReachedContent(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = AccentRed)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Daily limit reached",
            style = MaterialTheme.typography.headlineMedium,
            color = AccentRed,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You've used all ${EmergencyUnlockPolicy.DAILY_LIMIT} emergency unlocks for today. " +
                "Your phone and messaging apps still work while apps are locked. " +
                "Pass a quiz to earn free time, or try again tomorrow.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onCancel) { Text("Back to studying") }
    }
}

@Composable
private fun CreatePinContent(onPinCreated: (String) -> Unit, onCancel: () -> Unit) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    val confirming = firstPin != null

    PinPad(
        title = if (confirming) "Confirm Emergency PIN" else "Create Emergency PIN",
        subtitle = if (confirming) "Enter the same PIN again"
            else "You'll need this PIN to unlock apps in an emergency. Keep it memorable.",
        pin = pin,
        errorText = if (mismatch) "PINs didn't match. Start again." else null,
        confirmLabel = if (confirming) "Save PIN" else "Next",
        confirmEnabled = pin.length == PIN_LENGTH,
        onPinChange = { pin = it; mismatch = false },
        onConfirm = {
            when {
                !confirming -> { firstPin = pin; pin = "" }
                pin == firstPin -> onPinCreated(pin)
                else -> { firstPin = null; pin = ""; mismatch = true }
            }
        },
        onCancel = onCancel
    )
}

