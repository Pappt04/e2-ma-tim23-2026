package uns.ac.rs.team23.slagalica.utils

import java.text.Normalizer

/**
 * Normalizes a quiz answer so guesses match regardless of case, surrounding/duplicate
 * whitespace, Latin diacritics (č/ć/ž/š/đ → c/c/z/s/dj), or Serbian Cyrillic script.
 *
 * Used to compare a typed guess against the stored answer in the games (Asocijacije,
 * and reusable by any other answer-checking game).
 */
fun normalizeAnswer(input: String): String {
    if (input.isEmpty()) return ""
    // 1) Transliterate Serbian Cyrillic → Latin and fold Latin stroke letters (đ/Đ) that NFD
    //    does not decompose, so ђ/đ → dj survive the diacritic-stripping step below.
    val latin = buildString(input.length) {
        for (ch in input) append(preFold[ch] ?: ch.toString())
    }
    // 2) Strip combining diacritics via NFD decomposition.
    val decomposed = Normalizer.normalize(latin, Normalizer.Form.NFD)
    val stripped = decomposed.replace(COMBINING_MARKS, "")
    // 3) Uppercase, trim, and collapse internal whitespace.
    return stripped
        .uppercase()
        .trim()
        .replace(WHITESPACE, " ")
}

private val COMBINING_MARKS = Regex("\\p{M}+")
private val WHITESPACE = Regex("\\s+")

/**
 * Characters folded before NFD decomposition: Serbian Cyrillic → Latin (both cases), plus the
 * Latin stroke letters đ/Đ which NFD leaves intact. Digraphs (Lj, Nj, Dž) map to their multi-char
 * Latin forms; ђ/Ђ and џ/Џ map to dj/DJ and dz/DZ.
 */
private val preFold: Map<Char, String> = mapOf(
    'đ' to "dj", 'Đ' to "DJ",
) + mapOf<Char, String>(
    'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Ђ' to "DJ", 'Е' to "E",
    'Ж' to "Z", 'З' to "Z", 'И' to "I", 'Ј' to "J", 'К' to "K", 'Л' to "L", 'Љ' to "LJ",
    'М' to "M", 'Н' to "N", 'Њ' to "NJ", 'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S",
    'Т' to "T", 'Ћ' to "C", 'У' to "U", 'Ф' to "F", 'Х' to "H", 'Ц' to "C", 'Ч' to "C",
    'Џ' to "DZ", 'Ш' to "S",
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "dj", 'е' to "e",
    'ж' to "z", 'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj",
    'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s",
    'т' to "t", 'ћ' to "c", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "c",
    'џ' to "dz", 'ш' to "s",
)
