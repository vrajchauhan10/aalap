package com.music.aalap.ui.notification

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.music.aalap.MainActivity
import com.music.aalap.di.VoiceAsk

/** Quick-settings tile "Ask Aalap": opens Aalap listening for a song, artist or mood. */
class VoiceTileService : TileService() {
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).setAction(VoiceAsk.ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
