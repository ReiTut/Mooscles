package com.mooscles.backup

/** User-facing backup copy. Singular only for exactly one; zero stays plural. */
object BackupTexts {
    fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

    fun counts(workouts: Int, routines: Int, customExercises: Int) =
        "${count(workouts, "workout")}, ${count(routines, "routine")} and ${count(customExercises, "custom exercise")}"

    fun exported(workouts: Int, routines: Int, customExercises: Int) = "Exported ${counts(workouts, routines, customExercises)}"

    fun imported(summary: ImportSummary) =
        "Imported ${counts(summary.workoutsAdded, summary.routinesAdded, summary.customExercisesAdded)}"

    /** Body of the import confirmation dialog. */
    fun summary(summary: ImportSummary): String = buildString {
        append("Add ${counts(summary.workoutsAdded, summary.routinesAdded, summary.customExercisesAdded)}.")
        if (summary.workoutsKept > 0) {
            val kept = summary.workoutsKept
            append(
                if (kept == 1) " 1 workout is already on this phone and will be kept as it is."
                else " $kept workouts are already on this phone and will be kept as they are.",
            )
        }
        if (summary.restoresPreferences) append(" Your units, theme and rest settings will also be restored.")
    }

    /** Shown instead of a raw I/O message when the document cannot be written or read. */
    const val EXPORT_FILE_FAILED = "Couldn't save the backup. Try another location."
    const val IMPORT_FILE_FAILED = "Couldn't open the file. Try again or pick another file."

    const val NOTHING_NEW = "Everything in this backup is already on this phone."
    const val WORKING_EXPORT = "Exporting…"
    const val WORKING_CHECK = "Checking backup…"
    const val WORKING_IMPORT = "Importing…"

    /** True when importing would change nothing, so the dialog offers only OK. The planner decides. */
    fun isNothingNew(summary: ImportSummary) = summary.nothingToAdd
}
