package app.lenga.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a quiz question with multiple choice options
 */
data class Question(
    val id: String,
    val text: String,
    val options: List<Option>,
    val correctAnswerId: String,
    val explanation: String = "", // Explanation shown after answering
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val subject: String = "General",
    val points: Int = 10
)

/**
 * Represents a single answer option for a question
 */
data class Option(
    val id: String,
    val text: String,
    val isCorrect: Boolean = false // Optional: can be used instead of correctAnswerId in Question
)

/**
 * Enum representing question difficulty levels
 */
@Serializable
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD
}

/**
 * Represents the complete state of a quiz session
 */
data class QuizState(
    val questions: List<Question> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedAnswers: Map<String, String> = emptyMap(), // questionId -> optionId
    val showResults: Boolean = false,
    val score: Int = 0,
    val passingScore: Int = 70,
    val rewardMinutes: Int = 15,
    val isLastQuestion: Boolean = false,
    val timeSpentSeconds: Int = 0,
    val isCompleted: Boolean = false
)

/**
 * Extension function to calculate score percentage
 */
fun QuizState.calculatePercentage(): Int {
    return if (questions.isEmpty()) 0 else (score * 100) / (questions.size * 10)
}

/**
 * Extension function to check if user passed
 */
fun QuizState.hasPassed(): Boolean {
    return calculatePercentage() >= passingScore
}

/**
 * Sample questions for testing
 */
val sampleQuestions = listOf(
    Question(
        id = "q1",
        text = "What is the value of π (pi) to two decimal places?",
        options = listOf(
            Option("a", "3.12"),
            Option("b", "3.14"),
            Option("c", "3.16"),
            Option("d", "3.18")
        ),
        correctAnswerId = "b",
        explanation = "π is approximately 3.14159..., so to two decimal places it is 3.14",
        difficulty = Difficulty.EASY,
        subject = "Mathematics",
        points = 10
    ),
    Question(
        id = "q2",
        text = "What is the chemical symbol for gold?",
        options = listOf(
            Option("a", "Go"),
            Option("b", "Gd"),
            Option("c", "Au"),
            Option("d", "Ag")
        ),
        correctAnswerId = "c",
        explanation = "Au comes from the Latin word 'aurum' meaning gold",
        difficulty = Difficulty.MEDIUM,
        subject = "Chemistry",
        points = 15
    ),
    Question(
        id = "q3",
        text = "Who wrote 'Romeo and Juliet'?",
        options = listOf(
            Option("a", "Charles Dickens"),
            Option("b", "William Shakespeare"),
            Option("c", "Jane Austen"),
            Option("d", "Mark Twain")
        ),
        correctAnswerId = "b",
        explanation = "William Shakespeare wrote this famous tragedy around 1594-1596",
        difficulty = Difficulty.EASY,
        subject = "Literature",
        points = 10
    ),
    Question(
        id = "q4",
        text = "What is the speed of light in vacuum (approximate)?",
        options = listOf(
            Option("a", "300,000 km/s"),
            Option("b", "150,000 km/s"),
            Option("c", "1,000,000 km/s"),
            Option("d", "30,000 km/s")
        ),
        correctAnswerId = "a",
        explanation = "Light travels at approximately 299,792 kilometers per second in vacuum",
        difficulty = Difficulty.MEDIUM,
        subject = "Physics",
        points = 15
    ),
    Question(
        id = "q5",
        text = "What is the powerhouse of the cell?",
        options = listOf(
            Option("a", "Nucleus"),
            Option("b", "Ribosome"),
            Option("c", "Mitochondria"),
            Option("d", "Chloroplast")
        ),
        correctAnswerId = "c",
        explanation = "Mitochondria generate most of the cell's supply of adenosine triphosphate (ATP)",
        difficulty = Difficulty.EASY,
        subject = "Biology",
        points = 10
    ),
    Question(
        id = "q6",
        text = "What is 3 × 3?",
        options = listOf(
            Option("a", "6"),
            Option("b", "8"),
            Option("c", "9")
        ),
        correctAnswerId = "c",
        difficulty = Difficulty.EASY,
        subject = "Mathematics",
        points = 10
    ),
    Question(
        id = "q7",
        text = "What is 10 − 4?",
        options = listOf(
            Option("a", "6"),
            Option("b", "5"),
            Option("c", "7")
        ),
        correctAnswerId = "a",
        difficulty = Difficulty.EASY,
        subject = "Mathematics",
        points = 10
    ),
    Question(
        id = "q8",
        text = "What is 2 + 2?",
        options = listOf(
            Option("a", "3"),
            Option("b", "4"),
            Option("c", "5")
        ),
        correctAnswerId = "b",
        difficulty = Difficulty.EASY,
        subject = "Mathematics",
        points = 10
    )
)

/**
 * Factory function to create a quiz from subject and difficulty
 */
fun createQuiz(
    subject: String,
    difficulty: Difficulty,
    questionCount: Int = 5
): List<Question> {
    return sampleQuestions
        .filter { it.subject == subject && it.difficulty == difficulty }
        .take(questionCount)
        .ifEmpty { sampleQuestions.take(questionCount) } // Fallback to any questions if filter is empty
}

/**
 * Extension to shuffle options for randomization
 */
fun Question.shuffledOptions(): Question {
    return this.copy(options = this.options.shuffled())
}