package app.lenga.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.data.model.Achievement
import app.lenga.data.model.DailyStudyData
import app.lenga.data.model.StudySession
import app.lenga.ui.theme.*
import app.lenga.ui.components.*
import app.lenga.viewmodel.StudyViewModel
import app.lenga.data.model.ProgressData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: StudyViewModel, onBack: () -> Unit) {
    val progressData by viewModel.progressData.collectAsStateWithLifecycle(initialValue = ProgressData())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Progress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PrimaryBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading progress...", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(modifier = Modifier.weight(1f), Icons.Default.Timer, progressData.totalStudyHours, "Hours Studied", PrimaryBlue)
                        StatCard(modifier = Modifier.weight(1f), Icons.Default.EmojiEvents, progressData.quizzesPassed.toString(), "Quizzes Passed", SuccessGreen)
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(modifier = Modifier.weight(1f), Icons.Default.LocalFireDepartment, progressData.currentStreak.toString(), "Day Streak", AccentAmber)
                        StatCard(modifier = Modifier.weight(1f), Icons.Default.Star, "${progressData.avgScore}%", "Avg Score", StudyModeActive)
                    }
                }

                item {
                    StatCard(
                        modifier = Modifier.fillMaxWidth(),
                        Icons.Default.LockOpen,
                        progressData.emergencyUnlocksThisWeek.toString(),
                        "Emergency unlocks this week",
                        AccentRed
                    )
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("This Week", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            WeeklyChart(data = progressData.weeklyData)
                        }
                    }
                }

                item {
                    Text("Recent Achievements", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                }

                items(progressData.recentAchievements) { achievement ->
                    AchievementCard(achievement = achievement)
                }

                item {
                    Text("Recent Sessions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                }

                items(progressData.recentSessions) { session ->
                    SessionHistoryCard(session = session)
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, color: Color) {
    Card(modifier = modifier.height(120.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun WeeklyChart(data: List<DailyStudyData>) {
    val maxValue = data.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1

    Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        data.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height((day.minutes.toFloat() / maxValue * 100).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (day.minutes > 0) PrimaryBlue else Color(0xFFE2E8F0))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = day.dayName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun AchievementCard(achievement: Achievement) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(AccentAmber.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(text = achievement.icon, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = achievement.title, style = MaterialTheme.typography.titleMedium)
                Text(text = achievement.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Text(text = achievement.date, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
fun SessionHistoryCard(session: StudySession) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (session.completed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                contentDescription = null,
                tint = if (session.completed) SuccessGreen else TextSecondary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.moduleName, style = MaterialTheme.typography.bodyLarge)
                Text(text = "${session.duration} • ${session.date}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (session.quizScore != null) {
                Badge(containerColor = if (session.quizScore >= 80) SuccessLight else Color(0xFFFEF3C7)) {
                    Text("${session.quizScore}%", color = if (session.quizScore >= 80) SuccessGreen else AccentAmber)
                }
            }
        }
    }
}