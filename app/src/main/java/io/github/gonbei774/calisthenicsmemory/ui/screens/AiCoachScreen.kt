package io.github.gonbei774.calisthenicsmemory.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gonbei774.calisthenicsmemory.R
import io.github.gonbei774.calisthenicsmemory.data.AiMessage
import io.github.gonbei774.calisthenicsmemory.viewmodel.CommunityShareData
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import io.github.gonbei774.calisthenicsmemory.ui.theme.LocalAppColors
import io.github.gonbei774.calisthenicsmemory.ui.theme.Purple600
import io.github.gonbei774.calisthenicsmemory.ui.theme.Slate600
import io.github.gonbei774.calisthenicsmemory.viewmodel.extractWorkoutJson
import io.github.gonbei774.calisthenicsmemory.viewmodel.extractMemoryUpdate
import io.github.gonbei774.calisthenicsmemory.viewmodel.AiViewModel
import io.github.gonbei774.calisthenicsmemory.viewmodel.TrainingViewModel
import androidx.compose.material.icons.filled.Face
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(
    viewModel: AiViewModel,
    trainingViewModel: TrainingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProgramEdit: (Long) -> Unit = {},
    onNavigateToProgramExecution: (Long) -> Unit = {},
    onNavigateToIntervalEdit: (Long) -> Unit = {},
    onNavigateToIntervalExecution: (Long) -> Unit = {},
    initialPrompt: String? = null
) {
    val appColors = LocalAppColors.current
    val chatMessages by viewModel.chatMessages.collectAsState()
    val allThreads by viewModel.allThreads.collectAsState()
    val currentThreadId by viewModel.currentThreadId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var suggestedWorkoutJson by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showMemoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialPrompt) {
        if (initialPrompt != null && currentThreadId == null) {
            val contextData = trainingViewModel.getAllDataAsJson()
            viewModel.startNewThread(initialPrompt, contextData)
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = appColors.cardBackground,
                drawerContentColor = appColors.textPrimary
            ) {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("New Chat") },
                    selected = currentThreadId == null,
                    onClick = {
                        viewModel.startNewThread()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = appColors.textTertiary.copy(alpha = 0.2f))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Face, contentDescription = null) },
                    label = { Text(stringResource(R.string.ai_coach_memory)) },
                    selected = false,
                    onClick = {
                        showMemoryDialog = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = appColors.textTertiary.copy(alpha = 0.2f))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(allThreads) { thread ->
                        NavigationDrawerItem(
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = thread.title,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteThread(thread.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Thread",
                                            tint = appColors.textTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            selected = thread.id == currentThreadId,
                            onClick = {
                                viewModel.selectThread(thread.id)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        }
    ) {
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = Color.White
                            )
                        }
                        Text(
                            text = stringResource(R.string.ai_coach_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear Chat",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(chatMessages) { message ->
                        ChatBubble(message) { json -> suggestedWorkoutJson = json }
                    }
                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Purple600
                                )
                            }
                        }
                    }
                }

                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                    color = appColors.cardBackground
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask your coach...", color = appColors.textTertiary) },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = appColors.textPrimary,
                                unfocusedTextColor = appColors.textPrimary,
                                focusedBorderColor = Purple600,
                                unfocusedBorderColor = appColors.textSecondary
                            )
                        )
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    scope.launch {
                                        val contextData = trainingViewModel.getAllDataAsJson()
                                        viewModel.sendMessage(inputText, contextData)
                                        inputText = ""
                                    }
                                }
                            },
                            enabled = inputText.isNotBlank() && !isLoading
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Purple600 else appColors.textTertiary
                            )
                        }
                    }
                }
            }
        }
    }

    if (suggestedWorkoutJson != null) {
        val json = Json { ignoreUnknownKeys = true }
        val shareData = try {
            json.decodeFromString<CommunityShareData>(suggestedWorkoutJson!!)
        } catch (e: Exception) {
            null
        }

        if (shareData != null) {
            AlertDialog(
                onDismissRequest = { suggestedWorkoutJson = null },
                title = { Text(stringResource(R.string.ai_coach_suggested_workout)) },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        shareData.data.programs.forEach { program ->
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = appColors.cardBackground)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = program.name, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
                                        program.exercises.forEach { ex ->
                                            Text(
                                                text = "${ex.exerciseName} (${ex.sets} sets)",
                                                fontSize = 14.sp,
                                                color = appColors.textSecondary
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = {
                                                scope.launch {
                                                    val singleProgramJson = json.encodeToString(shareData.copy(data = shareData.data.copy(programs = listOf(program), intervalPrograms = emptyList())))
                                                    val report = trainingViewModel.importCommunityShare(singleProgramJson)
                                                    suggestedWorkoutJson = null
                                                    if (report.importedProgramIds.isNotEmpty()) {
                                                        onNavigateToProgramEdit(report.importedProgramIds.first())
                                                    }
                                                }
                                            }) {
                                                Text(stringResource(R.string.ai_coach_edit_workout))
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val singleProgramJson = json.encodeToString(shareData.copy(data = shareData.data.copy(programs = listOf(program), intervalPrograms = emptyList())))
                                                        val report = trainingViewModel.importCommunityShare(singleProgramJson)
                                                        suggestedWorkoutJson = null
                                                        if (report.importedProgramIds.isNotEmpty()) {
                                                            onNavigateToProgramExecution(report.importedProgramIds.first())
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Purple600)
                                            ) {
                                                Text(stringResource(R.string.ai_coach_start_workout))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        shareData.data.intervalPrograms.forEach { program ->
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = appColors.cardBackground)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = program.name, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
                                        Text(
                                            text = "Interval: ${program.workSeconds}s / ${program.restSeconds}s",
                                            fontSize = 14.sp,
                                            color = appColors.textSecondary
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = {
                                                scope.launch {
                                                    val singleProgramJson = json.encodeToString(shareData.copy(data = shareData.data.copy(programs = emptyList(), intervalPrograms = listOf(program))))
                                                    val report = trainingViewModel.importCommunityShare(singleProgramJson)
                                                    suggestedWorkoutJson = null
                                                    if (report.importedIntervalProgramIds.isNotEmpty()) {
                                                        onNavigateToIntervalEdit(report.importedIntervalProgramIds.first())
                                                    }
                                                }
                                            }) {
                                                Text(stringResource(R.string.ai_coach_edit_workout))
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val singleProgramJson = json.encodeToString(shareData.copy(data = shareData.data.copy(programs = emptyList(), intervalPrograms = listOf(program))))
                                                        val report = trainingViewModel.importCommunityShare(singleProgramJson)
                                                        suggestedWorkoutJson = null
                                                        if (report.importedIntervalProgramIds.isNotEmpty()) {
                                                            onNavigateToIntervalExecution(report.importedIntervalProgramIds.first())
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Purple600)
                                            ) {
                                                Text(stringResource(R.string.ai_coach_start_workout))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { suggestedWorkoutJson = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    if (showMemoryDialog) {
        var memoryText by remember { mutableStateOf(viewModel.getAiMemory()) }
        AlertDialog(
            onDismissRequest = { showMemoryDialog = false },
            title = { Text(stringResource(R.string.ai_coach_memory)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.ai_coach_memory_description),
                        fontSize = 12.sp,
                        color = appColors.textSecondary
                    )
                    OutlinedTextField(
                        value = memoryText,
                        onValueChange = { memoryText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        placeholder = { Text(stringResource(R.string.ai_coach_memory_hint)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedBorderColor = Purple600,
                            unfocusedBorderColor = appColors.textSecondary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateAiMemory(memoryText)
                    showMemoryDialog = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}




@Composable
fun ChatBubble(
    message: AiMessage,
    onReviewSuggestion: (String) -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (message.isUser) Purple600 else appColors.cardBackground
    val textColor = if (message.isUser) Color.White else appColors.textPrimary

    val workoutJson = if (!message.isUser) extractWorkoutJson(message.text) else null
    val memoryJson = if (!message.isUser) extractMemoryUpdate(message.text) else null
    var displayText = message.text
    if (workoutJson != null) displayText = displayText.replace(workoutJson, "")
    if (memoryJson != null) displayText = displayText.replace(memoryJson, "")
    displayText = displayText.trim()

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Surface(
                color = backgroundColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isUser) 16.dp else 0.dp,
                    bottomEnd = if (message.isUser) 0.dp else 16.dp
                ),
                tonalElevation = 1.dp
            ) {
                if (message.isUser) {
                    Text(
                        text = displayText,
                        color = textColor,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 16.sp
                    )
                } else {
                    MarkdownText(
                        markdown = displayText,
                        modifier = Modifier.padding(12.dp),
                        style = TextStyle(
                            color = textColor,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            if (workoutJson != null) {
                Button(
                    onClick = { onReviewSuggestion(workoutJson) },
                    modifier = Modifier.padding(top = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple600)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_coach_review_suggestion))
                }
            }
        }
    }
}
