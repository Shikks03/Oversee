package com.example.oversee.domain

/**
 * Cross-language false-positive guard for [TextAnalysisEngine].
 *
 * Word lists are loaded from assets at startup via [WordListRepository].
 *  - [COLLISION_FILTER_WORDS] — high-frequency function words never fuzzy-matched.
 *  - [PERSON_TARGETING_PRONOUNS] — pronouns used as proximity amplifiers by the scorer.
 */
object WordFilterLists {

    @Volatile var COLLISION_FILTER_WORDS: Set<String> = emptySet()
        private set

    @Volatile var PERSON_TARGETING_PRONOUNS: Set<String> = emptySet()
        private set

    /** Replaces the active filter sets. Ignored if the two sets overlap. */
    fun update(collisionFilter: Set<String>, pronouns: Set<String>) {
        val overlap = collisionFilter.intersect(pronouns)
        if (overlap.isNotEmpty()) {
            android.util.Log.w("WordFilterLists", "Ignoring update — overlap detected: $overlap")
            return
        }
        COLLISION_FILTER_WORDS = collisionFilter
        PERSON_TARGETING_PRONOUNS = pronouns
    }

    fun isCollisionFilterWord(normalizedToken: String): Boolean =
        normalizedToken in COLLISION_FILTER_WORDS

    fun isPersonTargetingPronoun(normalizedToken: String): Boolean =
        normalizedToken in PERSON_TARGETING_PRONOUNS
}
