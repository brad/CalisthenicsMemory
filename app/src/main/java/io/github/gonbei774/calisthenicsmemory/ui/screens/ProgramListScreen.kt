package io.github.gonbei774.calisthenicsmemory.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gonbei774.calisthenicsmemory.R
import io.github.gonbei774.calisthenicsmemory.data.Program
import io.github.gonbei774.calisthenicsmemory.data.SavedWorkoutState
import io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences
import io.github.gonbei774.calisthenicsmemory.ui.theme.*
import io.github.gonbei774.calisthenicsmemory.viewmodel.TrainingViewModel
import androidx.compose.ui.platform.LocalContext

@Composable
fun ProgramListScreen(
    viewModel: TrainingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long?) -> Unit,  // null = new program
    onNavigateToExecute: (Long) -> Unit,
    onNavigateToResume: (Long) -> Unit = onNavigateToExecute,
    onNavigateToAiCoach: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val programs by viewModel.programs.collectAsState()
    val context = LocalContext.current
    val savedWorkoutState = remember { SavedWorkoutState(context) }
    val savedProgramId = savedWorkoutState.getSavedProgramId()
    val workoutPrefs = remember { WorkoutPreferences(context) }
    val apiKey = remember { workoutPrefs.getGeminiApiKey() }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                color = Orange600
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
                        text = stringResource(R.string.program_list_title),
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
                onClick = { onNavigateToEdit(null) },
                containerColor = Orange600
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_program), tint = appColors.textPrimary)
            }
        }
    ) { paddingValues ->
        if (programs.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.program_empty),
                    fontSize = 16.sp,
                    color = appColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(programs, key = { it.id }) { program ->
                    ProgramItem(
                        program = program,
                        isSaved = program.id == savedProgramId,
                        onClick = { onNavigateToExecute(program.id) },
                        onEdit = { onNavigateToEdit(program.id) },
                        onDelete = { viewModel.deleteProgram(program.id) },
                        onResume = { onNavigateToResume(program.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgramItem(
    program: Program,
    isSaved: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onResume: () -> Unit
) {
    val appColors = LocalAppColors.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = if (isSaved) onResume else onClick,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = appColors.cardBackground
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = program.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary
                    )
                    if (isSaved) {
                        Text(
                            text = stringResource(R.string.program_start),
                            fontSize = 14.sp,
                            color = Orange600,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit),
                            tint = appColors.textSecondary
                        )
                    }
                    IconButton(onClick = if (isSaved) onResume else onClick) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.program_start),
                            tint = Orange600
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset(x = 100.dp, y = 0.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.Red
                        )
                    }
                )
            }
        }
    }
}
