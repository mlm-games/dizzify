package app.dizzify.helper

import android.icu.text.Transliterator
import java.text.Normalizer
import java.util.Locale

/**
 * Utilities for building and matching search aliases (transliteration and keyboard layout swap).
 */
object SearchAliasUtils {

    object Mode {
        const val OFF = 0
        const val TRANSLITERATION = 1
        const val KEYBOARD_SWAP = 2
        const val BOTH = 3
    }

    // RU<->EN keyboard layout (ЙЦУКЕН ↔ QWERTY) mapping; includes some punctuation on main rows
    private val en = charArrayOf(
        '`','q','w','e','r','t','y','u','i','o','p','[',']',
        'a','s','d','f','g','h','j','k','l',';','\'',
        'z','x','c','v','b','n','m',',','.'
    )
    private val ru = charArrayOf(
        'ё','й','ц','у','к','е','н','г','ш','щ','з','х','ъ',
        'ф','ы','в','а','п','р','о','л','д','ж','э',
        'я','ч','с','м','и','т','ь','б','ю'
    )
    private val enToRu = en.zip(ru).toMap() + en.map { it.uppercaseChar() }.zip(ru.map { it.uppercaseChar() }).toMap()
    private val ruToEn = ru.zip(en).toMap() + ru.map { it.uppercaseChar() }.zip(en.map { it.uppercaseChar() }).toMap()

    private val NON_ASCII_REGEX = "[^\\p{ASCII}]".toRegex()

    // Cached ICU transliterators — creating via reflection per-app/per-query was a hot-path cost.
    // minSdk 24 ships android.icu, so use it directly.
    private val anyLatinTransliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Any-Latin") }.getOrNull()
    }
    private val latinCyrillicTransliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Latin-Cyrillic") }.getOrNull()
    }

    fun swapKeyboardLayout(text: String, ruToEnDirection: Boolean): String {
        val map = if (ruToEnDirection) ruToEn else enToRu
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(map[ch] ?: ch)
        return sb.toString()
    }

    fun asciiFold(input: String): String {
        val norm = Normalizer.normalize(input, Normalizer.Form.NFD)
        val sb = StringBuilder(norm.length)
        for (c in norm) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) sb.append(c)
        }
        return sb.toString().replace(NON_ASCII_REGEX, "")
    }

    private fun transliterate(transliterator: Transliterator?, text: String): String {
        if (transliterator == null) return text
        return runCatching { transliterator.transliterate(text) }.getOrDefault(text)
    }

    fun anyToLatin(text: String): String {
        // Any script -> Latin -> ASCII-ish
        val toLatin = transliterate(anyLatinTransliterator, text)
        return asciiFold(toLatin)
    }

    fun latinToCyrillic(text: String): String = transliterate(latinCyrillicTransliterator, text)

    fun normalize(s: String): String = s.lowercase(Locale.ROOT).trim()

    /**
     * Build a set of aliases for an app label and optional package name.
     */
    fun buildAppAliases(
        label: String,
        packageName: String?,
        mode: Int,
        includePkg: Boolean
    ): Set<String> {
        val out = LinkedHashSet<String>(8)
        val base = normalize(label)
        out += base
        out += asciiFold(base)

        if (includePkg && !packageName.isNullOrBlank()) {
            val pkgTail = packageName.substringAfterLast('.')
            out += normalize(pkgTail)
            out += asciiFold(pkgTail)
        }

        if (mode == Mode.TRANSLITERATION || mode == Mode.BOTH) {
            val toLatin = normalize(anyToLatin(label))
            val toCyr = normalize(latinToCyrillic(label))
            out += toLatin
            out += asciiFold(toLatin)
            out += toCyr
        }

        if (mode == Mode.KEYBOARD_SWAP || mode == Mode.BOTH) {
            out += normalize(swapKeyboardLayout(label, ruToEnDirection = true))
            out += normalize(swapKeyboardLayout(label, ruToEnDirection = false))
        }

        return out.filter { it.isNotBlank() }.toSet()
    }

    /**
     * Build normalized variants of a user query based on selected mode.
     */
    fun buildQueryVariants(query: String, mode: Int): Set<String> {
        val q = normalize(query)
        val out = LinkedHashSet<String>(8)
        out += q
        out += asciiFold(q)

        if (mode == Mode.TRANSLITERATION || mode == Mode.BOTH) {
            val toLatin = normalize(anyToLatin(query))
            val toCyr = normalize(latinToCyrillic(query))
            out += toLatin
            out += asciiFold(toLatin)
            out += toCyr
        }

        if (mode == Mode.KEYBOARD_SWAP || mode == Mode.BOTH) {
            out += normalize(swapKeyboardLayout(query, ruToEnDirection = true))
            out += normalize(swapKeyboardLayout(query, ruToEnDirection = false))
        }

        return out.filter { it.isNotBlank() }.toSet()
    }
}