package io.github.gonbei774.calisthenicsmemory.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import io.github.gonbei774.calisthenicsmemory.R
import io.github.gonbei774.calisthenicsmemory.data.Exercise
import io.github.gonbei774.calisthenicsmemory.data.ExerciseGroup
import io.github.gonbei774.calisthenicsmemory.data.IntervalProgram
import io.github.gonbei774.calisthenicsmemory.data.Program
import io.github.gonbei774.calisthenicsmemory.data.TodoTask
import io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences
import io.github.gonbei774.calisthenicsmemory.ui.theme.*
import io.github.gonbei774.calisthenicsmemory.util.ProgramTimeEstimator
import io.github.gonbei774.calisthenicsmemory.util.SearchUtils
import io.github.gonbei774.calisthenicsmemory.viewmodel.TrainingViewModel

@Composable
fun ToDoScreen(
    viewModel: TrainingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRecord: (Long) -> Unit,
    onNavigateToWorkout: (Long) -> Unit,
    onNavigateToProgramPreview: (Long) -> Unit,
    onNavigateToIntervalPreview: (Long) -> Unit,
    onNavigateToAiCoach: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val todoTasks by viewModel.todoTasks.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val programs by viewModel.programs.collectAsState()
    val intervalPrograms by viewModel.intervalPrograms.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var repeatDialogTask by remember { mutableStateOf<TodoTask?>(null) }
    var deleteConfirmTask by remember { mutableStateOf<TodoTask?>(null) }

    val context = LocalContext.current
    val workoutPrefs = remember { WorkoutPreferences(context) }
    val apiKey = remember { workoutPrefs.getGeminiApiKey() }

    // Map IDs to objects for display
    val exerciseMap = remember(exercises) {
        exercises.associateBy { it.id }
    }
    val groupMap = remember(groups) {
        groups.associateBy { it.id }
    }
    val programMap = remember(programs) {
        programs.associateBy { it.id }
    }
    val intervalProgramMap = remember(intervalPrograms) {
        intervalPrograms.associateBy { it.id }
    }

    // グループごとの所属種目
    val groupExercisesMap = remember(groups, exercises) {
        groups.associate { group ->
            group.id to exercises.filter { it.group == group.name }.sortedBy { it.displayOrder }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                color = Amber500
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
                        text = stringResource(R.string.todo_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    if (apiKey.isNotBlank()) {
                        IconButton(onClick = onNavigateToAiCoach) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = stringResource(R.string.ai_coach_suggestion),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Amber500
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.todo_add_items), tint = Color.White)
            }
        }
    ) { paddingValues ->
        if (todoTasks.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.todo_empty),
                    fontSize = 16.sp,
                    color = appColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(todoTasks, key = { it.id }) { task ->
                    TodoItem(
                        task = task,
                        exercise = if (task.type == TodoTask.TYPE_EXERCISE) exerciseMap[task.referenceId] else null,
                        program = if (task.type == TodoTask.TYPE_PROGRAM) programMap[task.referenceId] else null,
                        intervalProgram = if (task.type == TodoTask.TYPE_INTERVAL) intervalProgramMap[task.referenceId] else null,
                        onClick = {
                            when (task.type) {
                                TodoTask.TYPE_EXERCISE -> onNavigateToWorkout(task.referenceId)
                                TodoTask.TYPE_PROGRAM -> onNavigateToProgramPreview(task.referenceId)
                                TodoTask.TYPE_INTERVAL -> onNavigateToIntervalPreview(task.referenceId)
                            }
                        },
                        onDelete = { deleteConfirmTask = task },
                        onRepeatEdit = { repeatDialogTask = task }
                    )
                }
            }
        }
    }

    // Add Task Dialog
    if (showAddDialog) {
        AddTaskDialog(
            exercises = exercises,
            groups = groups,
            programs = programs,
            intervalPrograms = intervalPrograms,
            groupExercisesMap = groupExercisesMap,
            onDismiss = { showAddDialog = false },
            onAdd = { type, id ->
                when (type) {
                    0 -> viewModel.addTodoTask(id)
                    1 -> viewModel.addTodoTaskProgram(id)
                    2 -> viewModel.addTodoTaskInterval(id)
                }
                showAddDialog = false
            }
        )
    }

    // Repeat Days Dialog
    repeatDialogTask?.let { task ->
        RepeatDaysDialog(
            currentRepeatDays = task.repeatDays,
            onSave = { days ->
                viewModel.updateTodoRepeatDays(task.id, days)
                repeatDialogTask = null
            },
            onDismiss = { repeatDialogTask = null }
        )
    }

    // Delete Confirmation
    deleteConfirmTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTask = null },
            containerColor = appColors.cardBackground,
            title = { Text(stringResource(R.string.delete_confirmation), color = appColors.textPrimary) },
            text = { Text(stringResource(R.string.todo_delete_confirm_message, ""), color = appColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTodoTask(task.id)
                    deleteConfirmTask = null
                }) {
                    Text(stringResource(R.string.delete), color = Red600)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTask = null }) {
                    Text(stringResource(R.string.cancel), color = appColors.textSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoItem(
    task: TodoTask,
    exercise: Exercise?,
    program: Program?,
    intervalProgram: IntervalProgram?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRepeatEdit: () -> Unit
) {
    val appColors = LocalAppColors.current
    var showMenu by remember { mutableStateOf(false) }

    val title = when (task.type) {
        TodoTask.TYPE_EXERCISE -> exercise?.name ?: stringResource(R.string.unknown_short)
        TodoTask.TYPE_PROGRAM -> program?.name ?: stringResource(R.string.unknown_short)
        TodoTask.TYPE_INTERVAL -> intervalProgram?.name ?: stringResource(R.string.unknown_short)
        else -> stringResource(R.string.unknown_short)
    }

    val typeLabel = when (task.type) {
        TodoTask.TYPE_EXERCISE -> stringResource(R.string.home_workout)
        TodoTask.TYPE_PROGRAM -> stringResource(R.string.program_list_title)
        TodoTask.TYPE_INTERVAL -> stringResource(R.string.todo_tab_intervals)
        else -> ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(containerColor = appColors.cardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = typeLabel,
                        fontSize = 12.sp,
                        color = Amber500,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary
                    )
                    RepeatDaysLabel(task.repeatDays)
                }

                IconButton(onClick = onRepeatEdit) {
                    val icon = if (task.repeatDays.isEmpty()) Icons.Default.Add else Icons.Default.Menu
                    Icon(
                        icon,
                        contentDescription = stringResource(R.string.todo_repeat_title),
                        tint = appColors.textSecondary
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Red600)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddTaskDialog(
    exercises: List<Exercise>,
    groups: List<ExerciseGroup>,
    programs: List<Program>,
    intervalPrograms: List<IntervalProgram>,
    groupExercisesMap: Map<Long, List<Exercise>>,
    onDismiss: () -> Unit,
    onAdd: (Int, Long) -> Unit
) {
    val appColors = LocalAppColors.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = appColors.cardBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tab-like Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        stringResource(R.string.exercises),
                        stringResource(R.string.program_list_title),
                        stringResource(R.string.todo_tab_intervals)
                    ).forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        Text(
                            text = title,
                            modifier = Modifier
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                                .padding(8.dp),
                            color = if (selected) Amber500 else appColors.textSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> ExerciseSelectionList(groups, groupExercisesMap) { onAdd(0, it) }
                        1 -> ProgramSelectionList(programs) { onAdd(1, it) }
                        2 -> IntervalSelectionList(intervalPrograms) { onAdd(2, it) }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(8.dp)
                ) {
                    Text(stringResource(R.string.cancel), color = appColors.textSecondary)
                }
            }
        }
    }
}

