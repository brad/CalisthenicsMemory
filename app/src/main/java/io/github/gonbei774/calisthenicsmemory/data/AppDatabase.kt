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
        TodoTask::class
    ],
    version = 22,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calisthenics_memory_db"
                )
                .addMigrations(
                    MIGRATION_20_21,
                    MIGRATION_21_22
                )
                .fallbackToDestructiveMigration() // Simplified for this task
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercise_groups ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE training_records ADD COLUMN rpe INTEGER")
            }
        }
    }
}
