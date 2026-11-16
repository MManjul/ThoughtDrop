package com.thoughtdrop.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thoughtdrop.app.data.repository.ApiKeyEntry
import com.thoughtdrop.app.data.repository.SettingsRepository
import com.thoughtdrop.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settingsRepo: SettingsRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val geminiPool by settingsRepo.apiKeyPoolFlow.collectAsState()
    val groqPool by settingsRepo.groqPoolFlow.collectAsState()
    val activeEngine by settingsRepo.aiEngineFlow.collectAsState()
    var selectedModel by remember { mutableStateOf(settingsRepo.getSelectedModel()) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentViolet.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.3f)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("AI Engine & Free Quotas", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 17.sp)
                    Text("Multi-Key Gemini & Groq Pools", fontSize = 11.5.sp, color = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. ENGINE SELECTION SECTION
                Text(
                    text = "AI ENGINE ROUTING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = AccentViolet
                )
                Spacer(modifier = Modifier.height(6.dp))

                val engines = listOf(
                    SettingsRepository.ENGINE_AUTO to "🔄 Smart Auto-Failover (Gemini ➔ Groq)",
                    SettingsRepository.ENGINE_GEMINI to "✦ Google Gemini Only (Native Multimodal)",
                    SettingsRepository.ENGINE_GROQ to "⚡ Groq Cloud Only (Whisper + Llama)"
                )
                engines.forEach { (engineKey, label) ->
                    val isSelected = activeEngine == engineKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { settingsRepo.setSelectedEngine(engineKey) },
                        label = {
                            Text(
                                label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (engineKey == SettingsRepository.ENGINE_GROQ) AccentAmber else AccentViolet,
                            selectedLabelColor = Color.White,
                            containerColor = StudioSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = StudioBorder,
                            selectedBorderColor = if (engineKey == SettingsRepository.ENGINE_GROQ) AccentAmber else AccentViolet
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. DAILY USAGE SUMMARY CARD
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DAILY USAGE METER",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentEmerald.copy(alpha = 0.15f),
                                border = BorderStroke(0.7.dp, AccentEmerald.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "$readyGeminiCount Gemini • $readyGroqCount Groq Ready",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentEmerald,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$todayCalls",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "notes processed today across all pools",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Automatic failover rotates keys seamlessly if any daily quota runs out.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 3. GROQ MULTI-KEY ROTATING POOL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(AccentAmber.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(13.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GROQ ROTATING POOL (${groqPool.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = AccentAmber
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (!isAddingGroqKey) {
                        TextButton(
                            onClick = { isAddingGroqKey = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentAmber)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Groq Key", fontSize = 11.5.sp, color = AccentAmber, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (groqPool.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "No Groq keys yet. Add 1 or more free Groq keys for 7,200s audio/day + 14,400 calls/day backup!",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groqPool.forEachIndexed { index, entry ->
                            KeyCardRow(
                                entry = entry,
                                isPrimary = index == 0,
                                activeColor = AccentAmber,
                                onReset = { settingsRepo.resetGroqKeyStatus(entry.id) },
                                onDelete = { settingsRepo.removeGroqApiKey(entry.id) }
                            )
                        }
                    }
                }

                // ADD GROQ KEY FORM
                AnimatedVisibility(visible = isAddingGroqKey) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioSurface,
                            border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Add Groq API Key to Pool",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Whisper Large v3 + Llama 3.3 70B. Free with no credit card required.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = newGroqKeyInput,
                                    onValueChange = {
                                        newGroqKeyInput = it
                                        groqInputError = null
                                    },
                                    placeholder = { Text("Paste Groq Key (gsk_...)", color = TextMuted, fontSize = 12.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = StudioSurfaceElevated,
                                        unfocusedContainerColor = StudioSurfaceElevated,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = AccentAmber,
                                        unfocusedBorderColor = StudioBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = newGroqKeyLabel,
                                    onValueChange = { newGroqKeyLabel = it },
                                    placeholder = { Text("Label (e.g. Groq Personal, Groq Work)", color = TextMuted, fontSize = 12.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = StudioSurfaceElevated,
                                        unfocusedContainerColor = StudioSurfaceElevated,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = AccentAmber,
                                        unfocusedBorderColor = StudioBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (groqInputError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = groqInputError!!, color = AccentCoral, fontSize = 11.5.sp)
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
                                        Text("Cancel", color = TextSecondary, fontSize = 12.5.sp)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            val key = newGroqKeyInput.trim()
                                            if (key.isBlank()) {
                                                groqInputError = "Please enter a Groq API key."
                                                return@Button
                                            }
                                            val success = settingsRepo.addGroqApiKey(key, newGroqKeyLabel.trim())
                                            if (success) {
                                                isAddingGroqKey = false
                                                newGroqKeyInput = ""
                                                newGroqKeyLabel = ""
                                                groqInputError = null
                                            } else {
                                                groqInputError = "This Groq key is already in your pool."
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
                                    ) {
                                        Text("Add to Groq Pool", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
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
                    Text("Get Free Groq Key (console.groq.com)", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(12.dp))
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 4. GEMINI MULTI-KEY ROTATING POOL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "GEMINI ROTATING POOL (${geminiPool.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = AccentViolet
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (!isAddingGeminiKey) {
                        TextButton(
                            onClick = { isAddingGeminiKey = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentViolet)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Gemini Key", fontSize = 11.5.sp, color = AccentViolet, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (geminiPool.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No Gemini keys configured yet. Tap '+ Add Gemini Key' below.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        geminiPool.forEachIndexed { index, entry ->
                            KeyCardRow(
                                entry = entry,
                                isPrimary = index == 0,
                                activeColor = AccentViolet,
                                onReset = { settingsRepo.resetKeyStatus(entry.id) },
                                onDelete = { settingsRepo.removeApiKey(entry.id) }
                            )
                        }
                    }
                }

                // ADD GEMINI KEY FORM
                AnimatedVisibility(visible = isAddingGeminiKey) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioSurface,
                            border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Add Another Free Gemini Key",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Generate a key from another Google account for automatic rotation.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = newGeminiKeyInput,
                                    onValueChange = {
                                        newGeminiKeyInput = it
                                        geminiInputError = null
                                    },
                                    placeholder = { Text("Paste Gemini Key (AQ.Ab...)", color = TextMuted, fontSize = 12.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = StudioSurfaceElevated,
                                        unfocusedContainerColor = StudioSurfaceElevated,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = AccentViolet,
                                        unfocusedBorderColor = StudioBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = newGeminiKeyLabel,
                                    onValueChange = { newGeminiKeyLabel = it },
                                    placeholder = { Text("Label (e.g. Account 2, Work Gmail)", color = TextMuted, fontSize = 12.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = StudioSurfaceElevated,
                                        unfocusedContainerColor = StudioSurfaceElevated,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = AccentViolet,
                                        unfocusedBorderColor = StudioBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (geminiInputError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = geminiInputError!!, color = AccentCoral, fontSize = 11.5.sp)
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
                                        Text("Cancel", color = TextSecondary, fontSize = 12.5.sp)
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
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                                    ) {
                                        Text("Add to Gemini Pool", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
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
                    Text("Get Free Key in Google AI Studio", color = AccentViolet, fontSize = 11.5.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(13.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. GEMINI MODEL SELECTOR
                Text("GEMINI MODEL", style = MaterialTheme.typography.titleSmall, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val models = listOf(
                    "gemini-4-hydrogen" to "Gemini 4 Hydrogen (Flagship 1M Context)",
                    "gemini-3.5-flash-lite" to "Gemini 3.5 Flash Lite (Sub-Second)",
                    "gemini-3.5-flash" to "Gemini 3.5 Flash",
                    "gemini-flash-lite-latest" to "Gemini Flash Lite Latest"
                )
                models.forEach { (modelId, label) ->
                    val isSelected = selectedModel == modelId
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedModel = modelId
                            settingsRepo.setSelectedModel(modelId)
                        },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet,
                            selectedLabelColor = Color.White,
                            containerColor = StudioSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = StudioBorder,
                            selectedBorderColor = AccentViolet
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
            ) {
                Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    )
}

@Composable
private fun KeyCardRow(
    entry: ApiKeyEntry,
    isPrimary: Boolean,
    activeColor: Color = AccentViolet,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, if (entry.isExhausted) AccentCoral.copy(alpha = 0.4f) else StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
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
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (entry.isExhausted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onReset() }
                        ) {
                            Box(modifier = Modifier.size(5.dp).background(AccentCoral, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Quota Exhausted",
                                color = AccentCoral,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Refresh, contentDescription = "Retry key", tint = AccentCoral, modifier = Modifier.size(11.dp))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(5.dp).background(AccentEmerald, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Active • ${entry.callCountToday} calls",
                                color = AccentEmerald,
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
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
