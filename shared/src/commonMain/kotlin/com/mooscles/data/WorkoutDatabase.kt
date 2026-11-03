package com.mooscles.data

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.immediateTransaction
import androidx.room.migration.Migration
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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
    // Backup import: the default ABORT strategy makes an unexpected id collision undo the whole import.
    @Insert suspend fun insertRoutineFolders(items: List<RoutineFolder>)
    @Insert suspend fun insertExercises(items: List<Exercise>)
    @Insert suspend fun insertRoutines(items: List<Routine>)
    @Insert suspend fun insertRoutineExercises(items: List<RoutineExercise>)
    @Insert suspend fun insertWorkouts(items: List<Workout>)
    @Insert suspend fun insertTargetCheckpoints(items: List<RoutineTargetCheckpoint>)
    @Query("DELETE FROM routine_folders WHERE id = :id") suspend fun deleteRoutineFolder(id: String)
    @Query("DELETE FROM routines WHERE id = :id") suspend fun deleteRoutine(id: String)
    @Query("DELETE FROM routine_exercises WHERE id = :id") suspend fun deleteRoutineExercise(id: String)
    @Query("DELETE FROM routine_sets WHERE id = :id") suspend fun deleteRoutineSet(id: String)
    @Query("DELETE FROM workouts WHERE id = :id") suspend fun deleteWorkout(id: String)
    @Query("DELETE FROM workout_exercises WHERE id = :id") suspend fun deleteWorkoutExercise(id: String)
    @Query("DELETE FROM workout_sets WHERE id = :id") suspend fun deleteSet(id: String)
    @Query("DELETE FROM workout_sets WHERE workoutExerciseId = :id") suspend fun deleteExerciseSets(id: String)
}

/** Additive migrations preserve routines and all independent workout snapshots. */
@Database(entities = [Exercise::class, RoutineFolder::class, Routine::class, RoutineExercise::class, RoutineSet::class, Workout::class, WorkoutExercise::class, WorkoutSet::class, RoutineTargetCheckpoint::class], version = 6, exportSchema = true)
@TypeConverters(EnumConverters::class)
@ConstructedBy(WorkoutDatabaseConstructor::class)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao

    companion object {
        /** The file every installed version has used, in the platform's databases directory. */
        const val FILE_NAME = "mooscles.db"

        // Each migration runs the same SQL, in the same order, as the SupportSQLite migrations
        // shipped through v1.4.5; MigrationEquivalenceTest compares the result with theirs.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS `routine_target_checkpoints` (`exerciseId` TEXT NOT NULL, `convention` TEXT NOT NULL, `weightKg` REAL, `reps` INTEGER NOT NULL, PRIMARY KEY(`exerciseId`, `convention`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS `routine_sets` (`id` TEXT NOT NULL, `routineExerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, `targetReps` INTEGER NOT NULL, `targetWeightKg` REAL, `inheritPr` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`routineExerciseId`) REFERENCES `routine_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_sets_routineExerciseId` ON `routine_sets` (`routineExerciseId`)")
                // Old shared targets are explicit prescriptions; never guess that they were inherited.
                // Read every plan first (same order), then insert, so no statement is stepped mid-write.
                val plans = connection.prepare("SELECT id, plannedSets, targetReps, targetWeightKg FROM routine_exercises").use { query ->
                    buildList {
                        while (query.step()) add(LegacyPlan(query.getText(0), query.getInt(1), query.getInt(2),
                            if (query.isNull(3)) null else query.getDouble(3)))
                    }
                }
                connection.prepare("INSERT INTO routine_sets (id, routineExerciseId, position, targetReps, targetWeightKg, inheritPr) VALUES (?, ?, ?, ?, ?, 0)").use { insert ->
                    for (plan in plans) repeat(plan.plannedSets.coerceAtLeast(0)) { position ->
                        insert.bindText(1, newId())
                        insert.bindText(2, plan.id)
                        insert.bindLong(3, position.toLong())
                        insert.bindLong(4, plan.targetReps.toLong())
                        plan.targetWeightKg?.let { insert.bindDouble(5, it) } ?: insert.bindNull(5)
                        insert.step()
                        insert.reset()
                        insert.clearBindings()
                    }
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                // Legacy names are not reliable identities. Leave their source unknown instead of
                // matching routines by name or position, and keep history independent of deletions.
                connection.execSQL("ALTER TABLE `workouts` ADD COLUMN `sourceRoutineId` TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                createDefaultFolder(connection)
                // SQLite fills every existing routine with the default without changing its ID,
                // order, targets or history association, and without touching cascading children.
                connection.execSQL("ALTER TABLE `routines` ADD COLUMN `folderId` TEXT NOT NULL DEFAULT 'default'")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_routines_folderId` ON `routines` (`folderId`)")
            }
        }

        /**
         * Planned warm-ups. Every existing planned set becomes a working set (0) without changing
         * its ID, values or order. SQLite writes the column after `inheritPr`, before the table
         * constraints, which is where Room's CREATE puts it on a new install.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `routine_sets` ADD COLUMN `warmup` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private class LegacyPlan(val id: String, val plannedSets: Int, val targetReps: Int, val targetWeightKg: Double?)

        internal fun createDefaultFolder(connection: SQLiteConnection) {
            connection.execSQL("CREATE TABLE IF NOT EXISTS `routine_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            connection.prepare("INSERT OR IGNORE INTO `routine_folders` (`id`, `name`, `position`) VALUES (?, ?, 0)").use { insert ->
                insert.bindText(1, RoutineFolder.DEFAULT_ID)
                insert.bindText(2, RoutineFolder.DEFAULT_NAME)
                insert.step()
            }
        }
    }
}

