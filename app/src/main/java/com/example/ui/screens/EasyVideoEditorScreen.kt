package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.VideoProject
import com.example.data.VideoScene
import com.example.ui.viewmodel.AiWizardState
import com.example.ui.viewmodel.ExportState
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// --- Visual Presets definitions ---
data class PresetDetail(
    val id: String,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val primaryColor: Color,
    val secondaryColor: Color
)

val ScenePresets = listOf(
    PresetDetail("nature_lake", "Scenic Lake", "Tranquil mountain lake sunrise", Icons.Rounded.Terrain, Color(0xFF1E3A8A), Color(0xFF10B981)),
    PresetDetail("city_night", "Neon Street", "Active city neon light paths", Icons.Rounded.LocationCity, Color(0xFF0F172A), Color(0xFFEC4899)),
    PresetDetail("tech_close_up", "Sleek Gadget", "Close-up tech scanning details", Icons.Rounded.Devices, Color(0xFF1E293B), Color(0xFF3B82F6)),
    PresetDetail("cozy_cafe", "Steam Cafe", "Hot coffee mug near keyboard", Icons.Rounded.LocalCafe, Color(0xFF78350F), Color(0xFFF59E0B)),
    PresetDetail("sports_run", "Race Track", "Fast horizontal sunset lines", Icons.Rounded.DirectionsRun, Color(0xFF7C2D12), Color(0xFFEF4444)),
    PresetDetail("cosmic_space", "Deep Cosmos", "Swirling gaseous purple nebula", Icons.Rounded.Tonality, Color(0xFF2E1065), Color(0xFF8B5CF6)),
    PresetDetail("abstract_shapes", "Geometry", "Pulsing multi-color shapes", Icons.Rounded.Category, Color(0xFF064E3B), Color(0xFFF43F5E)),
    PresetDetail("retro_clouds", "VHS Clouds", "Cinematic pastel pink skies", Icons.Rounded.CloudQueue, Color(0xFFBE185D), Color(0xFFF472B6))
)

// --- Aesthetic Mood filter profiles ---
data class MoodProfile(
    val id: String,
    val name: String,
    val description: String,
    val textStyle: FontFamily,
    val textColor: Color,
    val overlayColorAndAlpha: Color,
    val vignetteAlpha: Float,
    val hasBorder: Boolean = false,
    val letterboxHeight: Float = 0f,
    val dateOverlay: Boolean = false,
    val chromaticAberration: Boolean = false
)

