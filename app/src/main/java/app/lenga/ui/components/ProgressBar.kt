package app.lenga.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lenga.ui.theme.*

/**
 * Animated linear progress bar with percentage label
 */
@Composable
fun AnimatedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = PrimaryBlue,
    trackColor: Color = Color(0xFFE2E8F0),
    height: Int = 8,
    showPercentage: Boolean = true,
    animationDuration: Int = 1000
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(height / 2))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(height / 2))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(color, color.copy(alpha = 0.8f))
                        )
                    )
            )
        }

        if (showPercentage) {
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Circular progress indicator with centered text
 */
@Composable
fun CircularProgressWithText(
    progress: Float,
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 120,
    strokeWidth: Int = 8,
    color: Color = PrimaryBlue
) {
    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = strokeWidth.dp,
            trackColor = Color(0xFFE2E8F0),
            strokeCap = StrokeCap.Round
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

/**
 * Segmented progress bar for multi-step processes
 */
@Composable
fun SegmentedProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(totalSteps) { step ->
            val isCompleted = step < currentStep
            val isCurrent = step == currentStep

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            isCompleted -> SuccessGreen
                            isCurrent -> PrimaryBlue
                            else -> Color(0xFFE2E8F0)
                        }
                    )
            )
        }
    }
}

/**
 * Progress card with icon and stats
 */
@Composable
fun ProgressStatCard(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = PrimaryBlue
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Column {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            AnimatedProgressBar(
                progress = progress,
                color = color,
                height = 6,
                showPercentage = false
            )
        }
    }
}

/**
 * Daily goal progress indicator
 */
@Composable
fun DailyGoalProgress(
    currentMinutes: Int,
    goalMinutes: Int,
    modifier: Modifier = Modifier
) {
    val progress = (currentMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)
    val isGoalReached = currentMinutes >= goalMinutes

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGoalReached) SuccessLight else Surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressWithText(
                progress = progress,
                text = "${(progress * 100).toInt()}%",
                size = 80,
                strokeWidth = 6,
                color = if (isGoalReached) SuccessGreen else PrimaryBlue
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isGoalReached) "Daily Goal Reached! 🎉" else "Daily Goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$currentMinutes / $goalMinutes minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (!isGoalReached) {
                    Text(
                        text = "${goalMinutes - currentMinutes} min to go",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentAmber
                    )
                }
            }
        }
    }
}