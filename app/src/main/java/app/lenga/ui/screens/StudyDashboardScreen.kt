package app.lenga.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.data.model.ProgressData
import app.lenga.data.model.QuickNote
import app.lenga.data.model.StudyModule
import app.lenga.focus.FocusKind
import app.lenga.ui.theme.*
import app.lenga.ui.components.*
import app.lenga.viewmodel.StudyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyDashboardScreen(
    viewModel: StudyViewModel,
    onModuleClick: (String) -> Unit,
    onContentClick: (String) -> Unit,
    onProgressClick: () -> Unit,
    onEmergencyClick: () -> Unit,
    onModesClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val progress by viewModel.progressData.collectAsStateWithLifecycle(initialValue = ProgressData())
    val isBlockerRunning by viewModel.isBlockerRunning.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Settings.Secure isn't observable here, so re-read it whenever the user comes back to the app
    var isBlockerEnabled by remember { mutableStateOf(viewModel.isBlockerEnabledInSettings(context)) }
    LifecycleResumeEffect(Unit) {
        isBlockerEnabled = viewModel.isBlockerEnabledInSettings(context)
        onPauseOrDispose {}
    }
    val focusStatus by viewModel.focusStatus.collectAsStateWithLifecycle()
    val modesLoad by viewModel.modesLoad.collectAsStateWithLifecycle()
    val launchableApps by viewModel.launchableApps.collectAsStateWithLifecycle()
    var startKind by remember { mutableStateOf<FocusKind?>(null) }
    val requestNotifications = rememberNotificationPermissionRequest()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Study Mode", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Focus to unlock your apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudyModeActive,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = onModesClick) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "Modes",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onEmergencyClick) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = "Emergency unlock",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onProgressClick) {
                        Icon(
                            Icons.Default.Assessment,
                            contentDescription = "Progress",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (!isBlockerRunning) {
                item {
                    EnableBlockingCard(
                        stoppedBySystem = isBlockerEnabled,
                        showXiaomiTip = viewModel.isXiaomiDevice,
                        onEnable = { viewModel.openBlockerSettings(context) },
                        onOpenAutostart = { viewModel.openAutostartSettings(context) }
                    )
                }
            }

            focusStatus?.let { status ->
                item {
                    if (status.isActive) {
                        FocusBanner(status)
                    } else {
                        StartFocusCard(
                            onTimed = { startKind = FocusKind.TIMED },
                            onDetox = { startKind = FocusKind.DETOX }
                        )
                    }
                }
            }

            item {
                FocusStatsCard(
                    studyTime = progress.totalStudyHours,
                    completedModules = modules.count { it.isCompleted },
                    totalModules = modules.size
                )
            }

            if (uiState.isStudying) {
                item {
                    ActiveStudyTimer(
                        elapsedTime = uiState.elapsedStudyTime,
                        onPause = { viewModel.pauseStudy() },
                        onStop = { viewModel.stopStudy() }
                    )
                }
            }

            item {
                MotivationCard(quote = uiState.dailyQuote)
            }

            item {
                Text(
                    "Study Modules",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(modules) { module ->
                StudyModuleCard(
                    module = module,
                    onClick = {
                        if (module.isLocked) {
                            viewModel.showLockedMessage()
                        } else {
                            onModuleClick(module.id)
                        }
                    }
                )
            }

            item {
                Text(
                    "Quick Reference",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(viewModel.quickNotes) { note ->
                QuickNoteCard(
                    note = note,
                    onClick = { onContentClick(note.id) }
                )
            }
        }
    }

    startKind?.let { kind ->
        LaunchedEffect(kind) { viewModel.loadLaunchableApps() }
        StartFocusDialog(
            kind = kind,
            savedModes = modesLoad?.modes.orEmpty(),
            apps = launchableApps,
            essentials = viewModel.essentialPackages,
            onDismiss = { startKind = null },
            onStart = { end, allowed, onError ->
                viewModel.startFocus(kind, end, allowed) { error ->
                    if (error == null) {
                        startKind = null
                        requestNotifications()
                    } else {
                        onError(error)
                    }
                }
            }
        )
    }
}

@Composable
fun FocusStatsCard(studyTime: String, completedModules: Int, totalModules: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(FocusGradientStart, FocusGradientEnd)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("Studied", studyTime, Icons.Outlined.Timer)
                    StatItem("Completed", "$completedModules/$totalModules", Icons.Default.CheckCircle)
                }

                LinearProgressIndicator(
                    progress = if (totalModules > 0) completedModules.toFloat() / totalModules else 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White.copy(alpha = 0.9f),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
fun EnableBlockingCard(
    stoppedBySystem: Boolean,
    showXiaomiTip: Boolean,
    onEnable: () -> Unit,
    onOpenAutostart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (stoppedBySystem) "App blocking stopped" else "App blocking is off",
                style = MaterialTheme.typography.titleMedium,
                color = AccentRed
            )
            Text(
                if (stoppedBySystem) {
                    "Your phone stopped Lenga, so apps aren't being blocked. In Accessibility settings, switch Lenga off and on again."
                } else {
                    "Turn on Lenga in Accessibility settings so distracting apps stay locked until you earn free time."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onEnable, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                Text(if (stoppedBySystem) "Restart blocking" else "Enable blocking")
            }
            if (showXiaomiTip) {
                Text(
                    "Xiaomi phones stop Lenga when you clear it from Recents. Lock it in Recents (long-press its card) and allow Autostart to keep blocking on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 12.dp)
                )
                TextButton(onClick = onOpenAutostart) {
                    Text("Allow Autostart", color = AccentRed)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
fun StudyModuleCard(module: StudyModule, onClick: () -> Unit) {
    val cardColor = when {
        module.isCompleted -> SuccessLight
        module.isLocked -> Color(0xFFF1F5F9)
        else -> Surface
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (module.isLocked) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            module.isCompleted -> SuccessGreen
                            module.isLocked -> TextSecondary.copy(alpha = 0.2f)
                            else -> PrimaryLight
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        module.isCompleted -> Icons.Default.Check
                        module.isLocked -> Icons.Default.Lock
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (module.isLocked) TextSecondary else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = module.title, style = MaterialTheme.typography.titleMedium, color = if (module.isLocked) TextSecondary else TextPrimary)
                Text(text = module.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (!module.isLocked && !module.isCompleted) {
                    Text(
                        text = "Reward: ${module.rewardMinutes} min unlock",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (module.isLocked) {
                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = TextSecondary)
            }
        }
    }
}

@Composable
fun ActiveStudyTimer(elapsedTime: String, onPause: () -> Unit, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Currently Studying", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            Text(elapsedTime, style = MaterialTheme.typography.displayLarge, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onPause, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text("Pause")
                }
                Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                    Text("End Session")
                }
            }
        }
    }
}

@Composable
fun MotivationCard(quote: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = quote, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF92400E))
        }
    }
}

@Composable
fun QuickNoteCard(note: QuickNote, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Article, contentDescription = null, tint = PrimaryLight)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = note.title, style = MaterialTheme.typography.bodyLarge)
                Text(text = "${note.readTime} min read", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}