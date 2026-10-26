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
 * goes away at once. Opened by a held volume key ([AalapKeyService]), or by its own icon if
 * switched on in Profile → Voice (which the side key's double press can open).
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
 * Which button asks Aalap: hold volume up, hold volume down, hold either (the default), or a
 * button of your own phone (an AI, action or assistant key: pressed once, it's learned).
 * Kept on the phone; read by [AalapKeyService].
 */
object AskKeys {
    enum class Trigger(val label: String) { EITHER("Either volume key"), UP("Volume up"), DOWN("Volume down"), OWN("My own button") }

    private fun p(c: android.content.Context) = c.getSharedPreferences("ask_keys", android.content.Context.MODE_PRIVATE)
    fun trigger(c: android.content.Context): Trigger = runCatching { Trigger.valueOf(p(c).getString("trigger", null) ?: "EITHER") }.getOrDefault(Trigger.EITHER)
    fun setTrigger(c: android.content.Context, t: Trigger) = p(c).edit().putString("trigger", t.name).apply()

    /** The learned button (a KeyEvent code), 0 = none yet. */
    fun ownKey(c: android.content.Context): Int = p(c).getInt("own_key", 0)
    fun ownKeyName(c: android.content.Context): String =
        ownKey(c).takeIf { it != 0 }?.let { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { ch -> ch.uppercase() } }.orEmpty()

    /** Waiting for the next button press to learn it (set from Profile → Voice). */
    @Volatile var learning = false
    val learned = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun learn(c: android.content.Context, code: Int) {
        p(c).edit().putInt("own_key", code).putString("trigger", Trigger.OWN.name).apply()
        learning = false
        learned.value = code
    }
}

/**
 * Ask Aalap from anywhere, the way that suits your phone (see [AskKeys]): hold a volume key
 * (a quick press still changes the volume), press a button of your own, or tap Android's
 * Accessibility button, a small floating icon you can drag anywhere on screen. Switched on in
 * Android's Accessibility settings; only those buttons are looked at, nothing on screen is read.
 */
class AalapKeyService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var held = 0
    private var asked = false
    private val longPress = Runnable { asked = true; ask() }
    private var floating: android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback? = null

    private fun ask() = runCatching {
        startActivity(Intent(this, AskAalapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // The floating Accessibility button (when the user picks it as this shortcut): a tap asks.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val cb = object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController) { ask() }
            }
            accessibilityButtonController.registerAccessibilityButtonCallback(cb, main)
            floating = cb
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) floating?.let { accessibilityButtonController.unregisterAccessibilityButtonCallback(it) }
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        // Profile → Voice → "My own button": the next button pressed is the one.
        if (AskKeys.learning && event.action == KeyEvent.ACTION_UP && code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_HOME) {
            AskKeys.learn(this, code); return true
        }
        val audio = getSystemService(AudioManager::class.java)
        // In a call the buttons are the call's.
        if (held == 0 && audio.mode != AudioManager.MODE_NORMAL) return false
        val trigger = AskKeys.trigger(this)
        if (trigger == AskKeys.Trigger.OWN) {
            // A button of its own: one press asks (it does nothing else for Aalap to keep).
            if (code != AskKeys.ownKey(this) || code == 0) return false
            if (event.action == KeyEvent.ACTION_UP) ask()
            return true
        }
        val ours = when (trigger) {
            AskKeys.Trigger.UP -> code == KeyEvent.KEYCODE_VOLUME_UP
            AskKeys.Trigger.DOWN -> code == KeyEvent.KEYCODE_VOLUME_DOWN
            else -> code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
        }
        if (!ours) return false
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

        /** True when the user has switched the shortcut on in Accessibility settings. */
        fun isOn(c: android.content.Context): Boolean {
            val list = android.provider.Settings.Secure.getString(c.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return list.split(':').any { it.equals("${c.packageName}/${AalapKeyService::class.java.name}", true) }
        }
    }
}

/** The optional "Ask Aalap" icon in the app drawer (an alias, off unless switched on). */
object AskAalapIcon {
    private fun component(c: android.content.Context) = android.content.ComponentName(c.packageName, "com.music.aalap.AskAalapIcon")

    fun isOn(c: android.content.Context) =
        c.packageManager.getComponentEnabledSetting(component(c)) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun set(c: android.content.Context, on: Boolean) = c.packageManager.setComponentEnabledSetting(
        component(c),
        if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        android.content.pm.PackageManager.DONT_KILL_APP,
    )
}
