package io.github.gonbei774.calisthenicsmemory.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import io.github.gonbei774.calisthenicsmemory.BuildConfig
import io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences
import io.github.gonbei774.calisthenicsmemory.R
import io.github.gonbei774.calisthenicsmemory.data.AppLanguage
import io.github.gonbei774.calisthenicsmemory.data.AppTheme
import io.github.gonbei774.calisthenicsmemory.data.LanguagePreferences
import io.github.gonbei774.calisthenicsmemory.ui.theme.*
import io.github.gonbei774.calisthenicsmemory.viewmodel.TrainingViewModel
import io.github.gonbei774.calisthenicsmemory.viewmodel.CsvImportReport
import io.github.gonbei774.calisthenicsmemory.viewmodel.CsvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenNew(
    viewModel: TrainingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToCsvDataManagement: () -> Unit = {},
    onNavigateToShareHub: () -> Unit = {},
    currentTheme: AppTheme = AppTheme.SYSTEM,
    onThemeChange: (AppTheme) -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                color = Slate600
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========================================
            // Section: AI Settings
            // ========================================
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ai_coach_settings_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.ai_context_description),
                        fontSize = 14.sp,
                        color = appColors.textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            item {
                val workoutPrefs = remember { WorkoutPreferences(context) }
                var apiKey by remember { mutableStateOf(workoutPrefs.getGeminiApiKey()) }
                var selectedModel by remember { mutableStateOf(workoutPrefs.getGeminiModel()) }
                var aiMemory by remember { mutableStateOf(workoutPrefs.getAiMemory()) }
                var showModelDialog by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = appColors.cardBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                workoutPrefs.setGeminiApiKey(it)
                            },
                            label = { Text(stringResource(R.string.gemini_api_key)) },
                            placeholder = { Text(stringResource(R.string.gemini_api_key_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = appColors.textPrimary,
                                unfocusedTextColor = appColors.textPrimary,
                                focusedBorderColor = Purple600,
                                unfocusedBorderColor = appColors.textSecondary
                            )
                        )

                        // Model selection card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = appColors.cardBackgroundSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            onClick = { showModelDialog = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Gemini Model",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary
                                    )
                                    Text(
                                        text = selectedModel,
                                        fontSize = 12.sp,
                                        color = appColors.textSecondary
                                    )
                                }
                                Text(
                                    text = "▼",
                                    fontSize = 12.sp,
                                    color = appColors.textSecondary
                                )
                            }
                        }

                        if (showModelDialog) {
                            val models = listOf(
                                "gemini-2.5-flash",
                                "gemini-2.0-flash",
                                "gemini-2.0-flash-lite-preview",
                                "gemini-1.5-flash",
                                "gemini-1.5-pro",
                                "gemini-1.0-pro"
                            )
                            AlertDialog(
                                onDismissRequest = { showModelDialog = false },
                                title = { Text("Select Gemini Model") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        models.forEach { model ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (selectedModel == model) {
                                                        Purple600.copy(alpha = 0.3f)
                                                    } else {
                                                        appColors.cardBackgroundSecondary
                                                    }
                                                ),
                                                onClick = {
                                                    selectedModel = model
                                                    workoutPrefs.setGeminiModel(model)
                                                    showModelDialog = false
                                                }
                                            ) {
                                                Text(
                                                    text = model,
                                                    modifier = Modifier.padding(16.dp),
                                                    color = appColors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showModelDialog = false }) {
                                        Text(stringResource(R.string.close))
                                    }
                                }
                            )
                        }

                        OutlinedTextField(
                            value = aiMemory,
                            onValueChange = {
                                aiMemory = it
                                workoutPrefs.setAiMemory(it)
                            },
                            label = { Text(stringResource(R.string.ai_coach_memory)) },
                            placeholder = { Text(stringResource(R.string.ai_coach_memory_hint)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = appColors.textPrimary,
                                unfocusedTextColor = appColors.textPrimary,
                                focusedBorderColor = Purple600,
                                unfocusedBorderColor = appColors.textSecondary
                            )
                        )

                        Text(
                            text = stringResource(R.string.ai_coach_memory_description),
                            fontSize = 12.sp,
                            color = appColors.textSecondary
                        )

                        Text(
                            text = stringResource(R.string.gemini_api_key_info),
                            fontSize = 12.sp,
                            color = appColors.textSecondary
                        )

                        Text(
                            text = stringResource(R.string.gemini_api_key_link),
                            fontSize = 14.sp,
                            color = Blue600,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }

            // Section: Language settings
            // ========================================

            // Section title and description
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.section_language),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.section_language_description),
                        fontSize = 14.sp,
                        color = appColors.textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            // Language selection card
            item {
                val languagePrefs = remember { LanguagePreferences(context) }
                var selectedLanguage by remember { mutableStateOf(languagePrefs.getLanguage()) }
                var showLanguageDialog by remember { mutableStateOf(false) }

                // Get current system language
                val currentLocale = Locale.getDefault().language

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = appColors.cardBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { showLanguageDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83C\uDF10",
                            fontSize = 32.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.language_setting),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textPrimary
                            )
                            Text(
                                text = stringResource(
                                    R.string.current_language,
                                    selectedLanguage.getDisplayName(currentLocale)
                                ),
                                fontSize = 14.sp,
                                color = appColors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Language selection dialog
                if (showLanguageDialog) {
                    AlertDialog(
                        onDismissRequest = { showLanguageDialog = false },
                        title = {
                            Text(
                                text = stringResource(R.string.language_setting),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppLanguage.entries.forEach { language ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedLanguage == language) {
                                                Purple600.copy(alpha = 0.3f)
                                            } else {
                                                appColors.cardBackgroundSecondary
                                            }
                                        ),
                                        onClick = {
                                            android.util.Log.d("SettingsScreen", "Language selected: ${language.code}")
                                            selectedLanguage = language
                                            languagePrefs.setLanguage(language)
                                            android.util.Log.d("SettingsScreen", "Language saved, recreating activity")
                                            showLanguageDialog = false

                                            // Recreate activity to apply language
                                            (context as? Activity)?.recreate()
                                        }
                                    ) {
                                        Text(
                                            text = language.getDisplayName(currentLocale),
                                            modifier = Modifier.padding(16.dp),
                                            color = appColors.textPrimary,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showLanguageDialog = false }) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    )
                }
            }

            // ========================================
            // Section: Theme Settings
            // ========================================

            // Section title and description
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.section_theme),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.section_theme_description),
                        fontSize = 14.sp,
                        color = appColors.textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            // Theme selection card
            item {
                var showThemeDialog by remember { mutableStateOf(false) }

                val themeDisplayName = when (currentTheme) {
                    AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                    AppTheme.LIGHT -> stringResource(R.string.theme_light)
                    AppTheme.DARK -> stringResource(R.string.theme_dark)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = appColors.cardBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { showThemeDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83C\uDFA8",
                            fontSize = 32.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.theme_setting),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.current_theme, themeDisplayName),
                                fontSize = 14.sp,
                                color = appColors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Theme selection dialog
                if (showThemeDialog) {
                    AlertDialog(
                        onDismissRequest = { showThemeDialog = false },
                        title = {
                            Text(
                                text = stringResource(R.string.theme_setting),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppTheme.entries.forEach { theme ->
                                    val displayName = when (theme) {
                                        AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                                        AppTheme.LIGHT -> stringResource(R.string.theme_light)
                                        AppTheme.DARK -> stringResource(R.string.theme_dark)
                                    }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (currentTheme == theme) {
                                                Purple600.copy(alpha = 0.3f)
                                            } else {
                                                appColors.cardBackgroundSecondary
                                            }
                                        ),
                                        onClick = {
                                            onThemeChange(theme)
                                            showThemeDialog = false
                                        }
                                    ) {
                                        Text(
                                            text = displayName,
                                            modifier = Modifier.padding(16.dp),
                                            color = appColors.textPrimary,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showThemeDialog = false }) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    )
                }
            }

            // ========================================
            // Section: Workout Settings
            // ========================================

            // Section title and description
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.section_workout_settings),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.section_workout_settings_description),
                        fontSize = 14.sp,
                        color = appColors.textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            // Workout Settingscard
            item {
                val workoutPrefs = remember { io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences(context) }
                var prefillEnabled by remember { mutableStateOf(workoutPrefs.isPrefillPreviousRecordEnabled()) }
                var startCountdown by remember { mutableStateOf(workoutPrefs.getStartCountdown()) }
                var setInterval by remember { mutableStateOf(workoutPrefs.getSetInterval()) }
                var startCountdownEnabled by remember { mutableStateOf(workoutPrefs.isStartCountdownEnabled()) }
                var setIntervalEnabled by remember { mutableStateOf(workoutPrefs.isSetIntervalEnabled()) }
                var flashNotificationEnabled by remember { mutableStateOf(workoutPrefs.isFlashNotificationEnabled()) }
                var keepScreenOnEnabled by remember { mutableStateOf(workoutPrefs.isKeepScreenOnEnabled()) }
                var showStartCountdownDialog by remember { mutableStateOf(false) }
                var showSetIntervalDialog by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Prefill settings
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = appColors.cardBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\uD83D\uDCDD",
                                fontSize = 32.sp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_prefill_previous),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.settings_prefill_previous_description),
                                    fontSize = 14.sp,
                                    color = appColors.textSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Switch(
                                checked = prefillEnabled,
                                onCheckedChange = { enabled ->
                                    prefillEnabled = enabled
                                    workoutPrefs.setPrefillPreviousRecordEnabled(enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = appColors.switchThumb,
                                    checkedTrackColor = Orange600,
                                    uncheckedThumbColor = appColors.switchThumb,
                                    uncheckedTrackColor = Slate600
                                )
                            )
                        }
                    }

                    // Start countdown settings
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = appColors.cardBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { if (startCountdownEnabled) showStartCountdownDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\u23F1\uFE0F",
                                fontSize = 32.sp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.start_countdown_setting),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.current_start_countdown, startCountdown),
                                    fontSize = 14.sp,
                                    color = if (startCountdownEnabled) appColors.textSecondary else appColors.textSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Switch(
                                checked = startCountdownEnabled,
                                onCheckedChange = { enabled ->
                                    startCountdownEnabled = enabled
                                    workoutPrefs.setStartCountdownEnabled(enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = appColors.switchThumb,
                                    checkedTrackColor = Orange600,
                                    uncheckedThumbColor = appColors.switchThumb,
                                    uncheckedTrackColor = Slate600
                                )
                            )
                        }
                    }

                    // Set interval settings
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = appColors.cardBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { if (setIntervalEnabled) showSetIntervalDialog = true }
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "\u23F8\uFE0F",
                                    fontSize = 32.sp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.set_interval_setting),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.current_set_interval, setInterval),
                                        fontSize = 14.sp,
                                        color = if (setIntervalEnabled) appColors.textSecondary else appColors.textSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Switch(
                                    checked = setIntervalEnabled,
                                    onCheckedChange = { enabled ->
                                        setIntervalEnabled = enabled
                                        workoutPrefs.setSetIntervalEnabled(enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = appColors.switchThumb,
                                        checkedTrackColor = Orange600,
                                        uncheckedThumbColor = appColors.switchThumb,
                                        uncheckedTrackColor = Slate600
                                    )
                                )
                            }
                            // Note
                            Text(
                                text = stringResource(R.string.set_interval_note),
                                fontSize = 12.sp,
                                color = appColors.textSecondary,
                                modifier = Modifier.padding(start = 68.dp, end = 20.dp, bottom = 16.dp)
                            )
                        }
                    }

                    // LED flash notification settings
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = appColors.cardBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "\uD83D\uDCF8",
                                    fontSize = 32.sp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.flash_notification_setting),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.flash_notification_description),
                                        fontSize = 14.sp,
                                        color = appColors.textSecondary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Switch(
                                    checked = flashNotificationEnabled,
                                    onCheckedChange = { enabled ->
                                        flashNotificationEnabled = enabled
                                        workoutPrefs.setFlashNotificationEnabled(enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = appColors.switchThumb,
                                        checkedTrackColor = Orange600,
                                        uncheckedThumbColor = appColors.switchThumb,
                                        uncheckedTrackColor = Slate600
                                    )
                                )
                            }
                        }
                    }

                    // Keep screen on settings
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = appColors.cardBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "\uD83D\uDD06",
                                    fontSize = 32.sp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.keep_screen_on_setting),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appColors.textPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.keep_screen_on_description),
                                        fontSize = 14.sp,
                                        color = appColors.textSecondary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Switch(
                                    checked = keepScreenOnEnabled,
                                    onCheckedChange = { enabled ->
                                        keepScreenOnEnabled = enabled
                                        workoutPrefs.setKeepScreenOnEnabled(enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = appColors.switchThumb,
                                        checkedTrackColor = Orange600,
                                        uncheckedThumbColor = appColors.switchThumb,
                                        uncheckedTrackColor = Slate600
                                    )
                                )
                            }
                        }
                    }
                }

                // Start countdown settingsdialog
                if (showStartCountdownDialog) {
                    var inputValue by remember { mutableStateOf(startCountdown.toString()) }

                    AlertDialog(
                        onDismissRequest = { showStartCountdownDialog = false },
                        title = {
                            Text(
                                text = stringResource(R.string.start_countdown_dialog_title),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            OutlinedTextField(
                                value = inputValue,
                                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) inputValue = it },
                                label = { Text(stringResource(R.string.enter_seconds)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Orange600,
                                    focusedLabelColor = Orange600,
                                    cursorColor = Orange600
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val newValue = inputValue.toIntOrNull() ?: io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences.DEFAULT_START_COUNTDOWN
                                    workoutPrefs.setStartCountdown(newValue)
                                    startCountdown = newValue
                                    showStartCountdownDialog = false
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Orange600
                                )
                            ) {
                                Text(stringResource(R.string.save))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStartCountdownDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // Set interval settingsdialog
                if (showSetIntervalDialog) {
                    var inputValue by remember { mutableStateOf(setInterval.toString()) }
                    val maxInterval = io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences.MAX_SET_INTERVAL

                    AlertDialog(
                        onDismissRequest = { showSetIntervalDialog = false },
                        title = {
                            Text(
                                text = stringResource(R.string.set_interval_dialog_title),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            OutlinedTextField(
                                value = inputValue,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.all { c -> c.isDigit() }) {
                                        // Upper limit check
                                        val intValue = newValue.toIntOrNull()
                                        if (intValue == null || intValue <= maxInterval) {
                                            inputValue = newValue
                                        }
                                    }
                                },
                                label = { Text(stringResource(R.string.enter_seconds_max, maxInterval)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Orange600,
                                    focusedLabelColor = Orange600,
                                    cursorColor = Orange600
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val newValue = (inputValue.toIntOrNull() ?: io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences.DEFAULT_SET_INTERVAL)
                                        .coerceIn(1, maxInterval)
                                    workoutPrefs.setSetInterval(newValue)
                                    setInterval = newValue
                                    showSetIntervalDialog = false
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Orange600
                                )
                            ) {
                                Text(stringResource(R.string.save))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSetIntervalDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }
            }

            // ========================================
            // Section: Data Management
            // ========================================

            // Section title
            item {
                Text(
                    text = stringResource(R.string.data_management),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Navigation card: Full backup
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = appColors.cardBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onNavigateToBackup() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83D\uDCBE",
                            fontSize = 32.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.section_full_backup),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.section_full_backup_description),
                                fontSize = 14.sp,
                                color = appColors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2
                            )
                        }
                        Text(
                            text = "\u203A",
                            fontSize = 24.sp,
                            color = appColors.textSecondary
                        )
                    }
                }
            }

            // Navigationcard: Partial Data Management (CSV)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = appColors.cardBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onNavigateToCsvDataManagement() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83D\uDCCB",
                            fontSize = 32.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.section_partial_data_management),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.section_partial_data_management_description),
                                fontSize = 14.sp,
                                color = appColors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2
                            )
                        }
                        Text(
                            text = "\u203A",
                            fontSize = 24.sp,
                            color = appColors.textSecondary
                        )
                    }
                }
            }

            // Navigation card: Share
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = appColors.cardBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onNavigateToShareHub() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83E\uDD1D",
                            fontSize = 32.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.share_section_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.share_section_description),
                                fontSize = 14.sp,
                                color = appColors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2
                            )
                        }
                        Text(
                            text = "\u203A",
                            fontSize = 24.sp,
                            color = appColors.textSecondary
                        )
                    }
                }
            }

            // ========================================
            // Section: App Info
            // ========================================

            // Section title and description
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.section_app_info),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.section_app_info_description),
                        fontSize = 14.sp,
                        color = appColors.textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            // App Infocard
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = appColors.cardBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // App name and description (centered)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.app_description),
                                fontSize = 14.sp,
                                color = appColors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Version
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.app_version),
                                    fontSize = 16.sp,
                                    color = appColors.textPrimary
                                )
                                Text(
                                    text = BuildConfig.VERSION_NAME,
                                    fontSize = 14.sp,
                                    color = appColors.textSecondary
                                )
                            }
                        }

                        // Source code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/Gonbei774/CalisthenicsMemory"))
                                    context.startActivity(intent)
                                },
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "<>",
                                fontSize = 20.sp,
                                color = appColors.textSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.app_source_code),
                                fontSize = 16.sp,
                                color = appColors.textPrimary
                            )
                        }

                        // Open source licenses
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToLicenses() },
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\uD83D\uDCC4",
                                fontSize = 20.sp
                            )
                            Text(
                                text = stringResource(R.string.open_source_licenses),
                                fontSize = 16.sp,
                                color = appColors.textPrimary
                            )
                        }
                    }
                }
            }

            // Author card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = appColors.cardBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Section title
                        Text(
                            text = stringResource(R.string.app_author),
                            fontSize = 14.sp,
                            color = appColors.textSecondary
                        )

                        // Developer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Gonbei774",
                                fontSize = 16.sp,
                                color = appColors.textPrimary
                            )
                        }
                    }
                }
            }

            // Feedback card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = appColors.cardBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Section title
                        Text(
                            text = stringResource(R.string.app_feedback),
                            fontSize = 14.sp,
                            color = appColors.textSecondary
                        )

                        // Report issue on Codeberg
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/Gonbei774/CalisthenicsMemory/issues"))
                                    context.startActivity(intent)
                                },
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\uD83D\uDCDD",
                                fontSize = 20.sp
                            )
                            Text(
                                text = stringResource(R.string.report_issue_codeberg),
                                fontSize = 16.sp,
                                color = appColors.textPrimary
                            )
                        }

                        // Report issue on GitHub
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Gonbei774/CalisthenicsMemory/issues"))
                                    context.startActivity(intent)
                                },
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\uD83D\uDCDD",
                                fontSize = 20.sp
                            )
                            Text(
                                text = stringResource(R.string.report_issue_github),
                                fontSize = 16.sp,
                                color = appColors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convert CSV type to localized string
 */
@Composable
fun getCsvTypeLocalizedString(csvType: CsvType?): String {
    return when (csvType) {
        CsvType.GROUPS -> stringResource(R.string.groups)
        CsvType.EXERCISES -> stringResource(R.string.exercises)
        CsvType.RECORDS -> stringResource(R.string.records)
        null -> stringResource(R.string.unknown_short)
    }
}

/**
 * Automatically detect CSV type
 */
fun detectCsvType(csvString: String): CsvType? {
    val firstLine = csvString.lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .firstOrNull() ?: return null

    return when {
        firstLine.trim() == "name" -> CsvType.GROUPS
        firstLine.startsWith("name,type,group,sortOrder") -> CsvType.EXERCISES
        firstLine.startsWith("exerciseName,exerciseType") -> CsvType.RECORDS
        else -> null
    }
}

/**
 * Execute CSV import
 */
suspend fun executeCsvImport(
    viewModel: TrainingViewModel,
    csvData: String,
    csvImportType: CsvType?
): CsvImportReport? {
    return withContext(Dispatchers.IO) {
        when (csvImportType) {
            CsvType.GROUPS -> viewModel.importGroups(csvData)
            CsvType.EXERCISES -> viewModel.importExercises(csvData)
            CsvType.RECORDS -> viewModel.importRecordsFromCsv(csvData)
            else -> null
        }
    }
}
