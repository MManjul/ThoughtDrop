package com.thoughtdrop.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thoughtdrop.app.data.repository.ApiKeyEntry
import com.thoughtdrop.app.data.repository.SettingsRepository
import com.thoughtdrop.app.data.sync.FirebaseSyncManager
import com.thoughtdrop.app.data.sync.SyncState
import com.thoughtdrop.app.ui.theme.*
import com.thoughtdrop.app.ui.util.BiometricAuthManager

/**
 * Full-Screen Non-Transparent Settings & AI Engine Screen
 * - Solid non-transparent obsidian background (#090D0B)
 * - Full-screen edge-to-edge layout with top navigation bar
 * - Multi-key pool rotation, daily quota meters, Gemini 4 Hydrogen model selector
 * - Option A Security & Device Lock (Biometric / PIN / Pattern)
 */
@Composable
fun SettingsDialog(
    settingsRepo: SettingsRepository,
    syncManager: FirebaseSyncManager = FirebaseSyncManager.getInstance(),
    onSignInGoogle: ((android.content.Context, (Boolean, String?) -> Unit) -> Unit)? = null,
    onSignOutGoogle: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val geminiPool by settingsRepo.apiKeyPoolFlow.collectAsState()
    val groqPool by settingsRepo.groqPoolFlow.collectAsState()
    val activeEngine by settingsRepo.aiEngineFlow.collectAsState()
    val isDeviceLockEnabled by settingsRepo.deviceLockEnabledFlow.collectAsState()
    var selectedModel by remember { mutableStateOf(settingsRepo.getSelectedModel()) }

    // Google Cloud Sync state
    val currentUser by syncManager.currentUser.collectAsState()
    val syncState by syncManager.syncState.collectAsState()
    var isSigningInGoogle by remember { mutableStateOf(false) }
    var googleSignInError by remember { mutableStateOf<String?>(null) }

    // Gemini add key form
    var newGeminiKeyInput by remember { mutableStateOf("") }
    var newGeminiKeyLabel by remember { mutableStateOf("") }
    var geminiInputError by remember { mutableStateOf<String?>(null) }
    var isAddingGeminiKey by remember { mutableStateOf(false) }

    // Groq add key form
    var newGroqKeyInput by remember { mutableStateOf("") }
    var newGroqKeyLabel by remember { mutableStateOf("") }
    var groqInputError by remember { mutableStateOf<String?>(null) }
    var isAddingGroqKey by remember { mutableStateOf(false) }

    val todayCalls = remember(geminiPool, groqPool) { settingsRepo.getTodayTotalCalls() }
    val readyGeminiCount = remember(geminiPool) { geminiPool.count { !it.isExhausted } }
    val readyGroqCount = remember(groqPool) { groqPool.count { !it.isExhausted } }

    val accentAmber = Color(0xFFF59E0B)
    val accentEmerald = Color(0xFF10B981)
    val cardBackground = Color(0xFF111814)
    val cardBorder = BorderStroke(1.dp, Color(0x28FFFFFF))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090D0B)) // Solid non-transparent deep obsidian canvas
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // =========================================================================
                // 1. TOP APP BAR
                // =========================================================================
                Surface(
                    color = Color(0xFF0C130E),
                    border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Icon Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF), CircleShape)
                                .border(BorderStroke(1.dp, Color(0x33FFFFFF)), CircleShape)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Settings",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = "AI Routing, Quotas & Security",
                                fontSize = 11.5.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }

                        // Done Pill Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = accentAmber,
                            modifier = Modifier
                                .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = accentAmber)
                                .clickable { onDismiss() }
                        ) {
                            Text(
                                text = "Done",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // =========================================================================
                // 2. SCROLLABLE SETTINGS CONTENT
                // =========================================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // -----------------------------------------------------------------
                    // DAILY USAGE METER CARD
                    // -----------------------------------------------------------------
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardBackground,
                        border = BorderStroke(1.dp, Color(0x3310B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "✦ DAILY USAGE & QUOTA METER",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = accentEmerald
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = accentEmerald.copy(alpha = 0.20f),
                                    border = BorderStroke(0.8.dp, accentEmerald.copy(alpha = 0.40f))
                                ) {
                                    Text(
                                        text = "$readyGeminiCount Gemini • $readyGroqCount Groq Ready",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34D399),
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$todayCalls",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "notes processed today across all active keys",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFD1D5DB),
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Automatic key failover rotates active keys seamlessly if any free daily quota runs out.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF9CA3AF),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // -----------------------------------------------------------------
                    // SECTION: GOOGLE CLOUD SYNC & WEB STUDIO
                    // -----------------------------------------------------------------
                    SectionHeader(
                        title = "GOOGLE CLOUD SYNC & WEB STUDIO",
                        subtitle = "Instant bi-directional sync with your PC browser"
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardBackground,
                        border = BorderStroke(1.dp, if (currentUser != null) accentEmerald.copy(alpha = 0.4f) else Color(0x28FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            if (currentUser != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(accentEmerald.copy(alpha = 0.2f), CircleShape)
                                            .border(BorderStroke(1.dp, accentEmerald), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = currentUser?.displayName?.take(1)?.uppercase() ?: "G",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = accentEmerald
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentUser?.displayName ?: "Google Account",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = currentUser?.email ?: "",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF9CA3AF)
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            onSignOutGoogle?.invoke() ?: syncManager.signOut()
                                        }
                                    ) {
                                        Text(
                                            text = "Sign Out",
                                            color = Color(0xFFEF4444),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x2010B981),
                                    border = BorderStroke(0.8.dp, accentEmerald.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(accentEmerald, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = when (syncState) {
                                                is SyncState.Syncing -> "Syncing with Web Studio..."
                                                is SyncState.Synced -> "Synced with Web Studio ✦ Live"
                                                is SyncState.Error -> "Sync error: ${(syncState as SyncState.Error).message}"
                                                else -> "Live Cloud Sync Active"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF6EE7B7)
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Connect with your Google account to automatically share your notes, transcripts, and AI keys with your PC browser in real time.",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFD1D5DB),
                                    lineHeight = 18.sp
                                )

                                if (googleSignInError != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = googleSignInError!!,
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFEF4444)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0x20F59E0B),
                                    border = BorderStroke(1.dp, Color(0x60F59E0B)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isSigningInGoogle) {
                                            isSigningInGoogle = true
                                            googleSignInError = null
                                            onSignInGoogle?.invoke(context) { success, err ->
                                                isSigningInGoogle = false
                                                if (!success) {
                                                    googleSignInError = err ?: "Sign in failed"
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = if (isSigningInGoogle) "Connecting to Google..." else "✦ Sign in with Google to Sync",
                                            color = Color(0xFFFBBF24),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // -----------------------------------------------------------------
                    // SECTION: AI ENGINE ROUTING
                    // -----------------------------------------------------------------
                    SectionHeader(title = "AI ENGINE ROUTING", subtitle = "Choose your synthesis strategy")

                    val engines = listOf(
                        Triple(
                            SettingsRepository.ENGINE_AUTO,
                            "🔄 Smart Auto-Failover (Recommended)",
                            "Primary Google Gemini native multi-modal with instant Groq Cloud Whisper & Llama failover."
                        ),
                        Triple(
                            SettingsRepository.ENGINE_GEMINI,
                            "✦ Google Gemini Native Only",
                            "Sub-second direct audio synthesis with multi-photo whiteboard OCR and visual proof reasoning."
                        ),
                        Triple(
                            SettingsRepository.ENGINE_GROQ,
                            "⚡ Groq Cloud Whisper Only",
                            "High-speed Whisper audio transcription followed by Llama 3 structured note synthesis."
                        )
                    )

                    engines.forEach { (engineKey, title, desc) ->
                        val isSelected = activeEngine == engineKey
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0x1F10B981) else cardBackground,
                            border = BorderStroke(
                                1.2.dp,
                                if (isSelected) accentEmerald else Color(0x24FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { settingsRepo.setSelectedEngine(engineKey) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { settingsRepo.setSelectedEngine(engineKey) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = accentEmerald,
                                        unselectedColor = Color(0xFF6B7280)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFFE5E7EB)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = Color(0xFF9CA3AF),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // -----------------------------------------------------------------
                    // SECTION: GEMINI REASONING MODEL
                    // -----------------------------------------------------------------
                    SectionHeader(title = "GEMINI REASONING MODEL", subtitle = "Flagship sub-second synthesis tier")

                    val models = listOf(
                        Triple("gemini-4-hydrogen", "Gemini 4 Hydrogen", "Flagship 1M Context • Non-destructive whiteboard OCR"),
                        Triple("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", "Sub-second real-time voice synthesis"),
                        Triple("gemini-3.5-flash", "Gemini 3.5 Flash", "Balanced high-speed multi-modal model"),
                        Triple("gemini-flash-lite-latest", "Gemini Flash Lite Latest", "Lightweight preview tier")
                    )

                    models.forEach { (modelId, name, tag) ->
                        val isSelected = selectedModel == modelId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0x1FF59E0B) else cardBackground,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) accentAmber else Color(0x20FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedModel = modelId
                                    settingsRepo.setSelectedModel(modelId)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedModel = modelId
                                        settingsRepo.setSelectedModel(modelId)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = accentAmber,
                                        unselectedColor = Color(0xFF6B7280)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.White else Color(0xFFE5E7EB)
                                    )
                                    Text(
                                        text = tag,
                                        fontSize = 10.5.sp,
                                        color = if (isSelected) accentAmber else Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }
                    }

                    // -----------------------------------------------------------------
                    // SECTION: GEMINI MULTI-KEY POOL
                    // -----------------------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = "GEMINI MULTI-KEY POOL (${geminiPool.size})",
                            subtitle = "Automatic failover across free accounts"
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (!isAddingGeminiKey) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0x2210B981),
                                border = BorderStroke(0.8.dp, Color(0x4D10B981)),
                                modifier = Modifier.clickable { isAddingGeminiKey = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Key", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                                }
                            }
                        }
                    }

                    if (geminiPool.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cardBackground,
                            border = cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Gemini API Keys Configured",
                                    color = Color(0xFFE5E7EB),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Add a free key from Google AI Studio to start synthesizing voice notes.",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        geminiPool.forEachIndexed { index, entry ->
                            KeyCardRow(
                                entry = entry,
                                isPrimary = index == 0,
                                activeColor = Color(0xFF34D399),
                                onReset = { settingsRepo.resetKeyStatus(entry.id) },
                                onDelete = { settingsRepo.removeApiKey(entry.id) }
                            )
                        }
                    }

                    // Inline Add Gemini Key Form
                    AnimatedVisibility(
                        visible = isAddingGeminiKey,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF141F18),
                            border = BorderStroke(1.dp, Color(0x4D10B981)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Add Google AI Studio Gemini Key",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = newGeminiKeyInput,
                                    onValueChange = {
                                        newGeminiKeyInput = it
                                        geminiInputError = null
                                    },
                                    placeholder = { Text("Paste API Key (AIza...)", color = Color(0xFF6B7280), fontSize = 12.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF090D0B),
                                        unfocusedContainerColor = Color(0xFF090D0B),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF34D399),
                                        unfocusedBorderColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = newGeminiKeyLabel,
                                    onValueChange = { newGeminiKeyLabel = it },
                                    placeholder = { Text("Label (e.g. Personal Gmail, Account 2)", color = Color(0xFF6B7280), fontSize = 12.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF090D0B),
                                        unfocusedContainerColor = Color(0xFF090D0B),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF34D399),
                                        unfocusedBorderColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (geminiInputError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = geminiInputError!!, color = Color(0xFFFB7185), fontSize = 11.5.sp)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        isAddingGeminiKey = false
                                        newGeminiKeyInput = ""
                                        newGeminiKeyLabel = ""
                                        geminiInputError = null
                                    }) {
                                        Text("Cancel", color = Color(0xFF9CA3AF), fontSize = 12.5.sp)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            val key = newGeminiKeyInput.trim()
                                            if (key.isBlank()) {
                                                geminiInputError = "Please enter an API key."
                                                return@Button
                                            }
                                            val success = settingsRepo.addApiKey(key, newGeminiKeyLabel.trim())
                                            if (success) {
                                                isAddingGeminiKey = false
                                                newGeminiKeyInput = ""
                                                newGeminiKeyLabel = ""
                                                geminiInputError = null
                                            } else {
                                                geminiInputError = "This key is already in your pool."
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                    ) {
                                        Text("Add Key", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            }
                        }
                    }

                    // Direct Link to Google AI Studio
                    TextButton(
                        onClick = {
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/apikey")
                            )
                            context.startActivity(browserIntent)
                        },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Get Free Gemini Key in Google AI Studio", color = Color(0xFF34D399), fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                    }

                    // -----------------------------------------------------------------
                    // SECTION: GROQ MULTI-KEY POOL
                    // -----------------------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = "GROQ ROTATING POOL (${groqPool.size})",
                            subtitle = "Ultra-fast Whisper fallback tier"
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (!isAddingGroqKey) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0x22F59E0B),
                                border = BorderStroke(0.8.dp, Color(0x4DF59E0B)),
                                modifier = Modifier.clickable { isAddingGroqKey = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = accentAmber, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Groq Key", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = accentAmber)
                                }
                            }
                        }
                    }

                    if (groqPool.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cardBackground,
                            border = cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Groq Cloud Keys Configured",
                                    color = Color(0xFFE5E7EB),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Groq provides instant Whisper speech transcription as a backup when Gemini free limits are reached.",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        groqPool.forEachIndexed { index, entry ->
                            KeyCardRow(
                                entry = entry,
                                isPrimary = index == 0,
                                activeColor = accentAmber,
                                onReset = { settingsRepo.resetGroqKeyStatus(entry.id) },
                                onDelete = { settingsRepo.removeGroqApiKey(entry.id) }
                            )
                        }
                    }

                    // Inline Add Groq Key Form
                    AnimatedVisibility(
                        visible = isAddingGroqKey,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E170E),
                            border = BorderStroke(1.dp, Color(0x4DF59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Add Groq Cloud Key",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = newGroqKeyInput,
                                    onValueChange = {
                                        newGroqKeyInput = it
                                        groqInputError = null
                                    },
                                    placeholder = { Text("Paste Groq Key (gsk_...)", color = Color(0xFF6B7280), fontSize = 12.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF090D0B),
                                        unfocusedContainerColor = Color(0xFF090D0B),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = accentAmber,
                                        unfocusedBorderColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = newGroqKeyLabel,
                                    onValueChange = { newGroqKeyLabel = it },
                                    placeholder = { Text("Label (e.g. Backup Account)", color = Color(0xFF6B7280), fontSize = 12.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF090D0B),
                                        unfocusedContainerColor = Color(0xFF090D0B),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = accentAmber,
                                        unfocusedBorderColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (groqInputError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = groqInputError!!, color = Color(0xFFFB7185), fontSize = 11.5.sp)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        isAddingGroqKey = false
                                        newGroqKeyInput = ""
                                        newGroqKeyLabel = ""
                                        groqInputError = null
                                    }) {
                                        Text("Cancel", color = Color(0xFF9CA3AF), fontSize = 12.5.sp)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            val key = newGroqKeyInput.trim()
                                            if (key.isBlank()) {
                                                groqInputError = "Please enter an API key."
                                                return@Button
                                            }
                                            val success = settingsRepo.addGroqApiKey(key, newGroqKeyLabel.trim())
                                            if (success) {
                                                isAddingGroqKey = false
                                                newGroqKeyInput = ""
                                                newGroqKeyLabel = ""
                                                groqInputError = null
                                            } else {
                                                groqInputError = "This key is already in your pool."
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentAmber)
                                    ) {
                                        Text("Add Key", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            }
                        }
                    }

                    // Direct Link to Groq Console
                    TextButton(
                        onClick = {
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://console.groq.com/keys")
                            )
                            context.startActivity(browserIntent)
                        },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Get Free Groq Key in console.groq.com", color = accentAmber, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = accentAmber, modifier = Modifier.size(13.dp))
                    }

                    // -----------------------------------------------------------------
                    // SECTION: SECURITY & DEVICE LOCK (OPTION A)
                    // -----------------------------------------------------------------
                    SectionHeader(
                        title = "SECURITY & DEVICE LOCK (OPTION A)",
                        subtitle = "Instant quick-record capture, device-lock protected browsing"
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBackground,
                        border = BorderStroke(
                            1.2.dp,
                            if (isDeviceLockEnabled) accentAmber.copy(alpha = 0.8f) else Color(0x28FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isDeviceLockEnabled) accentAmber.copy(alpha = 0.20f) else Color(0x1AFFFFFF),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isDeviceLockEnabled) Icons.Default.Lock else Icons.Outlined.Fingerprint,
                                        contentDescription = null,
                                        tint = if (isDeviceLockEnabled) accentAmber else Color(0xFF9CA3AF),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Require Device Lock",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    if (isDeviceLockEnabled) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = accentAmber.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accentAmber,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Requires your phone's fingerprint, face, PIN, or pattern to open ThoughtDrop and browse your notes. (Notification shade quick-record tile remains instant).",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF9CA3AF),
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Switch(
                                checked = isDeviceLockEnabled,
                                onCheckedChange = { targetState ->
                                    val activity = context as? androidx.fragment.app.FragmentActivity
                                    if (activity != null) {
                                        BiometricAuthManager.authenticate(
                                            activity = activity,
                                            title = if (targetState) "Enable Device Lock" else "Disable Device Lock",
                                            subtitle = "Verify your credentials to change security settings",
                                            onSuccess = {
                                                settingsRepo.setDeviceLockEnabled(targetState)
                                            }
                                        )
                                    } else {
                                        settingsRepo.setDeviceLockEnabled(targetState)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = accentAmber,
                                    uncheckedThumbColor = Color(0xFF9CA3AF),
                                    uncheckedTrackColor = Color(0xFF1F2923)
                                )
                            )
                        }
                    }

                    // -----------------------------------------------------------------
                    // SECTION: LOCAL STORAGE & DIAGNOSTICS
                    // -----------------------------------------------------------------
                    SectionHeader(title = "LOCAL STORAGE & PRIVACY", subtitle = "100% On-device privacy guarantee")

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBackground,
                        border = cardBorder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Storage, contentDescription = null, tint = accentEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Room SQLite Database v5", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.weight(1f))
                                Text("Encrypted Sandbox", fontSize = 11.sp, color = accentEmerald, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "All voice transcripts, audio recordings, OCR attachments, and settings reside strictly on your phone. Zero cloud telemetry or central servers.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF9CA3AF),
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ThoughtDrop v1.0.0 (Living Optical Glass Edition)", fontSize = 11.sp, color = Color(0xFF6B7280))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = Color(0xFFFBBF24)
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = subtitle,
            fontSize = 11.5.sp,
            color = Color(0xFF6B7280)
        )
    }
}

@Composable
private fun KeyCardRow(
    entry: ApiKeyEntry,
    isPrimary: Boolean,
    activeColor: Color,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF111814),
        border = BorderStroke(
            1.dp,
            if (entry.isExhausted) Color(0x33F43F5E) else Color(0x24FFFFFF)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    if (isPrimary) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = activeColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "PRIORITY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.getMaskedKey(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (entry.isExhausted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onReset() }
                        ) {
                            Box(modifier = Modifier.size(5.dp).background(Color(0xFFFB7185), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Quota Exhausted",
                                color = Color(0xFFFB7185),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Refresh, contentDescription = "Retry key", tint = Color(0xFFFB7185), modifier = Modifier.size(11.dp))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(5.dp).background(activeColor, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Active • ${entry.callCountToday} calls",
                                color = activeColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Remove Key",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
