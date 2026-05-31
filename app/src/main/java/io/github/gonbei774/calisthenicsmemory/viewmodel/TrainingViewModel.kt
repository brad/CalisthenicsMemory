package io.github.gonbei774.calisthenicsmemory.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gonbei774.calisthenicsmemory.data.AppDatabase
import io.github.gonbei774.calisthenicsmemory.data.Exercise
import io.github.gonbei774.calisthenicsmemory.data.ExerciseGroup
import io.github.gonbei774.calisthenicsmemory.data.Program
import io.github.gonbei774.calisthenicsmemory.data.ProgramExercise
import io.github.gonbei774.calisthenicsmemory.data.IntervalProgram
import io.github.gonbei774.calisthenicsmemory.data.IntervalProgramExercise
import io.github.gonbei774.calisthenicsmemory.data.IntervalRecord
import io.github.gonbei774.calisthenicsmemory.data.ProgramLoop
import io.github.gonbei774.calisthenicsmemory.data.TodoTask
import io.github.gonbei774.calisthenicsmemory.data.TrainingRecord
import io.github.gonbei774.calisthenicsmemory.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Backup data classes
@Serializable
data class BackupData(
    val version: Int,
    val exportDate: String,
    val app: String,
    val groups: List<ExportGroup>,
    val exercises: List<ExportExercise>,
    val records: List<ExportRecord>,
    val programs: List<ExportProgram> = emptyList(),           // Added in v4
    val programExercises: List<ExportProgramExercise> = emptyList(),  // Added in v4
    val programLoops: List<ExportProgramLoop> = emptyList(),    // Added in v5 (default empty for backward compatibility)
    val intervalPrograms: List<ExportIntervalProgram> = emptyList(),              // Added in v7
    val intervalProgramExercises: List<ExportIntervalProgramExercise> = emptyList(), // Added in v7
    val intervalRecords: List<ExportIntervalRecord> = emptyList(),                 // Added in v7
    val todoTasks: List<ExportTodoTask> = emptyList()                             // Added in v8
)

@Serializable
data class ExportGroup(
    val id: Long,
    val name: String,
    val displayOrder: Int = 0
)

@Serializable
data class ExportExercise(
    val id: Long,
    val name: String,
    val type: String,
    val group: String?,
    val sortOrder: Int,
    val displayOrder: Int = 0,       // Display order (backward compatibility)
    val laterality: String,
    val targetSets: Int? = null,
    val targetValue: Int? = null,
    val isFavorite: Boolean = false, // Favorite (default false for backward compatibility)
    val restInterval: Int? = null,   // Exercise-specific rest interval (backward compatibility)
    val repDuration: Int? = null,    // Exercise-specific rep duration (backward compatibility)
    val distanceTrackingEnabled: Boolean = false,  // Enable distance input (added in v3)
    val weightTrackingEnabled: Boolean = false,    // Enable weight input (added in v3)
    val assistanceTrackingEnabled: Boolean = false, // Enable assistance input (added in v6)
    val description: String? = null                // Exercise description (added in v6)
)

@Serializable
data class ExportRecord(
    val id: Long,
    val exerciseId: Long,
    val valueRight: Int,
    val valueLeft: Int?,
    val setNumber: Int,
    val date: String,
    val time: String,
    val comment: String,
    val distanceCm: Int? = null,  // Distance (cm, added in v3)
    val weightG: Int? = null,     // Additional weight (g, added in v3)
    val assistanceG: Int? = null,  // Assistance amount (g, added in v6)
    val rpe: Int? = null          // RPE (v22 for AI)
)

@Serializable
data class ExportProgram(
    val id: Long,
    val name: String
    // timerMode/startInterval migrated to SharedPreferences (v14)
    // Ignored by ignoreUnknownKeys during old JSON import
)

@Serializable
data class ExportProgramExercise(
    val id: Long,
    val programId: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val sets: Int,
    val targetValue: Int,
    val intervalSeconds: Int,
    val loopId: Long? = null  // Added in v5 (default null for backward compatibility)
)

@Serializable
data class ExportProgramLoop(
    val id: Long,
    val programId: Long,
    val sortOrder: Int,
    val rounds: Int,
    val restBetweenRounds: Int
)

@Serializable
data class ExportIntervalProgram(
    val id: Long,
    val name: String,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val roundRestSeconds: Int
)

@Serializable
data class ExportIntervalProgramExercise(
    val id: Long,
    val programId: Long,
    val exerciseId: Long,
    val sortOrder: Int
)

@Serializable
data class ExportIntervalRecord(
    val id: Long,
    val programName: String,
    val date: String,
    val time: String,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val roundRestSeconds: Int,
    val completedRounds: Int,
    val completedExercisesInLastRound: Int,
    val exercisesJson: String,
    val comment: String? = null
)

@Serializable
data class ExportTodoTask(
    val id: Long,
    val type: String,
    val referenceId: Long,
    val sortOrder: Int,
    val repeatDays: String = "",
    val lastCompletedDate: String? = null
)

