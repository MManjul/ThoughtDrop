package com.thoughtdrop.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thoughtdrop.app.data.model.NoteEntity
import com.thoughtdrop.app.ui.theme.*
import com.thoughtdrop.app.ui.util.FullScreenImageViewerDialog
import com.thoughtdrop.app.ui.util.ImageUtils
import com.thoughtdrop.app.ui.viewmodel.MainViewModel
import com.thoughtdrop.app.ui.viewmodel.UiProcessingState
import java.io.File

private enum class SmartFilter {
    ALL, AUDIO_ONLY, TASKS_ONLY, PINNED_ONLY, INSIGHTS_ONLY
}

/**
 * ThoughtDrop Dashboard
 * Pixel-perfect match to the user's reference mockup:
 * - Top Brand Header: ✦ SPECTRUM ACTIVE + "Clear Thoughts, Bold Action" + M Avatar
 * - Search Capsule: Search voice notes, actions, photos...
 * - Horizontal Filter Pills: All (3), Voice (3), Tasks (2), Pinned (1)
 * - Hero Featured Card: ✦ GEMINI 4 HYDROGEN, "Neural Thought Synthesizer", ✦ Tap Orb To Speak, 3D Glowing Ember Orb
 * - Note Cards with green #CATEGORY, rose ★ PINNED, timestamp, star & trashcan, amber audio strip, OCR photo pills, checklist
 * - Floating Studio Dock: 46dp Camera, 62dp Center Radiant Amber Mic Orb, 46dp Gallery
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestRecordAudioPermission: () -> Unit,
    hasRecordPermission: Boolean,
    onTriggerBiometricUnlock: () -> Unit = {}
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val isDeviceLockEnabled by viewModel.isDeviceLockEnabled.collectAsState()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val uiProcessingState by viewModel.uiProcessingState.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val followUpState by viewModel.followUpState.collectAsState()
    val apiKey by viewModel.settingsRepo.apiKeyFlow.collectAsState()

    // Warm Sunset Amber Palette (Matching Screenshot)
    val accentAmber = Color(0xFFF59E0B)
    val accentGold = Color(0xFFFBBF24)
    val accentGlow = Color(0x66F59E0B)

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCameraSourceDialog by remember { mutableStateOf(false) }
    var selectedNoteForDetail by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToSchedule by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var activeSmartFilter by remember { mutableStateOf(SmartFilter.ALL) }
    var feedPreviewImage by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Camera and Gallery launchers
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraLaunchUri by remember { mutableStateOf<Uri?>(null) }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null) {
            viewModel.processImageNote(tempCameraFile!!)
        }
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingCameraLaunchUri != null) {
            takePhotoLauncher.launch(pendingCameraLaunchUri!!)
        }
    }

    val launchCameraSafe: (Uri) -> Unit = { uri ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhotoLauncher.launch(uri)
        } else {
            pendingCameraLaunchUri = uri
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val copied = ImageUtils.copyUriToFile(context, uri)
            if (copied != null) {
                viewModel.processImageNote(copied)
            }
        }
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) {
            showSettingsDialog = true
        }
    }

    LaunchedEffect(notes) {
        selectedNoteForDetail?.let { currentSelected ->
            val updated = notes.find { it.id == currentSelected.id }
            selectedNoteForDetail = updated
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiProcessingState) {
        if (uiProcessingState is UiProcessingState.Error) {
            val error = (uiProcessingState as UiProcessingState.Error).error
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = if (error.contains("API key", ignoreCase = true)) "Settings" else "Dismiss",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && error.contains("API key", ignoreCase = true)) {
                showSettingsDialog = true
            }
            viewModel.dismissError()
        }
    }

    // Filter notes
    val filteredNotes = remember(notes, activeSmartFilter, selectedCategory, searchQuery) {
        val base = when (activeSmartFilter) {
            SmartFilter.ALL -> notes
            SmartFilter.AUDIO_ONLY -> notes.filter { !it.audioPath.isNullOrBlank() }
            SmartFilter.TASKS_ONLY -> notes.filter { it.getActionItems().isNotEmpty() }
            SmartFilter.PINNED_ONLY -> notes.filter { it.isPinned }
            SmartFilter.INSIGHTS_ONLY -> notes.filter { it.getKeyPoints().isNotEmpty() }
        }
        val catFiltered = if (selectedCategory != null) {
            base.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        } else base

        if (searchQuery.isNotBlank()) {
            catFiltered.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.summary.contains(searchQuery, ignoreCase = true) ||
                it.transcript.contains(searchQuery, ignoreCase = true)
            }
        } else {
            catFiltered
        }
    }

    val audioCount = remember(notes) { notes.count { !it.audioPath.isNullOrBlank() } }
    val tasksCount = remember(notes) { notes.count { it.getActionItems().isNotEmpty() } }
    val pinnedCount = remember(notes) { notes.count { it.isPinned } }

    val startRecordingAction = {
        if (!hasRecordPermission) {
            onRequestRecordAudioPermission()
        } else {
            viewModel.startRecording()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A08))
    ) {
        // Living Ambient Optical Caustics (Rich Emerald Bloom + Sunset Amber)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Top-Right Sunset Amber Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44F59E0B), Color(0x18FBBF24), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.08f),
                    radius = size.width * 0.75f
                )
            )
            // 2. Center Living Emerald Bloom (Illuminates the glass cards from behind)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x5210B981), Color(0x24059669), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.52f),
                    radius = size.width * 0.85f
                )
            )
            // 3. Bottom Studio Dock Caustics
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3DF59E0B), Color(0x1E10B981), Color.Transparent),
                    center = Offset(size.width * 0.50f, size.height * 0.94f),
                    radius = size.width * 0.65f
                )
            )
        }

        if (isDeviceLockEnabled && !isAppUnlocked) {
            AppLockPrivacyScreen(
                onUnlockClick = onTriggerBiometricUnlock
            )
        } else {
            Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            floatingActionButton = {
                // THE EXACT INSTALLED APP FLOATING STUDIO DOCK RESTORED
                Surface(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .shadow(28.dp, shape = RoundedCornerShape(36.dp), spotColor = Color.Black),
                    shape = RoundedCornerShape(36.dp),
                    color = Color(0xD90E2016),
                    border = BorderStroke(1.2.dp, GlassBorderBrush)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left: 46dp Quick Camera Lens Circle
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0x2486EFAC), CircleShape)
                                .border(BorderStroke(1.dp, GlassBorderBrush), CircleShape)
                                .clickable { showCameraSourceDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = "Snap Photo Note",
                                tint = Color(0xFFE5E7EB),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Center Hero: 62dp Radiant Glowing Amber Mic Orb (Exact match to screenshot)
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .shadow(20.dp, CircleShape, spotColor = accentGlow)
                                .background(accentAmber, CircleShape)
                                .clickable { startRecordingAction() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Record Thought",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Right: 46dp Quick Gallery Circle
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0x2486EFAC), CircleShape)
                                .border(BorderStroke(1.dp, GlassBorderBrush), CircleShape)
                                .clickable {
                                    pickGalleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = "Gallery Upload",
                                tint = Color(0xFFE5E7EB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 135.dp)
            ) {
                // 1. Top Brand Header: ✦ SPECTRUM ACTIVE + "Clear Thoughts, Bold Action" + M Avatar
                item(key = "brand_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "✦ SPECTRUM ACTIVE",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = accentAmber,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Clear Thoughts, Bold Action",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                letterSpacing = (-0.4).sp
                            )
                        }

                        // M Avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accentAmber, CircleShape)
                                .clickable { showSettingsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "M",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                        }
                    }
                }

                // 2. Search Capsule (Exact match: compact rounded bar)
                item(key = "search_capsule") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xB30E1F16),
                        border = BorderStroke(1.dp, GlassBorderBrush),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = if (searchQuery.isNotBlank()) accentAmber else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                placeholder = {
                                    Text(
                                        "Search voice notes, actions, photos...",
                                        color = TextMuted,
                                        fontSize = 12.5.sp
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = accentAmber
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onSearchQueryChanged("") },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = "Filter",
                                    tint = accentAmber,
                                    modifier = Modifier
                                        .size(17.dp)
                                        .clickable { showSettingsDialog = true }
                                )
                            }
                        }
                    }
                }

                // 3. Category Filter Pills: All (3), Voice (3), Tasks (2), Pinned (1)
                item(key = "category_filter_pills") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // All (Amber background when selected)
                        FilterCapsule(
                            icon = Icons.Outlined.GridView,
                            label = "All",
                            count = notes.size,
                            isSelected = activeSmartFilter == SmartFilter.ALL && selectedCategory == null,
                            accentColor = accentAmber,
                            onClick = {
                                activeSmartFilter = SmartFilter.ALL
                                viewModel.selectCategory(null)
                            }
                        )

                        // Voice
                        FilterCapsule(
                            icon = Icons.Outlined.GraphicEq,
                            label = "Voice",
                            count = audioCount,
                            isSelected = activeSmartFilter == SmartFilter.AUDIO_ONLY,
                            accentColor = accentAmber,
                            onClick = {
                                activeSmartFilter = if (activeSmartFilter == SmartFilter.AUDIO_ONLY) SmartFilter.ALL else SmartFilter.AUDIO_ONLY
                                viewModel.selectCategory(null)
                            }
                        )

                        // Tasks
                        FilterCapsule(
                            icon = Icons.Outlined.Checklist,
                            label = "Tasks",
                            count = tasksCount,
                            isSelected = activeSmartFilter == SmartFilter.TASKS_ONLY,
                            accentColor = accentAmber,
                            onClick = {
                                activeSmartFilter = if (activeSmartFilter == SmartFilter.TASKS_ONLY) SmartFilter.ALL else SmartFilter.TASKS_ONLY
                                viewModel.selectCategory(null)
                            }
                        )

                        // Pinned
                        FilterCapsule(
                            icon = Icons.Outlined.BookmarkBorder,
                            label = "Pinned",
                            count = pinnedCount,
                            isSelected = activeSmartFilter == SmartFilter.PINNED_ONLY,
                            accentColor = accentAmber,
                            onClick = {
                                activeSmartFilter = if (activeSmartFilter == SmartFilter.PINNED_ONLY) SmartFilter.ALL else SmartFilter.PINNED_ONLY
                                viewModel.selectCategory(null)
                            }
                        )

                        // Custom tags
                        categories.forEach { cat ->
                            FilterCapsule(
                                icon = Icons.AutoMirrored.Outlined.Label,
                                label = cat,
                                count = null,
                                isSelected = selectedCategory == cat,
                                accentColor = accentAmber,
                                onClick = {
                                    activeSmartFilter = SmartFilter.ALL
                                    viewModel.selectCategory(if (selectedCategory == cat) null else cat)
                                }
                            )
                        }
                    }
                }

                // 4. Hero Featured Card (Matching Prototype: Translucent Living Glass + 3D Orb)
                item(key = "hero_featured_card") {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(20.dp, RoundedCornerShape(26.dp), spotColor = Color(0x40F59E0B)),
                        border = BorderStroke(1.2.dp, GlassBorderBrush)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x26FFFFFF), // Frosted translucent glass top
                                            Color(0x1F18231C), // Deep emerald-obsidian glass
                                            Color(0x240F1811)  // Obsidian base
                                        )
                                    )
                                )
                                .padding(horizontal = 18.dp, vertical = 18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1.25f)
                                        .padding(end = 6.dp)
                                ) {
                                    // "● GEMINI 4 HYDROGEN" Pill
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = accentAmber.copy(alpha = 0.20f),
                                        border = BorderStroke(0.8.dp, accentAmber.copy(alpha = 0.40f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Box(modifier = Modifier.size(5.dp).background(accentAmber, CircleShape))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "GEMINI 4 HYDROGEN",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = accentAmber,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Neural Thought Synthesizer",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary,
                                        lineHeight = 21.sp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Sub-second voice synthesis & non-destructive OCR merge.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFD1D5DB),
                                        lineHeight = 15.sp
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // ✦ Tap Orb To Speak (Solid Amber Button)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = accentAmber,
                                        modifier = Modifier
                                            .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = accentGlow)
                                            .clickable { startRecordingAction() }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "✦ Tap Orb To Speak",
                                                color = Color.Black,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }

                                // Right: Canvas-rendered 3D Glowing Ember Orb
                                Box(
                                    modifier = Modifier
                                        .weight(0.95f)
                                        .height(125.dp)
                                        .clickable { startRecordingAction() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    GlowingEmberOrb(
                                        size = 125.dp,
                                        amplitude = 0f,
                                        isThinking = false,
                                        primaryAccent = accentAmber,
                                        secondaryAccent = accentGold
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Notes Feed (Recent Notes)
                if (filteredNotes.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0x330E2016),
                                    border = BorderStroke(1.dp, GlassBorderBrush),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.MicNone,
                                            contentDescription = null,
                                            tint = accentAmber,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = if (searchQuery.isNotBlank()) "No notes match '$searchQuery'" else "Drop your first thought",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Try searching for a different keyword or tag." else "Tap the glowing mic below or 'Tap Orb To Speak' to begin.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            playbackState = playbackState,
                            onCardClick = { selectedNoteForDetail = note },
                            onPlayAudio = { viewModel.playAudio(note) },
                            onTogglePin = { viewModel.togglePin(note) },
                            onDelete = { noteToDelete = note },
                            onToggleActionItem = { item -> viewModel.toggleActionItem(note, item) },
                            onScheduleClick = { noteToSchedule = note },
                            onImageClick = { imgPath -> feedPreviewImage = imgPath },
                            accentColor = accentAmber
                        )
                    }
                }
            }
        }
    }

        // Delete Note Confirmation Dialog
        noteToDelete?.let { note ->
            AlertDialog(
                onDismissRequest = { noteToDelete = null },
                title = {
                    Text(
                        text = "Delete Note?",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete '${note.title.ifBlank { "this note" }}'?",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = noteToDelete
                            noteToDelete = null
                            if (target != null) {
                                viewModel.deleteNote(target)
                            }
                        }
                    ) {
                        Text("Delete", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { noteToDelete = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = Color(0xF20E1812),
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Schedule Task Reminder Dialog
        noteToSchedule?.let { note ->
            ScheduleTaskDialog(
                taskItem = note.title,
                parentNote = note,
                onDismiss = { noteToSchedule = null }
            )
        }

        // Detail Bottom Sheet
        selectedNoteForDetail?.let { note ->
            NoteDetailBottomSheet(
                note = note,
                playbackState = playbackState,
                followUpState = followUpState,
                recordingState = recordingState,
                onDismiss = { selectedNoteForDetail = null },
                onPlayAudio = { viewModel.playAudio(note) },
                onTogglePin = { viewModel.togglePin(note) },
                onToggleActionItem = { item -> viewModel.toggleActionItem(note, item) },
                onStartFollowUp = {
                    if (!hasRecordPermission) {
                        onRequestRecordAudioPermission()
                    } else {
                        viewModel.startFollowUpRecording(note)
                    }
                },
                onStopFollowUp = { viewModel.stopFollowUpAndRefine(note) },
                onCancelFollowUp = { viewModel.cancelFollowUp() },
                onDismissFollowUpError = { viewModel.dismissFollowUpError() },
                onAttachPhoto = { file -> viewModel.attachImageToNote(note, file) },
                onDelete = {
                    viewModel.deleteNote(note)
                    selectedNoteForDetail = null
                },
                onDeleteImage = { imgPath -> viewModel.removeImageFromNote(note, imgPath) },
                onPlayAudioPath = { noteId, path -> viewModel.playAudioPath(noteId, path) },
                onSeekAudio = { pos -> viewModel.seekAudio(pos) }
            )
        }

        // Feed Image Lightbox / Reviewer Dialog
        feedPreviewImage?.let { imgPath ->
            FullScreenImageViewerDialog(
                imagePath = imgPath,
                onDismiss = { feedPreviewImage = null }
            )
        }

        // Active Voice Recording Sheet
        if (uiProcessingState is UiProcessingState.Recording || uiProcessingState is UiProcessingState.Processing) {
            RecordBottomSheet(
                processingState = uiProcessingState,
                recordingState = recordingState,
                onStopAndProcess = { viewModel.stopAndProcess() },
                onCancel = { viewModel.cancelRecording() }
            )
        }

        // Home Screen Snap Photo Dialog (Camera vs Gallery)
        if (showCameraSourceDialog) {
            AlertDialog(
                onDismissRequest = { showCameraSourceDialog = false },
                containerColor = Color(0xF20E1F16),
                title = {
                    Text(
                        text = "Snap Photo to Note",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Snap a whiteboard, receipt, document, or handwritten note. AI synthesizes it into a complete structured note.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x590E2016),
                            border = BorderStroke(1.dp, GlassBorderBrush),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCameraSourceDialog = false
                                    val photoFile = ImageUtils.createTempImageFile(context)
                                    tempCameraFile = photoFile
                                    val uri = ImageUtils.getImageUri(context, photoFile)
                                    launchCameraSafe(uri)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = accentAmber, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Take Photo with Camera", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x590E2016),
                            border = BorderStroke(1.dp, GlassBorderBrush),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCameraSourceDialog = false
                                    pickGalleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = null, tint = accentGold, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Choose from Gallery", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showCameraSourceDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                settingsRepo = viewModel.settingsRepo,
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

/**
 * Filter Capsule matching the prototype:
 * Selected: Radiant solid amber background with black text
 * Unselected: Frosted dark surface with 1dp border and white text
 */
@Composable
private fun FilterCapsule(
    icon: ImageVector,
    label: String,
    count: Int?,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) accentColor else Color(0x22FFFFFF),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color.Transparent else Color(0x28FFFFFF)
        ),
        modifier = Modifier
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = accentColor
            )
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.Black else Color(0xFFD1D5DB),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (count != null) "$label ($count)" else label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else Color(0xFFF3F4F6)
            )
        }
    }
}
