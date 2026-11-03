package app.lenga.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.ui.theme.*
import app.lenga.ui.components.*
import app.lenga.viewmodel.StudyViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentReaderScreen(contentId: String, viewModel: StudyViewModel, onComplete: () -> Unit) {
    // Remember the flow: this screen recomposes every second as the read timer ticks
    val content by remember(contentId) { viewModel.getContent(contentId) }.collectAsStateWithLifecycle()
    val isLoading = content.body.isEmpty()
    val scrollState = rememberScrollState()
    var timeSpent by remember { mutableStateOf(0) }
    val requiredTime = content.requiredReadTime * 60

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            while (timeSpent < requiredTime) {
                delay(1000)
                timeSpent++
            }
            viewModel.markContentAsRead(contentId)
        }
    }

    val scrollProgress = if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
    val timeProgress = if (requiredTime > 0) timeSpent.toFloat() / requiredTime else 0f
    val overallProgress = (scrollProgress * 0.5f) + (timeProgress * 0.5f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content.title) },
                navigationIcon = {
                    IconButton(onClick = onComplete) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 16.dp)) {
                        CircularProgressIndicator(
                            progress = overallProgress,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = if (overallProgress >= 1f) SuccessGreen else PrimaryBlue
                        )
                        if (overallProgress >= 1f) {
                            Icon(Icons.Default.Check, contentDescription = "Completed", tint = SuccessGreen, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (overallProgress >= 1f) {
                BottomAppBar(containerColor = SuccessGreen) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SuccessGreen)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mark as Complete")
                    }
                }
            } else {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Keep reading... (${((1 - overallProgress) * 100).toInt()}% remaining)", color = TextSecondary)
                        Text(formatTime(requiredTime - timeSpent), color = PrimaryBlue)
                    }
                }
            }
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
                    Text("Loading content...", color = TextSecondary)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState)
            ) {
                Row(modifier = Modifier.padding(bottom = 16.dp)) {
                    AssistChip(
                        onClick = { },
                        label = { Text(content.subject) },
                        leadingIcon = { Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = { },
                        label = { Text("${content.readTime} min read") },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Text(text = content.body, style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}