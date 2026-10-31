package io.github.yorulmazsinan.kokpit.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

data class Contact(val name: String, val number: String, val starred: Boolean)

/** Contacts with a phone number, from Android's contacts (filled when the phone book is synced). */
fun loadContacts(context: Context): List<Contact> {
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.STARRED,
    )
    return runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < 1000) add(Contact(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getInt(2) == 1))
            }
        }.orEmpty().distinctBy { it.name to it.number.filter(Char::isDigit) }
    }.getOrDefault(emptyList())
}
