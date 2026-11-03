package app.lenga.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lenga.ui.theme.AccentRed
import app.lenga.ui.theme.TextSecondary

const val PIN_LENGTH = 4

/** On-screen number pad for entering a [PIN_LENGTH]-digit PIN. Needs no keyboard. */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    pin: String,
    errorText: String?,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = AccentRed)

        Spacer(modifier = Modifier.height(24.dp))

        Text(title, style = MaterialTheme.typography.headlineMedium, color = AccentRed)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(PIN_LENGTH) { index ->
                Box(
                    modifier = Modifier.size(20.dp).clip(CircleShape).background(if (index < pin.length) AccentRed else Color.LightGray)
                )
            }
        }

        if (errorText != null) {
            Text(errorText, color = AccentRed, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(3) { col ->
                        val number = row * 3 + col + 1
                        NumpadButton(number.toString()) {
                            if (pin.length < PIN_LENGTH) onPinChange(pin + number)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(modifier = Modifier.size(72.dp))
                NumpadButton("0") {
                    if (pin.length < PIN_LENGTH) onPinChange(pin + "0")
                }
                NumpadButton("⌫") {
                    onPinChange(pin.dropLast(1))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }

            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text(confirmLabel)
            }
        }
    }
}

@Composable
fun NumpadButton(number: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.size(72.dp), shape = CircleShape) {
        Text(number, style = MaterialTheme.typography.headlineMedium)
    }
}
