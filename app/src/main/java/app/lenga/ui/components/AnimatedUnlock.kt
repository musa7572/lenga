package app.lenga.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lenga.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Animated unlock celebration component
 */
@Composable
fun AnimatedUnlock(
    rewardMinutes: Int,
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
        delay(2000)
        onAnimationComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SuccessGreen.copy(alpha = 0.2f),
                        Background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = scaleIn(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Animated lock icon
                UnlockAnimation()

                // Reward text
                Text(
                    text = "Apps Unlocked!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )

                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = SuccessGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = "$rewardMinutes min free time",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnlockAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "unlock")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(150.dp)
            .scale(scale)
            .background(SuccessGreen.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.LockOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = SuccessGreen
        )
    }
}

/**
 * Lock icon with shake animation for incorrect attempts
 */
@Composable
fun ShakeLock(
    isLocked: Boolean,
    shake: Boolean,
    modifier: Modifier = Modifier
) {
    val shakeOffset by animateFloatAsState(
        targetValue = if (shake) 10f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "shake"
    )

    Box(
        modifier = modifier.offset(x = shakeOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (isLocked) "Locked" else "Unlocked",
            modifier = Modifier.size(64.dp),
            tint = if (isLocked) AccentRed else SuccessGreen
        )
    }
}

/**
 * Confetti celebration effect (simplified version)
 */
@Composable
fun ConfettiCelebration(
    modifier: Modifier = Modifier
) {
    val colors = listOf(PrimaryBlue, SuccessGreen, AccentAmber, AccentRed, StudyModeActive)

    Box(modifier = modifier.fillMaxSize()) {
        repeat(20) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "confetti_$index")

            val offsetY by infiniteTransition.animateFloat(
                initialValue = -100f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = (2000 + index * 100),
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "fall_$index"
            )

            val offsetX by infiniteTransition.animateFloat(
                initialValue = index * 50f,
                targetValue = index * 50f + 100f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sway_$index"
            )

            Box(
                modifier = Modifier
                    .offset(x = offsetX.dp, y = offsetY.dp)
                    .size(12.dp)
                    .background(colors[index % colors.size], CircleShape)
            )
        }
    }
}

/**
 * Streak flame animation
 */
@Composable
fun StreakFlame(
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flame")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_alpha"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🔥",
            modifier = Modifier.scale(scale),
            style = MaterialTheme.typography.displayLarge
        )
        Text(
            text = "$streakDays day${if (streakDays != 1) "s" else ""}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AccentAmber
        )
    }
}