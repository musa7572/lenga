package app.lenga.data.model

/**
 * Represents a study module/unit that contains learning materials and quizzes
 */
data class StudyModule(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val isLocked: Boolean = false,
    val rewardMinutes: Int = 15,
    val subject: String = "General",
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val estimatedTimeMinutes: Int = 30,
    val contentIds: List<String> = emptyList(),
    val quizId: String = "",
    val prerequisites: List<String> = emptyList(), // Module IDs that must be completed first
    val icon: String = "📚"
)

/**
 * Represents quick reference notes for students
 */
data class QuickNote(
    val id: String,
    val title: String,
    val readTime: Int, // Estimated minutes to read
    val subject: String = "Reference",
    val content: String = "",
    val isRead: Boolean = false,
    val tags: List<String> = emptyList()
)

/**
 * Represents study content (reading material)
 */
data class StudyContent(
    val id: String,
    val title: String,
    val body: String,
    val requiredReadTime: Int, // Minimum time required to "complete" reading
    val readTime: Int, // Actual reading time estimate
    val subject: String,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val relatedQuestions: List<String> = emptyList() // Question IDs related to this content
)

/**
 * Extension function to check if module is available to start
 */
fun StudyModule.isAvailable(completedModuleIds: List<String>): Boolean {
    return prerequisites.all { it in completedModuleIds }
}

/**
 * Sample modules for testing
 */
val sampleModules = listOf(
    StudyModule(
        id = "math_basics",
        title = "Mathematics Basics",
        description = "Fundamental algebra and arithmetic concepts",
        isCompleted = false,
        isLocked = false,
        rewardMinutes = 10,
        subject = "Mathematics",
        difficulty = Difficulty.EASY,
        estimatedTimeMinutes = 20,
        quizId = "quiz_math_1",
        icon = "🔢"
    ),
    StudyModule(
        id = "physics_concepts",
        title = "Physics Concepts",
        description = "Newton's laws and basic mechanics",
        isCompleted = false,
        isLocked = true,
        rewardMinutes = 15,
        subject = "Physics",
        difficulty = Difficulty.MEDIUM,
        estimatedTimeMinutes = 45,
        prerequisites = listOf("math_basics"),
        quizId = "quiz_physics_1",
        icon = "⚛️"
    ),
    StudyModule(
        id = "chemistry_101",
        title = "Chemistry 101",
        description = "Introduction to the periodic table",
        isCompleted = true,
        isLocked = false,
        rewardMinutes = 10,
        subject = "Chemistry",
        difficulty = Difficulty.EASY,
        estimatedTimeMinutes = 30,
        quizId = "quiz_chem_1",
        icon = "🧪"
    ),
    StudyModule(
        id = "advanced_math",
        title = "Advanced Calculus",
        description = "Derivatives and integrals",
        isCompleted = false,
        isLocked = true,
        rewardMinutes = 20,
        subject = "Mathematics",
        difficulty = Difficulty.HARD,
        estimatedTimeMinutes = 60,
        prerequisites = listOf("math_basics"),
        quizId = "quiz_math_2",
        icon = "📐"
    )
)

/**
 * Sample quick notes for reference
 */
val sampleQuickNotes = listOf(
    QuickNote(
        id = "formulas",
        title = "Essential Formulas",
        readTime = 5,
        subject = "Mathematics",
        content = "Area of circle: πr²\nPythagorean theorem: a² + b² = c²\nQuadratic formula: x = (-b ± √(b²-4ac)) / 2a",
        tags = listOf("formulas", "reference", "math")
    ),
    QuickNote(
        id = "periodic_table",
        title = "Periodic Table Guide",
        readTime = 8,
        subject = "Chemistry",
        content = "Groups: Vertical columns (1-18)\nPeriods: Horizontal rows\nMetals: Left side\nNon-metals: Right side",
        tags = listOf("chemistry", "reference")
    ),
    QuickNote(
        id = "physics_laws",
        title = "Newton's Laws Summary",
        readTime = 6,
        subject = "Physics",
        content = "1st Law: Object at rest stays at rest\n2nd Law: F = ma\n3rd Law: Every action has equal opposite reaction",
        tags = listOf("physics", "laws", "mechanics")
    )
)