val MoodFilters = mapOf(
    "DEFAULT" to MoodProfile("DEFAULT", "Normal", "Standard clean rendering profile", FontFamily.Default, Color.White, Color.Transparent, 0.0f),
    "CINEMATIC" to MoodProfile("CINEMATIC", "Cinema 4K", "Cinematic wide display with movie borders", FontFamily.Serif, Color(0xFFFFFAF0), Color(0xFF6B21A8).copy(alpha = 0.05f), 0.4f, hasBorder = true, letterboxHeight = 24f),
    "RETRO" to MoodProfile("RETRO", "1995 VHS", "Vintage analog CRT tape filter with scanlines", FontFamily.Monospace, Color(0xFF00FFCC), Color(0xFFD97706).copy(alpha = 0.1f), 0.2f, dateOverlay = true, chromaticAberration = true),
    "CYBERPUNK" to MoodProfile("CYBERPUNK", "Neo Tokyo", "High contrast neon cyan and magenta cast", FontFamily.SansSerif, Color(0xFFFF0055), Color(0xFF06B6D4).copy(alpha = 0.15f), 0.7f),
    "LOFI" to MoodProfile("LOFI", "Lofi Chill", "Soft, warm pastel film grain effect", FontFamily.Serif, Color(0xFFFDE047), Color(0xFFEC4899).copy(alpha = 0.08f), 0.3f),
    "POP" to MoodProfile("POP", "Electric Pop", "Vibrant flashing outline speedway style", FontFamily.Cursive, Color(0xFF3B82F6), Color(0xFFEF4444).copy(alpha = 0.08f), 0.1f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyVideoEditorScreen(
    viewModel: VideoEditorViewModel,
    onNavigateToHistory: () -> Unit = {}
) {
    val activeProject by viewModel.activeProject.collectAsState()
    val activeScenes by viewModel.activeScenes.collectAsState()
    val activeSceneIndex by viewModel.activeSceneIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPlayTimeMs by viewModel.currentPlayTimeMs.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val isPolishingText by viewModel.isPolishingText.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State controlling sheet visibility
    var showAiWizard by remember { mutableStateOf(false) }
    var showProjectSelector by remember { mutableStateOf(false) }
    var showAddSceneDialog by remember { mutableStateOf(false) }

    val activeScene = activeScenes.getOrNull(activeSceneIndex) ?: VideoScene("fallback", "nature_lake", 4, "")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = activeProject?.title ?: "Easy Video Editor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) Color.Green else Color.DarkGray)
                            )
                            Text(
                                text = if (isPlaying) "Playing live preview" else "Editor draft paused",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCAC4D0))
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showProjectSelector = true }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Projects List", tint = Color(0xFFCAC4D0))
                    }
                },
                actions = {
                    // Export trigger
                    Button(
                        onClick = { viewModel.runExportVideo() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF211F26),
                    titleContentColor = Color(0xFFE6E1E5)
                )
            )
        },
        bottomBar = {
            // Elegant, minimalist bottom wizard deck offering quick access to AI storyboarding help
            Surface(
                color = Color(0xFF211F26),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .border(BorderStroke(1.dp, Color(0xFF4A4458)))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(Color(0xFF332D41), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF4A4458), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFD0BCFF).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AI Editor Suggestion",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), letterSpacing = 1.sp)
                            )
                            Text(
                                text = "Auto-trim silences and draft custom outline?",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE6E1E5))
                            )
                        }
                    }

                    // Smart sparkly AI director trigger
                    Button(
                        onClick = { showAiWizard = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = Color(0xFF1C1B1F) // Immersive deep charcoal dark Background
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ----------------------------------------------------
            // SECTION 1: DYNAMIC LIVE PLAYER PREVIEW MONITOR
            // ----------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF4A4458), RoundedCornerShape(24.dp))
            ) {
                val currentMood = activeProject?.moodName ?: "DEFAULT"
                val moodProfile = MoodFilters[currentMood] ?: MoodFilters["DEFAULT"]!!

                // Real-time animated composition preview matching the VisualPreset
                VideoPreviewMonitor(
                    visualPresetId = activeScene.visualPresetId,
                    captionText = activeScene.captionText,
                    stickerId = activeScene.stickerId,
                    isPlaying = isPlaying,
                    moodProfile = moodProfile,
                    currentPlayTimeMs = currentPlayTimeMs,
                    modifier = Modifier.fillMaxSize()
                )

                // Time progress overlays
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val currentSec = currentPlayTimeMs / 1000
                    val totalSec = viewModel.getTotalDurationSeconds()
                    Text(
                        text = String.format("%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60),
                        style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontFamily = FontFamily.Monospace)
                    )
                }

                // Scene indicator label
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Scene ${activeSceneIndex + 1}/${activeScenes.size}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
            }

            // ----------------------------------------------------
            // PLAYER SEEKER AND PLAYBACK TIMELINE CONTROLLER
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause core button
                IconButton(
                    onClick = {
                        if (isPlaying) viewModel.pausePlayback() else viewModel.startPlayback()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF4A4458), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Custom timeline scrub slider
                Slider(
                    value = currentPlayTimeMs.toFloat(),
                    onValueChange = { viewModel.seekToTimeMs(it.toInt()) },
                    valueRange = 0f..(viewModel.getTotalDurationSeconds() * 1000).toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFD0BCFF),
                        inactiveTrackColor = Color(0xFF4A4458),
                        thumbColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ----------------------------------------------------
            // SECTION 2: THE "EASY SLATE" INLINE STORY SCENE BOARD
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎬 Video Storyboard Layout",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                )
                Text(
                    text = "Tap cards to preview",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCAC4D0))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal grid of scenes (The visual beginner timeline)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(activeScenes) { index, scene ->
                    val preset = ScenePresets.firstOrNull { it.id == scene.visualPresetId } ?: ScenePresets[0]
                    val isSelected = activeSceneIndex == index

                    StorySceneCard(
                        scene = scene,
                        preset = preset,
                        isSelected = isSelected,
                        index = index,
                        onSelect = { viewModel.selectSceneDirectly(index) },
                        onMoveLeft = { viewModel.moveSceneLeft(index) },
                        onMoveRight = { viewModel.moveSceneRight(index) },
                        onDelete = {
                            viewModel.removeScene(index)
                            scope.launch { snackbarHostState.showSnackbar("Scene ${index + 1} removed") }
                        }
                    )
                }

                // Quick add scene card trigger
                item {
                    Card(
                        onClick = { showAddSceneDialog = true },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        border = BorderStroke(1.dp, Color(0xFF4A4458)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(110.dp)
                            .height(115.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(Icons.Rounded.AddCircle, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Add Scene", style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------
            // SECTION 3: QUICK SCENE CONTENT INSPECTOR
            // ----------------------------------------------------
            Text(
                text = "📝 Edit Selected Scene Content",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5)),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Caption Text field & AI Polisher in same panel
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF4A4458)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Scene Caption overlay (displays over video)",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCAC4D0))
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = activeScene.captionText,
                        onValueChange = { txt ->
                            viewModel.updateScene(activeSceneIndex, activeScene.copy(captionText = txt))
                        },
                        placeholder = { Text("E.g. What a beautiful place!", color = Color.Gray) },
                        maxLines = 2,
                        textStyle = TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF4A4458)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // AI Text Optimizer bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖 AI Word Magic:", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFD0BCFF)))
                            if (isPolishingText) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFD0BCFF)
                                )
                            }
                        }

                        // Style modifiers for polishing caption
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Funny", "Epic", "Cute", "TikTok").forEach { styleOpt ->
                                SuggestionChip(
                                    onClick = {
                                        viewModel.polishActiveSceneCaption(activeSceneIndex, styleOpt) { polished ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("AI Polished: \"$polished\"")
                                            }
                                        }
                                    },
                                    label = { Text(styleOpt, fontSize = 11.sp, color = Color(0xFFE6E1E5)) },
                                    enabled = !isPolishingText,
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        labelColor = Color(0xFFCAC4D0),
                                        containerColor = Color(0xFF211F26)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ----------------------------------------------------
            // SECTION 4: ATMOSPHERE CONTROLLER (MOODS & SOUNDTRACKS)
            // ----------------------------------------------------
            Text(
                text = "🎨 Video Aesthetic & Tracks (Atmosphere)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5)),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF4A4458)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Color Mood Filter Profile",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFCAC4D0))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mood selector items
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(items = MoodFilters.keys.toList()) { key ->
                            val mood = MoodFilters[key]!!
                            val isSelected = activeProject?.moodName == key
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateMood(key) },
                                label = { Text(mood.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD0BCFF),
                                    selectedLabelColor = Color(0xFF381E72),
                                    labelColor = Color(0xFFCAC4D0)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "2. Background Soundtrack",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFCAC4D0))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Music selectors
                    val trackOptions = listOf(
                        "NONE" to "🔇 Silent (No sound)",
                        "LOFI_BEATER" to "🎧 Lo-fi Coffee Chill",
                        "ACOUSTIC" to "🎸 Sunrise Acoustic",
                        "SYNTHWAVE" to "⚡ Neon Cyber Synth",
                        "ORCHESTRAL" to "🎻 Epic Adventure"
                    )

                    trackOptions.forEach { (id, name) ->
                        val isTrackSelected = activeProject?.musicTrackName == id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isTrackSelected) Color(0xFFD0BCFF).copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.updateMusic(id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isTrackSelected,
                                onClick = { viewModel.updateMusic(id) },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD0BCFF))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, color = if (isTrackSelected) Color.White else Color(0xFFCAC4D0), fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- DIALOGS AND BOTTOM SHEETS ---

    // 1. AI story wizard deck overlay
    if (showAiWizard) {
        AiStoryWizardDialog(
            aiState = aiState,
            onGenerate = { idea, type, length -> viewModel.runAiStoryAssistant(idea, type, length) },
            onApply = { scenes, title ->
                viewModel.applyAiStoryboard(scenes, title)
                showAiWizard = false
            },
            onDismiss = {
                viewModel.clearAiWizardState()
                showAiWizard = false
            }
        )
    }

    // 2. Project List / Project Switcher
    if (showProjectSelector) {
        val projects by viewModel.projectsList.collectAsState()
        ProjectSelectorDialog(
            activeProjectId = activeProject?.id ?: 0,
            projects = projects,
            onSelect = { proj ->
                viewModel.loadProject(proj)
                showProjectSelector = false
            },
            onCreateNew = { title, mood, music ->
                viewModel.createNewProject(title, mood, music)
                showProjectSelector = false
            },
            onDelete = { proj -> viewModel.deleteProject(proj) },
            onDismiss = { showProjectSelector = false }
        )
    }

    // 3. Add Custom Scene Dialog picker
    if (showAddSceneDialog) {
        AddSceneDialog(
            onAdd = { presetId, duration, caption ->
                viewModel.addScene(
                    VideoScene(
                        id = "user_scene_${System.currentTimeMillis()}",
                        visualPresetId = presetId,
                        durationSeconds = duration,
                        captionText = caption
                    )
                )
                showAddSceneDialog = false
            },
            onDismiss = { showAddSceneDialog = false }
        )
    }

    // 4. Export Progress overlay simulator
    if (exportState != ExportState.Idle) {
        ExportingProgressDialog(
            exportState = exportState,
            onDismiss = { viewModel.dismissExport() }
        )
    }
}

