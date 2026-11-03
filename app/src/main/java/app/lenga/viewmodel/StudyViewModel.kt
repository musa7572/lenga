package app.lenga.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lenga.data.model.*
import app.lenga.data.repository.*
import app.lenga.service.BlockingState
import app.lenga.service.StudyAccessibilityService
import app.lenga.data.apps.LaunchableApp
import app.lenga.data.apps.LaunchableApps
import app.lenga.focus.FocusKind
import app.lenga.focus.FocusMode
import app.lenga.focus.FocusPolicy
import app.lenga.focus.FocusStatus
import app.lenga.focus.ModeRepository
import app.lenga.focus.ModeSchedule
import app.lenga.focus.ModesLoad
import app.lenga.service.EssentialApps
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyRepository(application)

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Use repository for progress data (single source of truth)
    val progressData: Flow<ProgressData> = repository.getProgressData()

    // Modules with completion and prerequisite lock state from the repository
    val modules: StateFlow<List<StudyModule>> = repository.getModules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickNotes: List<QuickNote> = sampleQuickNotes

    /** Null until the stored value has been read. */
    val isPinSet: StateFlow<Boolean?> = repository.isPinSet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Whether the accessibility service that enforces blocking is running. */
    val isBlockerRunning: StateFlow<Boolean> = BlockingState.serviceRunning

    private val _quizState = MutableStateFlow(QuizState())
    val quizState: StateFlow<QuizState> = _quizState.asStateFlow()
    private var quizModuleId: String? = null
    private var quizStartedAt = 0L

    private var studyTimerJob: Job? = null

    // ==================== Error Handling ====================

    private fun showError(message: String) {
        _errorState.value = message
    }

    fun clearError() {
        _errorState.value = null
    }

    // ==================== Study Timer ====================

    fun startStudy() {
        _uiState.update { it.copy(isStudying = true) }
        var seconds = 0
        studyTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                seconds++
                val hours = seconds / 3600
                val mins = (seconds % 3600) / 60
                val secs = seconds % 60
                _uiState.update {
                    it.copy(elapsedStudyTime = String.format("%02d:%02d:%02d", hours, mins, secs))
                }
            }
        }
    }

    fun pauseStudy() {
        studyTimerJob?.cancel()
        _uiState.update { it.copy(isStudying = false) }
    }

    fun toggleTimer() {
        if (_uiState.value.isStudying) pauseStudy() else startStudy()
    }

    fun stopStudy() {
        studyTimerJob?.cancel()
        _uiState.update { it.copy(isStudying = false, elapsedStudyTime = "00:00:00") }
    }

    // ==================== Focus Modes ====================

    private val modeRepository = ModeRepository(application)

    /** Saved modes; null until they've loaded. */
    val modesLoad: StateFlow<ModesLoad?> = modeRepository.load
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val clockTicks: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    /** Which modes are active, refreshed every second; null until modes have loaded. */
    val focusStatus: StateFlow<FocusStatus?> = combine(modesLoad, clockTicks, BlockingState.freeTime) { load, now, freeTime ->
        load?.let { FocusStatus(FocusPolicy.activeModes(now, it.modes, ZoneId.systemDefault()), now, freeTime.bankedMillis) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Pause or resume quiz free time even if the accessibility service isn't running
        viewModelScope.launch {
            focusStatus.filterNotNull().collect { BlockingState.syncWithSession(it.sessionStart, it.now) }
        }
    }

    /** Always-allowed apps, shown checked and disabled in the app picker. */
    val essentialPackages: Set<String> = EssentialApps.resolve(application)

    private val _launchableApps = MutableStateFlow<List<LaunchableApp>?>(null)
    /** Apps for the picker; null until [loadLaunchableApps] finishes. */
    val launchableApps: StateFlow<List<LaunchableApp>?> = _launchableApps.asStateFlow()

    fun loadLaunchableApps() {
        if (_launchableApps.value != null) return
        viewModelScope.launch {
            _launchableApps.value = withContext(Dispatchers.IO) { LaunchableApps.load(getApplication()) }
        }
    }

    fun mode(id: String): FocusMode? = modesLoad.value?.modes?.find { it.id == id }

    /** Starts a timed session or detox day now, ending at [end]. [onResult] receives an error or null. */
    fun startFocus(kind: FocusKind, end: Long, allowed: Set<String>, onResult: (String?) -> Unit) {
        val now = System.currentTimeMillis()
        val (name, schedule) = when (kind) {
            FocusKind.TIMED -> "Focus session" to ModeSchedule.Timed(now, end)
            FocusKind.DETOX -> "Detox day" to ModeSchedule.Detox(now, end)
        }
        saveMode(FocusMode(UUID.randomUUID().toString(), name, schedule, allowed), onResult)
    }

    fun saveMode(mode: FocusMode, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(modeRepository.save(mode)) }
    }

    fun deleteMode(id: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(modeRepository.delete(id)) }
    }

    private suspend fun isFocusActiveNow(): Boolean =
        FocusPolicy.activeModes(System.currentTimeMillis(), modeRepository.modes.first(), ZoneId.systemDefault())
            .isNotEmpty()

    // ==================== App Blocking ====================

    fun openBlockerSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Whether Lenga is switched on in Accessibility settings. This can be true while
     * [isBlockerRunning] is false: when the phone kills the app (e.g. MIUI's clear-recents),
     * Android marks the service as crashed and won't restart it until it is toggled off and on.
     */
    fun isBlockerEnabledInSettings(context: Context): Boolean {
        val expected = ComponentName(context, StudyAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /** Xiaomi/Redmi/POCO phones kill apps cleared from Recents, which stops the blocker. */
    val isXiaomiDevice: Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    /** Opens MIUI's Autostart screen, falling back to Lenga's app info page. */
    fun openAutostartSettings(context: Context) {
        val autostart = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        val appDetails = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        for (intent in listOf(autostart, appDetails)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next screen
            } catch (e: SecurityException) {
                // Try the next screen
            }
        }
    }

    fun freeTimeRemainingSeconds(): Int = BlockingState.remainingFreeTimeSeconds()

    /** Full length of the unlock the reward screen is counting down, for its progress ring. */
    fun freeTimeTotalSeconds(): Int {
        val freeTime = BlockingState.freeTime.value
        val emergencyIsLonger = freeTime.emergencyUntil > freeTime.unlockedUntil &&
            freeTime.emergencyUntil > System.currentTimeMillis()
        return if (emergencyIsLonger) EMERGENCY_UNLOCK_MINUTES * 60 else (freeTime.totalMillis / 1000).toInt()
    }

    fun endFreeTime() {
        BlockingState.endFreeTime()
    }

    // ==================== Quiz ====================

    /** Loads the quiz for [moduleId]; keeps an in-progress quiz for the same module (e.g. across rotation). */
    fun startQuiz(moduleId: String) {
        if (quizModuleId == moduleId && _quizState.value.questions.isNotEmpty()) return
        quizModuleId = moduleId
        quizStartedAt = System.currentTimeMillis()
        _quizState.value = QuizState()
        viewModelScope.launch {
            try {
                val quiz = repository.getQuizForModule(moduleId).first()
                _quizState.value = quiz.copy(isLastQuestion = quiz.questions.size <= 1)
            } catch (e: Exception) {
                Log.e("StudyViewModel", "Failed to load quiz", e)
                showError("Couldn't load the quiz. Please try again.")
            }
        }
    }

    fun abandonQuiz() {
        quizModuleId = null
        _quizState.value = QuizState()
    }

    fun selectAnswer(questionId: String, answerId: String) {
        _quizState.update { it.copy(selectedAnswers = it.selectedAnswers + (questionId to answerId)) }
    }

    fun nextQuestion() {
        _quizState.update { state ->
            val index = (state.currentQuestionIndex + 1).coerceAtMost(state.questions.lastIndex)
            state.copy(currentQuestionIndex = index, isLastQuestion = index >= state.questions.lastIndex)
        }
    }

    fun previousQuestion() {
        _quizState.update { state ->
            val index = (state.currentQuestionIndex - 1).coerceAtLeast(0)
            state.copy(currentQuestionIndex = index, isLastQuestion = index >= state.questions.lastIndex)
        }
    }

    /**
     * Scores the quiz, records the session, and on a pass completes the module and
     * unlocks apps for the module's reward time. [onResult] receives whether it passed,
     * or null if the quiz could not be submitted.
     */
    fun submitQuiz(onResult: (passed: Boolean?) -> Unit) {
        val moduleId = quizModuleId ?: return // already submitted
        quizModuleId = null
        val state = _quizState.value
        val studyMinutes = (System.currentTimeMillis() - quizStartedAt + 59_999) / 60_000

        viewModelScope.launch {
            try {
                val result = repository.submitQuiz(state, state.selectedAnswers)
                val module = sampleModules.find { it.id == moduleId }

                repository.recordStudySession(
                    name = module?.title ?: "Quiz",
                    minutes = studyMinutes,
                    completed = result.passed,
                    quizScore = result.score
                )

                if (result.passed) {
                    repository.completeModule(moduleId)
                    BlockingState.grantFreeTime(module?.rewardMinutes ?: result.rewardMinutes, sessionActive = isFocusActiveNow())
                } else {
                    showError("You scored ${result.score}%. You need ${state.passingScore}% to earn free time.")
                }

                _quizState.value = QuizState()
                onResult(result.passed)
            } catch (e: Exception) {
                Log.e("StudyViewModel", "Failed to submit quiz", e)
                quizModuleId = moduleId
                showError("Couldn't submit the quiz. Please try again.")
                onResult(null)
            }
        }
    }

    // ==================== Module ====================

    fun showLockedMessage() {
        showError("Complete prerequisite modules first to unlock this one.")
    }

    // ==================== Content ====================

    fun getContent(contentId: String): StateFlow<StudyContent> {
        return repository.getContent(contentId)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                StudyContent(
                    id = contentId,
                    title = "Loading...",
                    body = "",
                    requiredReadTime = 1,
                    readTime = 1,
                    subject = ""
                )
            )
    }

    fun markContentAsRead(contentId: String) {
        viewModelScope.launch {
            repository.markContentAsRead(contentId)
        }
    }

    // ==================== Emergency ====================

    fun setEmergencyPin(pin: String) {
        viewModelScope.launch {
            repository.setEmergencyPin(pin)
        }
    }

    /** Emergency unlocks left today; null until loaded. */
    val emergencyUnlocksLeftToday: StateFlow<Int?> = repository.emergencyUnlocksLeftToday
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val emergencyUnlocker = EmergencyUnlocker(repository)

    /** See [EmergencyUnlocker.unlock]. */
    fun emergencyUnlock(pin: String, onResult: (EmergencyUnlockResult) -> Unit) {
        viewModelScope.launch { onResult(emergencyUnlocker.unlock(pin)) }
    }

    // ==================== Export ====================

    fun exportData(): String {
        return runBlocking { repository.exportData() }
    }

    // ==================== Lifecycle ====================

    override fun onCleared() {
        super.onCleared()
        studyTimerJob?.cancel()
    }

    companion object {
        const val EMERGENCY_UNLOCK_MINUTES = EmergencyUnlocker.MINUTES
    }
}

data class StudyUiState(
    val isStudying: Boolean = false,
    val elapsedStudyTime: String = "00:00:00",
    val dailyQuote: String = "The expert in anything was once a beginner."
)
