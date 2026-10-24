package com.mooscles.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class EnumConverters {
    @TypeConverter fun muscle(value: String) = MuscleGroup.valueOf(value)
    @TypeConverter fun muscle(value: MuscleGroup) = value.name
    @TypeConverter fun equipment(value: String) = Equipment.valueOf(value)
    @TypeConverter fun equipment(value: Equipment) = value.name
    @TypeConverter fun convention(value: String) = LoadConvention.valueOf(value)
    @TypeConverter fun convention(value: LoadConvention) = value.name
    @TypeConverter fun status(value: String) = WorkoutStatus.valueOf(value)
    @TypeConverter fun status(value: WorkoutStatus) = value.name
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM routine_folders ORDER BY position, name COLLATE NOCASE") suspend fun routineFolders(): List<RoutineFolder>
    @Query("SELECT * FROM routine_folders WHERE id = :id") suspend fun routineFolder(id: String): RoutineFolder?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedRoutineFolder(item: RoutineFolder)
    @Insert suspend fun insertRoutineFolder(item: RoutineFolder)
    @Update suspend fun updateRoutineFolder(item: RoutineFolder)
    @Query("SELECT * FROM routine_target_checkpoints") suspend fun targetCheckpoints(): List<RoutineTargetCheckpoint>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTargetCheckpoint(item: RoutineTargetCheckpoint)
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE") suspend fun exercises(): List<Exercise>
    @Query("SELECT * FROM routines ORDER BY position") suspend fun routines(): List<Routine>
    @Query("SELECT * FROM routine_exercises ORDER BY position") suspend fun routineExercises(): List<RoutineExercise>
    @Query("SELECT * FROM routine_sets ORDER BY position") suspend fun routineSets(): List<RoutineSet>
    @Query("SELECT * FROM routine_sets WHERE routineExerciseId = :id ORDER BY position") suspend fun plannedSets(id: String): List<RoutineSet>
    @Query("SELECT * FROM routine_sets WHERE id = :id") suspend fun routineSet(id: String): RoutineSet?
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC") suspend fun workouts(): List<Workout>
    @Query("SELECT * FROM workout_exercises ORDER BY position") suspend fun workoutExercises(): List<WorkoutExercise>
    @Query("SELECT * FROM workout_sets ORDER BY position") suspend fun sets(): List<WorkoutSet>
    @Query("SELECT * FROM exercises WHERE id = :id") suspend fun exercise(id: String): Exercise?
    @Query("SELECT * FROM routines WHERE id = :id") suspend fun routine(id: String): Routine?
    @Query("SELECT * FROM routine_exercises WHERE id = :id") suspend fun routineExercise(id: String): RoutineExercise?
    @Query("SELECT * FROM routine_exercises WHERE routineId = :id ORDER BY position") suspend fun routineItems(id: String): List<RoutineExercise>
    @Query("SELECT * FROM exercises WHERE id IN (SELECT exerciseId FROM routine_exercises WHERE routineId = :routineId)")
    suspend fun routineMovements(routineId: String): List<Exercise>
    @Query("SELECT routine_sets.* FROM routine_sets INNER JOIN routine_exercises ON routine_sets.routineExerciseId = routine_exercises.id WHERE routine_exercises.routineId = :routineId ORDER BY routine_exercises.position, routine_sets.position")
    suspend fun routinePlannedSets(routineId: String): List<RoutineSet>
    @Query("SELECT * FROM workouts WHERE id = :id") suspend fun workout(id: String): Workout?
    @Query("SELECT * FROM workouts WHERE status = 'ACTIVE' LIMIT 1") suspend fun activeWorkout(): Workout?
    @Query("SELECT id FROM workouts WHERE status = 'ACTIVE' LIMIT 1") fun activeWorkoutId(): Flow<String?>
    @Query("SELECT * FROM workout_exercises WHERE id = :id") suspend fun workoutExercise(id: String): WorkoutExercise?
    @Query("SELECT * FROM workout_exercises WHERE workoutId = :id ORDER BY position") suspend fun workoutItems(id: String): List<WorkoutExercise>
    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :id ORDER BY position") suspend fun exerciseSets(id: String): List<WorkoutSet>
    @Query("SELECT workout_sets.* FROM workout_sets INNER JOIN workout_exercises ON workout_sets.workoutExerciseId = workout_exercises.id WHERE workout_exercises.workoutId = :workoutId ORDER BY workout_exercises.position, workout_sets.position")
    suspend fun workoutSets(workoutId: String): List<WorkoutSet>
    @Query("SELECT * FROM workout_sets WHERE id = :id") suspend fun set(id: String): WorkoutSet?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedExercises(items: List<Exercise>)
    @Insert suspend fun insertExercise(item: Exercise)
    @Update suspend fun updateExercise(item: Exercise)
    @Insert suspend fun insertRoutine(item: Routine)
    @Update suspend fun updateRoutine(item: Routine)
    @Insert suspend fun insertRoutineExercise(item: RoutineExercise)
    @Update suspend fun updateRoutineExercise(item: RoutineExercise)
    @Insert suspend fun insertRoutineSet(item: RoutineSet)
    @Insert suspend fun insertRoutineSets(items: List<RoutineSet>)
    @Update suspend fun updateRoutineSet(item: RoutineSet)
    @Insert suspend fun insertWorkout(item: Workout)
    @Update suspend fun updateWorkout(item: Workout)
    @Insert suspend fun insertWorkoutExercise(item: WorkoutExercise)
    @Insert suspend fun insertWorkoutExercises(items: List<WorkoutExercise>)
    @Update suspend fun updateWorkoutExercise(item: WorkoutExercise)
    @Insert suspend fun insertSet(item: WorkoutSet)
    @Insert suspend fun insertSets(items: List<WorkoutSet>)
    @Update suspend fun updateSet(item: WorkoutSet)
    @Query("DELETE FROM routines WHERE id = :id") suspend fun deleteRoutine(id: String)
    @Query("DELETE FROM routine_exercises WHERE id = :id") suspend fun deleteRoutineExercise(id: String)
    @Query("DELETE FROM routine_sets WHERE id = :id") suspend fun deleteRoutineSet(id: String)
    @Query("DELETE FROM workouts WHERE id = :id") suspend fun deleteWorkout(id: String)
    @Query("DELETE FROM workout_exercises WHERE id = :id") suspend fun deleteWorkoutExercise(id: String)
    @Query("DELETE FROM workout_sets WHERE id = :id") suspend fun deleteSet(id: String)
    @Query("DELETE FROM workout_sets WHERE workoutExerciseId = :id") suspend fun deleteExerciseSets(id: String)
}

