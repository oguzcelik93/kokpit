package io.github.yorulmazsinan.kokpit.intro

/** SEASONAL plays the built-in summer or winter clip, whichever fits the weather. */
enum class IntroMode { OFF, SELECTED, SEQUENTIAL, RANDOM, SEASONAL }

data class IntroSettings(
    val mode: IntroMode = IntroMode.SEASONAL,
    val selectedId: String? = null,
    val everyWake: Boolean = true,
    val withSound: Boolean = false,
    val lastPlayedIndex: Int = -1,
)
