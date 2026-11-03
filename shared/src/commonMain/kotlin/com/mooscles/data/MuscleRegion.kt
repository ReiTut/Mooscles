package com.mooscles.data

/**
 * The 17 regions of the body map, in alphabetical order. Each region is one highlightable area
 * of the map (front and back views); see the phase 4A spike report for how they group the source
 * paths. Notably, MIDDLE_BACK covers the area over the shoulder blade, while the band between the
 * shoulder blades belongs to TRAPS, so rowing movements list both.
 */
enum class MuscleRegion {
    ABDOMINALS, ABDUCTORS, ADDUCTORS, BICEPS, CALVES, CHEST, FOREARMS, GLUTES, HAMSTRINGS,
    LATS, LOWER_BACK, MIDDLE_BACK, NECK, QUADRICEPS, SHOULDERS, TRAPS, TRICEPS
}