/** Additive migrations preserve routines and all independent workout snapshots. */
@Database(entities = [Exercise::class, RoutineFolder::class, Routine::class, RoutineExercise::class, RoutineSet::class, Workout::class, WorkoutExercise::class, WorkoutSet::class, RoutineTargetCheckpoint::class], version = 5, exportSchema = true)
@TypeConverters(EnumConverters::class)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `routine_target_checkpoints` (`exerciseId` TEXT NOT NULL, `convention` TEXT NOT NULL, `weightKg` REAL, `reps` INTEGER NOT NULL, PRIMARY KEY(`exerciseId`, `convention`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `routine_sets` (`id` TEXT NOT NULL, `routineExerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, `targetReps` INTEGER NOT NULL, `targetWeightKg` REAL, `inheritPr` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`routineExerciseId`) REFERENCES `routine_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_sets_routineExerciseId` ON `routine_sets` (`routineExerciseId`)")
                // Old shared targets are explicit prescriptions; never guess that they were inherited.
                db.query("SELECT id, plannedSets, targetReps, targetWeightKg FROM routine_exercises").use { cursor ->
                    while (cursor.moveToNext()) {
                        repeat(cursor.getInt(1).coerceAtLeast(0)) { position ->
                            db.execSQL("INSERT INTO routine_sets (id, routineExerciseId, position, targetReps, targetWeightKg, inheritPr) VALUES (?, ?, ?, ?, ?, 0)",
                                arrayOf<Any?>(newId(), cursor.getString(0), position, cursor.getInt(2),
                                    if (cursor.isNull(3)) null else cursor.getDouble(3)))
                        }
                    }
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Legacy names are not reliable identities. Leave their source unknown instead of
                // matching routines by name or position, and keep history independent of deletions.
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `sourceRoutineId` TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createDefaultFolder(db)
                // SQLite fills every existing routine with the default without changing its ID,
                // order, targets or history association, and without touching cascading children.
                db.execSQL("ALTER TABLE `routines` ADD COLUMN `folderId` TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routines_folderId` ON `routines` (`folderId`)")
            }
        }

        private fun createDefaultFolder(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `routine_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("INSERT OR IGNORE INTO `routine_folders` (`id`, `name`, `position`) VALUES (?, ?, 0)",
                arrayOf(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME))
        }

        fun open(context: Context): WorkoutDatabase = Room.databaseBuilder(
            context.applicationContext, WorkoutDatabase::class.java, "mooscles.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = createDefaultFolder(db)
            }).build()
    }
}
