package com.music.aalap.audio

import android.content.Context

/** Whether the phone lets music play on its low-power audio chip (offload). */
object LowPowerAudio {
    /**
     * True when it doesn't, for the music Aalap plays (AAC, 44.1 kHz stereo). Samsung phones
     * refuse it while Dolby Atmos is on. Android 10+ only; older phones answer false.
     */
    fun blocked(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
        return runCatching {
            val format = android.media.AudioFormat.Builder()
                .setEncoding(android.media.AudioFormat.ENCODING_AAC_LC)
                .setSampleRate(44_100)
                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            !android.media.AudioManager.isOffloadedPlaybackSupported(format, attrs)
        }.getOrDefault(false)
    }
}
