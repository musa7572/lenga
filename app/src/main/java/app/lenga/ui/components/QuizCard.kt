package app.lenga.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lenga.data.model.Option
import app.lenga.data.model.Question
import app.lenga.ui.theme.*

/**
 * Card displaying a quiz question with selectable options
 */
@Composable
fun QuizCard(
    question: Question,
    selectedOptionId: String?,
    onOptionSelected: (String) -> Unit,
    showCorrectAnswer: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Question header with difficulty badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DifficultyBadge(difficulty = question.difficulty)

                Text(
                    text = "${question.points} pts",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            // Question text
            Text(
                text = question.text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEach { option ->
                    OptionButton(
                        option = option,
                        isSelected = selectedOptionId == option.id,
                        isCorrect = showCorrectAnswer && option.id == question.correctAnswerId,
                        isWrong = showCorrectAnswer && selectedOptionId == option.id && option.id != question.correctAnswerId,
                        onClick = { onOptionSelected(option.id) },
                        enabled = !showCorrectAnswer
                    )
                }
            }

            // Explanation (shown after answering)
            AnimatedVisibility(
                visible = showCorrectAnswer && question.explanation.isNotBlank(),
                enter = expandVertically() + fadeIn()
            ) {
                ExplanationCard(explanation = question.explanation)
            }
        }
    }
}

@Composable
private fun OptionButton(
    option: Option,
    isSelected: Boolean,
    isCorrect: Boolean,
    isWrong: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val backgroundColor = when {
        isCorrect -> SuccessLight
        isWrong -> Color(0xFFFEE2E2)
        isSelected -> PrimaryLight.copy(alpha = 0.15f)
        else -> Color(0xFFF8FAFC)
    }

    val borderColor = when {
        isCorrect -> SuccessGreen
        isWrong -> AccentRed
        isSelected -> PrimaryBlue
        else -> Color(0xFFE2E8F0)
    }

    val icon = when {
        isCorrect -> Icons.Default.CheckCircle
        isWrong -> Icons.Default.Close
        isSelected -> Icons.Default.CheckCircle
        else -> Icons.Default.RadioButtonUnchecked
    }

    val iconColor = when {
        isCorrect -> SuccessGreen
        isWrong -> AccentRed
        isSelected -> PrimaryBlue
        else -> TextSecondary.copy(alpha = 0.4f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = option.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: app.lenga.data.model.Difficulty) {
    val (text, color) = when (difficulty) {
        app.lenga.data.model.Difficulty.EASY -> "Easy" to SuccessGreen
        app.lenga.data.model.Difficulty.MEDIUM -> "Medium" to AccentAmber
        app.lenga.data.model.Difficulty.HARD -> "Hard" to AccentRed
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ExplanationCard(explanation: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "💡 Explanation",
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryBlue,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

/**
 * Compact quiz result card for lists
 */
@Composable
fun QuizResultCard(
    title: String,
    score: Int,
    date: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusText) = when {
        score >= 90 -> SuccessGreen to "Excellent"
        score >= 70 -> PrimaryBlue to "Passed"
        else -> AccentRed to "Try Again"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Score circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$score%",
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$date • $statusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (score >= 70) SuccessGreen else TextSecondary.copy(alpha = 0.3f)
            )
        }
    }
}