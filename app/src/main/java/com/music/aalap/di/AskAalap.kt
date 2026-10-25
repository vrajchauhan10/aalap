package com.music.aalap.di

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * "Ask Aalap" with no screen of its own: brings up the voice bar over whatever is showing and
 * goes away at once. Opened by a held volume key ([AalapKeyService]) or the side key
 * (Settings → Advanced features → Side button → Double press → Open app → Ask Aalap).
 * Being on screen for that moment is what lets Android hand Aalap the microphone.
 */
class AskAalapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HeyAalap.ask(this)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}

/**
 * Hold volume up or down for a moment to ask Aalap for a song, from anywhere. A quick press
 * still changes the volume as always. Switched on in Android's Accessibility settings (only
 * the two volume keys are looked at; nothing on screen is read).
 */
class AalapKeyService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var held = 0
    private var asked = false
    private val longPress = Runnable {
        asked = true
        runCatching {
            startActivity(Intent(this, AskAalapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val audio = getSystemService(AudioManager::class.java)
        // In a call the volume keys are the call's.
        if (held == 0 && audio.mode != AudioManager.MODE_NORMAL) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                held = code; asked = false
                main.removeCallbacks(longPress); main.postDelayed(longPress, HOLD_MS)
            }
            KeyEvent.ACTION_UP -> {
                main.removeCallbacks(longPress)
                // A quick press: the volume change Android would have made.
                if (!asked && held == code) audio.adjustSuggestedStreamVolume(
                    if (code == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI,
                )
                held = 0
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        private const val HOLD_MS = 600L

        /** True when the user has switched the volume-key shortcut on in Accessibility settings. */
        fun isOn(c: android.content.Context): Boolean {
            val list = android.provider.Settings.Secure.getString(c.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return list.split(':').any { it.equals("${c.packageName}/${AalapKeyService::class.java.name}", true) }
        }
    }
}
