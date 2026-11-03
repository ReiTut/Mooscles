package com.mooscles.data

/** One exercise as the search sees it: the stored name and the English labels of `ExerciseUi`. */
data class SearchEntry(val id: String, val name: String, val muscle: String, val equipment: String)

/**
 * The bilingual exercise search of the new screens (spec D13). [ExerciseSearch.matches] stays as it
 * is for the older screens; this index finds everything it finds, and more.
 *
 * Built once per exercise list (off the main thread), it keeps for each entry its candidates,
 * already folded by [ExerciseSearch.normalize]: the stored name, the catalog's English name, the
 * Portuguese name, the English and the Portuguese aliases, each followed by the muscle and the
 * equipment in English (the labels) and in Portuguese ([SearchTerms]). A query matches when all of
 * its words are in one candidate, in any order, so words of two different aliases never combine.
 * Per keystroke only the query is folded.
 */
class ExerciseSearchIndex private constructor(
    private val ids: List<String>,
    private val candidates: List<List<String>>,
) {
    // Only [matches] reads it (the screens use [matching]): built on its first use, not with the index.
    private val byId: Map<String, List<String>> by lazy {
        buildMap { ids.forEachIndexed { index, id -> put(id, getOrElse(id) { emptyList() } + candidates[index]) } }
    }

    /** The ids that match [query], in the order of the entries; a blank query matches them all. */
    fun matching(query: String): List<String> {
        val terms = terms(query)
        if (terms.isEmpty()) return ids
        return ids.filterIndexed { index, _ -> matchesAny(candidates[index], terms) }
    }

    /** Whether the entry [id] matches [query]; an id the index does not hold never matches. */
    fun matches(id: String, query: String): Boolean {
        val own = byId[id] ?: return false
        val terms = terms(query)
        return terms.isEmpty() || matchesAny(own, terms)
    }

    private fun terms(query: String): List<String> =
        ExerciseSearch.normalize(query).split(' ').filter(String::isNotBlank)

    private fun matchesAny(own: List<String>, terms: List<String>): Boolean =
        own.any { candidate -> terms.all { candidate.contains(it) } }

    companion object {
        fun build(entries: List<SearchEntry>): ExerciseSearchIndex =
            ExerciseSearchIndex(entries.map(SearchEntry::id), entries.map(::candidatesOf))

        private fun candidatesOf(entry: SearchEntry): List<String> {
            val portugueseName = if (entry.id.startsWith(ExerciseCatalog.ID_PREFIX)) {
                ExerciseNames.portuguese[entry.id.removePrefix(ExerciseCatalog.ID_PREFIX)]
            } else {
                null
            }
            val names = listOfNotNull(entry.name, ExerciseSearch.catalogNames[entry.id], portugueseName) +
                ExerciseSearch.aliases[entry.id].orEmpty() +
                ExerciseAliases.portuguese[entry.id].orEmpty()
            val metadata = (listOf(entry.muscle, entry.equipment) + portugueseMetadata(entry)).joinToString(" ")
            return names.distinct().map { ExerciseSearch.normalize("$it $metadata") }.distinct()
        }

        // The labels come from the enums (ExerciseUi carries the label); enum names are accepted too.
        private fun portugueseMetadata(entry: SearchEntry): List<String> {
            val muscle = MuscleGroup.fromLabelOrName(entry.muscle)
            val equipment = Equipment.fromLabelOrName(entry.equipment)
            return muscle?.let(SearchTerms::muscle).orEmpty() + equipment?.let(SearchTerms::equipment).orEmpty()
        }
    }
}
