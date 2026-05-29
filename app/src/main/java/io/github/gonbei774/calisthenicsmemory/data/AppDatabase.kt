package io.github.gonbei774.calisthenicsmemory.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        ExerciseGroup::class,
        TrainingRecord::class,
        Program::class,
        ProgramExercise::class,
        ProgramLoop::class,
        IntervalProgram::class,
        IntervalProgramExercise::class,
        IntervalRecord::class,
        TodoTask::class,
        AiThread::class,
        AiMessage::class
    ],
    version = 23,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun exerciseGroupDao(): ExerciseGroupDao
    abstract fun trainingRecordDao(): TrainingRecordDao
    abstract fun programDao(): ProgramDao
    abstract fun programExerciseDao(): ProgramExerciseDao
    abstract fun programLoopDao(): ProgramLoopDao
    abstract fun intervalProgramDao(): IntervalProgramDao
    abstract fun intervalProgramExerciseDao(): IntervalProgramExerciseDao
    abstract fun intervalRecordDao(): IntervalRecordDao
    abstract fun todoTaskDao(): TodoTaskDao
    abstract fun aiDao(): AiDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bodyweight_trainer_database"
                )
                    .addMigrations(
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                        MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                        MIGRATION_21_22
                        , MIGRATION_22_23
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Migration 9 -> 10: add displayOrder, restInterval, repDuration
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercises ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE exercises ADD COLUMN restInterval INTEGER")
                database.execSQL("ALTER TABLE exercises ADD COLUMN repDuration INTEGER")
            }
        }

        // Migration 10 -> 11: add distanceTrackingEnabled, weightTrackingEnabled
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercises ADD COLUMN distanceTrackingEnabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE exercises ADD COLUMN weightTrackingEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration 11 -> 12: add distanceCm, weightG to training_records
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE training_records ADD COLUMN distanceCm INTEGER")
                database.execSQL("ALTER TABLE training_records ADD COLUMN weightG INTEGER")
            }
        }

        // Migration 12 -> 13: add isFavorite to exercises
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercises ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration 13 -> 14: Migrate timerMode/startInterval to SharedPreferences
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS programs_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                database.execSQL("INSERT INTO programs_new (id, name) SELECT id, name FROM programs")
                database.execSQL("DROP TABLE programs")
                database.execSQL("ALTER TABLE programs_new RENAME TO programs")
            }
        }

        // Migration 14 -> 15: add Loop feature
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS program_loops (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, programId INTEGER NOT NULL, sortOrder INTEGER NOT NULL, rounds INTEGER NOT NULL, restBetweenRounds INTEGER NOT NULL, FOREIGN KEY (programId) REFERENCES programs(id) ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_program_loops_programId ON program_loops(programId)")
                database.execSQL("CREATE TABLE IF NOT EXISTS program_exercises_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, programId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, sortOrder INTEGER NOT NULL, sets INTEGER NOT NULL, targetValue INTEGER NOT NULL, intervalSeconds INTEGER NOT NULL, loopId INTEGER DEFAULT NULL, FOREIGN KEY (programId) REFERENCES programs(id) ON DELETE CASCADE, FOREIGN KEY (exerciseId) REFERENCES exercises(id) ON DELETE CASCADE, FOREIGN KEY (loopId) REFERENCES program_loops(id) ON DELETE CASCADE)")
                database.execSQL("INSERT INTO program_exercises_new (id, programId, exerciseId, sortOrder, sets, targetValue, intervalSeconds, loopId) SELECT id, programId, exerciseId, sortOrder, sets, targetValue, intervalSeconds, NULL FROM program_exercises")
                database.execSQL("DROP TABLE program_exercises")
                database.execSQL("ALTER TABLE program_exercises_new RENAME TO program_exercises")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_program_exercises_programId ON program_exercises(programId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_program_exercises_exerciseId ON program_exercises(exerciseId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_program_exercises_loopId ON program_exercises(loopId)")
            }
        }

        // Migration 15 -> 16: add assistance tracking
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercises ADD COLUMN assistanceTrackingEnabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE training_records ADD COLUMN assistanceG INTEGER")
            }
        }

        // Migration 16 -> 17: add exercise description
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercises ADD COLUMN description TEXT")
            }
        }

        // Migration 17 -> 18: add Interval mode
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS interval_programs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, workSeconds INTEGER NOT NULL, restSeconds INTEGER NOT NULL, rounds INTEGER NOT NULL, roundRestSeconds INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS interval_program_exercises (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, programId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, sortOrder INTEGER NOT NULL, FOREIGN KEY (programId) REFERENCES interval_programs(id) ON DELETE CASCADE, FOREIGN KEY (exerciseId) REFERENCES exercises(id) ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_interval_program_exercises_programId ON interval_program_exercises(programId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_interval_program_exercises_exerciseId ON interval_program_exercises(exerciseId)")
                database.execSQL("CREATE TABLE IF NOT EXISTS interval_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, programName TEXT NOT NULL, date TEXT NOT NULL, time TEXT NOT NULL, workSeconds INTEGER NOT NULL, restSeconds INTEGER NOT NULL, rounds INTEGER NOT NULL, roundRestSeconds INTEGER NOT NULL, completedRounds INTEGER NOT NULL, completedExercisesInLastRound INTEGER NOT NULL, exercisesJson TEXT NOT NULL, comment TEXT)")
            }
        }

        // Migration 18 -> 19: add type/referenceId to TodoTask
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS todo_tasks_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL DEFAULT 'EXERCISE', referenceId INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
                database.execSQL("INSERT INTO todo_tasks_new (id, type, referenceId, sortOrder) SELECT id, 'EXERCISE', exerciseId, sortOrder FROM todo_tasks")
                database.execSQL("DROP TABLE todo_tasks")
                database.execSQL("ALTER TABLE todo_tasks_new RENAME TO todo_tasks")
            }
        }

        // Migration 19 -> 20: add weekday repeat for TodoTask
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE todo_tasks ADD COLUMN repeatDays TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE todo_tasks ADD COLUMN lastCompletedDate TEXT")
            }
        }

        // Migration 20 -> 21: add displayOrder to exercise groups
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercise_groups ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE exercise_groups SET displayOrder = (SELECT COUNT(*) FROM exercise_groups e2 WHERE e2.name < exercise_groups.name)")
            }
        }

        // Migration 21 -> 22: add RPE field to training records for AI analysis
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE training_records ADD COLUMN rpe INTEGER")
            }
        }

        // Migration 22 -> 23: add AI threads and messages
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS ai_threads (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS ai_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, threadId INTEGER NOT NULL, text TEXT NOT NULL, isUser INTEGER NOT NULL, timestamp INTEGER NOT NULL, FOREIGN KEY (threadId) REFERENCES ai_threads(id) ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_messages_threadId ON ai_messages(threadId)")
            }
        }
    }
}