/** Generated by Room for each platform (KSP per target); it instantiates `WorkoutDatabase_Impl`. */
@Suppress("KotlinNoActualForExpect")
expect object WorkoutDatabaseConstructor : RoomDatabaseConstructor<WorkoutDatabase> {
    override fun initialize(): WorkoutDatabase
}

/**
 * Every platform opens the database through this: [driver], every migration and the creation
 * callback, as `WorkoutDatabase.open` applied them before the move. No query coroutine context is
 * set, so Android keeps Room's default executors.
 */
fun RoomDatabase.Builder<WorkoutDatabase>.buildMooscles(driver: SQLiteDriver): WorkoutDatabase =
    setDriver(driver)
        .addMigrations(WorkoutDatabase.MIGRATION_1_2, WorkoutDatabase.MIGRATION_2_3, WorkoutDatabase.MIGRATION_3_4,
            WorkoutDatabase.MIGRATION_4_5, WorkoutDatabase.MIGRATION_5_6)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) = WorkoutDatabase.createDefaultFolder(connection)
        }).build()

/**
 * All or nothing on Room's single writer connection, as `withTransaction` was before the driver
 * (which has no SupportSQLite helper for it). Every DAO call in [block] runs on that connection,
 * so reads see the transaction's own writes and an exception anywhere rolls everything back.
 *
 * A call made inside another one for the same database runs [block] directly in the enclosing
 * transaction. It must not call `useWriterConnection` again: Room refreshes the invalidation
 * tracker after every such call, and each refresh would park a Room IO thread on the connection
 * pool until the outer commit. Like before, the outer commit is the only refresh.
 *
 * As with the framework's nested transactions, a failed nested call poisons the enclosing
 * transaction: even if its caller catches the exception and returns normally, the top-level call
 * throws instead of committing, so its writes and the nested writes are all rolled back.
 */
suspend fun <R> WorkoutDatabase.writeTransaction(block: suspend () -> R): R {
    val enclosing = currentCoroutineContext()[EnclosingWriteTransaction]
    if (enclosing?.database === this) {
        return try {
            block()
        } catch (failure: Throwable) {
            enclosing.markFailed(failure)
            throw failure
        }
    }
    val transaction = EnclosingWriteTransaction(this)
    return useWriterConnection { connection ->
        connection.immediateTransaction {
            val result = withContext(transaction) { block() }
            transaction.failure?.let { nested ->
                throw IllegalStateException("A nested write transaction failed, so the enclosing one is rolled back", nested)
            }
            result
        }
    }
}

/** Marks the coroutine running the block of a top-level [writeTransaction] on [database]. */
@OptIn(ExperimentalAtomicApi::class)
private class EnclosingWriteTransaction(val database: WorkoutDatabase) :
    AbstractCoroutineContextElement(EnclosingWriteTransaction) {
    companion object Key : CoroutineContext.Key<EnclosingWriteTransaction>

    private val firstFailure = AtomicReference<Throwable?>(null)

    /** The first nested failure; once set, the transaction can only roll back. */
    val failure: Throwable? get() = firstFailure.load()

    /** The first failure wins: later ones never replace it. */
    fun markFailed(nested: Throwable) {
        firstFailure.compareAndSet(null, nested)
    }
}