// --------------------------------------------------------------------
// COMPOSTABLE DEFINITIONS: STORY SCENE CARD
// --------------------------------------------------------------------
@Composable
fun StorySceneCard(
    scene: VideoScene,
    preset: PresetDetail,
    isSelected: Boolean,
    index: Int,
    onSelect: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF332D41) else Color(0xFF2B2930)
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFFD0BCFF) else Color(0xFF4A4458)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(135.dp)
            .shadow(if (isSelected) 4.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header: Title index & Duration label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scene ${index + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color.Gray
                    )
                )
                Text(
                    text = "${scene.durationSeconds}s",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Body Thumbnail Simulation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(preset.primaryColor, preset.secondaryColor),
                            start = Offset(0f, 0f),
                            end = Offset(250f, 250f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = preset.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                )

                // Miniature Caption Outline
                if (scene.captionText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = scene.captionText,
                            color = Color.White,
                            fontSize = 7.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Timeline Operations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(
                        onClick = onMoveLeft,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowLeft, contentDescription = "Move Left", tint = Color.LightGray)
                    }
                    IconButton(
                        onClick = onMoveRight,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowRight, contentDescription = "Move Right", tint = Color.LightGray)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// --------------------------------------------------------------------
// COMPOSTABLE DEFINITIONS: LIVE VIDEO RENDERER SIMULATOR
// --------------------------------------------------------------------
@Composable
fun VideoPreviewMonitor(
    visualPresetId: String,
    captionText: String,
    stickerId: String,
    isPlaying: Boolean,
    moodProfile: MoodProfile,
    currentPlayTimeMs: Int,
    modifier: Modifier = Modifier
) {
    // Generate active dynamic timing coordinates for anims
    val infiniteTransition = rememberInfiniteTransition(label = "player_effects")
    
    // Slow oscillating tick driving shader dynamics
    val waveOscillator by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = Math.PI.toFloat() * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "oscillator"
    )

    // Constant high speed spinning modifier
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                clip = true
            }
            .drawBehind {
                val waveFactor1 = if (isPlaying) sin(waveOscillator.toDouble()).toFloat() else 0.5f
                val waveFactor2 = if (isPlaying) cos(waveOscillator.toDouble()).toFloat() else 0.2f

                when (visualPresetId) {
                    "nature_lake" -> {
                        // Sunrise sky gradient
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF0F172A), Color(0xFFF43F5E), Color(0xFFFBBF24)),
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height * 0.6f)
                            )
                        )

                        // Mountains
                        drawPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, size.height * 0.61f)
                                lineTo(size.width * 0.25f, size.height * 0.35f)
                                lineTo(size.width * 0.5f, size.height * 0.61f)
                                lineTo(size.width * 0.75f, size.height * 0.42f)
                                lineTo(size.width, size.height * 0.61f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            },
                            color = Color(0xFF1E293B)
                        )

                        // Sun
                        drawCircle(
                            color = Color(0xFFFDE047),
                            radius = 35.dp.toPx() + waveFactor1 * 3.dp.toPx(),
                            center = Offset(size.width * 0.65f, size.height * 0.38f + waveFactor2 * 5.dp.toPx())
                        )

                        // Lake water floor with moving ripple markers
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF065F46), Color(0xFF10B981)),
                                start = Offset(0f, size.height * 0.61f),
                                end = Offset(0f, size.height)
                            ),
                            topLeft = Offset(0f, size.height * 0.61f),
                            size = Size(size.width, size.height * 0.39f)
                        )

                        // Light reflections
                        for (i in 0..4) {
                            val ry = size.height * (0.65f + i * 0.07f)
                            val rxWidth = (40.dp.toPx() + i * 15.dp.toPx()) + waveFactor1 * 12.dp.toPx()
                            drawRoundRect(
                                color = Color(0xFFFBBF24).copy(alpha = 0.6f - i * 0.1f),
                                topLeft = Offset(size.width * 0.61f - rxWidth / 2, ry),
                                size = Size(rxWidth, 4.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                            )
                        }
                    }

                    "city_night" -> {
                        // Night slate backdrop
                        drawRect(color = Color(0xFF0F172A))

                        // Tall dark skyscrapers silhouettes
                        for (i in 0..5) {
                            val w = size.width * 0.20f
                            val lx = i * w
                            val h = size.height * (0.35f + (i % 3) * 0.12f)
                            drawRect(
                                color = Color(0xFF1E293B),
                                topLeft = Offset(lx, size.height - h),
                                size = Size(w - 2.dp.toPx(), h)
                            )

                            // Tiny skyscraper windows
                            val rows = 5
                            val cols = 2
                            for (r in 0 until rows) {
                                for (c in 0 until cols) {
                                    if ((r + c + i) % 3 != 0) {
                                        drawRect(
                                            color = Color(0xFFFDE047).copy(alpha = 0.8f),
                                            topLeft = Offset(lx + 8.dp.toPx() + c * 10.dp.toPx(), size.height - h + 12.dp.toPx() + r * 14.dp.toPx()),
                                            size = Size(4.dp.toPx(), 4.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }

                        // Highway road tracks in 3D perspective
                        val roadPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.4f, size.height * 0.70f)
                            lineTo(size.width * 0.6f, size.height * 0.70f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path = roadPath, color = Color(0xFF020617))

                        // Speeding laser traffic lanes
                        val pathCount = 12
                        for (p in 0 until pathCount) {
                            val progressOffset = (p.toFloat() / pathCount + (if (isPlaying) waveOscillator / 5f else 0f)) % 1.0f
                            val startX = size.width * (0.4f + progressOffset * 0.6f)
                            val startY = size.height * (0.70f + progressOffset * 0.30f)
                            val color = if (p % 2 == 0) Color(0xFFEC4899) else Color(0xFF06B6D4)
                            val dWidth = 4.dp.toPx() + progressOffset * 10.dp.toPx()

                            drawCircle(
                                color = color.copy(alpha = (1.0f - progressOffset)),
                                radius = dWidth,
                                center = Offset(startX, startY)
                            )
                        }
                    }

                    "tech_close_up" -> {
                        // Techno slate backdrop
                        drawRect(color = Color(0xFF090D1A))

                        // Draw silicon glowing circuit channels
                        val borderGap = 40.dp.toPx()
                        drawRect(
                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                            topLeft = Offset(borderGap, borderGap),
                            size = Size(size.width - borderGap * 2, size.height - borderGap * 2)
                        )

                        // Circular core matrix visual
                        drawCircle(
                            color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                            radius = 65.dp.toPx() + waveFactor2 * 10.dp.toPx(),
                            center = Offset(size.width / 2, size.height / 2)
                        )
                        drawCircle(
                            color = Color(0xFF3B82F6).copy(alpha = 0.6f),
                            radius = 45.dp.toPx(),
                            center = Offset(size.width / 2, size.height / 2),
                            style = Stroke(2.dp.toPx())
                        )

                        // Glowing electrical pulse nodes sliding on circuit lines
                        val nodesCount = 6
                        for (n in 0 until nodesCount) {
                            val angle = (n.toFloat() * (Math.PI / 3) + (if (isPlaying) waveOscillator else 0f))
                            val nx = (size.width / 2) + cos(angle).toFloat() * 45.dp.toPx()
                            val ny = (size.height / 2) + sin(angle).toFloat() * 45.dp.toPx()
                            drawCircle(
                                color = Color(0xFF60A5FA),
                                radius = 6.dp.toPx(),
                                center = Offset(nx, ny)
                            )
                        }
                    }

                    "cozy_cafe" -> {
                        // Warm wood & window light
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF78350F), Color(0xFF451A03)),
                                center = Offset(size.width / 2, size.height / 2),
                                radius = size.width * 0.8f
                            )
                        )

                        // Flat outline mug
                        val mx = size.width / 2
                        val my = size.height * 0.65f
                        val mw = 55.dp.toPx()
                        val mh = 50.dp.toPx()

                        // Cafe steam rising animations
                        for (s in 1..3) {
                            val steamProg = (s.toFloat() / 3f + (if (isPlaying) waveOscillator / 6f else 0f)) % 1.0f
                            val sxOffset = sin(steamProg * Math.PI * 4).toFloat() * 8.dp.toPx()
                            val sy = my - 15.dp.toPx() - (steamProg * 50.dp.toPx())
                            drawCircle(
                                color = Color.White.copy(alpha = (1f - steamProg) * 0.4f),
                                radius = (4.dp.toPx() + steamProg * 6.dp.toPx()),
                                center = Offset(mx + sxOffset - 15.dp.toPx() + s * 10.dp.toPx(), sy)
                            )
                        }

                        // Drawing Mug Cup & handle
                        drawRoundRect(
                            color = Color(0xFFF59E0B),
                            topLeft = Offset(mx - mw / 2, my),
                            size = Size(mw, mh),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color(0xFFFBBF24),
                            topLeft = Offset(mx + mw / 2 - 4.dp.toPx(), my + 10.dp.toPx()),
                            size = Size(14.dp.toPx(), 25.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                            style = Stroke(4.dp.toPx())
                        )
                    }

                    "sports_run" -> {
                        // Running ground sunset backdrop
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF450A0A), Color(0xFF991B1B)),
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height)
                            )
                        )

                        // Diagonal racing track lanes
                        drawPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, size.height * 0.5f)
                                lineTo(size.width, size.height * 0.9f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            },
                            color = Color(0xFFF97316).copy(alpha = 0.4f)
                        )

                        // Speed blur particle bands shifting horizontal
                        for (i in 0..7) {
                            val speedPhase = (i * 0.15f + (if (isPlaying) waveOscillator * 1.5f else 0f)) % 1.0f
                            val lx = size.width * (1.0f - speedPhase)
                            val ly = size.height * (0.2f + (i % 4) * 0.18f)
                            val lwidth = 40.dp.toPx() + (i % 3) * 20.dp.toPx()
                            drawRoundRect(
                                color = Color(0xFFFDE047).copy(alpha = (1.0f - speedPhase) * 0.35f),
                                topLeft = Offset(lx, ly),
                                size = Size(lwidth, 3.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                            )
                        }
                    }

                    "cosmic_space" -> {
                        // Midnight celestial sky background
                        drawRect(color = Color(0xFF030712))

                        // Swirling multi-scale gaseous gas cloud
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.5f), Color.Transparent),
                                center = Offset(size.width / 2 + waveFactor1 * 20.dp.toPx(), size.height / 2 + waveFactor2 * 10.dp.toPx()),
                                radius = 110.dp.toPx()
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFEC4899).copy(alpha = 0.4f), Color.Transparent),
                                center = Offset(size.width / 2 - waveFactor2 * 25.dp.toPx(), size.height / 2 - waveFactor1 * 12.dp.toPx()),
                                radius = 95.dp.toPx()
                            )
                        )

                        // Glowing star coordinates pulsing
                        val stars = 12
                        for (s in 0 until stars) {
                            val spRadius = 3.dp.toPx() + (sin(waveOscillator * 2 + s).toFloat() + 1f) * 1.5.dp.toPx()
                            val sx = size.width * (0.15f + (s * 0.081f) % 0.8f)
                            val sy = size.height * (0.10f + (s * 0.147f) % 0.8f)
                            drawCircle(
                                color = Color.White.copy(alpha = 0.8f),
                                radius = spRadius,
                                center = Offset(sx, sy)
                            )
                        }
                    }

                    "abstract_shapes" -> {
                        // Pitch dark canvas
                        drawRect(color = Color(0xFF022C22))

                        // Glowing rotating polygon geometry
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val rotAngleRad = Math.toRadians((rotationAngle + waveOscillator).toDouble())

                        // Base rotating square
                        val sizeSq = 60.dp.toPx() + waveFactor1 * 8.dp.toPx()
                        drawPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                val corners = 4
                                for (c in 0 until corners) {
                                    val localAng = rotAngleRad + (c * Math.PI / 2)
                                    val px = cx + cos(localAng).toFloat() * sizeSq
                                    val py = cy + sin(localAng).toFloat() * sizeSq
                                    if (c == 0) moveTo(px, py) else lineTo(px, py)
                                }
                                close()
                            },
                            color = Color(0xFFEF4444).copy(alpha = 0.7f)
                        )

                        // Overlapping secondary rotating triangle
                        drawPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                val corners = 3
                                val triangleAng = -rotAngleRad * 1.5
                                for (c in 0 until corners) {
                                    val localAng = triangleAng + (c * 2 * Math.PI / 3)
                                    val px = cx + cos(localAng).toFloat() * (sizeSq * 0.8f)
                                    val py = cy + sin(localAng).toFloat() * (sizeSq * 0.8f)
                                    if (c == 0) moveTo(px, py) else lineTo(px, py)
                                }
                                close()
                            },
                            color = Color(0xFF3B82F6).copy(alpha = 0.7f)
                        )
                    }

                    "retro_clouds" -> {
                        // Sunset pastel sky
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFDA4AF), Color(0xFFF472B6), Color(0xFF86198F)),
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height)
                            )
                        )

                        // Sunset horizon disk
                        drawCircle(
                            color = Color(0xFFFDE047),
                            radius = 50.dp.toPx(),
                            center = Offset(size.width / 2, size.height * 0.7f + waveFactor2 * 3.dp.toPx())
                        )

                        // 3 horizontal cloud rows sliding
                        val cloudsCount = 4
                        for (cl in 0 until cloudsCount) {
                            val cloudOffset = (cl * 0.25f + (if (isPlaying) waveOscillator * 0.05f else 0f)) % 1.0f
                            val cyCloud = size.height * (0.35f + (cl % 2) * 0.15f)
                            val cxCloud = size.width * (1.1f - cloudOffset * 1.3f)

                            drawCircle(
                                color = Color.White.copy(alpha = 0.5f),
                                radius = 24.dp.toPx(),
                                center = Offset(cxCloud, cyCloud)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.5f),
                                radius = 30.dp.toPx(),
                                center = Offset(cxCloud + 18.dp.toPx(), cyCloud + 5.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.5f),
                                radius = 22.dp.toPx(),
                                center = Offset(cxCloud - 18.dp.toPx(), cyCloud + 3.dp.toPx())
                            )
                        }
                    }
                }

                // Apply active mood aesthetic overlays over everything!
                // 1. Vintage CRT VHS scanning bar overlays
                if (moodProfile.id == "RETRO") {
                    val scanProgress = (waveOscillator / (Math.PI * 2).toFloat())
                    val barY = size.height * scanProgress
                    drawRect(
                        color = Color.White.copy(alpha = 0.15f),
                        topLeft = Offset(0f, barY),
                        size = Size(size.width, 10.dp.toPx())
                    )

                    // CRT horizontal phosphor scanlines
                    val lines = 30
                    val gap = size.height / lines
                    for (l in 0 until lines) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.12f),
                            topLeft = Offset(0f, l * gap),
                            size = Size(size.width, 1.dp.toPx())
                        )
                    }
                }

                // 2. Heavy letterbox panoramic overlay bars (CINEMATIC mode)
                if (moodProfile.hasBorder) {
                    val barHeight = moodProfile.letterboxHeight.dp.toPx()
                    // Top letterbox
                    drawRect(color = Color.Black, size = Size(size.width, barHeight))
                    // Bottom letterbox
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight)
                    )
                }

                // 3. Vignette lighting filter
                if (moodProfile.vignetteAlpha > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = moodProfile.vignetteAlpha)),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * 0.72f
                        )
                    )
                }

                // 4. Color mood temperature filter slice
                if (moodProfile.overlayColorAndAlpha != Color.Transparent) {
                    drawRect(color = moodProfile.overlayColorAndAlpha)
                }
            }
    ) {
        // Overlay Title text (centered & beautifully stylized based on Select Mood)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (captionText.isNotBlank()) {
                Text(
                    text = captionText,
                    color = moodProfile.textColor,
                    fontFamily = moodProfile.textStyle,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        // Simulate neon text glowing cast
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = moodProfile.textColor.copy(alpha = 0.8f),
                            offset = Offset(0f, 0f),
                            blurRadius = if (moodProfile.id == "CYBERPUNK") 20f else 6f
                        )
                    ),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Live date stamp overlay (Retro vhs timestamp simulation)
        if (moodProfile.dateOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = if (moodProfile.hasBorder) 28.dp else 16.dp)
            ) {
                Text(
                    text = "JUNE 15, 1995\nAM 04:12:05",
                    fontSize = 11.sp,
                    color = Color(0xFFDDEE00),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 16.dp, vertical = if (moodProfile.hasBorder) 28.dp else 16.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏺️ PLAY" else "⏸️ PAUSED",
                    fontSize = 11.sp,
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --------------------------------------------------------------------
// COMPOSTABLE DEFINITIONS: ADVANCED AI STORY GENERATOR DIALOG
// --------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStoryWizardDialog(
    aiState: AiWizardState,
    onGenerate: (String, String, Int) -> Unit,
    onApply: (List<VideoScene>, String) -> Unit,
    onDismiss: () -> Unit
) {
    var promptInput by remember { mutableStateOf("") }
    var speedMultiplierTarget by remember { mutableStateOf(30) } // Default 30 sec total targeted video length
    var videoTypeSelected by remember { mutableStateOf("Travel Vlog") }

    val videoTypes = listOf("Travel Vlog", "Product Review", "Tutorial / How-To", "TikTok / Reel Reel")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF4A4458)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "AI Video Director Wizard",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Describe your vision and Gemini AI will magically structure a professional story, caption slides, and ideal visual themes!",
                    fontSize = 12.sp,
                    color = Color(0xFFCAC4D0)
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (aiState) {
                    is AiWizardState.Idle -> {
                        // User prompt input controls
                        Text(
                            text = "Describe what your video is about:",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCAC4D0))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            placeholder = { Text("E.g. A quick morning routine walking in Kyoto streets and buying matcha latte", color = Color.DarkGray, fontSize = 13.sp) },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFD0BCFF),
                                unfocusedBorderColor = Color(0xFF4A4458)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Video Type drop options (Cards grid)
                        Text(
                            text = "Select content style:",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCAC4D0))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            videoTypes.chunked(2).forEach { rowList ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    rowList.forEach { type ->
                                        val isSelected = videoTypeSelected == type
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) Color(0xFFD0BCFF) else Color(0xFF4A4458))
                                                .clickable { videoTypeSelected = type }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = type,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF381E72) else Color(0xFFCAC4D0)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Total Target Duration slider
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Desired Total Length:", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCAC4D0)))
                            Text(
                                text = "$speedMultiplierTarget seconds",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                            )
                        }
                        Slider(
                             value = speedMultiplierTarget.toFloat(),
                             onValueChange = { speedMultiplierTarget = it.toInt() },
                             valueRange = 10f..60f,
                             colors = SliderDefaults.colors(
                                 activeTrackColor = Color(0xFFD0BCFF),
                                 inactiveTrackColor = Color(0xFF4A4458),
                                 thumbColor = Color.White
                             )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Generate triggers
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCAC4D0))) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { onGenerate(promptInput, videoTypeSelected, speedMultiplierTarget) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                                enabled = promptInput.isNotBlank()
                            ) {
                                Text("Generate scenes 🪄", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is AiWizardState.Generating -> {
                        // AI generating feedback state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFFD0BCFF), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Gemini is director planning your storyboard...",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Creating beautiful scene outline details",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    is AiWizardState.Success -> {
                        val scenes = aiState.scenes
                        // Preview lists of generated AI templates
                        Text(
                            text = "🎉 Generated Story Outline!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.Green)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Total scenes: ${scenes.size} • Expected length: ${scenes.sumOf { it.durationSeconds }}s", fontSize = 12.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Render preview checklist scrollable list of scenes
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1C1B1F))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            scenes.forEachIndexed { i, sc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFD0BCFF).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "${i + 1}", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sc.captionText,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Preset style: ${sc.visualPresetId.replace("_", " ")} • Duration: ${sc.durationSeconds}s",
                                            fontSize = 10.sp,
                                            color = Color(0xFFCAC4D0)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Controls
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCAC4D0))) {
                                Text("Discard")
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { onApply(scenes, promptInput.take(15)) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72))
                            ) {
                                Text("Apply to Editor ✅", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is AiWizardState.Error -> {
                        // Display error
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.HighlightOff, contentDescription = null, tint = Color.Red, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Storyboard Generation Failed",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = aiState.message,
                                color = Color.Red.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Text("Acknowledge")
                            }
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------
// COMPOSTABLE DEFINITIONS: PROJECT DIALOG SELECTOR
// --------------------------------------------------------------------
@Composable
fun ProjectSelectorDialog(
    activeProjectId: Int,
    projects: List<VideoProject>,
    onSelect: (VideoProject) -> Unit,
    onCreateNew: (title: String, mood: String, music: String) -> Unit,
    onDelete: (VideoProject) -> Unit,
    onDismiss: () -> Unit
) {
    var newProjectTitle by remember { mutableStateOf("") }
    var isCreatingMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF4A4458)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (isCreatingMode) "New Project Details" else "📁 Saved Projects Drawer",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isCreatingMode) "Start editing a brand new creative clip draft" else "Tap a project to inspect and load it",
                    fontSize = 12.sp,
                    color = Color(0xFFCAC4D0)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isCreatingMode) {
                    Text("Project Title label", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCAC4D0)))
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newProjectTitle,
                        onValueChange = { newProjectTitle = it },
                        placeholder = { Text("E.g. Sunday Tokyo Coffee Run", color = Color.Gray) },
                        maxLines = 1,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF4A4458)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { isCreatingMode = false }) {
                            Text("Back", color = Color(0xFFCAC4D0))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newProjectTitle.isNotBlank()) {
                                    onCreateNew(newProjectTitle, "DEFAULT", "NONE")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                            enabled = newProjectTitle.isNotBlank()
                        ) {
                            Text("Create & Open")
                        }
                    }
                } else {
                    // Scrollable list of historic projects
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 240.dp)
                    ) {
                        items(items = projects) { proj ->
                            val isCurrent = proj.id == activeProjectId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCurrent) Color(0xFFD0BCFF).copy(alpha = 0.15f) else Color(0xFF1C1B1F))
                                    .border(1.dp, if (isCurrent) Color(0xFFD0BCFF) else Color(0xFF4A4458), RoundedCornerShape(10.dp))
                                    .clickable { onSelect(proj) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = proj.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${proj.getScenes().size} scenes • Mood: ${proj.moodName}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFCAC4D0)
                                    )
                                }

                                if (projects.size > 1) {
                                    IconButton(
                                        onClick = { onDelete(proj) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onDismiss) {
                            Text("Dismiss", color = Color(0xFFCAC4D0))
                        }
                        Button(
                            onClick = { isCreatingMode = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72))
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Project")
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------
// COMPOSTABLE DEFINITIONS: ADD SCENE DIALOG PANEL
// --------------------------------------------------------------------
@Composable
fun AddSceneDialog(
    onAdd: (presetId: String, duration: Int, caption: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectPresetId by remember { mutableStateOf("nature_lake") }
    var durationSelected by remember { mutableStateOf(4) }
    var captionInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF4A4458)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Add Custom Video Scene",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Select Visual preset block
                Text("Select visual backdrop template:", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCAC4D0)))
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScenePresets.forEach { ps ->
                        val isSel = selectPresetId == ps.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFFD0BCFF).copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (isSel) Color(0xFFD0BCFF) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selectPresetId = ps.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(ps.icon, contentDescription = null, tint = ps.primaryColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(ps.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(ps.description, color = Color(0xFFCAC4D0), fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Duration dropdown
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Scene Track Duration:", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCAC4D0)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (durationSelected > 2) durationSelected-- }) {
                            Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Shorter", tint = Color(0xFFCAC4D0))
                        }
                        Text(
                            text = "$durationSelected seconds",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = { if (durationSelected < 15) durationSelected++ }) {
                            Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Longer", tint = Color(0xFFCAC4D0))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Caption text
                Text("Optional initial caption:", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCAC4D0)))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = captionInput,
                    onValueChange = { captionInput = it },
                    placeholder = { Text("E.g. Coffee time! ☕", color = Color.Gray) },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF4A4458)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFFCAC4D0))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { onAdd(selectPresetId, durationSelected, captionInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72))
                    ) {
                        Text("Add to Timeline")
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------
// COMPOSTABLE DEFINITIONS: SIMULATED EXPORT SCREEN TIMELINE
// --------------------------------------------------------------------
@Composable
fun ExportingProgressDialog(
    exportState: ExportState,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (exportState is ExportState.Success) onDismiss() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF4A4458)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (exportState) {
                    is ExportState.Exporting -> {
                        Text(
                            text = "🎬 Rendering Your Video...",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Please don't close the editor screen", fontSize = 12.sp, color = Color(0xFFCAC4D0))

                        Spacer(modifier = Modifier.height(24.dp))

                        // Progress circle
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                            CircularProgressIndicator(
                                progress = { exportState.progress },
                                modifier = Modifier.size(90.dp),
                                strokeWidth = 6.dp,
                                color = Color(0xFFD0BCFF),
                                trackColor = Color(0xFF4A4458)
                            )
                            Text(
                                text = "${(exportState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Export stage descriptor
                        Text(
                            text = exportState.stage,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = Color(0xFFCAC4D0)),
                            textAlign = TextAlign.Center
                        )
                    }

                    is ExportState.Success -> {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "🎉 Export Successful!",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your clip was rendered and saved matching the selected filters and soundtracks!",
                            fontSize = 12.sp,
                            color = Color(0xFFCAC4D0),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Display simulated saved file path
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1C1B1F))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("SIMULATED FILE NAME:", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                                Text(
                                    text = exportState.mockPath,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Green
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cool, back to editor", fontWeight = FontWeight.Bold)
                        }
                    }

                    is ExportState.Error -> {
                        Icon(Icons.Rounded.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Export Engine Error",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = exportState.message, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4458)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Acknowledge")
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}