@Composable
fun ExerciseSelectionList(
    groups: List<ExerciseGroup>,
    groupExercisesMap: Map<Long, List<Exercise>>,
    onSelect: (Long) -> Unit
) {
    val appColors = LocalAppColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            val groupExercises = groupExercisesMap[group.id] ?: emptyList()
            if (groupExercises.isNotEmpty()) {
                item {
                    Text(
                        text = group.name,
                        fontSize = 14.sp,
                        color = Amber500,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(groupExercises) { exercise ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(exercise.id) },
                        colors = CardDefaults.cardColors(containerColor = appColors.cardBackgroundSecondary)
                    ) {
                        Text(
                            text = exercise.name,
                            modifier = Modifier.padding(12.dp),
                            color = appColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProgramSelectionList(programs: List<Program>, onSelect: (Long) -> Unit) {
    val appColors = LocalAppColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(programs) { program ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(program.id) },
                colors = CardDefaults.cardColors(containerColor = appColors.cardBackgroundSecondary)
            ) {
                Text(
                    text = program.name,
                    modifier = Modifier.padding(12.dp),
                    color = appColors.textPrimary
                )
            }
        }
    }
}

@Composable
fun IntervalSelectionList(programs: List<IntervalProgram>, onSelect: (Long) -> Unit) {
    val appColors = LocalAppColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(programs) { program ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(program.id) },
                colors = CardDefaults.cardColors(containerColor = appColors.cardBackgroundSecondary)
            ) {
                Text(
                    text = program.name,
                    modifier = Modifier.padding(12.dp),
                    color = appColors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun RepeatDaysLabel(repeatDays: String) {
    if (repeatDays.isEmpty()) return
    val dayNumbers = try { repeatDays.split(",").filter { it.isNotBlank() }.map { it.trim().toInt() } } catch (e: Exception) { emptyList<Int>() }
    if (dayNumbers.isEmpty()) return
    val locale = java.util.Locale.getDefault()
    val dayNames = dayNumbers.map { dayNum ->
        try {
            java.time.DayOfWeek.of(dayNum).getDisplayName(java.time.format.TextStyle.SHORT, locale)
        } catch (e: Exception) {
            ""
        }
    }.filter { it.isNotBlank() }

    if (dayNames.isNotEmpty()) {
        Text(
            text = dayNames.joinToString(" "),
            fontSize = 10.sp,
            color = Amber500,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun RepeatDaysDialog(
    currentRepeatDays: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current
    val locale = java.util.Locale.getDefault()
    var selectedDays by remember(currentRepeatDays) {
        val initial = if (currentRepeatDays.isEmpty()) emptySet()
        else currentRepeatDays.split(",").filter { it.isNotBlank() }.map { it.trim().toInt() }.toSet()
        mutableStateOf(initial)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.cardBackground,
        title = {
            Text(
                text = stringResource(R.string.todo_repeat_title),
                color = appColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                (1..7).forEach { dayNum ->
                    val dayOfWeek = java.time.DayOfWeek.of(dayNum)
                    val dayName = dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale)
                    val isSelected = dayNum in selectedDays

                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Amber500 else appColors.cardBackgroundSecondary,
                        onClick = {
                            selectedDays = if (isSelected) selectedDays - dayNum else selectedDays + dayNum
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = dayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else appColors.textSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = selectedDays.sorted().joinToString(",")
                onSave(result)
            }) {
                Text(stringResource(R.string.todo_repeat_save), color = Amber500)
            }
        }
    )
}
