package com.mooscles.data

/** Shared offline search for the library and picker; synonyms never create exercise identities. */
object ExerciseSearch {
    // Internal so ExerciseSearchIndex reads the same English aliases and folding; no behavior change.
    internal val aliases = mapOf(
        "catalog_selectorized_shoulder_press" to listOf(
            "Selectorized shoulder press", "Seated machine shoulder press", "Machine overhead press"
        ),
        "catalog_plate_shoulder_press" to listOf("Plate-loaded seated overhead press", "Plate-loaded machine shoulder press"),
        "catalog_smith_shoulder_press" to listOf("Smith machine seated overhead press"),
        "catalog_barbell_overhead_press" to listOf("Military press", "Barbell strict press", "Standing barbell shoulder press", "Barbell OHP"),
        "catalog_barbell_seated_press" to listOf("Seated barbell overhead press"),
        "catalog_dumbbell_press_seated" to listOf("Seated dumbbell overhead press"),
        "catalog_dumbbell_press_standing" to listOf("Standing dumbbell overhead press"),
        "catalog_cable_shoulder_press" to listOf("Seated cable overhead press"),
        "catalog_selectorized_pec_fly" to listOf("Chest fly pec deck", "Machine chest fly", "Butterfly machine", "Pectoral fly"),
        "catalog_selectorized_reverse_fly" to listOf("Rear delt fly machine", "Rear deltoid fly", "Reverse pec deck"),
        "catalog_cable_chest_fly_mid" to listOf("Cable crossover", "Standing cable fly"),
        "catalog_cable_curl_behind_body" to listOf("Bayesian cable curl"),
        "catalog_cable_triceps_pushdown_bar" to listOf("Straight-bar triceps pressdown"),
        "catalog_cable_triceps_pushdown_rope" to listOf("Rope triceps pressdown"),
        "catalog_cable_triceps_pushdown_reverse" to listOf("Reverse-grip triceps pressdown"),
        "catalog_barbell_triceps_lying" to listOf("Barbell skull crusher"),
        "catalog_dumbbell_triceps_lying" to listOf("Dumbbell skull crusher"),
        "catalog_ez_bar_triceps_lying" to listOf("EZ-bar skull crusher"),
        "catalog_ez_bar_curl" to listOf("EZ curl bar biceps curl"),
        "catalog_ez_bar_curl_preacher" to listOf("EZ curl bar preacher curl"),
        "catalog_barbell_squat_bulgarian" to listOf("Rear-foot elevated barbell split squat"),
        "catalog_dumbbell_squat_bulgarian" to listOf("Rear-foot elevated dumbbell split squat"),
        "catalog_bodyweight_bulgarian_split_squat" to listOf("Rear-foot elevated bodyweight split squat"),
        "catalog_trap_bar_deadlift" to listOf("Hex-bar deadlift"),
        "catalog_barbell_deadlift_romanian" to listOf("Barbell RDL"),
        "catalog_dumbbell_deadlift_romanian" to listOf("Dumbbell RDL"),
        "catalog_smith_deadlift_romanian" to listOf("Smith machine RDL"),
        "catalog_kettlebell_deadlift_romanian" to listOf("Kettlebell RDL"),
        "catalog_cable_row_close_neutral" to listOf("Seated cable row close grip", "Seated cable row V bar"),
        "catalog_bodyweight_pull_up" to listOf("Pull-up", "Pronated pull-up"),
        "catalog_bodyweight_chin_up" to listOf("Underhand pull-up", "Supinated pull-up"),
        "catalog_bodyweight_dip_chest" to listOf("Parallel-bar chest dip"),
        "catalog_bodyweight_row_inverted" to listOf("Australian pull-up", "Bodyweight row"),
        "catalog_plate_hip_thrust" to listOf("Glute drive machine", "Plate-loaded glute drive")
    )

    // Catalog id -> English name; also read by ExerciseNames and ExerciseSearchIndex.
    internal val catalogNames: Map<String, String> by lazy { ExerciseCatalog.exercises.associate { it.id to it.name } }
    private val equivalents = listOf(
        Regex("\\bdb\\b") to "dumbbell",
        Regex("\\bbb\\b") to "barbell",
        Regex("\\bkb\\b") to "kettlebell",
        Regex("\\bflye?s?\\b|\\bflies\\b") to "fly",
        Regex("\\bpecdeck\\b") to "pec deck",
        Regex("\\bpullups?\\b|\\bpull ups\\b") to "pull up",
        Regex("\\bchinups?\\b|\\bchin ups\\b") to "chin up",
        Regex("\\bpushups?\\b|\\bpush ups\\b") to "push up",
        Regex("\\bpull down\\b") to "pulldown",
        Regex("\\bskullcrushers?\\b|\\bskull crushers\\b") to "skull crusher",
        Regex("\\bsingle arm\\b") to "one arm",
        Regex("\\bsingle leg\\b") to "one leg"
    )

    fun matches(exercise: Exercise, query: String): Boolean = matches(
        exercise.id, exercise.name, exercise.muscle.label, exercise.equipment.label, query
    )

    fun matches(id: String, name: String, muscle: String, equipment: String, query: String): Boolean {
        val terms = normalize(query).split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return true
        val metadata = "$muscle $equipment"
        val names = listOfNotNull(name, catalogNames[id]) + aliases[id].orEmpty()
        // All query words must describe one actual name/alias, in any order. Metadata remains
        // searchable without combining contradictory words from separate synonym phrases.
        return names.any { candidate ->
            val searchable = normalize("$candidate $metadata")
            terms.all { searchable.contains(it) }
        }
    }

    internal fun normalize(value: String): String {
        var normalized = SearchFolding.fold(value)
        equivalents.forEach { (pattern, replacement) -> normalized = pattern.replace(normalized, replacement) }
        return normalized
    }
}
