package com.music.aalap.di

import kotlinx.coroutines.flow.MutableStateFlow

/** What the voice overlay shows: listening, thinking, what it found, or what went wrong. */
object VoiceUi {
    sealed class State {
        data class Listening(val heard: String = "", val level: Float = 0f) : State()
        data class Thinking(val heard: String) : State()
        data class Found(val title: String, val subtitle: String, val cover: String) : State()
        data class Failed(val message: String) : State()
    }

    val state = MutableStateFlow<State?>(null)

    /** The bar is showing in its own window over everything ("Hey Aalap"), so Aalap's copy stays hidden. */
    val overApps = MutableStateFlow(false)
}
