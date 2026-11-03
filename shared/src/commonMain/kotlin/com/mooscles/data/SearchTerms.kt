package com.mooscles.data

/**
 * Brazilian Portuguese search words for the muscle group and the equipment, so the search finds an
 * exercise by its metadata in both languages (spec D13; the English words are the enum labels).
 * Each list holds the label the screens show (`values-pt-rBR/strings_catalog.xml` in :screens,
 * checked by its SearchTermsDriftTest) plus the other words gyms use for it.
 */
object SearchTerms {
    fun muscle(group: MuscleGroup): List<String> = when (group) {
        MuscleGroup.CHEST -> listOf("Peito", "Peitoral")
        MuscleGroup.BACK -> listOf("Costas")
        MuscleGroup.SHOULDERS -> listOf("Ombro", "Ombros", "Deltoide")
        MuscleGroup.BICEPS -> listOf("Bíceps")
        MuscleGroup.TRICEPS -> listOf("Tríceps")
        MuscleGroup.QUADRICEPS -> listOf("Quadríceps", "Coxa")
        MuscleGroup.HAMSTRINGS -> listOf("Posterior", "Posteriores de coxa")
        MuscleGroup.GLUTES -> listOf("Glúteo", "Glúteos")
        MuscleGroup.CALVES -> listOf("Panturrilha", "Panturrilhas")
        MuscleGroup.CORE -> listOf("Abdômen", "Abdominal")
        MuscleGroup.FOREARMS -> listOf("Antebraço", "Antebraços")
        MuscleGroup.FULL_BODY -> listOf("Corpo inteiro")
    }

    fun equipment(equipment: Equipment): List<String> = when (equipment) {
        Equipment.BARBELL -> listOf("Barra")
        Equipment.DUMBBELL -> listOf("Halter", "Halteres")
        Equipment.KETTLEBELL -> listOf("Kettlebell")
        Equipment.CABLE -> listOf("Polia", "Cabo")
        Equipment.BODYWEIGHT -> listOf("Peso corporal")
        Equipment.SMITH_MACHINE -> listOf("Smith")
        Equipment.SELECTORIZED_MACHINE -> listOf("Máquina de placas", "Máquina")
        Equipment.PLATE_LOADED_MACHINE -> listOf("Máquina de anilhas", "Máquina")
        Equipment.OTHER -> listOf("Outro")
    }
}
