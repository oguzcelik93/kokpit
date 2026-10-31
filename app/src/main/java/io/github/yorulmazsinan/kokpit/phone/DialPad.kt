package io.github.yorulmazsinan.kokpit.phone

private const val MAX_DIAL_LENGTH = 20
private const val DIAL_CHARS = "0123456789*#+"

fun dialAppend(current: String, key: Char): String =
    if (key in DIAL_CHARS && current.length < MAX_DIAL_LENGTH) current + key else current

fun dialBackspace(current: String): String = current.dropLast(1)
