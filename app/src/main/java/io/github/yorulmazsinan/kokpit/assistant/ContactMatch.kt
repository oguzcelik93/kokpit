package io.github.yorulmazsinan.kokpit.assistant

import io.github.yorulmazsinan.kokpit.phone.Contact
import java.util.Locale

private val TURKISH = Locale.forLanguageTag("tr-TR")

/** Lower case without Turkish accents, so "Ayşe", "ayse" and "AYŞE" all match. */
fun foldTurkish(text: String): String = text.lowercase(TURKISH)
    .map { c ->
        when (c) {
            'ı' -> 'i'
            'ş' -> 's'
            'ğ' -> 'g'
            'ü' -> 'u'
            'ö' -> 'o'
            'ç' -> 'c'
            'â' -> 'a'
            'î' -> 'i'
            'û' -> 'u'
            else -> c
        }
    }
    .joinToString("")
    .replace(Regex("[^a-z0-9 ]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

/**
 * Contacts for a spoken name, best first: an exact name wins outright; otherwise names that
 * contain it, then names whose words all start with the spoken words ("ali yil" → "Ali Yılmaz").
 * One entry per person (the first number).
 */
fun matchContacts(query: String, contacts: List<Contact>): List<Contact> {
    val q = foldTurkish(query)
    if (q.isEmpty()) return emptyList()
    val people = contacts.distinctBy { foldTurkish(it.name) }
    people.filter { foldTurkish(it.name) == q }.takeIf { it.isNotEmpty() }?.let { return it }
    val words = q.split(' ')
    return people.filter { contact ->
        val name = foldTurkish(contact.name)
        val nameWords = name.split(' ')
        name.contains(q) || words.all { w -> nameWords.any { it.startsWith(w) } }
    }.sortedWith(compareByDescending<Contact> { it.starred }.thenBy { it.name.length })
}

/** A spoken phone number ("0532 111 22 33", "+90 532…"), or null when it is a name. */
fun spokenNumber(query: String): String? {
    val digits = query.filter { it.isDigit() || it == '+' }
    return digits.takeIf { it.count(Char::isDigit) >= 3 && query.count(Char::isLetter) == 0 }
}
