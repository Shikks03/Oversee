package com.example.oversee.domain

/**
 * Cross-language false-positive guard for [TextAnalysisEngine].
 *
 * The Levenshtein matcher in [TextAnalysisEngine] occasionally lights up on
 * benign Tagalog/English function words because their edit distance to a
 * flagged word is small enough to clear the threshold. This object holds the
 * two allowlists used to short-circuit those collisions before fuzzy matching
 * runs.
 *
 *  - [COLLISION_FILTER_WORDS] — high-frequency function words (articles,
 *    conjunctions, prepositions, particles, modals, common adjectives/verbs,
 *    written numbers, fillers). Tokens in this set are NEVER fuzzy-matched.
 *  - [PERSON_TARGETING_PRONOUNS] — second/third-person pronouns the
 *    rule-based scorer uses as proximity amplifiers. Excluded from fuzzy
 *    matching defensively (they shouldn't ever be flagged) but kept available
 *    to scoring code via the helper below.
 *
 * Invariant (asserted in [init]): the two sets are mutually exclusive.
 */
object WordFilterLists {

    val COLLISION_FILTER_WORDS: Set<String> = setOf(
        // === TAGALOG FUNCTION WORDS ===

        // Articles / Markers (linkers, case markers)
        "ang", "ng", "mga", "si", "ni", "kay", "kina",

        // Conjunctions
        "at", "o", "pero", "ngunit", "subalit", "kundi",
        "dahil", "kaya", "kung", "kapag", "pag", "habang",
        "samantalang", "gayunman", "bagaman", "kahit", "bagamat",

        // Prepositions
        "sa", "para", "tungkol", "ukol", "ayon", "mula",
        "hanggang", "laban", "kasama", "katulad", "tulad",
        "gaya", "bukod",

        // Particles / Enclitic markers
        "ba", "nga", "raw", "daw", "pa", "na", "din", "rin",
        "lang", "lamang", "man", "po", "ho", "yata",
        "pala", "naman", "muna", "talaga", "halos",

        // Existential / Copula
        "ay", "may", "mayroon", "wala",

        // Affirmation / Negation (non-pronoun)
        "oo", "hindi", "opo", "hinde", "huwag",

        // Modal / Auxiliary
        "gusto", "ibig", "nais", "pwede", "maaari", "maaaring",
        "kailangan", "dapat", "puwede",

        // Question words (non-pronoun)
        "ano", "saan", "kailan", "bakit", "paano",
        "magkano", "ilang", "alin",
        "kanino", "sino", "nasaan",

        // Time words
        "ngayon", "bukas", "kahapon", "mamaya", "kanina",
        "palagi", "lagi", "madalas", "minsan", "dati",
        "noon", "taon", "araw", "gabi", "umaga", "hapon",
        "linggo", "buwan",

        // Written numbers / Ordinals (Tagalog)
        "isa", "isang", "dalawa", "tatlo", "apat", "lima",
        "anim", "pito", "walo", "siyam", "sampu",
        "una", "pangalawa",

        // 1st-person pronouns / possessives (NOT person-targeting — kept here
        // because they don't direct hostility at someone else)
        "ako", "ko", "kong", "akin", "aking",
        "kami", "namin", "amin", "aming",
        "tayo", "atin", "ating",

        // Demonstratives
        "ito", "iyon", "nito",

        // Locatives / direction words
        "dito", "doon", "narito",
        "ibaba", "ibabaw", "ilalim", "itaas", "likod",
        "pababa", "pataas", "palabas", "pagitan",

        // 2nd/3rd-person pronoun linker forms — parked in COLLISION_FILTER
        // for benign filtering. If the scorer ever needs to amplify on these
        // (e.g. "kanyang ina" → his/her mother near a slur), promote them to
        // PERSON_TARGETING_PRONOUNS and drop them from this list.
        "inyong", "iyong",
        "kanilang", "kanyang", "nilang", "niyang",

        // High-frequency non-toxic verbs (Tagalog)
        "bababa", "gagawin", "ginagawa", "ginawa", "ginawang", "gumawa",
        "ilagay", "kumuha", "maging", "mahusay", "makita",
        "nabanggit", "naging", "nagkaroon", "nakita",
        "paggawa", "pagkakaroon", "pagkatapos",
        "pumunta", "pumupunta", "sabi", "sabihin",

        // Common high-frequency non-toxic adjectives / adverbs
        "mabuti", "masama", "malaki", "maliit", "bago",
        "luma", "maganda", "mahal", "mura", "mabilis",
        "mabagal", "marami", "kaunti", "lahat", "ilan",
        "ibang", "iba", "pareho", "mismo", "ganito",
        "ganoon", "ganyan",

        // Misc function words / quantifiers / fillers (Tagalog)
        "anumang", "bawat", "bilang", "kailanman",
        "kapwa", "karamihan", "katiyakan", "kaysa",
        "kulang", "marapat", "masyado", "muli",
        "napaka", "panahon", "pamamagitan",
        "paraan", "sarili", "walang",

        // Common greetings / fillers
        "sige", "ayos", "salamat", "pasensya", "paumanhin",
        "hala", "naku", "grabe",

        // === ENGLISH FUNCTION WORDS ===

        // Articles
        "a", "an", "the",

        // Conjunctions
        "and", "but", "or", "nor", "for", "yet", "so",
        "although", "because", "since", "unless", "until",
        "while", "after", "before", "when", "where",
        "whether", "though", "even", "if",

        // Prepositions
        "in", "on", "at", "by", "with", "about", "against",
        "between", "through", "during", "above", "below",
        "to", "from", "up", "down", "out", "off", "over",
        "under", "into", "onto", "upon", "within", "without",
        "along", "across", "behind", "beyond", "near",
        "around", "among", "of",

        // Auxiliary / Modal verbs
        "is", "am", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "having",
        "do", "does", "did", "doing",
        "will", "would", "shall", "should",
        "may", "might", "must", "can", "could",

        // Common adverbs (non-pronoun)
        "not", "no", "never", "always", "often", "sometimes",
        "usually", "already", "still", "just", "only", "also",
        "too", "very", "quite", "rather", "much", "more",
        "most", "less", "least", "enough", "almost", "nearly",
        "here", "there", "now", "then", "today", "yesterday",
        "tomorrow", "soon", "later", "early", "late",
        "again", "once", "twice", "ever", "perhaps", "maybe",
        "away", "back", "forward", "together", "apart",

        // Question words (non-pronoun)
        "what", "when", "where", "why", "how", "which", "whose",

        // Common non-toxic adjectives / determiners
        "good", "bad", "big", "small", "new", "old",
        "first", "last", "long", "great", "little",
        "own", "right", "high", "low", "next",
        "young", "important", "public", "private", "real",
        "best", "free", "same", "different", "other",
        "another", "each", "every", "both", "few", "many",
        "some", "any", "all", "whole", "such",

        // Common high-frequency non-toxic verbs
        "go", "get", "make", "know", "think", "take",
        "see", "come", "want", "look", "use", "find",
        "give", "tell", "work", "call", "try", "ask",
        "need", "feel", "become", "leave", "put", "mean",
        "keep", "let", "begin", "show", "hear", "play",
        "run", "move", "live", "believe", "hold", "bring",
        "happen", "write", "provide", "sit", "stand",
        "lose", "pay", "meet", "include", "continue",
        "set", "learn", "change", "lead", "understand",
        "watch", "follow", "stop", "create", "speak",
        "read", "spend", "grow", "open", "walk", "offer",
        "remember", "love", "consider", "appear", "buy",
        "wait", "serve", "send", "expect", "build",
        "stay", "fall", "cut", "reach", "raise",

        // Written numbers (English)
        "one", "two", "three", "four", "five",
        "six", "seven", "eight", "nine", "ten",

        // Greetings / Common fillers
        "hi", "hello", "hey", "yes", "ok", "okay",
        "thanks", "thank", "please", "sorry", "welcome"
    )

    val PERSON_TARGETING_PRONOUNS: Set<String> = setOf(
        // === TAGALOG — Second person (direct targeting) ===
        "ikaw", "ka", "mo", "iyo", "sayo", "kayo", "inyo",

        // === TAGALOG — Third person (talking about someone) ===
        "siya", "niya", "kanya", "sila", "nila", "kanila",

        // === ENGLISH — Second person ===
        "you", "your", "yours", "yourself", "yourselves",
        "ur", // common informal shorthand

        // === ENGLISH — Third person ===
        "he", "him", "his",
        "she", "her", "hers",
        "they", "them", "their"
    )

    init {
        val overlap = COLLISION_FILTER_WORDS.intersect(PERSON_TARGETING_PRONOUNS)
        check(overlap.isEmpty()) {
            "DESIGN ERROR: the following words appear in both COLLISION_FILTER_WORDS " +
                "and PERSON_TARGETING_PRONOUNS: $overlap"
        }
    }

    fun isCollisionFilterWord(normalizedToken: String): Boolean =
        normalizedToken in COLLISION_FILTER_WORDS

    fun isPersonTargetingPronoun(normalizedToken: String): Boolean =
        normalizedToken in PERSON_TARGETING_PRONOUNS
}