class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val exerciseDao = database.exerciseDao()
    private val recordDao = database.trainingRecordDao()
    private val groupDao = database.exerciseGroupDao()
    private val todoTaskDao = database.todoTaskDao()
    private val programDao = database.programDao()
    private val programExerciseDao = database.programExerciseDao()
    private val programLoopDao = database.programLoopDao()
    private val intervalProgramDao = database.intervalProgramDao()
    private val intervalProgramExerciseDao = database.intervalProgramExerciseDao()
    private val intervalRecordDao = database.intervalRecordDao()

    companion object {
        // Fixed key for favorite group (translated in UI)
        const val FAVORITE_GROUP_KEY = "★FAVORITES"
    }

    // Exercises
    val exercises: StateFlow<List<Exercise>> = exerciseDao.getAllExercises()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // Exercise Groups
    val groups: StateFlow<List<ExerciseGroup>> = groupDao.getAllGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // Training Records
    val records: StateFlow<List<TrainingRecord>> = recordDao.getAllRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // Snackbar message (UiMessage type follows language changes)
    private val _snackbarMessage = MutableStateFlow<UiMessage?>(null)
    val snackbarMessage: StateFlow<UiMessage?> = _snackbarMessage.asStateFlow()

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun showWrongFileTypeMessage(detected: String, expected: String) {
        _snackbarMessage.value = UiMessage.WrongFileType(detected = detected, expected = expected)
    }

    fun showSnackbar(message: UiMessage) {
        _snackbarMessage.value = message
    }

    fun showBackupResult(success: Boolean) {
        _snackbarMessage.value = if (success) {
            UiMessage.BackupSaved
        } else {
            UiMessage.BackupFailed
        }
    }

    // Exercise operations
    fun addExercise(
        name: String,
        type: String,
        group: String? = null,
        sortOrder: Int = 0,
        laterality: String = "Bilateral",
        targetSets: Int? = null,
        targetValue: Int? = null,
        isFavorite: Boolean = false,
        restInterval: Int? = null,       // Exercise-specific rest interval (seconds)
        repDuration: Int? = null,        // Exercise-specific 1-rep duration (seconds)
        distanceTrackingEnabled: Boolean = false,  // Distance tracking enabled
        weightTrackingEnabled: Boolean = false,    // Weight tracking enabled
        assistanceTrackingEnabled: Boolean = false, // Assistance tracking enabled
        description: String? = null                // Exercise description
    ) {
        viewModelScope.launch {
            try {
                val existingExercises = exercises.value
                val isDuplicate = existingExercises.any {
                    it.name.equals(name, ignoreCase = true) && it.type == type
                }

                if (isDuplicate) {
                    _snackbarMessage.value = UiMessage.AlreadyRegistered(name, type)
                    return@launch
                }

                val exercise = Exercise(
                    name = name,
                    type = type,
                    group = group,
                    sortOrder = sortOrder,
                    laterality = laterality,
                    targetSets = targetSets,
                    targetValue = targetValue,
                    isFavorite = isFavorite,
                    restInterval = restInterval,
                    repDuration = repDuration,
                    distanceTrackingEnabled = distanceTrackingEnabled,
                    weightTrackingEnabled = weightTrackingEnabled,
                    assistanceTrackingEnabled = assistanceTrackingEnabled,
                    description = description
                )
                exerciseDao.insertExercise(exercise)
                _snackbarMessage.value = UiMessage.ExerciseAdded
            } catch (e: SQLiteConstraintException) {
                _snackbarMessage.value = UiMessage.ExerciseAlreadyExists
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun updateExercise(exercise: Exercise) {
        viewModelScope.launch {
            try {
                val existingExercises = exercises.value
                val isDuplicate = existingExercises.any {
                    it.id != exercise.id &&
                            it.name.equals(exercise.name, ignoreCase = true) &&
                            it.type == exercise.type
                }

                if (isDuplicate) {
                    _snackbarMessage.value = UiMessage.AlreadyInUse(exercise.name, exercise.type)
                    return@launch
                }

                exerciseDao.updateExercise(exercise)
                _snackbarMessage.value = UiMessage.ExerciseUpdated
            } catch (e: SQLiteConstraintException) {
                _snackbarMessage.value = UiMessage.ExerciseAlreadyExists
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch {
            try {
                exerciseDao.deleteExercise(exercise)
                todoTaskDao.deleteByReference(TodoTask.TYPE_EXERCISE, exercise.id)
                _snackbarMessage.value = UiMessage.ExerciseDeleted
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun toggleFavorite(exerciseId: Long) {
        viewModelScope.launch {
            try {
                val exercise = exercises.value.find { it.id == exerciseId }
                exercise?.let {
                    val updated = it.copy(isFavorite = !it.isFavorite)
                    exerciseDao.updateExercise(updated)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    // Training Record operations
    // Supports set-by-set distance/weight/assistance. Caller must ensure lists are aligned with values index.
    fun addTrainingRecords(
        exerciseId: Long,
        values: List<Int>,
        date: String,
        time: String,
        comment: String,
        distancesCm: List<Int?> = emptyList(),   // Set-by-set distance (cm)
        weightsG: List<Int?> = emptyList(),      // Set-by-set additional weight (g)
        assistancesG: List<Int?> = emptyList(),  // Set-by-set assistance amount (g)
        emitMessage: Boolean = true              // If false, suppress snackbar notification (to show total once in program mode)
    ) {
        viewModelScope.launch {
            try {
                val records = values.mapIndexed { index, value ->
                    TrainingRecord(
                        exerciseId = exerciseId,
                        valueRight = value,
                        valueLeft = null,
                        setNumber = index + 1,
                        date = date,
                        time = time,
                        comment = comment,
                        distanceCm = distancesCm.getOrNull(index),
                        weightG = weightsG.getOrNull(index),
                        assistanceG = assistancesG.getOrNull(index)
                    )
                }
                recordDao.insertRecords(records)
                if (emitMessage) {
                    _snackbarMessage.value = UiMessage.SetsRecorded(values.size)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    // For Unilateral exercises (supports set-by-set distance/weight/assistance)
    fun addTrainingRecordsUnilateral(
        exerciseId: Long,
        valuesRight: List<Int>,
        valuesLeft: List<Int?>,
        date: String,
        time: String,
        comment: String,
        distancesCm: List<Int?> = emptyList(),   // Set-by-set distance (cm)
        weightsG: List<Int?> = emptyList(),      // Set-by-set additional weight (g)
        assistancesG: List<Int?> = emptyList(),  // Set-by-set assistance amount (g)
        emitMessage: Boolean = true              // If false, suppress snackbar notification (to show total once in program mode)
    ) {
        viewModelScope.launch {
            try {
                // Create record based on right-side value
                val records = valuesRight.mapIndexed { index, valueRight ->
                    TrainingRecord(
                        exerciseId = exerciseId,
                        valueRight = valueRight,
                        valueLeft = valuesLeft.getOrNull(index),  // Left-side value (null if none)
                        setNumber = index + 1,
                        date = date,
                        time = time,
                        comment = comment,
                        distanceCm = distancesCm.getOrNull(index),
                        weightG = weightsG.getOrNull(index),
                        assistanceG = assistancesG.getOrNull(index)
                    )
                }
                recordDao.insertRecords(records)
                if (emitMessage) {
                    _snackbarMessage.value = UiMessage.SetsRecorded(valuesRight.size)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    /** Notification for program mode save completion (shows total sets once) */
    fun notifyProgramSetsRecorded(totalSets: Int) {
        _snackbarMessage.value = UiMessage.ProgramSetsRecorded(totalSets)
    }

    fun updateRecord(record: TrainingRecord) {
        viewModelScope.launch {
            try {
                recordDao.updateRecord(record)
                _snackbarMessage.value = UiMessage.RecordUpdated
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun deleteSession(exerciseId: Long, date: String, time: String) {
        viewModelScope.launch {
            try {
                recordDao.deleteSession(exerciseId, date, time)
                _snackbarMessage.value = UiMessage.RecordDeleted
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    // ========================================
    // Group operations
    // ========================================

    fun createGroup(name: String) {
        viewModelScope.launch {
            flushGroupOrder()
            try {
                val existingGroups = groupDao.getAllGroupsSync()
                val group = ExerciseGroup(name = name, displayOrder = existingGroups.size)
                groupDao.insertGroup(group)
                _snackbarMessage.value = UiMessage.GroupCreated
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.GroupAlreadyExists
            }
        }
    }

    fun renameGroup(oldName: String, newName: String) {
        viewModelScope.launch {
            flushGroupOrder()
            try {
                // 1. Update groups table
                val group = groupDao.getGroupByName(oldName)
                if (group != null) {
                    groupDao.updateGroup(group.copy(name = newName))
                }

                // 2. Also update exercise group fields
                val affectedExercises = exercises.value.filter { it.group == oldName }
                affectedExercises.forEach { exercise ->
                    exerciseDao.updateExercise(exercise.copy(group = newName))
                }

                _snackbarMessage.value = UiMessage.GroupRenamed
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun deleteGroup(groupName: String) {
        viewModelScope.launch {
            flushGroupOrder()
            try {
                // Linked deletion of ToDo (get IDs before deleting group)
                val group = groupDao.getGroupByName(groupName)
                if (group != null) {
                    todoTaskDao.deleteByReference(TodoTask.TYPE_GROUP, group.id)
                }

                // 1. Delete from groups table
                groupDao.deleteGroupByName(groupName)

                // 2. Set exercise group to null
                val affectedExercises = exercises.value.filter { it.group == groupName }
                affectedExercises.forEach { exercise ->
                    exerciseDao.updateExercise(exercise.copy(group = null, sortOrder = 0))
                }

                _snackbarMessage.value = UiMessage.GroupDeleted
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }
    // ========================================
    // Data structure for hierarchical display
    // ========================================

    data class GroupWithExercises(
        val groupName: String?,  // null means "no group"
        val exercises: List<Exercise>,
        val isExpanded: Boolean = true  // Expanded state
    )

    // Expanded statemanagement
    private val _expandedGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedGroups: StateFlow<Set<String>> = _expandedGroups.asStateFlow()

    fun toggleGroupExpansion(groupName: String) {
        _expandedGroups.value = if (groupName in _expandedGroups.value) {
            _expandedGroups.value - groupName
        } else {
            _expandedGroups.value + groupName
        }
    }

    // Local state for group reordering (saved to DB on screen exit)
    private val _localGroupOrder = MutableStateFlow<List<ExerciseGroup>?>(null)

    // Hierarchical data preparation
    val hierarchicalExercises: StateFlow<List<GroupWithExercises>> =
        combine(groups, exercises, expandedGroups, _localGroupOrder) { dbGroups, exercises, expanded, localOrder ->
            prepareHierarchicalData(localOrder ?: dbGroups, exercises, expanded)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private fun prepareHierarchicalData(
        groups: List<ExerciseGroup>,
        exercises: List<Exercise>,
        expandedGroups: Set<String>
    ): List<GroupWithExercises> {
        // 0. Favorite group (added to top, shown even if empty)
        // Use fixed key (translated in UI)
        val favoriteGroupKey = FAVORITE_GROUP_KEY
        val favoriteExercises = exercises.filter { it.isFavorite }.sortedBy { it.displayOrder }
        val favoriteGroup = listOf(
            GroupWithExercises(
                groupName = favoriteGroupKey,
                exercises = favoriteExercises,
                isExpanded = favoriteGroupKey in expandedGroups
            )
        )

        // 1. Show groups based on groups table (shown even if empty)
        // groups are already retrieved in displayOrder sequence
        val groupedExercises = groups.map { group ->
            val groupExercises = exercises.filter { it.group == group.name }
            GroupWithExercises(
                groupName = group.name,
                exercises = groupExercises.sortedBy { it.displayOrder },
                isExpanded = group.name in expandedGroups  // All-closed state also possible
            )
        }

        // 2. Ungrouped exercises
        val ungroupedExercises = exercises.filter { it.group == null }
        val ungroupedGroup = if (ungroupedExercises.isNotEmpty()) {
            listOf(
                GroupWithExercises(
                    groupName = null,
                    exercises = ungroupedExercises.sortedBy { it.displayOrder },
                    isExpanded = "ungrouped" in expandedGroups  // All-closed state also possible
                )
            )
        } else {
            emptyList()
        }

        // Place favorite group at the top
        return favoriteGroup + groupedExercises + ungroupedGroup
    }

    // ========================================
    // Reordering function
    // ========================================

    /**
     * Change exercise order
     * @param groupName Group name (null = no group, FAVORITE_GROUP_KEY = favorites)
     * @param fromIndex Source index
     * @param toIndex Destination index
     */
    fun reorderExercises(groupName: String?, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            // Reordering not allowed in favorite group (affects original group order)
            if (groupName == FAVORITE_GROUP_KEY) {
                return@launch
            }

            // Get exercises for target group
            val targetExercises = when (groupName) {
                null -> {
                    // No group
                    exerciseDao.getUngroupedExercises()
                }
                else -> {
                    // Normal group
                    exerciseDao.getExercisesByGroup(groupName)
                }
            }

            if (fromIndex < 0 || toIndex < 0 ||
                fromIndex >= targetExercises.size ||
                toIndex >= targetExercises.size) {
                return@launch
            }

            // Reorder
            val reordered = targetExercises.toMutableList()
            val item = reordered.removeAt(fromIndex)
            reordered.add(toIndex, item)

            // Update displayOrder
            reordered.forEachIndexed { index, exercise ->
                exerciseDao.updateExercise(exercise.copy(displayOrder = index))
            }
        }
    }

    /**
     * Change group order (update local state only)
     * Saving to DB is done by saveGroupOrder()
     */
    fun reorderGroups(fromIndex: Int, toIndex: Int) {
        val current = (_localGroupOrder.value ?: groups.value).toList()

        if (fromIndex < 0 || toIndex < 0 ||
            fromIndex >= current.size ||
            toIndex >= current.size) {
            return
        }

        val reordered = current.toMutableList()
        val item = reordered.removeAt(fromIndex)
        reordered.add(toIndex, item)
        _localGroupOrder.value = reordered
    }

    /**
     * Save pending group order to DB
     */
    fun saveGroupOrder() {
        val order = _localGroupOrder.value ?: return
        _localGroupOrder.value = null
        viewModelScope.launch {
            order.forEachIndexed { index, group ->
                groupDao.updateGroup(group.copy(displayOrder = index))
            }
        }
    }

    /**
     * Save pending group order to DB(suspend version, for internal use)
     */
    private suspend fun flushGroupOrder() {
        val order = _localGroupOrder.value ?: return
        _localGroupOrder.value = null
        order.forEachIndexed { index, group ->
            groupDao.updateGroup(group.copy(displayOrder = index))
        }
    }

    // ========================================
    // Export/Import features
    // ========================================

    /**
     * Export data in JSON format
     */
    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        try {
            val currentGroups = groups.value
            val currentExercises = exercises.value
            val currentRecords = records.value

            val exportGroups = currentGroups.map { group ->
                ExportGroup(
                    id = group.id,
                    name = group.name,
                    displayOrder = group.displayOrder
                )
            }

            val exportExercises = currentExercises.map { exercise ->
                ExportExercise(
                    id = exercise.id,
                    name = exercise.name,
                    type = exercise.type,
                    group = exercise.group,
                    sortOrder = exercise.sortOrder,
                    displayOrder = exercise.displayOrder,
                    laterality = exercise.laterality,
                    targetSets = exercise.targetSets,
                    targetValue = exercise.targetValue,
                    isFavorite = exercise.isFavorite,
                    restInterval = exercise.restInterval,
                    repDuration = exercise.repDuration,
                    distanceTrackingEnabled = exercise.distanceTrackingEnabled,
                    weightTrackingEnabled = exercise.weightTrackingEnabled,
                    assistanceTrackingEnabled = exercise.assistanceTrackingEnabled,
                    description = exercise.description
                )
            }

            val exportRecords = currentRecords.map { record ->
                ExportRecord(
                    id = record.id,
                    exerciseId = record.exerciseId,
                    valueRight = record.valueRight,
                    valueLeft = record.valueLeft,
                    setNumber = record.setNumber,
                    date = record.date,
                    time = record.time,
                    comment = record.comment,
                    distanceCm = record.distanceCm,
                    weightG = record.weightG,
                    assistanceG = record.assistanceG
                )
            }

            // Export programs (added in v4)
            val currentPrograms = programs.value
            val exportPrograms = currentPrograms.map { program ->
                ExportProgram(
                    id = program.id,
                    name = program.name
                )
            }

            // Export program exercises (added in v4, loopId added in v5)
            val allProgramExercises = mutableListOf<ExportProgramExercise>()
            currentPrograms.forEach { program ->
                val programExercises = programExerciseDao.getExercisesForProgramSync(program.id)
                programExercises.forEach { pe ->
                    allProgramExercises.add(
                        ExportProgramExercise(
                            id = pe.id,
                            programId = pe.programId,
                            exerciseId = pe.exerciseId,
                            sortOrder = pe.sortOrder,
                            sets = pe.sets,
                            targetValue = pe.targetValue,
                            intervalSeconds = pe.intervalSeconds,
                            loopId = pe.loopId  // v5added
                        )
                    )
                }
            }

            // Export program loops (added in v5)
            val allProgramLoops = mutableListOf<ExportProgramLoop>()
            currentPrograms.forEach { program ->
                val loops = programLoopDao.getLoopsForProgramSync(program.id)
                loops.forEach { loop ->
                    allProgramLoops.add(
                        ExportProgramLoop(
                            id = loop.id,
                            programId = loop.programId,
                            sortOrder = loop.sortOrder,
                            rounds = loop.rounds,
                            restBetweenRounds = loop.restBetweenRounds
                        )
                    )
                }
            }

            // Export interval programs (added in v7)
            val currentIntervalPrograms = intervalPrograms.value
            val exportIntervalPrograms = currentIntervalPrograms.map { program ->
                ExportIntervalProgram(
                    id = program.id,
                    name = program.name,
                    workSeconds = program.workSeconds,
                    restSeconds = program.restSeconds,
                    rounds = program.rounds,
                    roundRestSeconds = program.roundRestSeconds
                )
            }

            // Export interval program exercises (added in v7)
            val allIntervalProgramExercises = mutableListOf<ExportIntervalProgramExercise>()
            currentIntervalPrograms.forEach { program ->
                val exercises = intervalProgramExerciseDao.getExercisesForProgramSync(program.id)
                exercises.forEach { pe ->
                    allIntervalProgramExercises.add(
                        ExportIntervalProgramExercise(
                            id = pe.id,
                            programId = pe.programId,
                            exerciseId = pe.exerciseId,
                            sortOrder = pe.sortOrder
                        )
                    )
                }
            }

            // Export interval records (added in v7)
            val currentIntervalRecords = intervalRecords.value
            val exportIntervalRecords = currentIntervalRecords.map { record ->
                ExportIntervalRecord(
                    id = record.id,
                    programName = record.programName,
                    date = record.date,
                    time = record.time,
                    workSeconds = record.workSeconds,
                    restSeconds = record.restSeconds,
                    rounds = record.rounds,
                    roundRestSeconds = record.roundRestSeconds,
                    completedRounds = record.completedRounds,
                    completedExercisesInLastRound = record.completedExercisesInLastRound,
                    exercisesJson = record.exercisesJson,
                    comment = record.comment
                )
            }

            // Export ToDo tasks (added in v8)
            val currentTodoTasks = todoTasks.value
            val exportTodoTasks = currentTodoTasks.map { task ->
                ExportTodoTask(
                    id = task.id,
                    type = task.type,
                    referenceId = task.referenceId,
                    sortOrder = task.sortOrder,
                    repeatDays = task.repeatDays,
                    lastCompletedDate = task.lastCompletedDate
                )
            }

            val backupData = BackupData(
                version = 8,  // v8: Added ToDo tasks
                exportDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                app = "CalisthenicsMemory",
                groups = exportGroups,
                exercises = exportExercises,
                records = exportRecords,
                programs = exportPrograms,
                programExercises = allProgramExercises,
                programLoops = allProgramLoops,
                intervalPrograms = exportIntervalPrograms,
                intervalProgramExercises = allIntervalProgramExercises,
                intervalRecords = exportIntervalRecords,
                todoTasks = exportTodoTasks
            )

            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ExportComplete(exportGroups.size, exportExercises.size, exportRecords.size)
            }

            val json = Json { ignoreUnknownKeys = true }
            json.encodeToString(backupData)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ExportError(e.message ?: "")
            }
            throw e
        }
    }

    /**
     * Import JSON data (full overwrite)
     */
    suspend fun importData(jsonString: String) {
        withContext(Dispatchers.IO) {
            try {
                // File type check: error if community share JSON is passed
                if (detectJsonFileType(jsonString) == "share") {
                    withContext(Dispatchers.Main) {
                        _snackbarMessage.value = UiMessage.WrongFileType(
                            detected = "share",
                            expected = "backup"
                        )
                    }
                    return@withContext
                }

                val json = Json { ignoreUnknownKeys = true }
                val backupData = json.decodeFromString<BackupData>(jsonString)

                // 1. Delete all existing data
                database.clearAllTables()

                // 2. Import groups
                backupData.groups.forEach { exportGroup ->
                    val group = ExerciseGroup(
                        id = exportGroup.id,
                        name = exportGroup.name,
                        displayOrder = exportGroup.displayOrder
                    )
                    groupDao.insertGroup(group)
                }

                // 3. Import exercises
                backupData.exercises.forEach { exportExercise ->
                    val exercise = Exercise(
                        id = exportExercise.id,
                        name = exportExercise.name,
                        type = exportExercise.type,
                        group = exportExercise.group,
                        sortOrder = exportExercise.sortOrder,
                        displayOrder = exportExercise.displayOrder,
                        laterality = exportExercise.laterality,
                        targetSets = exportExercise.targetSets,
                        targetValue = exportExercise.targetValue,
                        isFavorite = exportExercise.isFavorite,
                        restInterval = exportExercise.restInterval,
                        repDuration = exportExercise.repDuration,
                        distanceTrackingEnabled = exportExercise.distanceTrackingEnabled,
                        weightTrackingEnabled = exportExercise.weightTrackingEnabled,
                        assistanceTrackingEnabled = exportExercise.assistanceTrackingEnabled,
                        description = exportExercise.description
                    )
                    exerciseDao.insertExercise(exercise)
                }

                // 4. Import records
                backupData.records.forEach { exportRecord ->
                    val record = TrainingRecord(
                        id = exportRecord.id,
                        exerciseId = exportRecord.exerciseId,
                        valueRight = exportRecord.valueRight,
                        valueLeft = exportRecord.valueLeft,
                        setNumber = exportRecord.setNumber,
                        date = exportRecord.date,
                        time = exportRecord.time,
                        comment = exportRecord.comment,
                        distanceCm = exportRecord.distanceCm,
                        weightG = exportRecord.weightG,
                        assistanceG = exportRecord.assistanceG
                    )
                    recordDao.insertRecord(record)
                }

                // 5. Import programs (added in v4)
                backupData.programs.forEach { exportProgram ->
                    val program = Program(
                        id = exportProgram.id,
                        name = exportProgram.name
                    )
                    programDao.insert(program)
                }

                // 6. Import program loops (added in v5)
                // Import loops first, then ProgramExercises
                // (Because ProgramExercise references loopId)
                backupData.programLoops.forEach { exportLoop ->
                    val loop = ProgramLoop(
                        id = exportLoop.id,
                        programId = exportLoop.programId,
                        sortOrder = exportLoop.sortOrder,
                        rounds = exportLoop.rounds,
                        restBetweenRounds = exportLoop.restBetweenRounds
                    )
                    programLoopDao.insert(loop)
                }

                // 7. Import program exercises (added in v4, loopId added in v5)
                backupData.programExercises.forEach { exportPe ->
                    val programExercise = ProgramExercise(
                        id = exportPe.id,
                        programId = exportPe.programId,
                        exerciseId = exportPe.exerciseId,
                        sortOrder = exportPe.sortOrder,
                        sets = exportPe.sets,
                        targetValue = exportPe.targetValue,
                        intervalSeconds = exportPe.intervalSeconds,
                        loopId = exportPe.loopId  // v5added(null for backups before v4)
                    )
                    programExerciseDao.insert(programExercise)
                }

                // 8. Import interval programs (added in v7)
                backupData.intervalPrograms.forEach { exportIp ->
                    val intervalProgram = IntervalProgram(
                        id = exportIp.id,
                        name = exportIp.name,
                        workSeconds = exportIp.workSeconds,
                        restSeconds = exportIp.restSeconds,
                        rounds = exportIp.rounds,
                        roundRestSeconds = exportIp.roundRestSeconds
                    )
                    intervalProgramDao.insert(intervalProgram)
                }

                // 9. Import interval program exercises (added in v7)
                backupData.intervalProgramExercises.forEach { exportIpe ->
                    val intervalProgramExercise = IntervalProgramExercise(
                        id = exportIpe.id,
                        programId = exportIpe.programId,
                        exerciseId = exportIpe.exerciseId,
                        sortOrder = exportIpe.sortOrder
                    )
                    intervalProgramExerciseDao.insert(intervalProgramExercise)
                }

                // 10. Import interval records (added in v7)
                backupData.intervalRecords.forEach { exportIr ->
                    val intervalRecord = IntervalRecord(
                        id = exportIr.id,
                        programName = exportIr.programName,
                        date = exportIr.date,
                        time = exportIr.time,
                        workSeconds = exportIr.workSeconds,
                        restSeconds = exportIr.restSeconds,
                        rounds = exportIr.rounds,
                        roundRestSeconds = exportIr.roundRestSeconds,
                        completedRounds = exportIr.completedRounds,
                        completedExercisesInLastRound = exportIr.completedExercisesInLastRound,
                        exercisesJson = exportIr.exercisesJson,
                        comment = exportIr.comment
                    )
                    intervalRecordDao.insert(intervalRecord)
                }

                // 11. Import ToDo tasks (added in v8)
                backupData.todoTasks.forEach { exportTask ->
                    val task = TodoTask(
                        id = exportTask.id,
                        type = exportTask.type,
                        referenceId = exportTask.referenceId,
                        sortOrder = exportTask.sortOrder,
                        repeatDays = exportTask.repeatDays,
                        lastCompletedDate = exportTask.lastCompletedDate
                    )
                    todoTaskDao.insert(task)
                }

                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = UiMessage.ImportComplete(backupData.groups.size, backupData.exercises.size, backupData.records.size)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = UiMessage.ImportError(e.message ?: "")
                }
            }
        }
    }

    // ========================================
    // CSV Export/Import features
    // ========================================

    /**
     * Export groups CSV
     */
    suspend fun exportGroups(): String = withContext(Dispatchers.IO) {
        try {
            val currentGroups = groups.value

            val csvBuilder = StringBuilder()
            csvBuilder.appendLine("name")

            currentGroups.forEach { group ->
                csvBuilder.appendLine(group.name)
            }

            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.CsvExportSuccess("Groups", currentGroups.size)
            }

            csvBuilder.toString()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ExportError(e.message ?: "")
            }
            throw e
        }
    }

    /**
     * Export exercises CSV
     */
    suspend fun exportExercises(): String = withContext(Dispatchers.IO) {
        try {
            val currentExercises = exercises.value

            val csvBuilder = StringBuilder()
            csvBuilder.appendLine("name,type,group,sortOrder,laterality,targetSets,targetValue,isFavorite,displayOrder,restInterval,repDuration,distanceTrackingEnabled,weightTrackingEnabled,assistanceTrackingEnabled,description")

            currentExercises.forEach { exercise ->
                csvBuilder.appendLine(
                    "${exercise.name},${exercise.type},${exercise.group ?: ""}," +
                    "${exercise.sortOrder},${exercise.laterality}," +
                    "${exercise.targetSets ?: ""},${exercise.targetValue ?: ""},${exercise.isFavorite}," +
                    "${exercise.displayOrder},${exercise.restInterval ?: ""},${exercise.repDuration ?: ""}," +
                    "${exercise.distanceTrackingEnabled},${exercise.weightTrackingEnabled},${exercise.assistanceTrackingEnabled}," +
                    (exercise.description ?: "")
                )
            }

            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.CsvExportSuccess("Exercises", currentExercises.size)
            }

            csvBuilder.toString()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ExportError(e.message ?: "")
            }
            throw e
        }
    }

    /**
     * Export records template CSV
     * Exercise list + comment examples
     */
    suspend fun exportRecordTemplate(): String = withContext(Dispatchers.IO) {
        try {
            val currentExercises = exercises.value

            val csvBuilder = StringBuilder()

            // Header (v12: 11 columns)
            csvBuilder.appendLine("exerciseName,exerciseType,date,time,setNumber,valueRight,valueLeft,comment,distanceCm,weightG,assistanceG")

            // Example input (comments in English only)
            csvBuilder.appendLine("# Example: Multiple sets with same date/time (one session)")
            csvBuilder.appendLine("# Wall Push-up,Dynamic,2025-11-09,10:00,1,20,,Morning session,,,")
            csvBuilder.appendLine("# Wall Push-up,Dynamic,2025-11-09,10:00,2,19,,Morning session,,,")
            csvBuilder.appendLine("# Wall Push-up,Dynamic,2025-11-09,10:00,3,15,,Morning session,,,")
            csvBuilder.appendLine("# Unilateral exercise example (with valueLeft)")
            csvBuilder.appendLine("# One-leg Squat,Dynamic,2025-11-09,10:30,1,8,7,Right leg stronger,,,")
            csvBuilder.appendLine("# With distance (cm) and weight (g)")
            csvBuilder.appendLine("# Running,Dynamic,2025-11-09,08:00,1,1,,5km run,500000,,")
            csvBuilder.appendLine("# Weighted Push-up,Dynamic,2025-11-09,10:00,1,10,,With vest,,10000,")
            csvBuilder.appendLine("# With assistance (g) - band assisted")
            csvBuilder.appendLine("# Pull-up,Dynamic,2025-11-09,11:00,1,5,,With band,,,22000")
            csvBuilder.appendLine("#")

            // Exercise list (blank template)
            currentExercises.forEach { exercise ->
                csvBuilder.appendLine("${exercise.name},${exercise.type},,,,,,,,,")
            }

            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.CsvTemplateExported(currentExercises.size)
            }

            csvBuilder.toString()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ExportError(e.message ?: "")
            }
            throw e
        }
    }

    /**
     * Export actual records in CSV format (v12: 11 columns)
     */
    suspend fun exportRecords(): String = withContext(Dispatchers.IO) {
        try {
            val currentRecords = records.value
            val currentExercises = exercises.value

            // Create map from exercise ID to info
            val exerciseMap = currentExercises.associateBy { it.id }

            val csvBuilder = StringBuilder()

            // Header (v12: 11 columns)
            csvBuilder.appendLine("exerciseName,exerciseType,date,time,setNumber,valueRight,valueLeft,comment,distanceCm,weightG,assistanceG")

            // Output records (sorted by date, time, set number)
            currentRecords
                .sortedWith(compareBy({ it.date }, { it.time }, { it.setNumber }))
                .forEach { record ->
                    val exercise = exerciseMap[record.exerciseId]
                    if (exercise != null) {
                        val valueLeft = record.valueLeft?.toString() ?: ""
                        val distanceCm = record.distanceCm?.toString() ?: ""
                        val weightG = record.weightG?.toString() ?: ""
                        val assistanceG = record.assistanceG?.toString() ?: ""
                        csvBuilder.appendLine(
                            "${exercise.name},${exercise.type},${record.date},${record.time}," +
                            "${record.setNumber},${record.valueRight},$valueLeft,${record.comment}," +
                            "$distanceCm,$weightG,$assistanceG"
                        )
                    }
                }

            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.CsvExportSuccess("Records", currentRecords.size)
            }

            csvBuilder.toString()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ExportError(e.message ?: "")
            }
            throw e
        }
    }

    /**
     * Import groups CSV (merge mode)
     */
    suspend fun importGroups(csvString: String): CsvImportReport = withContext(Dispatchers.IO) {
        var successCount = 0
        var skippedCount = 0
        var errorCount = 0
        val skippedItems = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            val lines = csvString.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }

            if (lines.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = UiMessage.CsvEmpty
                }
                return@withContext CsvImportReport(CsvType.GROUPS, 0, 0, 0, emptyList(), emptyList())
            }

            // Skip header row
            val dataLines = lines.drop(1)

            dataLines.forEachIndexed { index, line ->
                try {
                    val name = line.trim()

                    if (name.isEmpty()) {
                        errors.add("Line ${index + 2}: Group name is empty")
                        errorCount++
                        return@forEachIndexed
                    }

                    // Duplicate check
                    val existing = groupDao.getGroupByName(name)
                    if (existing != null) {
                        skippedItems.add("\"$name\" (already exists)")
                        skippedCount++
                        return@forEachIndexed
                    }

                    val group = ExerciseGroup(name = name)
                    groupDao.insertGroup(group)
                    successCount++

                } catch (e: Exception) {
                    errors.add("Line ${index + 2}: ${e.message}")
                    errorCount++
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ImportError(e.message ?: "")
            }
        }

        return@withContext CsvImportReport(CsvType.GROUPS, successCount, skippedCount, errorCount, skippedItems, errors)
    }

    /**
     * Import exercises CSV (merge mode)
     * - Supports both 8 columns (old format) and 11 columns (new format)
     * - Auto-create group if it does not exist
     */
    suspend fun importExercises(csvString: String): CsvImportReport = withContext(Dispatchers.IO) {
        var successCount = 0
        var skippedCount = 0
        var errorCount = 0
        var groupsCreatedCount = 0
        val skippedItems = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            val lines = csvString.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }

            if (lines.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = UiMessage.CsvEmpty
                }
                return@withContext CsvImportReport(CsvType.EXERCISES, 0, 0, 0, emptyList(), emptyList())
            }

            // Skip header row
            val dataLines = lines.drop(1)

            dataLines.forEachIndexed { index, line ->
                try {
                    val columns = line.split(",")
                    // Allow 8 columns (old format) or 11 columns (new format)
                    if (columns.size < 8) {
                        errors.add("Line ${index + 2}: Invalid format (expected at least 8 columns, got ${columns.size})")
                        errorCount++
                        return@forEachIndexed
                    }

                    val name = columns[0].trim()
                    val type = columns[1].trim()
                    val group = columns[2].trim().ifEmpty { null }
                    val sortOrderStr = columns[3].trim()
                    val laterality = columns[4].trim()
                    val targetSetsStr = columns[5].trim()
                    val targetValueStr = columns[6].trim()
                    val isFavoriteStr = columns[7].trim()

                    // New fields (9th column onwards, optional)
                    val displayOrderStr = columns.getOrNull(8)?.trim() ?: ""
                    val restIntervalStr = columns.getOrNull(9)?.trim() ?: ""
                    val repDurationStr = columns.getOrNull(10)?.trim() ?: ""
                    val distanceTrackingEnabledStr = columns.getOrNull(11)?.trim() ?: ""
                    val weightTrackingEnabledStr = columns.getOrNull(12)?.trim() ?: ""
                    val assistanceTrackingEnabledStr = columns.getOrNull(13)?.trim() ?: ""
                    val descriptionStr = columns.getOrNull(14)?.trim() ?: ""

                    // Required field check
                    if (name.isEmpty() || type.isEmpty() || laterality.isEmpty()) {
                        errors.add("Line ${index + 2}: Missing required fields (name, type, or laterality)")
                        errorCount++
                        return@forEachIndexed
                    }

                    // Validation
                    if (type !in listOf("Dynamic", "Isometric")) {
                        errors.add("Line ${index + 2}: Invalid type \"$type\" (must be Dynamic or Isometric)")
                        errorCount++
                        return@forEachIndexed
                    }

                    if (laterality !in listOf("Bilateral", "Unilateral")) {
                        errors.add("Line ${index + 2}: Invalid laterality \"$laterality\" (must be Bilateral or Unilateral)")
                        errorCount++
                        return@forEachIndexed
                    }

                    // Check if group exists (auto-create if not)
                    if (group != null) {
                        val groupExists = groupDao.getGroupByName(group) != null
                        if (!groupExists) {
                            // Auto-create group
                            val newGroup = ExerciseGroup(name = group)
                            groupDao.insertGroup(newGroup)
                            groupsCreatedCount++
                        }
                    }

                    // Number conversion
                    val sortOrder = sortOrderStr.toIntOrNull() ?: 0
                    val targetSets = targetSetsStr.toIntOrNull()
                    val targetValue = targetValueStr.toIntOrNull()
                    val isFavorite = isFavoriteStr.toBooleanStrictOrNull() ?: false
                    val displayOrder = displayOrderStr.toIntOrNull() ?: 0
                    val restInterval = restIntervalStr.toIntOrNull()
                    val repDuration = repDurationStr.toIntOrNull()
                    val distanceTrackingEnabled = distanceTrackingEnabledStr.toBooleanStrictOrNull() ?: false
                    val weightTrackingEnabled = weightTrackingEnabledStr.toBooleanStrictOrNull() ?: false
                    val assistanceTrackingEnabled = assistanceTrackingEnabledStr.toBooleanStrictOrNull() ?: false
                    val description = descriptionStr.ifEmpty { null }

                    // Duplicate check
                    val existing = exercises.value.find {
                        it.name == name && it.type == type
                    }
                    if (existing != null) {
                        // Skip if laterality mismatch
                        if (existing.laterality != laterality) {
                            skippedItems.add("\"$name, $type\" (laterality mismatch: existing=${existing.laterality}, CSV=$laterality)")
                            skippedCount++
                            return@forEachIndexed
                        }
                        // Skip if exact match
                        skippedItems.add("\"$name, $type\" (already exists)")
                        skippedCount++
                        return@forEachIndexed
                    }

                    val exercise = Exercise(
                        name = name,
                        type = type,
                        group = group,
                        sortOrder = sortOrder,
                        displayOrder = displayOrder,
                        laterality = laterality,
                        targetSets = targetSets,
                        targetValue = targetValue,
                        isFavorite = isFavorite,
                        restInterval = restInterval,
                        repDuration = repDuration,
                        distanceTrackingEnabled = distanceTrackingEnabled,
                        weightTrackingEnabled = weightTrackingEnabled,
                        assistanceTrackingEnabled = assistanceTrackingEnabled,
                        description = description
                    )
                    exerciseDao.insertExercise(exercise)
                    successCount++

                } catch (e: Exception) {
                    errors.add("Line ${index + 2}: ${e.message}")
                    errorCount++
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ImportError(e.message ?: "")
            }
        }

        return@withContext CsvImportReport(
            type = CsvType.EXERCISES,
            successCount = successCount,
            skippedCount = skippedCount,
            errorCount = errorCount,
            skippedItems = skippedItems,
            errors = errors,
            groupsCreatedCount = groupsCreatedCount
        )
    }

    /**
     * Import records from CSV (merge mode)
     */
    suspend fun importRecordsFromCsv(csvString: String): CsvImportReport = withContext(Dispatchers.IO) {
        var successCount = 0
        var skippedCount = 0
        var errorCount = 0
        val skippedItems = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            val lines = csvString.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }

            if (lines.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = UiMessage.CsvEmpty
                }
                return@withContext CsvImportReport(CsvType.RECORDS, 0, 0, 1, emptyList(), listOf("CSV file is empty"))
            }

            // Skip header row
            val dataLines = lines.drop(1)

            dataLines.forEachIndexed { index, line ->
                try {
                    val columns = line.split(",")
                    if (columns.size < 8) {
                        errors.add("Line ${index + 2}: Invalid format (not enough columns)")
                        errorCount++
                        return@forEachIndexed
                    }

                    val exerciseName = columns[0].trim()
                    val exerciseType = columns[1].trim()
                    val date = columns[2].trim()
                    val time = columns[3].trim()
                    val setNumberStr = columns[4].trim()
                    val valueRightStr = columns[5].trim()
                    val valueLeftStr = columns[6].trim()
                    val comment = columns.getOrNull(7)?.trim() ?: ""
                    // v11+ new fields (optional)
                    val distanceCmStr = columns.getOrNull(8)?.trim() ?: ""
                    val weightGStr = columns.getOrNull(9)?.trim() ?: ""
                    // v12 new fields (optional)
                    val assistanceGStr = columns.getOrNull(10)?.trim() ?: ""

                    // Required field check
                    if (exerciseName.isEmpty() || exerciseType.isEmpty() ||
                        date.isEmpty() || time.isEmpty() ||
                        setNumberStr.isEmpty() || valueRightStr.isEmpty()) {
                        errors.add("Line ${index + 2}: Missing required fields")
                        errorCount++
                        return@forEachIndexed
                    }

                    // Search for exercise
                    val exercise = exercises.value.find {
                        it.name == exerciseName && it.type == exerciseType
                    }

                    if (exercise == null) {
                        errors.add("Line ${index + 2}: Exercise not found: $exerciseName ($exerciseType)")
                        errorCount++
                        return@forEachIndexed
                    }

                    // Number conversion
                    val setNumber = setNumberStr.toIntOrNull()
                    val valueRight = valueRightStr.toIntOrNull()
                    val valueLeft = if (valueLeftStr.isEmpty()) null else valueLeftStr.toIntOrNull()
                    val distanceCm = if (distanceCmStr.isEmpty()) null else distanceCmStr.toIntOrNull()
                    val weightG = if (weightGStr.isEmpty()) null else weightGStr.toIntOrNull()
                    val assistanceG = if (assistanceGStr.isEmpty()) null else assistanceGStr.toIntOrNull()

                    if (setNumber == null || valueRight == null) {
                        errors.add("Line ${index + 2}: Invalid number format")
                        errorCount++
                        return@forEachIndexed
                    }

                    // Duplicate check
                    val isDuplicate = records.value.any { existingRecord ->
                        existingRecord.exerciseId == exercise.id &&
                        existingRecord.date == date &&
                        existingRecord.time == time &&
                        existingRecord.setNumber == setNumber
                    }

                    if (isDuplicate) {
                        skippedItems.add("\"$exerciseName ($exerciseType)\" - $date $time Set $setNumber (already exists)")
                        skippedCount++
                        return@forEachIndexed
                    }

                    // Create record
                    val record = TrainingRecord(
                        exerciseId = exercise.id,
                        valueRight = valueRight,
                        valueLeft = valueLeft,
                        setNumber = setNumber,
                        date = date,
                        time = time,
                        comment = comment,
                        distanceCm = distanceCm,
                        weightG = weightG,
                        assistanceG = assistanceG
                    )

                    recordDao.insertRecord(record)
                    successCount++

                } catch (e: Exception) {
                    errors.add("Line ${index + 2}: ${e.message}")
                    errorCount++
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ImportError(e.message ?: "")
            }
        }

        return@withContext CsvImportReport(CsvType.RECORDS, successCount, skippedCount, errorCount, skippedItems, errors)
    }

    // ========================================
    // To Do Task operations
    // ========================================

    val todoTasks: StateFlow<List<TodoTask>> = todoTaskDao.getAllTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun addTodoTask(exerciseId: Long) {
        viewModelScope.launch {
            try {
                val sortOrder = todoTaskDao.getNextSortOrder()
                val task = TodoTask(
                    type = TodoTask.TYPE_EXERCISE,
                    referenceId = exerciseId,
                    sortOrder = sortOrder
                )
                todoTaskDao.insert(task)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun addTodoTasks(exerciseIds: List<Long>) {
        viewModelScope.launch {
            try {
                var sortOrder = todoTaskDao.getNextSortOrder()
                exerciseIds.forEach { exerciseId ->
                    val task = TodoTask(
                        type = TodoTask.TYPE_EXERCISE,
                        referenceId = exerciseId,
                        sortOrder = sortOrder++
                    )
                    todoTaskDao.insert(task)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun addTodoTaskProgram(programId: Long) {
        viewModelScope.launch {
            try {
                val sortOrder = todoTaskDao.getNextSortOrder()
                val task = TodoTask(
                    type = TodoTask.TYPE_PROGRAM,
                    referenceId = programId,
                    sortOrder = sortOrder
                )
                todoTaskDao.insert(task)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun addTodoTaskPrograms(programIds: List<Long>) {
        viewModelScope.launch {
            try {
                var sortOrder = todoTaskDao.getNextSortOrder()
                programIds.forEach { programId ->
                    val task = TodoTask(
                        type = TodoTask.TYPE_PROGRAM,
                        referenceId = programId,
                        sortOrder = sortOrder++
                    )
                    todoTaskDao.insert(task)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun addTodoTaskGroups(groupIds: List<Long>) {
        viewModelScope.launch {
            try {
                var sortOrder = todoTaskDao.getNextSortOrder()
                groupIds.forEach { groupId ->
                    val task = TodoTask(
                        type = TodoTask.TYPE_GROUP,
                        referenceId = groupId,
                        sortOrder = sortOrder++
                    )
                    todoTaskDao.insert(task)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun addTodoTaskInterval(intervalProgramId: Long) {
        viewModelScope.launch {
            try {
                val sortOrder = todoTaskDao.getNextSortOrder()
                val task = TodoTask(
                    type = TodoTask.TYPE_INTERVAL,
                    referenceId = intervalProgramId,
                    sortOrder = sortOrder
                )
                todoTaskDao.insert(task)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun addTodoTaskIntervals(intervalProgramIds: List<Long>) {
        viewModelScope.launch {
            try {
                var sortOrder = todoTaskDao.getNextSortOrder()
                intervalProgramIds.forEach { intervalProgramId ->
                    val task = TodoTask(
                        type = TodoTask.TYPE_INTERVAL,
                        referenceId = intervalProgramId,
                        sortOrder = sortOrder++
                    )
                    todoTaskDao.insert(task)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun deleteTodoTask(taskId: Long) {
        viewModelScope.launch {
            try {
                todoTaskDao.deleteById(taskId)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun deleteTodoTaskByReference(type: String, referenceId: Long) {
        viewModelScope.launch {
            try {
                todoTaskDao.deleteByReference(type, referenceId)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun completeTodoTaskByReference(type: String, referenceId: Long) {
        viewModelScope.launch {
            try {
                val task = todoTaskDao.getTaskByReference(type, referenceId)
                if (task != null && task.isRepeating()) {
                    val todayStr = java.time.LocalDate.now().toString()
                    todoTaskDao.updateLastCompletedDate(type, referenceId, todayStr)
                } else {
                    todoTaskDao.deleteByReference(type, referenceId)
                }

                // Check group ToDo completion when exercise is done
                if (type == TodoTask.TYPE_EXERCISE) {
                    checkGroupTodoCompletion(referenceId)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    private suspend fun checkGroupTodoCompletion(exerciseId: Long) {
        val exercise = exercises.value.find { it.id == exerciseId } ?: return
        val groupName = exercise.group ?: return
        val group = groupDao.getGroupByName(groupName) ?: return

        // Check if this group is registered in ToDo
        val groupTask = todoTaskDao.getTaskByReference(TodoTask.TYPE_GROUP, group.id) ?: return

        // Check if it is an active task for today
        val todayStr = java.time.LocalDate.now().toString()
        if (groupTask.isRepeating()) {
            val todayDayNumber = java.time.LocalDate.now().dayOfWeek.value
            if (todayDayNumber !in groupTask.getRepeatDayNumbers()) return
            if (groupTask.lastCompletedDate == todayStr) return
        }

        // Verify if all exercises in the group have records for today
        val groupExercises = exercises.value.filter { it.group == groupName }
        val allCompleted = groupExercises.all { ex ->
            recordDao.hasRecordOnDate(ex.id, todayStr)
        }

        if (allCompleted) {
            if (groupTask.isRepeating()) {
                todoTaskDao.updateLastCompletedDate(TodoTask.TYPE_GROUP, group.id, todayStr)
            } else {
                todoTaskDao.deleteByReference(TodoTask.TYPE_GROUP, group.id)
            }
        }
    }

    suspend fun hasRecordOnDate(exerciseId: Long, date: String): Boolean {
        return recordDao.hasRecordOnDate(exerciseId, date)
    }

    fun updateTodoRepeatDays(taskId: Long, repeatDays: String) {
        viewModelScope.launch {
            try {
                todoTaskDao.updateRepeatDays(taskId, repeatDays)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun reorderTodoTasks(taskIds: List<Long>) {
        viewModelScope.launch {
            try {
                todoTaskDao.reorderTasks(taskIds)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun reorderTodoTasks(fromIndex: Int, toIndex: Int) {
        val currentTasks = todoTasks.value.toMutableList()
        if (fromIndex < 0 || toIndex < 0 ||
            fromIndex >= currentTasks.size || toIndex >= currentTasks.size) {
            return
        }

        // Move the item in the list
        val item = currentTasks.removeAt(fromIndex)
        currentTasks.add(toIndex, item)

        // Update database with new order
        val reorderedIds = currentTasks.map { it.id }
        reorderTodoTasks(reorderedIds)
    }

    /**
     * Get last session records for specified exercise
     * For auto-fill feature
     */
    suspend fun getLatestSession(exerciseId: Long): List<TrainingRecord> {
        return recordDao.getLatestSessionByExercise(exerciseId)
    }

    // ========================================
    // Program operations
    // ========================================

    val programs: StateFlow<List<Program>> = programDao.getAllPrograms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun createProgram(name: String) {
        viewModelScope.launch {
            try {
                val program = Program(name = name)
                programDao.insert(program)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    suspend fun createProgramAndGetId(name: String): Long? {
        return try {
            val program = Program(name = name)
            programDao.insert(program)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
            null
        }
    }

    suspend fun updateProgram(program: Program) {
        try {
            programDao.update(program)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    fun deleteProgram(programId: Long) {
        viewModelScope.launch {
            try {
                programDao.deleteById(programId)
                todoTaskDao.deleteByReference(TodoTask.TYPE_PROGRAM, programId)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    fun duplicateProgram(programId: Long, copySuffix: String) {
        viewModelScope.launch {
            try {
                val sourceProgram = programDao.getProgramById(programId) ?: return@launch
                val sourceExercises = programExerciseDao.getExercisesForProgramSync(programId)
                val sourceLoops = programLoopDao.getLoopsForProgramSync(programId)

                // Create new program with copy suffix
                val newProgram = Program(name = "${sourceProgram.name} $copySuffix")
                val newProgramId = programDao.insert(newProgram)

                // Copy all loops and create ID mapping
                val loopIdMapping = mutableMapOf<Long, Long>()
                sourceLoops.forEach { loop ->
                    val newLoop = ProgramLoop(
                        programId = newProgramId,
                        sortOrder = loop.sortOrder,
                        rounds = loop.rounds,
                        restBetweenRounds = loop.restBetweenRounds
                    )
                    val newLoopId = programLoopDao.insert(newLoop)
                    loopIdMapping[loop.id] = newLoopId
                }

                // Copy all exercises with updated loopId
                sourceExercises.forEach { pe ->
                    val newPe = ProgramExercise(
                        programId = newProgramId,
                        exerciseId = pe.exerciseId,
                        sortOrder = pe.sortOrder,
                        sets = pe.sets,
                        targetValue = pe.targetValue,
                        intervalSeconds = pe.intervalSeconds,
                        loopId = pe.loopId?.let { loopIdMapping[it] }
                    )
                    programExerciseDao.insert(newPe)
                }

                _snackbarMessage.value = UiMessage.ProgramDuplicated
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    suspend fun getProgramById(programId: Long): Program? {
        return programDao.getProgramById(programId)
    }

    // ========================================
    // ProgramExercise operations
    // ========================================

    fun getProgramExercisesFlow(programId: Long) = programExerciseDao.getExercisesForProgram(programId)

    suspend fun getProgramExercisesSync(programId: Long): List<ProgramExercise> {
        return programExerciseDao.getExercisesForProgramSync(programId)
    }

    fun addProgramExercise(
        programId: Long,
        exerciseId: Long,
        sets: Int = 1,
        targetValue: Int,
        intervalSeconds: Int = 60
    ) {
        viewModelScope.launch {
            try {
                val sortOrder = programExerciseDao.getNextSortOrder(programId)
                val programExercise = ProgramExercise(
                    programId = programId,
                    exerciseId = exerciseId,
                    sortOrder = sortOrder,
                    sets = sets,
                    targetValue = targetValue,
                    intervalSeconds = intervalSeconds
                )
                programExerciseDao.insert(programExercise)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    suspend fun addProgramExerciseSync(
        programId: Long,
        exerciseId: Long,
        sets: Int = 1,
        targetValue: Int,
        intervalSeconds: Int = 60,
        loopId: Long? = null,
        sortOrder: Int? = null
    ): Long? {
        return try {
            val finalSortOrder = sortOrder ?: programExerciseDao.getNextSortOrder(programId)
            val programExercise = ProgramExercise(
                programId = programId,
                exerciseId = exerciseId,
                sortOrder = finalSortOrder,
                sets = sets,
                targetValue = targetValue,
                intervalSeconds = intervalSeconds,
                loopId = loopId
            )
            programExerciseDao.insert(programExercise)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
            null
        }
    }

    suspend fun updateProgramExercise(programExercise: ProgramExercise) {
        try {
            programExerciseDao.update(programExercise)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    suspend fun deleteProgramExercise(programExercise: ProgramExercise) {
        try {
            programExerciseDao.delete(programExercise)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    suspend fun reorderProgramExercises(exerciseIds: List<Long>) {
        try {
            programExerciseDao.reorderExercises(exerciseIds)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    suspend fun reorderProgramExercises(programId: Long, fromIndex: Int, toIndex: Int) {
        try {
            val currentExercises = programExerciseDao.getExercisesForProgramSync(programId).toMutableList()
            if (fromIndex < 0 || toIndex < 0 ||
                fromIndex >= currentExercises.size || toIndex >= currentExercises.size) {
                return
            }

            val item = currentExercises.removeAt(fromIndex)
            currentExercises.add(toIndex, item)

            val reorderedIds = currentExercises.map { it.id }
            programExerciseDao.reorderExercises(reorderedIds)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    // ========================================
    // ProgramLoop operations
    // ========================================

    fun getProgramLoopsFlow(programId: Long) = programLoopDao.getLoopsForProgram(programId)

    suspend fun getProgramLoopsSync(programId: Long): List<ProgramLoop> {
        return programLoopDao.getLoopsForProgramSync(programId)
    }

    suspend fun getProgramLoopById(loopId: Long): ProgramLoop? {
        return programLoopDao.getLoopById(loopId)
    }

    suspend fun addProgramLoop(
        programId: Long,
        rounds: Int = 3,
        restBetweenRounds: Int = 60
    ): Long? {
        return try {
            val sortOrder = programLoopDao.getNextSortOrder(programId)
            val loop = ProgramLoop(
                programId = programId,
                sortOrder = sortOrder,
                rounds = rounds,
                restBetweenRounds = restBetweenRounds
            )
            programLoopDao.insert(loop)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
            null
        }
    }

    suspend fun updateProgramLoop(loop: ProgramLoop) {
        try {
            programLoopDao.update(loop)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    suspend fun deleteProgramLoop(loop: ProgramLoop) {
        try {
            programLoopDao.delete(loop)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    suspend fun addProgramExerciseToLoop(
        programId: Long,
        exerciseId: Long,
        loopId: Long,
        sets: Int = 1,
        targetValue: Int,
        intervalSeconds: Int = 60
    ): Long? {
        return try {
            val sortOrder = programExerciseDao.getNextSortOrder(programId)
            val programExercise = ProgramExercise(
                programId = programId,
                exerciseId = exerciseId,
                sortOrder = sortOrder,
                sets = sets,
                targetValue = targetValue,
                intervalSeconds = intervalSeconds,
                loopId = loopId
            )
            programExerciseDao.insert(programExercise)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
            null
        }
    }

    suspend fun moveExerciseToLoop(programExercise: ProgramExercise, loopId: Long?) {
        try {
            programExerciseDao.update(programExercise.copy(loopId = loopId))
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    // ========================================
    // IntervalProgram operations
    // ========================================

    val intervalPrograms: StateFlow<List<IntervalProgram>> = intervalProgramDao.getAllPrograms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    suspend fun createIntervalProgramAndGetId(
        name: String,
        workSeconds: Int,
        restSeconds: Int,
        rounds: Int,
        roundRestSeconds: Int
    ): Long? {
        return try {
            val program = IntervalProgram(
                name = name,
                workSeconds = workSeconds,
                restSeconds = restSeconds,
                rounds = rounds,
                roundRestSeconds = roundRestSeconds
            )
            intervalProgramDao.insert(program)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
            null
        }
    }

    suspend fun updateIntervalProgram(program: IntervalProgram) {
        try {
            intervalProgramDao.update(program)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    fun deleteIntervalProgram(programId: Long) {
        viewModelScope.launch {
            try {
                intervalProgramDao.deleteById(programId)
                todoTaskDao.deleteByReference(TodoTask.TYPE_INTERVAL, programId)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    suspend fun getIntervalProgramById(programId: Long): IntervalProgram? {
        return intervalProgramDao.getProgramById(programId)
    }

    fun duplicateIntervalProgram(programId: Long, copySuffix: String) {
        viewModelScope.launch {
            try {
                val source = intervalProgramDao.getProgramById(programId) ?: return@launch
                val sourceExercises = intervalProgramExerciseDao.getExercisesForProgramSync(programId)

                val newId = intervalProgramDao.insert(
                    IntervalProgram(
                        name = "${source.name} $copySuffix",
                        workSeconds = source.workSeconds,
                        restSeconds = source.restSeconds,
                        rounds = source.rounds,
                        roundRestSeconds = source.roundRestSeconds
                    )
                )

                sourceExercises.forEach { exercise ->
                    intervalProgramExerciseDao.insert(
                        IntervalProgramExercise(
                            programId = newId,
                            exerciseId = exercise.exerciseId,
                            sortOrder = exercise.sortOrder
                        )
                    )
                }

                _snackbarMessage.value = UiMessage.ProgramDuplicated
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    // ========================================
    // IntervalProgramExercise operations
    // ========================================

    fun getIntervalProgramExercisesFlow(programId: Long) =
        intervalProgramExerciseDao.getExercisesForProgram(programId)

    suspend fun getIntervalProgramExercisesSync(programId: Long): List<IntervalProgramExercise> {
        return intervalProgramExerciseDao.getExercisesForProgramSync(programId)
    }

    suspend fun addIntervalProgramExerciseSync(
        programId: Long,
        exerciseId: Long,
        sortOrder: Int? = null
    ): Long? {
        return try {
            val finalSortOrder = sortOrder ?: intervalProgramExerciseDao.getNextSortOrder(programId)
            val exercise = IntervalProgramExercise(
                programId = programId,
                exerciseId = exerciseId,
                sortOrder = finalSortOrder
            )
            intervalProgramExerciseDao.insert(exercise)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
            null
        }
    }

    suspend fun deleteIntervalProgramExercise(exercise: IntervalProgramExercise) {
        try {
            intervalProgramExerciseDao.delete(exercise)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    suspend fun updateIntervalProgramExercise(exercise: IntervalProgramExercise) {
        try {
            intervalProgramExerciseDao.update(exercise)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    suspend fun reorderIntervalProgramExercises(exerciseIds: List<Long>) {
        try {
            intervalProgramExerciseDao.reorderExercises(exerciseIds)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    // ========================================
    // IntervalRecord operations
    // ========================================

    val intervalRecords: StateFlow<List<IntervalRecord>> = intervalRecordDao.getAllRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    suspend fun saveIntervalRecord(record: IntervalRecord): Long? {
        return try {
            intervalRecordDao.insert(record)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
            null
        }
    }

    suspend fun updateIntervalRecord(record: IntervalRecord) {
        try {
            intervalRecordDao.update(record)
        } catch (e: Exception) {
            _snackbarMessage.value = UiMessage.ErrorOccurred
        }
    }

    fun updateIntervalRecordAsync(record: IntervalRecord) {
        viewModelScope.launch {
            updateIntervalRecord(record)
        }
    }

    fun deleteIntervalRecord(recordId: Long) {
        viewModelScope.launch {
            try {
                intervalRecordDao.deleteById(recordId)
            } catch (e: Exception) {
                _snackbarMessage.value = UiMessage.ErrorOccurred
            }
        }
    }

    // ========================================
    // Community Share Export/Import features
    // ========================================

    /**
     * Determine file type of JSON string
     * @return "backup", "share", "unknown"
     */
    fun detectJsonFileType(jsonString: String): String {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val jsonElement = json.parseToJsonElement(jsonString)
            val jsonObject = jsonElement as? JsonObject ?: return "unknown"

            when {
                jsonObject.containsKey("exportType") -> {
                    val exportType = jsonObject["exportType"]
                    if (exportType is JsonPrimitive && exportType.content == "share") {
                        "share"
                    } else {
                        "unknown"
                    }
                }
                jsonObject.containsKey("version") && jsonObject.containsKey("app") -> "backup"
                else -> "unknown"
            }
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * Export selected programs, intervals, and exercises to community share JSON
     */
    suspend fun exportCommunityShare(
        selectedProgramIds: Set<Long>,
        selectedIntervalProgramIds: Set<Long>,
        selectedExerciseIds: Set<Long>
    ): String = withContext(Dispatchers.IO) {
        try {
            // Aggregate all exercise IDs (direct selection + dependencies)
            val allExerciseIds = selectedExerciseIds.toMutableSet()

            // Collect program dependencies
            val sharePrograms = mutableListOf<ShareProgram>()
            for (programId in selectedProgramIds) {
                val program = programDao.getProgramById(programId) ?: continue
                val programExercises = programExerciseDao.getExercisesForProgramSync(programId)
                val programLoops = programLoopDao.getLoopsForProgramSync(programId)

                // Add dependency exercise IDs
                programExercises.forEach { pe -> allExerciseIds.add(pe.exerciseId) }

                // Map loop IDs to local sequence
                val loopIdMap = mutableMapOf<Long, Int>()
                programLoops.forEachIndexed { index, loop ->
                    loopIdMap[loop.id] = index + 1
                }

                val shareLoops = programLoops.mapIndexed { index, loop ->
                    ShareProgramLoop(
                        id = index + 1,
                        sortOrder = loop.sortOrder,
                        rounds = loop.rounds,
                        restBetweenRounds = loop.restBetweenRounds
                    )
                }

                val shareProgramExercises = programExercises.map { pe ->
                    val exercise = exerciseDao.getExerciseById(pe.exerciseId)
                    ShareProgramExercise(
                        exerciseName = exercise?.name ?: "",
                        exerciseType = exercise?.type ?: "",
                        sortOrder = pe.sortOrder,
                        sets = pe.sets,
                        targetValue = pe.targetValue,
                        intervalSeconds = pe.intervalSeconds,
                        loopId = pe.loopId?.let { loopIdMap[it] }
                    )
                }

                sharePrograms.add(
                    ShareProgram(
                        name = program.name,
                        exercises = shareProgramExercises,
                        loops = shareLoops
                    )
                )
            }

            // Collect interval program dependencies
            val shareIntervalPrograms = mutableListOf<ShareIntervalProgram>()
            for (intervalId in selectedIntervalProgramIds) {
                val interval = intervalProgramDao.getProgramById(intervalId) ?: continue
                val intervalExercises = intervalProgramExerciseDao.getExercisesForProgramSync(intervalId)

                // Add dependency exercise IDs
                intervalExercises.forEach { ie -> allExerciseIds.add(ie.exerciseId) }

                val shareIntervalExercises = intervalExercises.map { ie ->
                    val exercise = exerciseDao.getExerciseById(ie.exerciseId)
                    ShareIntervalProgramExercise(
                        exerciseName = exercise?.name ?: "",
                        exerciseType = exercise?.type ?: "",
                        sortOrder = ie.sortOrder
                    )
                }

                shareIntervalPrograms.add(
                    ShareIntervalProgram(
                        name = interval.name,
                        workSeconds = interval.workSeconds,
                        restSeconds = interval.restSeconds,
                        rounds = interval.rounds,
                        roundRestSeconds = interval.roundRestSeconds,
                        exercises = shareIntervalExercises
                    )
                )
            }

            // Collect exercises, remove duplicates
            val exerciseMap = mutableMapOf<Long, ShareExercise>()
            val groupNames = mutableSetOf<String>()
            for (exerciseId in allExerciseIds) {
                val exercise = exerciseDao.getExerciseById(exerciseId) ?: continue
                exerciseMap[exerciseId] = ShareExercise(
                    name = exercise.name,
                    type = exercise.type,
                    group = exercise.group,
                    sortOrder = exercise.sortOrder,
                    laterality = exercise.laterality,
                    targetSets = exercise.targetSets,
                    targetValue = exercise.targetValue,
                    restInterval = exercise.restInterval,
                    repDuration = exercise.repDuration,
                    distanceTrackingEnabled = exercise.distanceTrackingEnabled,
                    weightTrackingEnabled = exercise.weightTrackingEnabled,
                    assistanceTrackingEnabled = exercise.assistanceTrackingEnabled,
                    description = exercise.description
                )
                exercise.group?.let { groupNames.add(it) }
            }

            val shareGroups = groupNames.sorted().map { ShareGroup(name = it) }

            // Get app version
            val appVersion = try {
                val packageInfo = getApplication<android.app.Application>().packageManager
                    .getPackageInfo(getApplication<android.app.Application>().packageName, 0)
                packageInfo.versionName ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }

            val shareData = CommunityShareData(
                formatVersion = 1,
                exportType = "share",
                exportDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                exportId = java.util.UUID.randomUUID().toString(),
                appVersion = appVersion,
                data = CommunityShareContent(
                    groups = shareGroups,
                    exercises = exerciseMap.values.toList(),
                    programs = sharePrograms,
                    intervalPrograms = shareIntervalPrograms
                )
            )

            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.CommunityShareExportComplete(
                    exerciseCount = exerciseMap.size,
                    programCount = sharePrograms.size,
                    intervalProgramCount = shareIntervalPrograms.size
                )
            }

            val json = Json { ignoreUnknownKeys = true }
            json.encodeToString(shareData)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.ExportError(e.message ?: "")
            }
            throw e
        }
    }

    /**
     * Import community share JSON
     */
    suspend fun previewCommunityShareImport(data: CommunityShareData): CommunityShareImportReport = withContext(Dispatchers.IO) {
        val content = data.data
        var groupsAdded = 0
        var groupsReused = 0
        var exercisesAdded = 0
        var exercisesSkipped = 0
        var programsAdded = 0
        var programsSkipped = 0
        var intervalProgramsAdded = 0
        var intervalProgramsSkipped = 0

        // Groups
        for (shareGroup in content.groups) {
            val existing = groupDao.getGroupByName(shareGroup.name)
            if (existing != null) groupsReused++ else groupsAdded++
        }

        // Exercises
        val seenExerciseKeys = mutableSetOf<String>()
        for (shareExercise in content.exercises) {
            val key = "${shareExercise.name}|${shareExercise.type}"
            if (!seenExerciseKeys.add(key)) continue
            val existing = exerciseDao.getExerciseByNameAndType(shareExercise.name, shareExercise.type)
            if (existing != null) exercisesSkipped++ else exercisesAdded++
        }

        // Programs
        for (shareProgram in content.programs) {
            val existing = programDao.getProgramByName(shareProgram.name)
            if (existing != null) programsSkipped++ else programsAdded++
        }

        // Intervals
        for (shareInterval in content.intervalPrograms) {
            val existing = intervalProgramDao.getProgramByName(shareInterval.name)
            if (existing != null) intervalProgramsSkipped++ else intervalProgramsAdded++
        }

        CommunityShareImportReport(
            groupsAdded = groupsAdded,
            groupsReused = groupsReused,
            exercisesAdded = exercisesAdded,
            exercisesSkipped = exercisesSkipped,
            programsAdded = programsAdded,
            programsSkipped = programsSkipped,
            intervalProgramsAdded = intervalProgramsAdded,
            intervalProgramsSkipped = intervalProgramsSkipped
        )
    }

    suspend fun importCommunityShare(jsonString: String): CommunityShareImportReport = withContext(Dispatchers.IO) {
        try {
            // File type check: error if backup JSON is passed
            if (detectJsonFileType(jsonString) == "backup") {
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = UiMessage.WrongFileType(
                        detected = "backup",
                        expected = "share"
                    )
                }
                return@withContext CommunityShareImportReport(
                    errors = listOf("Wrong file type: backup")
                )
            }

            val json = Json { ignoreUnknownKeys = true }
            val shareData = json.decodeFromString<CommunityShareData>(jsonString)

            // Validation
            val validationErrors = validateCommunityShareContent(shareData)
            if (validationErrors.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = UiMessage.CommunityShareImportError(
                        validationErrors.first()
                    )
                }
                return@withContext CommunityShareImportReport(errors = validationErrors)
            }

            val content = shareData.data
            var groupsAdded = 0
            var groupsReused = 0
            var exercisesAdded = 0
            var exercisesSkipped = 0
            var programsAdded = 0
            var programsSkipped = 0
            var intervalProgramsAdded = 0
            var intervalProgramsSkipped = 0
            val importedProgramIds = mutableListOf<Long>()
            val importedIntervalProgramIds = mutableListOf<Long>()

            // Get current maximum displayOrder
            var nextDisplayOrder = exerciseDao.getMaxDisplayOrder() + 1

            // 1. Import groups
            for (shareGroup in content.groups) {
                val existing = groupDao.getGroupByName(shareGroup.name)
                if (existing != null) {
                    groupsReused++
                } else {
                    groupDao.insertGroup(ExerciseGroup(name = shareGroup.name))
                    groupsAdded++
                }
            }

            // 2. Import exercises (match by name+type, skip duplicates)
            // exerciseIdMap: "name|type" -> DB ID
            val exerciseIdMap = mutableMapOf<String, Long>()
            val seenExerciseKeys = mutableSetOf<String>()
            for (shareExercise in content.exercises) {
                val key = "${shareExercise.name}|${shareExercise.type}"
                // First duplicate in JSON wins
                if (!seenExerciseKeys.add(key)) continue

                val existing = exerciseDao.getExerciseByNameAndType(shareExercise.name, shareExercise.type)
                if (existing != null) {
                    exerciseIdMap[key] = existing.id
                    exercisesSkipped++
                } else {
                    val newExercise = Exercise(
                        name = shareExercise.name,
                        type = shareExercise.type,
                        group = shareExercise.group,
                        sortOrder = shareExercise.sortOrder,
                        displayOrder = nextDisplayOrder++,
                        laterality = shareExercise.laterality,
                        targetSets = shareExercise.targetSets,
                        targetValue = shareExercise.targetValue,
                        restInterval = shareExercise.restInterval,
                        repDuration = shareExercise.repDuration,
                        distanceTrackingEnabled = shareExercise.distanceTrackingEnabled,
                        weightTrackingEnabled = shareExercise.weightTrackingEnabled,
                        assistanceTrackingEnabled = shareExercise.assistanceTrackingEnabled,
                        description = shareExercise.description?.take(60)
                    )
                    val newId = exerciseDao.insertExercise(newExercise)
                    exerciseIdMap[key] = newId
                    exercisesAdded++
                }
            }

            // 3. Import programs
            for (shareProgram in content.programs) {
                val existing = programDao.getProgramByName(shareProgram.name)
                if (existing != null) {
                    programsSkipped++
                    importedProgramIds.add(existing.id)
                    continue
                }

                // Programs creation
                val newProgramId = programDao.insert(Program(name = shareProgram.name))

                // Create loops (local ID -> DB ID mapping)
                val loopIdMap = mutableMapOf<Int, Long>()
                for (shareLoop in shareProgram.loops) {
                    val newLoopId = programLoopDao.insert(
                        ProgramLoop(
                            programId = newProgramId,
                            sortOrder = shareLoop.sortOrder,
                            rounds = shareLoop.rounds,
                            restBetweenRounds = shareLoop.restBetweenRounds
                        )
                    )
                    loopIdMap[shareLoop.id] = newLoopId
                }

                // Create ProgramExercise
                for (sharePe in shareProgram.exercises) {
                    val exerciseKey = "${sharePe.exerciseName}|${sharePe.exerciseType}"
                    val exerciseId = exerciseIdMap[exerciseKey] ?: continue
                    programExerciseDao.insert(
                        ProgramExercise(
                            programId = newProgramId,
                            exerciseId = exerciseId,
                            sortOrder = sharePe.sortOrder,
                            sets = sharePe.sets,
                            targetValue = sharePe.targetValue,
                            intervalSeconds = sharePe.intervalSeconds,
                            loopId = sharePe.loopId?.let { loopIdMap[it] }
                        )
                    )
                }

                importedProgramIds.add(newProgramId)
                programsAdded++
            }

            // 4. Import interval programs
            for (shareInterval in content.intervalPrograms) {
                val existing = intervalProgramDao.getProgramByName(shareInterval.name)
                if (existing != null) {
                    intervalProgramsSkipped++
                    importedIntervalProgramIds.add(existing.id)
                    continue
                }

                // Intervals program creation
                val newIntervalId = intervalProgramDao.insert(
                    IntervalProgram(
                        name = shareInterval.name,
                        workSeconds = shareInterval.workSeconds,
                        restSeconds = shareInterval.restSeconds,
                        rounds = shareInterval.rounds,
                        roundRestSeconds = shareInterval.roundRestSeconds
                    )
                )

                // Create IntervalProgramExercise
                for (shareIe in shareInterval.exercises) {
                    val exerciseKey = "${shareIe.exerciseName}|${shareIe.exerciseType}"
                    val exerciseId = exerciseIdMap[exerciseKey] ?: continue
                    intervalProgramExerciseDao.insert(
                        IntervalProgramExercise(
                            programId = newIntervalId,
                            exerciseId = exerciseId,
                            sortOrder = shareIe.sortOrder
                        )
                    )
                }

                importedIntervalProgramIds.add(newIntervalId)
                intervalProgramsAdded++
            }

            val report = CommunityShareImportReport(
                groupsAdded = groupsAdded,
                groupsReused = groupsReused,
                exercisesAdded = exercisesAdded,
                exercisesSkipped = exercisesSkipped,
                programsAdded = programsAdded,
                programsSkipped = programsSkipped,
                intervalProgramsAdded = intervalProgramsAdded,
                intervalProgramsSkipped = intervalProgramsSkipped,
                importedProgramIds = importedProgramIds,
                importedIntervalProgramIds = importedIntervalProgramIds
            )

            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.CommunityShareImportComplete(report)
            }

            report
        } catch (e: kotlinx.serialization.SerializationException) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.CommunityShareImportError(
                    e.message ?: "Invalid JSON format"
                )
            }
            CommunityShareImportReport(errors = listOf(e.message ?: "Invalid JSON format"))
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = UiMessage.CommunityShareImportError(
                    e.message ?: "Import failed"
                )
            }
            CommunityShareImportReport(errors = listOf(e.message ?: "Import failed"))
        }
    }


    /**
     * Get all data as JSON for AI context
     */
    suspend fun getAllDataAsJson(): String {
        return withContext(Dispatchers.IO) {
            val groupsList = groupDao.getAllGroupsSync()
            val exercisesList = exerciseDao.getAllExercisesSync()
            val recordsList = recordDao.getAllRecordsSync()
            val programsList = programDao.getAllProgramsSync()
            val programExercisesList = programExerciseDao.getAllProgramExercisesSync()
            val programLoopsList = programLoopDao.getAllProgramLoopsSync()
            val intervalProgramsList = intervalProgramDao.getAllIntervalProgramsSync()
            val intervalExercisesList = intervalProgramExerciseDao.getAllIntervalProgramExercisesSync()
            val intervalRecordsList = intervalRecordDao.getAllIntervalRecordsSync()
            val todoTasksList = todoTaskDao.getAllTodoTasksSync()

            val backupData = BackupData(
                version = 22,
                exportDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                app = "Calisthenics Memory",
                groups = groupsList.map { ExportGroup(it.id, it.name, it.displayOrder) },
                exercises = exercisesList.map {
                    ExportExercise(it.id, it.name, it.type, it.group, it.sortOrder, it.displayOrder,
                        it.laterality, it.targetSets, it.targetValue, it.isFavorite, it.restInterval,
                        it.repDuration, it.distanceTrackingEnabled, it.weightTrackingEnabled,
                        it.assistanceTrackingEnabled, it.description)
                },
                records = recordsList.map {
                    ExportRecord(it.id, it.exerciseId, it.valueRight, it.valueLeft, it.setNumber,
                        it.date, it.time, it.comment, it.distanceCm, it.weightG, it.assistanceG, it.rpe)
                },
                programs = programsList.map { ExportProgram(it.id, it.name) },
                programExercises = programExercisesList.map {
                    ExportProgramExercise(it.id, it.programId, it.exerciseId, it.sortOrder, it.sets,
                        it.targetValue, it.intervalSeconds, it.loopId)
                },
                programLoops = programLoopsList.map { ExportProgramLoop(it.id, it.programId, it.sortOrder, it.rounds, it.restBetweenRounds) },
                intervalPrograms = intervalProgramsList.map {
                    ExportIntervalProgram(it.id, it.name, it.workSeconds, it.restSeconds, it.rounds, it.roundRestSeconds)
                },
                intervalProgramExercises = intervalExercisesList.map { ExportIntervalProgramExercise(it.id, it.programId, it.exerciseId, it.sortOrder) },
                intervalRecords = intervalRecordsList.map {
                    ExportIntervalRecord(it.id, it.programName, it.date, it.time, it.workSeconds, it.restSeconds, it.rounds, it.roundRestSeconds, it.completedRounds, it.completedExercisesInLastRound, it.exercisesJson, it.comment)
                },
                todoTasks = todoTasksList.map { ExportTodoTask(it.id, it.type, it.referenceId, it.sortOrder, it.repeatDays, it.lastCompletedDate) }
            )

            Json.encodeToString(backupData)
        }
    }
}
