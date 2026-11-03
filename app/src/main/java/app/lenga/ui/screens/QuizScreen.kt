package app.lenga.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.data.model.Question
import app.lenga.ui.theme.*
import app.lenga.ui.components.*
import app.lenga.viewmodel.StudyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun QuizScreen(
    moduleId: String,
    viewModel: StudyViewModel,
    onQuizComplete: (passed: Boolean) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(moduleId) { viewModel.startQuiz(moduleId) }
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge Check") },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    Text(
                        text = "${quizState.currentQuestionIndex + 1}/${quizState.questions.size}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        if (quizState.questions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            LinearProgressIndicator(
                progress = (quizState.currentQuestionIndex + 1).toFloat() / quizState.questions.size,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = PrimaryBlue
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = quizState.currentQuestionIndex,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() with
                            slideOutHorizontally { width -> -width } + fadeOut()
                }
            ) { index ->
                val question = quizState.questions[index]
                QuestionCard(
                    question = question,
                    selectedAnswer = quizState.selectedAnswers[question.id],
                    onAnswerSelected = { answer -> viewModel.selectAnswer(question.id, answer) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (quizState.currentQuestionIndex > 0) {
                    OutlinedButton(onClick = { viewModel.previousQuestion() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Previous")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (quizState.isLastQuestion) {
                            submitting = true
                            viewModel.submitQuiz { passed ->
                                submitting = false
                                if (passed != null) onQuizComplete(passed)
                            }
                        } else {
                            viewModel.nextQuestion()
                        }
                    },
                    enabled = !submitting && quizState.selectedAnswers.containsKey(quizState.questions[quizState.currentQuestionIndex].id),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text(if (quizState.isLastQuestion) "Submit" else "Next")
                    if (!quizState.isLastQuestion) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Leave Quiz?") },
                text = { Text("Your progress will be lost. Are you sure?") },
                confirmButton = {
                    TextButton(onClick = { viewModel.abandonQuiz(); onBack() }, colors = ButtonDefaults.textButtonColors(contentColor = AccentRed)) {
                        Text("Leave")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Stay")
                    }
                }
            )
        }
    }
}

@Composable
fun QuestionCard(question: Question, selectedAnswer: String?, onAnswerSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(text = question.text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 24.dp))

        question.options.forEach { option ->
            val isSelected = selectedAnswer == option.id
            val backgroundColor = if (isSelected) PrimaryLight.copy(alpha = 0.1f) else Surface
            val borderColor = if (isSelected) PrimaryBlue else Color(0xFFE2E8F0)

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onAnswerSelected(option.id) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = { onAnswerSelected(option.id) }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = option.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}