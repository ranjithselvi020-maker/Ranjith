package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AiWizardState {
    object Idle : AiWizardState
    object Generating : AiWizardState
    data class Success(val scenes: List<VideoScene>) : AiWizardState
    data class Error(val message: String) : AiWizardState
}

sealed interface ExportState {
    object Idle : ExportState
    data class Exporting(val progress: Float, val stage: String) : ExportState
    data class Success(val mockPath: String) : ExportState
    data class Error(val message: String) : ExportState
}

class VideoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProjectRepository(db.projectDao())

    // All historic projects
    val projectsList: StateFlow<List<VideoProject>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current active editing state
    private val _activeProject = MutableStateFlow<VideoProject?>(null)
    val activeProject: StateFlow<VideoProject?> = _activeProject.asStateFlow()

    private val _activeScenes = MutableStateFlow<List<VideoScene>>(emptyList())
    val activeScenes: StateFlow<List<VideoScene>> = _activeScenes.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayTimeMs = MutableStateFlow(0)
    val currentPlayTimeMs: StateFlow<Int> = _currentPlayTimeMs.asStateFlow()

    private val _activeSceneIndex = MutableStateFlow(0)
    val activeSceneIndex: StateFlow<Int> = _activeSceneIndex.asStateFlow()

    // AI wizard state
    private val _aiState = MutableStateFlow<AiWizardState>(AiWizardState.Idle)
    val aiState: StateFlow<AiWizardState> = _aiState.asStateFlow()

    // Export status
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    // Busy polishing text
    private val _isPolishingText = MutableStateFlow(false)
    val isPolishingText: StateFlow<Boolean> = _isPolishingText.asStateFlow()

    // Background player ticker
    private var playerJob: Job? = null

    init {
        // Automatically preload the most recent project or create a default one
        viewModelScope.launch {
            repository.allProjects.first().let { list ->
                if (list.isNotEmpty()) {
                    loadProject(list.first())
                } else {
                    createNewProject("My Travel Vlog", "DEFAULT", "NONE")
                }
            }
        }
    }

    fun loadProject(project: VideoProject) {
        stopPlayback()
        _activeProject.value = project
        _activeScenes.value = project.getScenes()
        _activeSceneIndex.value = 0
        _currentPlayTimeMs.value = 0
    }

    fun createNewProject(title: String, moodName: String, musicTrackName: String) {
        stopPlayback()
        viewModelScope.launch {
            val defaultScenes = listOf(
                VideoScene("scene_1", "nature_lake", 4, "Welcome to my journey! 🏔️", 1.0f, "A beautiful tranquil lake surrounded by mountain peaks at sunrise"),
                VideoScene("scene_2", "cozy_cafe", 4, "Time for a quick coffee break ☕", 1.0f, "Close-up of hot steam rising gently from a mug in a coffee shop"),
                VideoScene("scene_3", "city_night", 5, "Exploring the energetic neon lights! ✨", 1.0f, "Slightly wet urban street with multi-colored lights reflecting on ground")
            )
            val scenesJson = VideoProject.createScenesJson(defaultScenes)
            val proj = VideoProject(
                title = if (title.isBlank()) "Untitled Project" else title,
                moodName = moodName,
                musicTrackName = musicTrackName,
                scenesJson = scenesJson
            )
            val id = repository.insertProject(proj)
            loadProject(proj.copy(id = id.toInt()))
        }
    }

    fun updateProjectTitle(newTitle: String) {
        val proj = _activeProject.value ?: return
        val updated = proj.copy(title = newTitle)
        _activeProject.value = updated
        saveProjectToDb(updated)
    }

    fun updateMood(moodName: String) {
        val proj = _activeProject.value ?: return
        val updated = proj.copy(moodName = moodName)
        _activeProject.value = updated
        saveProjectToDb(updated)
    }

    fun updateMusic(musicName: String) {
        val proj = _activeProject.value ?: return
        val updated = proj.copy(musicTrackName = musicName)
        _activeProject.value = updated
        saveProjectToDb(updated)
    }

    fun saveProjectToDb(project: VideoProject) {
        viewModelScope.launch {
            repository.updateProject(project)
        }
    }

    // --- Scene Operations ---

    fun addScene(scene: VideoScene) {
        val currentList = _activeScenes.value.toMutableList()
        currentList.add(scene)
        updateScenesList(currentList)
    }

    fun removeScene(index: Int) {
        val currentList = _activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            if (currentList.isEmpty()) {
                currentList.add(VideoScene("scene_fallback", "nature_lake", 4, "Start telling your story!", 1.0f))
            }
            updateScenesList(currentList)
            if (_activeSceneIndex.value >= currentList.size) {
                _activeSceneIndex.value = currentList.size - 1
            }
        }
    }

    fun updateScene(index: Int, updated: VideoScene) {
        val currentList = _activeScenes.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = updated
            updateScenesList(currentList)
        }
    }

    fun moveSceneLeft(index: Int) {
        if (index <= 0) return
        val currentList = _activeScenes.value.toMutableList()
        val temp = currentList[index]
        currentList[index] = currentList[index - 1]
        currentList[index - 1] = temp
        updateScenesList(currentList)
        if (_activeSceneIndex.value == index) {
            _activeSceneIndex.value = index - 1
        } else if (_activeSceneIndex.value == index - 1) {
            _activeSceneIndex.value = index
        }
    }

    fun moveSceneRight(index: Int) {
        if (index >= _activeScenes.value.size - 1) return
        val currentList = _activeScenes.value.toMutableList()
        val temp = currentList[index]
        currentList[index] = currentList[index + 1]
        currentList[index + 1] = temp
        updateScenesList(currentList)
        if (_activeSceneIndex.value == index) {
            _activeSceneIndex.value = index + 1
        } else if (_activeSceneIndex.value == index + 1) {
            _activeSceneIndex.value = index
        }
    }

    private fun updateScenesList(newList: List<VideoScene>) {
        _activeScenes.value = newList
        val proj = _activeProject.value ?: return
        val updated = proj.copy(scenesJson = VideoProject.createScenesJson(newList))
        _activeProject.value = updated
        saveProjectToDb(updated)
    }

    // --- Playback Controls ---

    fun startPlayback() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        
        val totalMs = getTotalDurationSeconds() * 1000
        if (_currentPlayTimeMs.value >= totalMs) {
            _currentPlayTimeMs.value = 0
            _activeSceneIndex.value = 0
        }

        playerJob = viewModelScope.launch {
            val stepIntervalMs = 50
            while (_isPlaying.value) {
                delay(stepIntervalMs.toLong())
                val newTime = _currentPlayTimeMs.value + stepIntervalMs
                val limit = getTotalDurationSeconds() * 1000
                if (newTime >= limit) {
                    _currentPlayTimeMs.value = limit
                    _isPlaying.value = false
                    break
                } else {
                    _currentPlayTimeMs.value = newTime
                    recalculateActiveSceneIndex(newTime)
                }
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playerJob?.cancel()
    }

    fun stopPlayback() {
        pausePlayback()
        _currentPlayTimeMs.value = 0
        _activeSceneIndex.value = 0
    }

    fun seekToTimeMs(ms: Int) {
        val totalMs = getTotalDurationSeconds() * 1000
        val clamped = ms.coerceIn(0, totalMs)
        _currentPlayTimeMs.value = clamped
        recalculateActiveSceneIndex(clamped)
    }

    fun selectSceneDirectly(index: Int) {
        val scenes = _activeScenes.value
        if (index in scenes.indices) {
            _activeSceneIndex.value = index
            // Seek playback progress to the start of this scene
            var cumSec = 0
            for (i in 0 until index) {
                cumSec += scenes[i].durationSeconds
            }
            _currentPlayTimeMs.value = cumSec * 1000
        }
    }

    fun getTotalDurationSeconds(): Int {
        return _activeScenes.value.sumOf { it.durationSeconds }
    }

    private fun recalculateActiveSceneIndex(timeMs: Int) {
        val scenes = _activeScenes.value
        var cumulativeMs = 0
        for (i in scenes.indices) {
            val sceneMs = scenes[i].durationSeconds * 1000
            if (timeMs >= cumulativeMs && timeMs < cumulativeMs + sceneMs) {
                _activeSceneIndex.value = i
                return
            }
            cumulativeMs += sceneMs
        }
        // Fallback for end of video
        if (scenes.isNotEmpty() && timeMs >= cumulativeMs) {
            _activeSceneIndex.value = scenes.size - 1
        }
    }

    // --- Gemini Interactive Features ---

    /**
     * Run the AI storyboard assistant
     */
    fun runAiStoryAssistant(idea: String, type: String, totalLength: Int) {
        _aiState.value = AiWizardState.Generating
        viewModelScope.launch {
            GeminiHelper.generateStoryboard(idea, type, totalLength)
                .onSuccess { scenes ->
                    _aiState.value = AiWizardState.Success(scenes)
                }
                .onFailure { error ->
                    _aiState.value = AiWizardState.Error(error.message ?: "An unknown AI error occurred")
                }
        }
    }

    fun applyAiStoryboard(scenes: List<VideoScene>, title: String) {
        stopPlayback()
        viewModelScope.launch {
            val scenesJson = VideoProject.createScenesJson(scenes)
            val updatedTitle = "AI: $title"
            val proj = VideoProject(
                title = updatedTitle,
                moodName = "DEFAULT",
                musicTrackName = "NONE",
                scenesJson = scenesJson
            )
            val id = repository.insertProject(proj)
            loadProject(proj.copy(id = id.toInt()))
            _aiState.value = AiWizardState.Idle
        }
    }

    fun clearAiWizardState() {
        _aiState.value = AiWizardState.Idle
    }

    /**
     * Call Gemini to polish the caption of the active scene
     */
    fun polishActiveSceneCaption(sceneIndex: Int, style: String, onFinished: (String) -> Unit) {
        val scene = _activeScenes.value.getOrNull(sceneIndex) ?: return
        _isPolishingText.value = true
        viewModelScope.launch {
            GeminiHelper.polishCaption(scene.captionText, style)
                .onSuccess { polished ->
                    updateScene(sceneIndex, scene.copy(captionText = polished))
                    onFinished(polished)
                }
                .onFailure {
                    // Fallback to capitalizing if fails
                    val basicCapitalized = scene.captionText.uppercase()
                    updateScene(sceneIndex, scene.copy(captionText = basicCapitalized))
                    onFinished(basicCapitalized)
                }
            _isPolishingText.value = false
        }
    }

    /**
     * simulated high-fidelity export renderer
     */
    fun runExportVideo() {
        stopPlayback()
        _exportState.value = ExportState.Exporting(0.0f, "Initializing Render Engine...")
        viewModelScope.launch {
            val stages = listOf(
                Pair(0.15f, "Decompressing video media layers..."),
                Pair(0.35f, "Stitching transition frames..."),
                Pair(0.60f, "Injecting overlay titles & emojis..."),
                Pair(0.80f, "Mixing high-fidelity background music tracker..."),
                Pair(0.95f, "Muxing video/audio rendering pipelines..."),
                Pair(1.00f, "Finalizing package encoding...")
            )

            for (stage in stages) {
                var currentProg = _exportState.value.let {
                    if (it is ExportState.Exporting) it.progress else 0.0f
                }
                val targetProg = stage.first
                while (currentProg < targetProg) {
                    delay(100)
                    currentProg += 0.05f
                    if (currentProg > targetProg) currentProg = targetProg
                    _exportState.value = ExportState.Exporting(currentProg, stage.second)
                }
                delay(200)
            }

            val proj = _activeProject.value
            if (proj != null) {
                val fileName = "ezvideo_${System.currentTimeMillis()}.mp4"
                val updatedProj = proj.copy(isExported = true, exportedFilePath = fileName)
                _activeProject.value = updatedProj
                saveProjectToDb(updatedProj)
                _exportState.value = ExportState.Success(fileName)
            } else {
                _exportState.value = ExportState.Error("No active project to export.")
            }
        }
    }

    fun dismissExport() {
        _exportState.value = ExportState.Idle
    }

    fun deleteProject(project: VideoProject) {
        viewModelScope.launch {
            repository.deleteProject(project)
            // Reload most recent project remaining or create a new default
            val remaining = repository.allProjects.first()
            if (remaining.isNotEmpty()) {
                loadProject(remaining.first())
            } else {
                createNewProject("My Travel Vlog", "DEFAULT", "NONE")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerJob?.cancel()
    }
}
