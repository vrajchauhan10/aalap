package com.music.aalap.di

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The voice button ("Ask Aalap"): the mic on Home, the "Ask Aalap" shortcut on the app
 * icon, and the quick-settings tile all call [start]; the open screen listens with the
 * phone's speech recogniser and hands the words to [VoicePlay].
 */
object VoiceAsk {
    const val ACTION = "com.music.aalap.VOICE"
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests
    fun start() { _requests.tryEmit(Unit) }
}
