package app.lenga.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lenga.data.model.StudyModule
import app.lenga.ui.theme.*

/**
 * Enhanced module card with progress and status
 */
@Composable
fun ModuleCard(
    module: StudyModule,
    progress: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isClickable = !module.isLocked && !module.isCompleted

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isClickable, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                module.isCompleted -> SuccessLight
                module.isLocked -> Color(0xFFF1F5F9)
                else -> Surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (module.isLocked) 0.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon/Status
                ModuleIcon(module = module)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (module.isLocked) TextSecondary else TextPrimary
                    )
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Status icon
                when {
                    module.isCompleted -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = SuccessGreen
                    )
                    module.isLocked -> Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = TextSecondary.copy(alpha = 0.5f)
                    )
                    else -> Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Start",
                        tint = PrimaryBlue
                    )
                }
            }

            // Progress bar (if in progress)
            if (!module.isCompleted && !module.isLocked && progress > 0) {
                AnimatedProgressBar(
                    progress = progress / 100f,
                    height = 6,
                    showPercentage = true
                )
            }

            // Footer info
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subject tag
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = PrimaryBlue.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = module.subject,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryBlue
                        )
                    }

                    // Time estimate
                    Text(
                        text = "${module.estimatedTimeMinutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                // Reward badge
                if (!module.isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (module.isLocked) Color(0xFFE2E8F0) else SuccessGreen.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (module.isLocked) TextSecondary else SuccessGreen
                            )
                            Text(
                                text = "+${module.rewardMinutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (module.isLocked) TextSecondary else SuccessGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleIcon(module: StudyModule) {
    val backgroundColor = when {
        module.isCompleted -> SuccessGreen
        module.isLocked -> TextSecondary.copy(alpha = 0.2f)
        else -> PrimaryLight
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (module.isCompleted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Text(
                text = module.icon,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

/**
 * Grid of module cards
 */
@Composable
fun ModuleGrid(
    modules: List<StudyModule>,
    progressMap: Map<String, Int>,
    onModuleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        modules.forEach { module ->
            ModuleCard(
                module = module,
                progress = progressMap[module.id] ?: 0,
                onClick = { onModuleClick(module.id) }
            )
        }
    }
}